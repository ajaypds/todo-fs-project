// Package api contains the HTTP layer: routing, security, and all REST handlers. Behaviour (status codes,
// JSON shapes, error messages and quirks) follows the Spring Boot backend this server replaces.
package api

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"strings"
	"time"

	"github.com/ajaypds/todo-fs-project/backend-go/internal/ai"
	"github.com/ajaypds/todo-fs-project/backend-go/internal/apperr"
	"github.com/ajaypds/todo-fs-project/backend-go/internal/config"
	"github.com/ajaypds/todo-fs-project/backend-go/internal/jsonx"
	"github.com/ajaypds/todo-fs-project/backend-go/internal/realtime"
	"github.com/ajaypds/todo-fs-project/backend-go/internal/security"
	"github.com/ajaypds/todo-fs-project/backend-go/internal/store"
	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
)

// Server wires dependencies together.
type Server struct {
	Cfg   *config.Config
	Store *store.Store
	JWT   *security.JWT
	Hub   *realtime.Hub
	AI    *ai.Client
}

// publicPrefixes are reachable without authentication (SecurityConfig.permitAll).
var publicPrefixes = []string{
	"/api/v1/auth", "/oauth2", "/oauth", "/api/v1/oauth2", "/api/v1/oauth", "/.well-known", "/actuator", "/ws",
	// Built-in MCP server: these routes authenticate callers themselves (Bearer token or API key).
	"/mcp", "/streamable", "/sse", "/message", "/health", "/info",
}

func isPublic(path string) bool {
	for _, p := range publicPrefixes {
		if path == p || strings.HasPrefix(path, p+"/") {
			return true
		}
	}
	return false
}

const userKey = "todoflow.user"

// New builds the router. Extra routes (e.g. the built-in MCP server) can be mounted by the caller.
func (s *Server) Router(extra ...func(*gin.Engine)) *gin.Engine {
	gin.SetMode(gin.ReleaseMode)
	r := gin.New()
	r.RedirectTrailingSlash = false
	r.RedirectFixedPath = false
	r.HandleMethodNotAllowed = false
	r.UseRawPath = false
	r.Use(s.recoverer(), s.securityMiddleware())

	// Anything that doesn't match a route: the Java backend answers 500 {"message":"Something went wrong"}
	// for authenticated callers (NoResourceFoundException hits the catch-all handler).
	var routes []gin.RouteInfo
	r.NoRoute(func(c *gin.Context) {
		if c.Request.Method == http.MethodOptions {
			if allow := allowedMethods(routes, c.Request.URL.Path); allow != "" { // Spring answers OPTIONS on known paths
				c.Writer.Header().Set("Allow", allow)
				writeStatus(c, http.StatusOK)
				return
			}
		}
		s.fail(c, apperr.Generic())
	})

	v1 := r.Group("/api/v1")
	s.registerAuth(v1)
	s.registerUsers(v1)
	s.registerTasks(v1)
	s.registerProjects(v1)
	s.registerLabels(v1)
	s.registerComments(v1)
	s.registerActivities(v1)
	s.registerWorkspaces(v1)
	s.registerAPIKeys(v1)
	s.registerAI(v1)
	s.registerMisc(r, v1)
	s.registerOAuth(r)

	// Spring serves HEAD for every GET mapping automatically.
	for _, rt := range r.Routes() {
		if rt.Method == http.MethodGet {
			r.HEAD(rt.Path, rt.HandlerFunc)
		}
	}
	routes = r.Routes()

	for _, fn := range extra {
		fn(r)
	}
	return r
}

// allowedMethods lists the methods registered for a request path (matching :param and *wildcard segments).
func allowedMethods(routes []gin.RouteInfo, path string) string {
	seen := map[string]bool{}
	for _, rt := range routes {
		if rt.Method != http.MethodHead && matchesPattern(rt.Path, path) {
			seen[rt.Method] = true
		}
	}
	if len(seen) == 0 {
		return ""
	}
	if seen[http.MethodGet] {
		seen[http.MethodHead] = true
	}
	seen[http.MethodOptions] = true
	var out []string
	for _, m := range []string{"GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"} {
		if seen[m] {
			out = append(out, m)
		}
	}
	return strings.Join(out, ",")
}

