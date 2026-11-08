package realtime

import (
	"encoding/json"
	"fmt"
	"strings"
	"sync"
	"sync/atomic"

	"github.com/google/uuid"
)

// Authenticator validates a bearer token from the STOMP CONNECT frame.
type Authenticator func(token string) (userID uuid.UUID, email string, err error)

// PresenceEvent is published on /topic/users/{id}/presence.
type PresenceEvent struct {
	Type           string `json:"type"`
	Username       string `json:"username"`
	UserID         string `json:"userId"`
	ActiveSessions int    `json:"activeSessions"`
	Online         bool   `json:"online"`
}

// Conn is one live client connection (raw WebSocket or SockJS) handled by the hub.
type Conn struct {
	id     string
	out    chan string // serialised frames to deliver
	closed atomic.Bool
	done   chan struct{}

	mu       sync.Mutex
	subs     map[string]string // subscription id -> destination
	userID   *uuid.UUID
	email    string
	msgSeq   atomic.Int64
	protoErr atomic.Bool
}

// Hub routes published events to subscribed connections and tracks presence.
type Hub struct {
	mu       sync.RWMutex
	conns    map[string]*Conn
	bySub    map[string]map[*Conn]struct{} // destination -> connections
	presence map[uuid.UUID]map[string]struct{}
	auth     Authenticator
}

// NewHub creates a hub; auth validates CONNECT credentials.
func NewHub(auth Authenticator) *Hub {
	return &Hub{
		conns:    map[string]*Conn{},
		bySub:    map[string]map[*Conn]struct{}{},
		presence: map[uuid.UUID]map[string]struct{}{},
		auth:     auth,
	}
}

// NewConn registers a connection and returns it; call Close when the transport ends.
func (h *Hub) NewConn() *Conn {
	c := &Conn{
		id:   strings.ReplaceAll(uuid.NewString(), "-", "")[:8],
		out:  make(chan string, 256),
		done: make(chan struct{}),
		subs: map[string]string{},
	}
	h.mu.Lock()
	h.conns[c.id] = c
	h.mu.Unlock()
	return c
}

// Out is the channel of frames to write to the client.
func (c *Conn) Out() <-chan string { return c.out }

// Done is closed when the connection is shut down.
func (c *Conn) Done() <-chan struct{} { return c.done }

func (c *Conn) send(f *Frame) {
	if c.closed.Load() {
		return
	}
	select {
	case c.out <- f.String():
	default: // slow consumer: drop the connection rather than block publishers
		c.shutdown()
	}
}

// fail answers a rejected frame the way Spring does when a channel interceptor throws: an ERROR frame
// without body, followed by a protocol-error (1002) close.
func (c *Conn) fail(message string) {
	c.send(errorFrame(message))
	c.protoErr.Store(true)
}

func (c *Conn) shutdown() {
	if c.closed.CompareAndSwap(false, true) {
		close(c.done)
	}
}

// Close unregisters the connection, announces the disconnect for presence, and drops its subscriptions.
func (h *Hub) Close(c *Conn) {
	c.shutdown()
	h.mu.Lock()
	delete(h.conns, c.id)
	for _, dest := range c.subs {
		if set := h.bySub[dest]; set != nil {
			delete(set, c)
			if len(set) == 0 {
				delete(h.bySub, dest)
			}
		}
	}
	h.mu.Unlock()

	c.mu.Lock()
	uid, email := c.userID, c.email
	c.userID = nil
	c.mu.Unlock()
	if uid != nil {
		n := h.sessionDisconnected(*uid, c.id)
		h.Publish("/topic/users/"+uid.String()+"/presence", PresenceEvent{
			Type: "USER_DISCONNECTED", Username: email, UserID: uid.String(), ActiveSessions: n, Online: n > 0,
		})
	}
}

// ActiveSessions is the number of live authenticated sessions of a user.
func (h *Hub) ActiveSessions(userID uuid.UUID) int {
	h.mu.RLock()
	defer h.mu.RUnlock()
	return len(h.presence[userID])
}

func (h *Hub) sessionConnected(userID uuid.UUID, sessionID string) int {
	h.mu.Lock()
	defer h.mu.Unlock()
	set := h.presence[userID]
	if set == nil {
		set = map[string]struct{}{}
		h.presence[userID] = set
	}
	set[sessionID] = struct{}{}
	return len(set)
}

func (h *Hub) sessionDisconnected(userID uuid.UUID, sessionID string) int {
	h.mu.Lock()
	defer h.mu.Unlock()
	set := h.presence[userID]
	if set == nil {
		return 0
	}
	delete(set, sessionID)
	if len(set) == 0 {
		delete(h.presence, userID)
		return 0
	}
	return len(set)
}

// Publish sends a JSON payload to every subscriber of the destination.
func (h *Hub) Publish(destination string, payload any) {
	body, err := json.Marshal(payload)
	if err != nil {
		return
	}
	h.mu.RLock()
	targets := make([]*Conn, 0, len(h.bySub[destination]))
	for c := range h.bySub[destination] {
		targets = append(targets, c)
	}
	h.mu.RUnlock()
	for _, c := range targets {
		c.mu.Lock()
		var subIDs []string
		for id, dest := range c.subs {
			if dest == destination {
				subIDs = append(subIDs, id)
			}
		}
		c.mu.Unlock()
		for _, sid := range subIDs {
			c.send(&Frame{
				Command: "MESSAGE",
				Headers: [][2]string{
					{"destination", destination},
					{"content-type", "application/json"},
					{"subscription", sid},
					{"message-id", fmt.Sprintf("%s-%d", c.id, c.msgSeq.Add(1))},
					{"content-length", fmt.Sprint(len(body))},
				},
				Body: string(body),
			})
		}
	}
}

