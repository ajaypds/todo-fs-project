// Package mcp implements the built-in Model Context Protocol server: the same 16 TodoFlow tools,
// transports and discovery endpoints the standalone Node server offers, served by the backend itself.
package mcp

import (
	"context"
	"crypto/rand"
	"fmt"
	"io"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/gin-gonic/gin"
)

type streamSession struct {
	key     string
	touched time.Time
	closed  chan struct{}
	stream  bool // a standalone GET stream is attached
}

type sseSession struct {
	key string
	out chan string
}

type mount struct {
	eng *engine

	mu        sync.Mutex
	streamSes map[string]*streamSession
	sseSes    map[string]*sseSession
}

// Mount registers the MCP routes on the engine. api returns the handler used for in-process REST calls.
func Mount(r *gin.Engine, api func() http.Handler) {
	m := &mount{eng: newEngine(api), streamSes: map[string]*streamSession{}, sseSes: map[string]*sseSession{}}
	go m.janitor()

	for _, p := range []string{"/mcp", "/mcp/streamable", "/streamable"} {
		r.Any(p, m.streamable)
	}
	for _, p := range []string{"/sse", "/mcp/sse"} {
		r.GET(p, m.sseConnect)
	}
	for _, p := range []string{"/message", "/mcp/message"} {
		r.POST(p, m.sseMessage)
	}
	for _, p := range []string{"/health", "/mcp/health"} {
		r.GET(p, m.health)
		r.HEAD(p, m.health)
	}
	for _, p := range []string{"/", "/info", "/mcp/info"} {
		r.GET(p, m.info)
		r.HEAD(p, m.info)
	}
}

func newID() string {
	b := make([]byte, 16)
	_, _ = rand.Read(b)
	b[6] = b[6]&0x0f | 0x40
	b[8] = b[8]&0x3f | 0x80
	return fmt.Sprintf("%x-%x-%x-%x-%x", b[0:4], b[4:6], b[6:8], b[8:10], b[10:])
}

// janitor drops streamable sessions that have been idle for a day (the Node server never did; this only
// keeps memory bounded on long-lived instances).
func (m *mount) janitor() {
	for range time.Tick(time.Hour) {
		m.mu.Lock()
		for id, s := range m.streamSes {
			if time.Since(s.touched) > 24*time.Hour {
				close(s.closed)
				delete(m.streamSes, id)
			}
		}
		m.mu.Unlock()
	}
}

// ---- helpers ---------------------------------------------------------------------------------------------

func writeBody(c *gin.Context, status int, ctype string, body string) {
	if ctype != "" {
		c.Writer.Header().Set("Content-Type", ctype)
	}
	c.Writer.WriteHeader(status)
	_, _ = io.WriteString(c.Writer, body)
}

// expressJSON mimics res.json().
func expressJSON(c *gin.Context, status int, v any) {
	writeBody(c, status, "application/json; charset=utf-8", Stringify(v, ""))
}

// sdkJSON mimics the MCP SDK's own JSON responses.
func sdkJSON(c *gin.Context, status int, v any) {
	writeBody(c, status, "application/json", Stringify(v, ""))
}

func wrapperBase(c *gin.Context) string {
	scheme := c.GetHeader("X-Forwarded-Proto")
	if scheme == "" {
		scheme = "http"
		if c.Request.TLS != nil {
			scheme = "https"
		}
	}
	host := c.Request.Host
	if h := c.GetHeader("Host"); h != "" {
		host = h
	}
	return scheme + "://" + host
}

// extractKey mirrors the Node helper: X-API-Key, ?apiKey=, ?key=, then the Authorization header
// (with or without the "Bearer " prefix).
func extractKey(c *gin.Context) string {
	if k := c.GetHeader("X-Api-Key"); k != "" {
		return k
	}
	if k := c.Query("apiKey"); k != "" {
		return k
	}
	if k := c.Query("key"); k != "" {
		return k
	}
	if auth := c.GetHeader("Authorization"); auth != "" {
		if strings.HasPrefix(auth, "Bearer ") {
			return strings.TrimSpace(auth[7:])
		}
		return strings.TrimSpace(auth)
	}
	return ""
}