func matchesPattern(pattern, path string) bool {
	ps, xs := strings.Split(strings.Trim(pattern, "/"), "/"), strings.Split(strings.Trim(path, "/"), "/")
	for i, seg := range ps {
		if strings.HasPrefix(seg, "*") {
			return true
		}
		if i >= len(xs) {
			return false
		}
		if !strings.HasPrefix(seg, ":") && seg != xs[i] {
			return false
		}
		if strings.HasPrefix(seg, ":") && xs[i] == "" {
			return false
		}
	}
	return len(ps) == len(xs)
}

// ---- response helpers ------------------------------------------------------------------------------

// marshal encodes like Jackson: no HTML escaping, no trailing newline.
func marshal(v any) []byte {
	var buf bytes.Buffer
	enc := json.NewEncoder(&buf)
	enc.SetEscapeHTML(false)
	_ = enc.Encode(v)
	return bytes.TrimRight(buf.Bytes(), "\n")
}

func writeJSON(c *gin.Context, status int, v any) {
	c.Writer.Header().Set("Content-Type", "application/json")
	c.Writer.WriteHeader(status)
	_, _ = c.Writer.Write(marshal(v))
}

func writeStatus(c *gin.Context, status int) {
	c.Writer.WriteHeader(status)
	c.Writer.WriteHeaderNow()
}

func (s *Server) fail(c *gin.Context, err error) {
	status, body := apperr.Resolve(err)
	if status == http.StatusInternalServerError {
		var ae *apperr.Error
		if !errors.As(err, &ae) {
			log.Printf("unhandled error on %s %s: %v", c.Request.Method, c.Request.URL.Path, err)
		}
	}
	c.Abort()
	writeJSON(c, status, body)
}

// handle adapts a handler that returns an error.
func (s *Server) handle(fn func(c *gin.Context) error) gin.HandlerFunc {
	return func(c *gin.Context) {
		if err := fn(c); err != nil {
			s.fail(c, err)
		}
	}
}

func (s *Server) recoverer() gin.HandlerFunc {
	return func(c *gin.Context) {
		defer func() {
			if rec := recover(); rec != nil {
				log.Printf("panic on %s %s: %v", c.Request.Method, c.Request.URL.Path, rec)
				if !c.Writer.Written() {
					s.fail(c, apperr.Generic())
				}
			}
		}()
		c.Next()
	}
}

// ---- request helpers -------------------------------------------------------------------------------

// bind reads the JSON body like @RequestBody: a non-JSON Content-Type is an unhandled 500,
// an unreadable body is the 400 "Malformed request body" error.
func bind(c *gin.Context, dst any) error {
	ct := strings.ToLower(strings.TrimSpace(strings.Split(c.GetHeader("Content-Type"), ";")[0]))
	if ct != "application/json" && !(strings.HasPrefix(ct, "application/") && strings.HasSuffix(ct, "+json")) {
		return apperr.Generic()
	}
	if err := jsonx.Decode(c.Request.Body, dst); err != nil {
		return apperr.Malformed()
	}
	return nil
}

// pathUUID parses a UUID path variable; a malformed value is an unhandled error (500) in the Java backend.
func pathUUID(c *gin.Context, name string) (uuid.UUID, error) {
	id, err := jsonx.ParseUUID(c.Param(name))
	if err != nil {
		return uuid.Nil, apperr.Generic()
	}
	return id, nil
}

// queryUUID parses an optional UUID query parameter.
func queryUUID(c *gin.Context, name string) (*uuid.UUID, error) {
	raw, ok := c.GetQuery(name)
	if !ok || raw == "" {
		return nil, nil
	}
	id, err := jsonx.ParseUUID(raw)
	if err != nil {
		return nil, apperr.Generic()
	}
	return &id, nil
}

func (s *Server) me(c *gin.Context) *store.User {
	v, _ := c.Get(userKey)
	u, _ := v.(*store.User)
	return u
}

func (s *Server) ctx(c *gin.Context) context.Context { return c.Request.Context() }

// ---- security ---------------------------------------------------------------------------------------

func (s *Server) securityMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		s.securityHeaders(c)
		if s.cors(c) { // preflight answered
			return
		}
		s.authenticate(c)
		if s.me(c) == nil && !isPublic(c.Request.URL.Path) && c.Request.Method != http.MethodOptions {
			// Spring's default entry point: 403 with an empty body.
			c.Abort()
			c.Writer.WriteHeader(http.StatusForbidden)
			c.Writer.WriteHeaderNow()
			return
		}
		c.Next()
	}
}

