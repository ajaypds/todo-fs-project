// Command server is the Go (Gin) implementation of the TodoFlow backend: same database, same endpoints,
// same JSON, same environment variables as the Spring Boot backend, with the MCP server built in.
package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	_ "time/tzdata" // IANA zone database inside the binary, so scratch/distroless images work

	"github.com/ajaypds/todo-fs-project/backend-go/internal/ai"
	"github.com/ajaypds/todo-fs-project/backend-go/internal/api"
	"github.com/ajaypds/todo-fs-project/backend-go/internal/config"
	"github.com/ajaypds/todo-fs-project/backend-go/internal/mcp"
	"github.com/ajaypds/todo-fs-project/backend-go/internal/realtime"
	"github.com/ajaypds/todo-fs-project/backend-go/internal/security"
	"github.com/ajaypds/todo-fs-project/backend-go/internal/store"
	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("configuration error: %v", err)
	}
	jwt, err := security.NewJWT(cfg.JWTSecret, cfg.JWTExpiration, cfg.RefreshExpiration)
	if err != nil {
		log.Fatalf("configuration error: %v", err)
	}

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	st, err := store.Open(ctx, cfg.DatabaseURL)
	if err != nil {
		log.Fatalf("database error: %v", err)
	}
	defer st.Close()
	pingCtx, cancel := context.WithTimeout(ctx, 10*time.Second)
	if err := st.Ping(pingCtx); err != nil {
		// Keep serving: /actuator/health reports DOWN and requests retry the connection.
		log.Printf("WARNING: database not reachable yet: %v", err)
	}
	cancel()

	hub := realtime.NewHub(func(token string) (uuid.UUID, string, error) {
		claims, err := jwt.Parse(token)
		if err != nil {
			return uuid.Nil, "", err
		}
		id, err := uuid.Parse(claims.Subject)
		return id, claims.User, err
	})

	srv := &api.Server{
		Cfg: cfg, Store: st, JWT: jwt, Hub: hub,
		AI: ai.New(cfg.GeminiAPIKey, cfg.GeminiModel, cfg.GeminiBaseURL),
	}
	var engine *gin.Engine
	engine = srv.Router(func(r *gin.Engine) { mcp.Mount(r, func() http.Handler { return engine }) })

	httpServer := &http.Server{
		Addr:              ":" + cfg.Port,
		Handler:           engine,
		ReadHeaderTimeout: 10 * time.Second,
	}
	go func() {
		<-ctx.Done()
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		_ = httpServer.Shutdown(shutdownCtx)
	}()

	log.Printf("TodoFlow Go backend listening on :%s", cfg.Port)
	if err := httpServer.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Fatalf("server error: %v", err)
	}
	log.Println("server stopped")
	_ = os.Stdout.Sync()
}
