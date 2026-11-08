package api

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"net/http"
	"strings"
	"time"

	"github.com/ajaypds/todo-fs-project/backend-go/internal/jsonx"
	"github.com/ajaypds/todo-fs-project/backend-go/internal/store"
	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
)

const apiKeyPrefix = "todo_live_"

func hashAPIKey(raw string) string {
	sum := sha256.Sum256([]byte(raw))
	return hex.EncodeToString(sum[:])
}

func (s *Server) registerAPIKeys(v1 *gin.RouterGroup) {
	g := v1.Group("/api-keys")
	g.POST("", s.handle(s.createAPIKey))
	g.GET("", s.handle(s.listAPIKeys))
	g.DELETE("/:id", s.handle(s.revokeAPIKey))
}

func (s *Server) createAPIKey(c *gin.Context) error {
	var req struct {
		Name jsonx.Str `json:"name"`
	}
	if err := bind(c, &req); err != nil {
		return err
	}
	v := newCheck()
	v.notBlank("name", req.Name.Ptr(), "API key name is required")
	if err := v.err(); err != nil {
		return err
	}
	if _, err := store.UserByID(s.ctx(c), s.Store.Pool, s.me(c).ID); err != nil {
		return err // orElseThrow()
	}
	buf := make([]byte, 24)
	if _, err := rand.Read(buf); err != nil {
		return err
	}
	raw := apiKeyPrefix + hex.EncodeToString(buf)
	k := &store.APIKey{
		ID: uuid.New(), UserID: s.me(c).ID, Name: strings.TrimSpace(req.Name.V), KeyHash: hashAPIKey(raw),
		KeyPrefix: raw[:16], CreatedAt: time.Now().UTC(),
	}
	if err := store.InsertAPIKey(s.ctx(c), s.Store.Pool, k); err != nil {
		return err
	}
	writeJSON(c, http.StatusCreated, apiKeyResponse{ID: k.ID, Name: k.Name, KeyPrefix: k.KeyPrefix, APIKey: &raw,
		CreatedAt: jsonx.From(k.CreatedAt)})
	return nil
}

func (s *Server) listAPIKeys(c *gin.Context) error {
	keys, err := store.APIKeysByUser(s.ctx(c), s.Store.Pool, s.me(c).ID)
	if err != nil {
		return err
	}
	out := make([]apiKeyResponse, 0, len(keys))
	for _, k := range keys {
		r := apiKeyResponse{ID: k.ID, Name: k.Name, KeyPrefix: k.KeyPrefix, CreatedAt: jsonx.From(k.CreatedAt)}
		if k.LastUsedAt != nil {
			r.LastUsedAt = jsonx.Ptr(*k.LastUsedAt)
		}
		out = append(out, r)
	}
	writeJSON(c, http.StatusOK, out)
	return nil
}

func (s *Server) revokeAPIKey(c *gin.Context) error {
	id, err := pathUUID(c, "id")
	if err != nil {
		return err
	}
	if err := store.DeleteAPIKey(s.ctx(c), s.Store.Pool, id, s.me(c).ID); err != nil {
		return err
	}
	writeStatus(c, http.StatusNoContent)
	return nil
}
