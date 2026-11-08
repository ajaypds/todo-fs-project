package realtime

import (
	"encoding/json"
	"math/rand"
	"net/http"
	"strings"
	"time"

	"github.com/gorilla/websocket"
)

var upgrader = websocket.Upgrader{
	ReadBufferSize:  4096,
	WriteBufferSize: 4096,
	CheckOrigin:     func(r *http.Request) bool { return true }, // setAllowedOriginPatterns("*")
	Subprotocols:    []string{"v12.stomp", "v11.stomp", "v10.stomp"},
}

// RawHandler serves STOMP frames as plain WebSocket text messages (/ws/websocket), used by the Android app.
func (h *Hub) RawHandler(w http.ResponseWriter, r *http.Request) {
	ws, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		return
	}
	h.serve(ws, false)
}

// SockJSHandler serves the SockJS WebSocket transport (/ws/{server}/{session}/websocket).
func (h *Hub) SockJSHandler(w http.ResponseWriter, r *http.Request) {
	ws, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		return
	}
	h.serve(ws, true)
}

// SockJSInfo answers /ws/info like Spring's SockJS support.
func SockJSInfo(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json;charset=UTF-8")
	w.Header().Set("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
	_ = json.NewEncoder(w).Encode(map[string]any{
		"entropy":       rand.Int31() - (1 << 30),
		"origins":       []string{"*:*"},
		"cookie_needed": true,
		"websocket":     true,
	})
}

func (h *Hub) serve(ws *websocket.Conn, sockjs bool) {
	conn := h.NewConn()
	defer h.Close(conn)
	defer ws.Close()

	write := func(text string) error {
		_ = ws.SetWriteDeadline(time.Now().Add(15 * time.Second))
		return ws.WriteMessage(websocket.TextMessage, []byte(text))
	}
	if sockjs {
		if write("o") != nil {
			return
		}
	}

	// Writer: delivers frames and SockJS heart-beats until the connection closes.
	stop := make(chan struct{})
	go func() {
		defer close(stop)
		beat := time.NewTicker(25 * time.Second)
		defer beat.Stop()
		for {
			select {
			case frame := <-conn.Out():
				msg := frame
				if sockjs {
					b, _ := json.Marshal([]string{frame})
					msg = "a" + string(b)
				}
				if write(msg) != nil {
					return
				}
			case <-beat.C:
				if sockjs && write("h") != nil {
					return
				}
				if !sockjs {
					_ = ws.SetWriteDeadline(time.Now().Add(15 * time.Second))
					if ws.WriteMessage(websocket.PingMessage, nil) != nil {
						return
					}
				}
			case <-conn.Done():
				// flush frames queued before the close (e.g. an ERROR frame)
				for {
					select {
					case frame := <-conn.Out():
						msg := frame
						if sockjs {
							b, _ := json.Marshal([]string{frame})
							msg = "a" + string(b)
						}
						_ = write(msg)
					default:
						if sockjs {
							_ = write(`c[3000,"Go away!"]`)
						}
						_ = ws.WriteControl(websocket.CloseMessage,
							websocket.FormatCloseMessage(websocket.CloseNormalClosure, ""), time.Now().Add(time.Second))
						return
					}
				}
			}
		}
	}()

	ws.SetReadLimit(1 << 20)
	for {
		_, data, err := ws.ReadMessage()
		if err != nil {
			break
		}
		text := string(data)
		if sockjs {
			var msgs []string
			if json.Unmarshal(data, &msgs) != nil {
				var one string
				if json.Unmarshal(data, &one) != nil {
					continue
				}
				msgs = []string{one}
			}
			text = strings.Join(msgs, "\x00")
		}
		if !h.Handle(conn, text) {
			conn.shutdown()
			break
		}
	}
	conn.shutdown()
	<-stop
}