// Handle processes one text message received from a client. It returns false when the connection must close.
func (h *Hub) Handle(c *Conn, text string) bool {
	for _, f := range ParseFrames(text) {
		switch f.Command {
		case "CONNECT", "STOMP":
			if !h.onConnect(c, f) {
				return false
			}
		case "SUBSCRIBE":
			if !h.onSubscribe(c, f) {
				return false
			}
		case "UNSUBSCRIBE":
			h.onUnsubscribe(c, f)
		case "DISCONNECT":
			if r := f.Header("receipt"); r != "" {
				c.send(&Frame{Command: "RECEIPT", Headers: [][2]string{{"receipt-id", r}}})
			}
			h.dropSubscriptions(c) // the connection itself stays open until the client closes it
		case "SEND", "ACK", "NACK", "BEGIN", "COMMIT", "ABORT":
			// The Java backend defines no @MessageMapping handlers: client SEND frames are ignored.
			if r := f.Header("receipt"); r != "" {
				c.send(&Frame{Command: "RECEIPT", Headers: [][2]string{{"receipt-id", r}}})
			}
		default:
			c.fail("No enum constant org.springframework.messaging.simp.stomp.StompCommand." + f.Command)
			return false
		}
	}
	return true
}

func (h *Hub) onConnect(c *Conn, f *Frame) bool {
	if authHeader := f.Header("Authorization"); authHeader != "" || f.Header("authorization") != "" {
		if authHeader == "" {
			authHeader = f.Header("authorization")
		}
		if strings.HasPrefix(authHeader, "Bearer ") {
			uid, email, err := h.auth(authHeader[7:])
			if err != nil {
				c.fail(interceptorFailure)
				return false
			}
			c.mu.Lock()
			c.userID, c.email = &uid, email
			c.mu.Unlock()
			n := h.sessionConnected(uid, c.id)
			h.Publish("/topic/users/"+uid.String()+"/presence", PresenceEvent{
				Type: "USER_CONNECTED", Username: email, UserID: uid.String(), ActiveSessions: n, Online: true,
			})
		}
	}
	version := "1.2"
	if accept := f.Header("accept-version"); accept != "" {
		ok := false
		for _, v := range strings.Split(accept, ",") {
			if strings.TrimSpace(v) == "1.2" {
				ok = true
			}
		}
		if !ok {
			if strings.Contains(accept, "1.1") {
				version = "1.1"
			} else if strings.Contains(accept, "1.0") {
				version = "1.0"
			}
		}
	}
	headers := [][2]string{{"version", version}, {"heart-beat", "0,0"}}
	c.mu.Lock()
	if c.email != "" {
		headers = append(headers, [2]string{"user-name", c.email})
	}
	c.mu.Unlock()
	c.send(&Frame{Command: "CONNECTED", Headers: headers})
	return true
}

func (h *Hub) onSubscribe(c *Conn, f *Frame) bool {
	dest, id := f.Header("destination"), f.Header("id")
	if dest == "" || id == "" {
		return true // Spring silently drops a SUBSCRIBE that lacks its id/destination
	}
	// Users may only follow their own channels: /topic/users/{userId}/...
	if strings.HasPrefix(dest, "/topic/users/") {
		parts := strings.Split(dest, "/")
		if len(parts) >= 4 {
			c.mu.Lock()
			uid := c.userID
			c.mu.Unlock()
			if uid == nil || !strings.EqualFold(uid.String(), parts[3]) {
				c.fail(interceptorFailure)
				return false
			}
		}
	}
	h.mu.Lock()
	c.mu.Lock()
	c.subs[id] = dest
	c.mu.Unlock()
	set := h.bySub[dest]
	if set == nil {
		set = map[*Conn]struct{}{}
		h.bySub[dest] = set
	}
	set[c] = struct{}{}
	h.mu.Unlock()
	if r := f.Header("receipt"); r != "" {
		c.send(&Frame{Command: "RECEIPT", Headers: [][2]string{{"receipt-id", r}}})
	}
	return true
}

func (h *Hub) onUnsubscribe(c *Conn, f *Frame) {
	id := f.Header("id")
	c.mu.Lock()
	dest, ok := c.subs[id]
	delete(c.subs, id)
	still := false
	for _, d := range c.subs {
		if d == dest {
			still = true
		}
	}
	c.mu.Unlock()
	if ok && !still {
		h.mu.Lock()
		if set := h.bySub[dest]; set != nil {
			delete(set, c)
			if len(set) == 0 {
				delete(h.bySub, dest)
			}
		}
		h.mu.Unlock()
	}
}

const interceptorFailure = "Failed to send message to ExecutorSubscribableChannel[clientInboundChannel]"

// dropSubscriptions removes every subscription of the connection (what the broker does on DISCONNECT).
func (h *Hub) dropSubscriptions(c *Conn) {
	h.mu.Lock()
	defer h.mu.Unlock()
	c.mu.Lock()
	defer c.mu.Unlock()
	for id, dest := range c.subs {
		if set := h.bySub[dest]; set != nil {
			delete(set, c)
			if len(set) == 0 {
				delete(h.bySub, dest)
			}
		}
		delete(c.subs, id)
	}
}
