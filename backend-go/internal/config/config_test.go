package config

import (
	"net/url"
	"strings"
	"testing"
)

func TestConvertJDBCURL(t *testing.T) {
	cases := []struct {
		raw, user, pass string
		host, db, user2 string
		query           map[string]string
	}{
		{"jdbc:postgresql://db.example.com:5432/todo?sslmode=require", "u", "p@ss/w", "db.example.com:5432", "todo", "u", map[string]string{"sslmode": "require"}},
		{"jdbc:postgresql://h1:5432,h2:5432/todo?ssl=true", "u", "p", "h1:5432", "todo", "u", map[string]string{"sslmode": "require"}},
		{"postgres://h/todo?currentSchema=app&connectTimeout=5", "u", "p", "h", "todo", "u", map[string]string{"search_path": "app", "connect_timeout": "5"}},
		{"jdbc:postgresql:///todo?host=/cloudsql/p:r:i", "u", "p", "", "todo", "u", map[string]string{"host": "/cloudsql/p:r:i"}},
	}
	for _, c := range cases {
		got, err := ConvertJDBCURL(c.raw, c.user, c.pass)
		if err != nil {
			t.Fatalf("%s: %v", c.raw, err)
		}
		u, err := url.Parse(got)
		if err != nil {
			t.Fatalf("%s → %s: %v", c.raw, got, err)
		}
		if u.Scheme != "postgresql" || u.Host != c.host || strings.TrimPrefix(u.Path, "/") != c.db || u.User.Username() != c.user2 {
			t.Errorf("%s → %s", c.raw, got)
		}
		if pw, _ := u.User.Password(); pw != c.pass {
			t.Errorf("%s: password %q != %q", c.raw, pw, c.pass)
		}
		for k, v := range c.query {
			if u.Query().Get(k) != v {
				t.Errorf("%s: %s=%q want %q (%s)", c.raw, k, u.Query().Get(k), v, got)
			}
		}
	}
	if _, err := ConvertJDBCURL("", "u", "p"); err == nil {
		t.Error("empty URL must fail")
	}
	if _, err := ConvertJDBCURL("jdbc:mysql://h/db", "u", "p"); err == nil {
		t.Error("non-postgres URL must fail")
	}
}
