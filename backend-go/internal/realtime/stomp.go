// Package realtime implements the STOMP-over-WebSocket broker (plain and SockJS framing) that Spring's
// simple broker provides in the Java backend: topics /topic/users/{id}/{tasks,activities,presence} and
// /topic/tasks/{id}/comments.
package realtime

import (
	"strings"
)

// Frame is one STOMP frame.
type Frame struct {
	Command string
	Headers [][2]string // ordered
	Body    string
}

// Header returns the first value for a header name.
func (f *Frame) Header(name string) string {
	for _, h := range f.Headers {
		if h[0] == name {
			return h[1]
		}
	}
	return ""
}

var unescaper = strings.NewReplacer(`\n`, "\n", `\r`, "\r", `\c`, ":", `\\`, `\`)
var escaper = strings.NewReplacer("\n", `\n`, "\r", `\r`, ":", `\c`, `\`, `\\`)

// ParseFrames splits a WebSocket text message into frames. Heart-beat newlines and garbage yield none.
func ParseFrames(text string) []*Frame {
	var out []*Frame
	for _, chunk := range strings.Split(text, "\x00") {
		chunk = strings.TrimLeft(chunk, "\r\n")
		if strings.TrimSpace(chunk) == "" {
			continue
		}
		head, body := chunk, ""
		if i := strings.Index(chunk, "\n\n"); i >= 0 {
			head, body = chunk[:i], chunk[i+2:]
		} else if i := strings.Index(chunk, "\r\n\r\n"); i >= 0 {
			head, body = chunk[:i], chunk[i+4:]
		}
		lines := strings.Split(head, "\n")
		cmd := strings.TrimSpace(lines[0])
		if cmd == "" {
			continue
		}
		f := &Frame{Command: cmd, Body: body}
		raw := cmd == "CONNECT" || cmd == "STOMP" || cmd == "CONNECTED"
		for _, line := range lines[1:] {
			line = strings.TrimSuffix(line, "\r")
			colon := strings.Index(line, ":")
			if colon <= 0 {
				continue
			}
			k, v := line[:colon], line[colon+1:]
			if !raw {
				k, v = unescaper.Replace(k), unescaper.Replace(v)
			}
			f.Headers = append(f.Headers, [2]string{k, v})
		}
		out = append(out, f)
	}
	return out
}

// String serialises a frame (including the trailing NUL).
func (f *Frame) String() string {
	var b strings.Builder
	b.WriteString(f.Command)
	b.WriteString("\n")
	raw := f.Command == "CONNECTED"
	for _, h := range f.Headers {
		k, v := h[0], h[1]
		if !raw {
			k, v = escaper.Replace(k), escaper.Replace(v)
		}
		b.WriteString(k + ":" + v + "\n")
	}
	b.WriteString("\n")
	b.WriteString(f.Body)
	b.WriteString("\x00")
	return b.String()
}

func errorFrame(message string) *Frame {
	return &Frame{
		Command: "ERROR",
		Headers: [][2]string{{"message", message}, {"content-type", "text/plain;charset=UTF-8"}},
		Body:    message,
	}
}
