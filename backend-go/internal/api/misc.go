package api

import (
	"context"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/ajaypds/todo-fs-project/backend-go/internal/realtime"
	"github.com/gin-gonic/gin"
)

func (s *Server) registerMisc(r *gin.Engine, v1 *gin.RouterGroup) {
	// Authenticated smoke test
	v1.GET("/test", func(c *gin.Context) {
		c.Writer.Header().Set("Content-Type", "text/plain;charset=UTF-8")
		c.Writer.WriteHeader(http.StatusOK)
		_, _ = c.Writer.Write([]byte("Authenticated!"))
	})

	// Presence of the current user (live WebSocket sessions)
	v1.GET("/presence", func(c *gin.Context) {
		u := s.me(c)
		n := s.Hub.ActiveSessions(u.ID)
		writeJSON(c, http.StatusOK, struct {
			UserID         string `json:"userId"`
			Username       string `json:"username"`
			ActiveSessions int    `json:"activeSessions"`
			Online         bool   `json:"online"`
		}{u.ID.String(), u.Email, n, n > 0})
	})

	// Spring Boot Actuator: only the health endpoint is exposed
	r.GET("/actuator", func(c *gin.Context) {
		base := requestBase(c) + "/actuator"
		c.Writer.Header().Set("Content-Type", "application/vnd.spring-boot.actuator.v3+json")
		c.Writer.WriteHeader(http.StatusOK)
		_, _ = c.Writer.Write(marshal(gin.H{"_links": map[string]any{
			"self":        map[string]any{"href": base, "templated": false},
			"health":      map[string]any{"href": base + "/health", "templated": false},
			"health-path": map[string]any{"href": base + "/health/{*path}", "templated": true},
		}}))
	})
	r.GET("/actuator/health", func(c *gin.Context) {
		ctx, cancel := context.WithTimeout(c.Request.Context(), 3*time.Second)
		defer cancel()
		status, code := "UP", http.StatusOK
		if err := s.Store.Ping(ctx); err != nil {
			status, code = "DOWN", http.StatusServiceUnavailable
		}
		c.Writer.Header().Set("Content-Type", "application/vnd.spring-boot.actuator.v3+json")
		c.Writer.WriteHeader(code)
		_, _ = c.Writer.Write(marshal(map[string]string{"status": status}))
	})

	// WebSocket / STOMP (Spring registers /ws with SockJS enabled)
	r.GET("/ws", func(c *gin.Context) {
		c.Writer.Header().Set("Content-Type", "text/plain;charset=UTF-8")
		c.Writer.WriteHeader(http.StatusOK)
		_, _ = c.Writer.Write([]byte("Welcome to SockJS!\n"))
	})
	r.GET("/ws/info", func(c *gin.Context) { realtime.SockJSInfo(c.Writer, c.Request) })
	r.GET("/ws/websocket", func(c *gin.Context) { s.Hub.RawHandler(c.Writer, c.Request) })
	r.GET("/ws/:server/:session/websocket", func(c *gin.Context) { s.Hub.SockJSHandler(c.Writer, c.Request) })
}

// requestBase is scheme://host honouring X-Forwarded-Proto, like the Java discovery endpoints.
func requestBase(c *gin.Context) string {
	scheme := c.GetHeader("X-Forwarded-Proto")
	if scheme == "" {
		scheme = "http"
		if c.Request.TLS != nil {
			scheme = "https"
		}
	}
	host := c.GetHeader("Host")
	if host == "" {
		host = c.Request.Host
	}
	return fmt.Sprintf("%s://%s", scheme, strings.TrimSuffix(host, "/"))
}