func unauthorized(c *gin.Context, body any) {
	c.Writer.Header().Set("WWW-Authenticate", fmt.Sprintf(
		`Bearer realm="todoflow", error="invalid_token", error_description="Missing Bearer access token or API key", resource_metadata="%s/.well-known/oauth-protected-resource"`,
		wrapperBase(c)))
	expressJSON(c, http.StatusUnauthorized, body)
}

func (m *mount) exposeHeaders(c *gin.Context) {
	if c.GetHeader("Origin") != "" {
		c.Writer.Header().Add("Access-Control-Expose-Headers", "Mcp-Session-Id")
	}
}

// ---- info & health -------------------------------------------------------------------------------------

func (m *mount) health(c *gin.Context) {
	m.mu.Lock()
	sse, streamable := len(m.sseSes), len(m.streamSes)
	m.mu.Unlock()
	expressJSON(c, http.StatusOK, Obj{
		{"status", "UP"},
		{"activeSessions", Obj{{"sse", sse}, {"streamableHttp", streamable}}},
		{"transports", []any{"sse", "streamableHttp", "stdio"}},
		{"timestamp", time.Now().UTC().Format("2006-01-02T15:04:05.000Z")},
	})
}

func (m *mount) info(c *gin.Context) {
	expressJSON(c, http.StatusOK, Obj{
		{"name", "TodoFlow Remote MCP Server"},
		{"version", "1.0.0"},
		{"status", "running"},
		{"transports", Obj{
			{"sse", Obj{
				{"handshake", "/sse (or /mcp/sse)"},
				{"messages", "/message (or /mcp/message)"},
				{"description", "Server-Sent Events transport for ChatGPT and Claude Mobile"},
			}},
			{"streamableHttp", Obj{
				{"endpoint", "/mcp (or /streamable)"},
				{"description", "Unified Streamable HTTP transport (POST/GET/DELETE) for MCP v1.6+ clients"},
			}},
		}},
		{"health", "/health"},
	})
}

// ---- Streamable HTTP -----------------------------------------------------------------------------------

func (m *mount) streamable(c *gin.Context) {
	r := c.Request
	m.exposeHeaders(c)
	if r.Method == http.MethodOptions {
		c.Writer.Header().Set("Access-Control-Allow-Methods", "GET,HEAD,PUT,PATCH,POST,DELETE")
		writeBody(c, http.StatusNoContent, "", "")
		return
	}
	sid := c.GetHeader("Mcp-Session-Id")
	if sid == "" {
		sid = c.Query("sessionId")
	}
	if r.Method == http.MethodGet && c.GetHeader("Mcp-Session-Id") == "" && c.Query("sessionId") == "" &&
		!strings.Contains(c.GetHeader("Accept"), "text/event-stream") {
		expressJSON(c, http.StatusOK, Obj{
			{"name", "TodoFlow Streamable HTTP MCP Endpoint"},
			{"version", "1.0.0"},
			{"endpoint", "/mcp"},
			{"supportedMethods", []any{"POST", "GET", "DELETE"}},
			{"transport", "streamableHttp"},
		})
		return
	}

	var body any = Undef{}
	if r.Method == http.MethodPost && isJSONContentType(c.GetHeader("Content-Type")) {
		raw, _ := io.ReadAll(r.Body)
		if strings.TrimSpace(string(raw)) == "" {
			body = Obj{}
		} else {
			parsed, err := ParseJSON(raw)
			if err != nil {
				writeBody(c, http.StatusBadRequest, "text/html; charset=utf-8", expressErrorPage("SyntaxError: "+err.Error()))
				return
			}
			body = parsed
		}
	}

	var sess *streamSession
	if sid != "" {
		m.mu.Lock()
		sess = m.streamSes[sid]
		if sess != nil {
			sess.touched = time.Now()
		}
		m.mu.Unlock()
		if sess == nil {
			expressJSON(c, http.StatusNotFound, Obj{{"error", fmt.Sprintf("Streamable HTTP session '%s' not found or expired", sid)}})
			return
		}
	} else {
		key := extractKey(c)
		if key == "" {
			var id any = nil
			if o, ok := body.(Obj); ok {
				if v := o.Val("id"); truthy(v) {
					id = v
				}
			}
			unauthorized(c, Obj{{"jsonrpc", "2.0"}, {"error", Obj{{"code", -32001}, {"message", "Unauthorized: Missing Authorization Bearer token or X-API-Key header"}}}, {"id", id}})
			return
		}
		m.newSession(c, key, body)
		return
	}
	m.existing(c, sess, sid, body)
}