func (s *Server) securityHeaders(c *gin.Context) {
	h := c.Writer.Header()
	h.Set("X-Content-Type-Options", "nosniff")
	h.Set("X-XSS-Protection", "0")
	h.Set("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate")
	h.Set("Pragma", "no-cache")
	h.Set("Expires", "0")
	h.Set("X-Frame-Options", "DENY")
	if strings.EqualFold(c.GetHeader("X-Forwarded-Proto"), "https") || c.Request.TLS != nil {
		h.Set("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains")
	}
}

// cors mirrors CorsConfiguration: any origin pattern, credentials allowed, the listed methods, any header.
func (s *Server) cors(c *gin.Context) (handled bool) {
	origin := c.GetHeader("Origin")
	h := c.Writer.Header()
	h.Add("Vary", "Origin")
	h.Add("Vary", "Access-Control-Request-Method")
	h.Add("Vary", "Access-Control-Request-Headers")
	if origin == "" {
		return false
	}
	h.Set("Access-Control-Allow-Origin", origin)
	h.Set("Access-Control-Allow-Credentials", "true")
	if c.Request.Method == http.MethodOptions && c.GetHeader("Access-Control-Request-Method") != "" {
		h.Set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,PATCH,OPTIONS")
		if req := c.GetHeader("Access-Control-Request-Headers"); req != "" {
			var names []string
			for _, n := range strings.Split(req, ",") {
				if n = strings.TrimSpace(n); n != "" {
					names = append(names, n)
				}
			}
			h.Set("Access-Control-Allow-Headers", strings.Join(names, ", "))
		}
		h.Set("Access-Control-Max-Age", "1800")
		c.Abort()
		c.Writer.WriteHeader(http.StatusOK)
		c.Writer.WriteHeaderNow()
		return true
	}
	return false
}

// authenticate runs the API-key filter first, then the JWT filter, like the Spring filter chain.
func (s *Server) authenticate(c *gin.Context) {
	if key := extractAPIKey(c); key != "" {
		if u := s.userForAPIKey(c, key); u != nil {
			c.Set(userKey, u)
			return
		}
	}
	auth := c.GetHeader("Authorization")
	if !strings.HasPrefix(auth, "Bearer ") {
		return
	}
	claims, err := s.JWT.Parse(auth[7:])
	if err != nil {
		return
	}
	id, err := jsonx.ParseUUID(claims.Subject)
	if err != nil {
		return
	}
	u, err := store.UserByID(c.Request.Context(), s.Store.Pool, id)
	if err != nil {
		return
	}
	c.Set(userKey, u)
}

// extractAPIKey mirrors ApiKeyAuthenticationFilter.extractApiKey.
func extractAPIKey(c *gin.Context) string {
	if x := strings.TrimSpace(c.GetHeader("X-API-Key")); x != "" {
		if len(x) >= 7 && strings.EqualFold(x[:7], "Bearer ") {
			x = strings.TrimSpace(x[7:])
		}
		return x
	}
	auth := strings.TrimSpace(c.GetHeader("Authorization"))
	if auth == "" {
		return ""
	}
	token := auth
	switch {
	case len(token) >= 7 && strings.EqualFold(token[:7], "Bearer "):
		token = strings.TrimSpace(token[7:])
	case len(token) >= 7 && strings.EqualFold(token[:7], "ApiKey "):
		token = strings.TrimSpace(token[7:])
	}
	if len(token) >= 7 && strings.EqualFold(token[:7], "Bearer ") {
		token = strings.TrimSpace(token[7:])
	}
	if strings.HasPrefix(token, "todo_live_") {
		return token
	}
	return ""
}

func (s *Server) userForAPIKey(c *gin.Context, raw string) *store.User {
	if !strings.HasPrefix(raw, apiKeyPrefix) {
		return nil
	}
	u, err := store.UserByAPIKeyHash(c.Request.Context(), s.Store.Pool, hashAPIKey(strings.TrimSpace(raw)), time.Now())
	if err != nil {
		return nil
	}
	return u
}
