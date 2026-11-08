// Package security implements token and password handling compatible with the Java backend:
// tokens issued by either backend are accepted by the other, and so are stored password hashes.
package security

import (
	"crypto/hmac"
	"crypto/sha256"
	"crypto/sha512"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"hash"
	"strings"
	"time"

	"github.com/google/uuid"
	"golang.org/x/crypto/bcrypt"
)

// JWT signs and verifies HMAC tokens the way JJWT does: the algorithm is chosen from the secret's
// length (>=64 bytes HS512, >=48 HS384, >=32 HS256) and the header is just {"alg":"..."}.
type JWT struct {
	key        []byte
	alg        string
	AccessTTL  time.Duration
	RefreshTTL time.Duration
}

// NewJWT builds a signer from JWT_SECRET (used as raw UTF-8 bytes, like Keys.hmacShaKeyFor).
func NewJWT(secret string, access, refresh time.Duration) (*JWT, error) {
	key := []byte(secret)
	alg := ""
	switch {
	case len(key) >= 64:
		alg = "HS512"
	case len(key) >= 48:
		alg = "HS384"
	case len(key) >= 32:
		alg = "HS256"
	default:
		return nil, fmt.Errorf("JWT secret must be at least 256 bits (32 bytes)")
	}
	return &JWT{key: key, alg: alg, AccessTTL: access, RefreshTTL: refresh}, nil
}

type accessClaims struct {
	Sub  string `json:"sub"`
	User string `json:"user"`
	Iat  int64  `json:"iat"`
	Exp  int64  `json:"exp"`
}

type refreshClaims struct {
	Sub string `json:"sub"`
	Iat int64  `json:"iat"`
	Exp int64  `json:"exp"`
}

func hasher(alg string) (func() hash.Hash, int, bool) {
	switch alg {
	case "HS256":
		return sha256.New, 256, true
	case "HS384":
		return sha512.New384, 384, true
	case "HS512":
		return sha512.New, 512, true
	}
	return nil, 0, false
}

func (j *JWT) sign(alg string, claims any) string {
	enc := base64.RawURLEncoding
	h, _ := json.Marshal(map[string]string{"alg": alg})
	p, _ := json.Marshal(claims)
	signingInput := enc.EncodeToString(h) + "." + enc.EncodeToString(p)
	newHash, _, _ := hasher(alg)
	mac := hmac.New(newHash, j.key)
	mac.Write([]byte(signingInput))
	return signingInput + "." + enc.EncodeToString(mac.Sum(nil))
}

// AccessToken issues a short-lived token: {sub: userId, user: email, iat, exp}.
func (j *JWT) AccessToken(userID uuid.UUID, email string) string {
	now := time.Now()
	return j.sign(j.alg, accessClaims{Sub: userID.String(), User: email, Iat: now.Unix(), Exp: now.Add(j.AccessTTL).Unix()})
}

// RefreshToken issues a long-lived token: {sub: email, iat, exp}.
func (j *JWT) RefreshToken(email string) string {
	now := time.Now()
	return j.sign(j.alg, refreshClaims{Sub: email, Iat: now.Unix(), Exp: now.Add(j.RefreshTTL).Unix()})
}

// Claims are the verified fields of a token.
type Claims struct {
	Subject string
	User    string
}

// Parse verifies the signature and expiry. Any problem returns an error (like JwtService.isTokenValid=false).
func (j *JWT) Parse(token string) (*Claims, error) {
	parts := strings.Split(token, ".")
	if len(parts) != 3 {
		return nil, fmt.Errorf("malformed token")
	}
	enc := base64.RawURLEncoding
	rawHeader, err := enc.DecodeString(strings.TrimRight(parts[0], "="))
	if err != nil {
		return nil, err
	}
	var header struct {
		Alg string `json:"alg"`
	}
	if err := json.Unmarshal(rawHeader, &header); err != nil {
		return nil, err
	}
	newHash, bits, ok := hasher(header.Alg)
	if !ok || bits > len(j.key)*8 { // unsigned, unknown algorithm, or key too weak for it
		return nil, fmt.Errorf("unsupported algorithm")
	}
	sig, err := enc.DecodeString(strings.TrimRight(parts[2], "="))
	if err != nil {
		return nil, err
	}
	mac := hmac.New(newHash, j.key)
	mac.Write([]byte(parts[0] + "." + parts[1]))
	if !hmac.Equal(sig, mac.Sum(nil)) {
		return nil, fmt.Errorf("bad signature")
	}
	rawPayload, err := enc.DecodeString(strings.TrimRight(parts[1], "="))
	if err != nil {
		return nil, err
	}
	var payload struct {
		Sub  string   `json:"sub"`
		User string   `json:"user"`
		Exp  *float64 `json:"exp"`
		Nbf  *float64 `json:"nbf"`
	}
	if err := json.Unmarshal(rawPayload, &payload); err != nil {
		return nil, err
	}
	now := float64(time.Now().UnixMilli()) / 1000
	if payload.Exp != nil && now >= *payload.Exp {
		return nil, fmt.Errorf("token expired")
	}
	if payload.Nbf != nil && now < *payload.Nbf {
		return nil, fmt.Errorf("token not yet valid")
	}
	return &Claims{Subject: payload.Sub, User: payload.User}, nil
}

// HashPassword hashes like Spring's BCryptPasswordEncoder (cost 10, $2a$). Input is cut to 72 bytes, as BCrypt does.
func HashPassword(password string) (string, error) {
	b := []byte(password)
	if len(b) > 72 {
		b = b[:72]
	}
	h, err := bcrypt.GenerateFromPassword(b, 10)
	return string(h), err
}

// CheckPassword reports whether password matches a stored bcrypt hash.
func CheckPassword(hashed, password string) bool {
	b := []byte(password)
	if len(b) > 72 {
		b = b[:72]
	}
	return bcrypt.CompareHashAndPassword([]byte(hashed), b) == nil
}