func isJSONContentType(ct string) bool {
	ct = strings.ToLower(ct)
	return strings.HasPrefix(ct, "application/json") || strings.Contains(ct, "+json")
}

func expressErrorPage(msg string) string {
	esc := strings.NewReplacer("&", "&amp;", "<", "&lt;", ">", "&gt;", `"`, "&quot;", "'", "&#39;").Replace(msg)
	return "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n<title>Error</title>\n</head>\n<body>\n<pre>" + esc + "</pre>\n</body>\n</html>\n"
}

func (m *mount) acceptOK(c *gin.Context) bool {
	accept := c.GetHeader("Accept")
	if c.Request.Method == http.MethodPost {
		if !(strings.Contains(accept, "application/json") && strings.Contains(accept, "text/event-stream")) {
			sdkJSON(c, http.StatusNotAcceptable, transportError(-32000, "Not Acceptable: Client must accept both application/json and text/event-stream"))
			return false
		}
		return true
	}
	if c.Request.Method == http.MethodGet && !strings.Contains(accept, "text/event-stream") {
		sdkJSON(c, http.StatusNotAcceptable, transportError(-32000, "Not Acceptable: Client must accept text/event-stream"))
		return false
	}
	return true
}

// parseMessages validates a POST body (single message or batch).
func parseMessages(body any) (msgs []any, batch bool, ok bool) {
	if arr, isArr := body.([]any); isArr {
		for _, v := range arr {
			if classify(v) == kindInvalid {
				return nil, true, false
			}
		}
		return arr, true, true
	}
	if classify(body) == kindInvalid {
		return nil, false, false
	}
	return []any{body}, false, true
}

func (m *mount) newSession(c *gin.Context, key string, body any) {
	switch c.Request.Method {
	case http.MethodPost:
		if !m.acceptOK(c) {
			return
		}
		if !isJSONContentType(c.GetHeader("Content-Type")) {
			sdkJSON(c, http.StatusUnsupportedMediaType, transportError(-32000, "Unsupported Media Type: Content-Type must be application/json"))
			return
		}
		msgs, batch, ok := parseMessages(body)
		if !ok {
			sdkJSON(c, http.StatusBadRequest, transportError(-32700, "Parse error: Invalid JSON-RPC message"))
			return
		}
		inits := 0
		for _, v := range msgs {
			if isInitialize(v) {
				inits++
			}
		}
		if inits == 0 {
			sdkJSON(c, http.StatusBadRequest, transportError(-32000, "Bad Request: Server not initialized"))
			return
		}
		if inits > 1 || (batch && len(msgs) > 1 && inits > 0) {
			sdkJSON(c, http.StatusBadRequest, transportError(-32600, "Invalid Request: Only one initialization request is allowed"))
			return
		}
		id := newID()
		sess := &streamSession{key: key, touched: time.Now(), closed: make(chan struct{})}
		m.mu.Lock()
		m.streamSes[id] = sess
		m.mu.Unlock()
		c.Writer.Header().Set("Mcp-Session-Id", id)
		m.respond(c, sess, msgs, batch)
	case http.MethodGet:
		if !m.acceptOK(c) {
			return
		}
		sdkJSON(c, http.StatusBadRequest, transportError(-32000, "Bad Request: Server not initialized"))
	case http.MethodDelete:
		sdkJSON(c, http.StatusBadRequest, transportError(-32000, "Bad Request: Server not initialized"))
	default:
		methodNotAllowed(c)
	}
}

