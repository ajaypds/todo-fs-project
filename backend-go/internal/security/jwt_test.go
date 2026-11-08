package security

import (
	"strings"
	"testing"
	"time"

	"github.com/google/uuid"
)

func TestAlgorithmFollowsSecretLength(t *testing.T) {
	cases := map[int]string{32: "HS256", 47: "HS256", 48: "HS384", 63: "HS384", 64: "HS512", 100: "HS512"}
	for n, want := range cases {
		j, err := NewJWT(strings.Repeat("k", n), time.Minute, time.Hour)
		if err != nil || j.alg != want {
			t.Fatalf("len %d: alg=%v err=%v want %s", n, j.alg, err, want)
		}
	}
	if _, err := NewJWT(strings.Repeat("k", 31), time.Minute, time.Hour); err == nil {
		t.Fatal("31 byte secret must be rejected")
	}
}

func TestRoundTripAndTampering(t *testing.T) {
	j, _ := NewJWT(strings.Repeat("s", 64), time.Minute, time.Hour)
	id := uuid.New()
	tok := j.AccessToken(id, "a@b.c")
	c, err := j.Parse(tok)
	if err != nil || c.Subject != id.String() || c.User != "a@b.c" {
		t.Fatalf("parse: %v %+v", err, c)
	}
	if _, err := j.Parse(tok + "x"); err == nil {
		t.Fatal("tampered signature accepted")
	}
	other, _ := NewJWT(strings.Repeat("t", 64), time.Minute, time.Hour)
	if _, err := other.Parse(tok); err == nil {
		t.Fatal("token from a different secret accepted")
	}
}

func TestExpiredAndHeaderFormat(t *testing.T) {
	j, _ := NewJWT(strings.Repeat("s", 64), -time.Second, time.Hour)
	if _, err := j.Parse(j.AccessToken(uuid.New(), "a@b.c")); err == nil {
		t.Fatal("expired token accepted")
	}
	tok := j.RefreshToken("a@b.c")
	if !strings.HasPrefix(tok, "eyJhbGciOiJIUzUxMiJ9.") { // {"alg":"HS512"} exactly, like JJWT
		t.Fatalf("unexpected header: %s", tok)
	}
}

func TestBcryptCompatibility(t *testing.T) {
	// Real hash produced by the Java backend's BCryptPasswordEncoder for the password "secret12".
	const springHash = "$2a$10$QF5DV5iraKZUhk58NXKJEOI82pH1ngLustoSbVo.0Oh8hT4AzIHmW"
	if !CheckPassword(springHash, "secret12") {
		t.Fatal("hash created by Spring must verify")
	}
	if CheckPassword(springHash, "wrong") {
		t.Fatal("wrong password accepted")
	}
	h, err := HashPassword("secret12")
	if err != nil || !strings.HasPrefix(h, "$2a$10$") || !CheckPassword(h, "secret12") || CheckPassword(h, "nope") {
		t.Fatalf("hash/verify failed: %v %s", err, h)
	}
}
