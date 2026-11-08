// Package config reads the same environment variables as the Spring Boot backend.
package config

import (
	"fmt"
	"net/url"
	"os"
	"strings"
	"time"
)

// Config holds runtime settings. Variable names and meanings match the Java backend:
//
//	PORT, DB_URL (a JDBC URL), DB_USERNAME, DB_PASSWORD, JWT_SECRET, GEMINI_API_KEY,
//	MAIL_FROM, MAIL_USERNAME, MAIL_PASSWORD (accepted for compatibility; mail is console-only).
type Config struct {
	Port              string
	DatabaseURL       string // pgx connection string derived from DB_URL
	JWTSecret         string
	GeminiAPIKey      string
	GeminiModel       string
	JWTExpiration     time.Duration // access token lifetime (jwt.expiration = 900000 ms)
	RefreshExpiration time.Duration // refresh token lifetime (jwt.refresh-expiration = 604800000 ms)
	MailFrom          string
	GeminiBaseURL     string // overridable for tests
}

// Load reads and validates the environment.
func Load() (*Config, error) {
	cfg := &Config{
		Port:              envOr("PORT", "8080"),
		JWTSecret:         os.Getenv("JWT_SECRET"),
		GeminiAPIKey:      os.Getenv("GEMINI_API_KEY"),
		GeminiModel:       "gemini-3.5-flash",
		JWTExpiration:     900000 * time.Millisecond,
		RefreshExpiration: 604800000 * time.Millisecond,
		MailFrom:          envOr("MAIL_FROM", "noreply@todoflow.app"),
		GeminiBaseURL:     envOr("GEMINI_BASE_URL", "https://generativelanguage.googleapis.com"),
	}
	if cfg.JWTSecret == "" {
		return nil, fmt.Errorf("JWT_SECRET is required")
	}
	if len([]byte(cfg.JWTSecret)) < 32 {
		return nil, fmt.Errorf("JWT_SECRET must be at least 32 bytes long (same rule as the Java backend)")
	}
	dsn, err := ConvertJDBCURL(os.Getenv("DB_URL"), os.Getenv("DB_USERNAME"), os.Getenv("DB_PASSWORD"))
	if err != nil {
		return nil, err
	}
	cfg.DatabaseURL = dsn
	return cfg, nil
}

func envOr(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

// ConvertJDBCURL turns the Spring-style DB_URL ("jdbc:postgresql://host:5432/db?sslmode=require")
// into a pgx connection URL. Plain postgres:// URLs are accepted as well.
func ConvertJDBCURL(raw, user, password string) (string, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return "", fmt.Errorf("DB_URL is required")
	}
	raw = strings.TrimPrefix(raw, "jdbc:")
	if strings.HasPrefix(raw, "postgres://") {
		raw = "postgresql://" + strings.TrimPrefix(raw, "postgres://")
	}
	if !strings.HasPrefix(raw, "postgresql:") {
		return "", fmt.Errorf("DB_URL must be a PostgreSQL JDBC URL, got %q", raw)
	}
	u, err := url.Parse(raw)
	if err != nil {
		return "", fmt.Errorf("invalid DB_URL: %w", err)
	}

	q := u.Query()
	out := url.Values{}
	// Query parameters understood by both pgjdbc and libpq/pgx.
	for jdbcKey, pgxKey := range map[string]string{
		"sslmode":         "sslmode",
		"sslrootcert":     "sslrootcert",
		"sslcert":         "sslcert",
		"sslkey":          "sslkey",
		"applicationName": "application_name",
		"connectTimeout":  "connect_timeout",
		"currentSchema":   "search_path",
	} {
		if v := q.Get(jdbcKey); v != "" {
			out.Set(pgxKey, v)
		}
	}
	if out.Get("sslmode") == "" && strings.EqualFold(q.Get("ssl"), "true") {
		out.Set("sslmode", "require")
	}
	if user == "" {
		user = q.Get("user")
	}
	if password == "" {
		password = q.Get("password")
	}

	host := u.Host
	if host == "" {
		host = q.Get("host") // e.g. jdbc:postgresql:///db?host=/cloudsql/project:region:instance
	}
	// pgjdbc allows "h1:5432,h2:5432"; use the first host.
	if i := strings.Index(host, ","); i >= 0 {
		host = host[:i]
	}
	db := strings.TrimPrefix(u.Path, "/")

	res := url.URL{Scheme: "postgresql", Host: host, Path: "/" + db, RawQuery: out.Encode()}
	if strings.HasPrefix(host, "/") { // unix socket directory
		res.Host = ""
		out.Set("host", host)
		res.RawQuery = out.Encode()
	}
	if user != "" {
		if password != "" {
			res.User = url.UserPassword(user, password)
		} else {
			res.User = url.User(user)
		}
	}
	return res.String(), nil
}