func methodNotAllowed(c *gin.Context) {
	c.Writer.Header().Set("Allow", "GET, POST, DELETE")
	sdkJSON(c, http.StatusMethodNotAllowed, transportError(-32000, "Method not allowed."))
}

func (m *mount) existing(c *gin.Context, sess *streamSession, sid string, body any) {
	switch c.Request.Method {
	case http.MethodPost, http.MethodGet, http.MethodDelete:
	default:
		methodNotAllowed(c)
		return
	}
	if c.Request.Method == http.MethodPost {
		if !m.acceptOK(c) {
			return
		}
		if !isJSONContentType(c.GetHeader("Content-Type")) {
			sdkJSON(c, http.StatusUnsupportedMediaType, transportError(-32000, "Unsupported Media Type: Content-Type must be application/json"))
			return
		}
	}
	if c.Request.Method == http.MethodGet && !m.acceptOK(c) {
		return
	}
	// The session exists; validate the protocol version header like the SDK does.
	if v := c.GetHeader("Mcp-Protocol-Version"); v != "" {
		okv := false
		for _, s := range supportedProtocols {
			if s == v {
				okv = true
			}
		}
		if !okv {
			sdkJSON(c, http.StatusBadRequest, transportError(-32000, fmt.Sprintf("Bad Request: Unsupported protocol version: %s (supported versions: %s)", v, strings.Join(supportedProtocols, ", "))))
			return
		}
	}
	switch c.Request.Method {
	case http.MethodDelete:
		m.mu.Lock()
		if cur := m.streamSes[sid]; cur != nil {
			close(cur.closed)
			delete(m.streamSes, sid)
		}
		m.mu.Unlock()
		c.Writer.WriteHeader(http.StatusOK)
	case http.MethodGet:
		m.openStream(c, sess, sid)
	case http.MethodPost:
		msgs, batch, ok := parseMessages(body)
		if !ok {
			sdkJSON(c, http.StatusBadRequest, transportError(-32700, "Parse error: Invalid JSON-RPC message"))
			return
		}
		for _, v := range msgs {
			if isInitialize(v) {
				sdkJSON(c, http.StatusBadRequest, transportError(-32600, "Invalid Request: Server already initialized"))
				return
			}
		}
		c.Writer.Header().Set("Mcp-Session-Id", sid)
		m.respond(c, sess, msgs, batch)
	}
}

// respond runs the messages and writes either 202 (nothing to answer) or the JSON response(s).
func (m *mount) respond(c *gin.Context, sess *streamSession, msgs []any, batch bool) {
	var responses []any
	for _, v := range msgs {
		if resp := m.eng.handle(c.Request.Context(), sess.key, v); resp != nil {
			responses = append(responses, resp)
		}
	}
	if len(responses) == 0 {
		c.Writer.Header().Del("Mcp-Session-Id")
		c.Writer.WriteHeader(http.StatusAccepted)
		return
	}
	if batch {
		sdkJSON(c, http.StatusOK, responses)
		return
	}
	sdkJSON(c, http.StatusOK, responses[0])
}

// openStream attaches the optional standalone SSE stream (server → client notifications). The server
// has nothing to push, so it simply stays open until the client leaves or the session is closed.
func (m *mount) openStream(c *gin.Context, sess *streamSession, sid string) {
	m.mu.Lock()
	if sess.stream {
		m.mu.Unlock()
		sdkJSON(c, http.StatusConflict, transportError(-32000, "Conflict: Only one SSE stream is allowed per session"))
		return
	}
	sess.stream = true
	m.mu.Unlock()
	defer func() {
		m.mu.Lock()
		sess.stream = false
		m.mu.Unlock()
	}()
	h := c.Writer.Header()
	h.Set("Content-Type", "text/event-stream")
	h.Set("Cache-Control", "no-cache, no-transform")
	h.Set("Connection", "keep-alive")
	h.Set("Mcp-Session-Id", sid)
	h.Set("X-Accel-Buffering", "no")
	c.Writer.WriteHeader(http.StatusOK)
	c.Writer.Flush()
	tick := time.NewTicker(25 * time.Second)
	defer tick.Stop()
	for {
		select {
		case <-c.Request.Context().Done():
			return
		case <-sess.closed:
			return
		case <-tick.C:
			_, _ = io.WriteString(c.Writer, ": keepalive\n\n")
			c.Writer.Flush()
		}
	}
}

// ---- legacy HTTP+SSE transport ---------------------------------------------------------------------------

func (m *mount) sseConnect(c *gin.Context) {
	m.exposeHeaders(c)
	key := extractKey(c)
	if key == "" {
		unauthorized(c, Obj{{"error", "Unauthorized: Missing Authorization Bearer token or X-API-Key header"}})
		return
	}
	prefix := strings.TrimSuffix(c.GetHeader("X-Forwarded-Prefix"), "/")
	endpoint := "/message"
	if prefix == "/mcp" || strings.HasPrefix(c.Request.URL.Path, "/mcp") {
		endpoint = "/mcp/message"
	}
	id := newID()
	sess := &sseSession{key: key, out: make(chan string, 64)}
	m.mu.Lock()
	m.sseSes[id] = sess
	m.mu.Unlock()
	defer func() {
		m.mu.Lock()
		delete(m.sseSes, id)
		m.mu.Unlock()
	}()

	h := c.Writer.Header()
	h.Set("Content-Type", "text/event-stream")
	h.Set("Cache-Control", "no-cache, no-transform")
	h.Set("Connection", "keep-alive")
	h.Set("X-Accel-Buffering", "no")
	c.Writer.WriteHeader(http.StatusOK)
	fmt.Fprintf(c.Writer, "event: endpoint\ndata: %s?sessionId=%s\n\n", endpoint, id)
	c.Writer.Flush()

	tick := time.NewTicker(25 * time.Second)
	defer tick.Stop()
	for {
		select {
		case <-c.Request.Context().Done():
			return
		case msg := <-sess.out:
			fmt.Fprintf(c.Writer, "event: message\ndata: %s\n\n", msg)
			c.Writer.Flush()
		case <-tick.C:
			_, _ = io.WriteString(c.Writer, ": keepalive\n\n")
			c.Writer.Flush()
		}
	}
}

func (m *mount) sseMessage(c *gin.Context) {
	m.exposeHeaders(c)
	sid := c.Query("sessionId")
	if sid == "" {
		expressJSON(c, http.StatusBadRequest, Obj{{"error", "Missing sessionId query parameter"}})
		return
	}
	m.mu.Lock()
	sess := m.sseSes[sid]
	m.mu.Unlock()
	if sess == nil {
		expressJSON(c, http.StatusNotFound, Obj{{"error", fmt.Sprintf("Session '%s' not found or expired", sid)}})
		return
	}
	ct := c.GetHeader("Content-Type")
	if !strings.HasPrefix(strings.ToLower(strings.TrimSpace(ct)), "application/json") {
		writeBody(c, http.StatusBadRequest, "text/plain", "Error: Unsupported content-type: "+strings.TrimSpace(strings.SplitN(ct, ";", 2)[0]))
		return
	}
	raw, _ := io.ReadAll(c.Request.Body)
	var body any = Obj{}
	if strings.TrimSpace(string(raw)) != "" {
		parsed, err := ParseJSON(raw)
		if err != nil {
			writeBody(c, http.StatusBadRequest, "text/html; charset=utf-8", expressErrorPage("SyntaxError: "+err.Error()))
			return
		}
		body = parsed
	}
	if classify(body) == kindInvalid {
		writeBody(c, http.StatusBadRequest, "text/plain", "Invalid message: [object Object]")
		return
	}
	writeBody(c, http.StatusAccepted, "", "Accepted")
	go func() {
		// The POST is already answered; the result travels over the SSE stream.
		if resp := m.eng.handle(context.Background(), sess.key, body); resp != nil {
			select {
			case sess.out <- Stringify(resp, ""):
			case <-time.After(10 * time.Second):
			}
		}
	}()
}
