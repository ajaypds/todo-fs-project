package api

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/ajaypds/todo-fs-project/backend-go/internal/apperr"
	"github.com/ajaypds/todo-fs-project/backend-go/internal/security"
	"github.com/ajaypds/todo-fs-project/backend-go/internal/store"
	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
)

var oauthBases = []string{"/oauth2", "/oauth", "/api/v1/oauth2", "/api/v1/oauth"}

// oauthError is the RFC 6749 error body {"error", "error_description"}.
type oauthError struct {
	Status      int
	Code        string
	Description string
}

func (e *oauthError) Error() string { return e.Code + ": " + e.Description }

func oerr(code, desc string) *oauthError { return &oauthError{http.StatusBadRequest, code, desc} }
func oerrAuth(code, desc string) *oauthError {
	return &oauthError{http.StatusUnauthorized, code, desc}
}

type oauthErrorBody struct {
	Error       string `json:"error"`
	Description string `json:"error_description"`
}

// tokenResponse omits null fields like the Java DTO (@JsonInclude NON_NULL).
type tokenResponse struct {
	AccessToken  string  `json:"access_token"`
	TokenType    string  `json:"token_type"`
	ExpiresIn    int64   `json:"expires_in"`
	RefreshToken string  `json:"refresh_token"`
	Scope        *string `json:"scope,omitempty"`
}

func (s *Server) registerOAuth(r *gin.Engine) {
	for _, base := range oauthBases {
		r.GET(base+"/authorize", s.authorizePage)
		r.POST(base+"/authorize", s.authorizeSubmit)
		r.POST(base+"/token", s.tokenEndpoint)
		r.POST(base+"/revoke", s.revoke)
		r.POST(base+"/register", s.register_)
	}
	for _, p := range []string{"/.well-known/oauth-authorization-server", "/.well-known/openid-configuration"} {
		r.GET(p, s.discovery)
	}
	r.GET("/.well-known/oauth-authorization-server/*rest", s.discovery)
	r.GET("/.well-known/oauth-protected-resource", s.protectedResource)
	r.GET("/.well-known/oauth-protected-resource/*rest", s.protectedResource)
	r.GET("/mcp/.well-known/oauth-protected-resource", s.protectedResource)
}

func escapeHTML(text string) string {
	return strings.NewReplacer("&", "&amp;", "<", "&lt;", ">", "&gt;", `"`, "&quot;", "'", "&#39;").Replace(text)
}

func writeHTML(c *gin.Context, status int, html string) {
	c.Writer.Header().Set("Content-Type", "text/html;charset=UTF-8")
	c.Writer.WriteHeader(status)
	_, _ = c.Writer.Write([]byte(html))
}

func (s *Server) oauthClient(c *gin.Context, clientID string) (*store.OAuthClient, error) {
	if strings.TrimSpace(clientID) == "" {
		return nil, oerr("invalid_request", "Missing client_id parameter")
	}
	cl, err := store.OAuthClientByClientID(s.ctx(c), s.Store.Pool, strings.TrimSpace(clientID))
	if err == store.ErrNotFound {
		return nil, oerrAuth("invalid_client", "Client not found: "+clientID)
	}
	return cl, err
}

func redirectAllowed(cl *store.OAuthClient, candidate string) bool {
	if strings.TrimSpace(candidate) == "" || strings.TrimSpace(cl.RedirectURIs) == "" {
		return false
	}
	for _, uri := range strings.Split(cl.RedirectURIs, ",") {
		t := strings.TrimSpace(uri)
		if candidate == t || strings.HasPrefix(candidate, t) {
			return true
		}
	}
	return false
}

func cancelURL(redirectURI, state string) string {
	sep := "?"
	if strings.Contains(redirectURI, "?") {
		sep = "&"
	}
	u := redirectURI + sep + "error=access_denied"
	if strings.TrimSpace(state) != "" {
		u += "&state=" + state
	}
	return u
}

func (s *Server) authorizePage(c *gin.Context) {
	q := c.Request.URL.Query()
	if q.Get("response_type") != "code" {
		writeHTML(c, http.StatusBadRequest, renderErrorPageHTML("Invalid Request", "response_type must be 'code' in OAuth 2.1"))
		return
	}
	cl, err := s.oauthClient(c, q.Get("client_id"))
	var oe *oauthError
	if errors.As(err, &oe) {
		writeHTML(c, http.StatusBadRequest, renderErrorPageHTML("Client Error", oe.Description))
		return
	} else if err != nil {
		s.fail(c, err)
		return
	}
	redirectURI := q.Get("redirect_uri")
	if redirectURI == "" || !redirectAllowed(cl, redirectURI) {
		writeHTML(c, http.StatusBadRequest, renderErrorPageHTML("Redirect URI Error", "The redirect_uri provided is not registered for this client."))
		return
	}
	state := q.Get("state")
	writeHTML(c, http.StatusOK, renderAuthorizeFormHTML(c.Request.URL.Path, cl.ClientName, q.Get("client_id"), redirectURI,
		q.Get("scope"), state, q.Get("code_challenge"), q.Get("code_challenge_method"), "", "", cancelURL(redirectURI, state)))
}

// formParams returns merged query + form values (Spring's @RequestParam sees both).
func formParams(c *gin.Context) url.Values {
	_ = c.Request.ParseForm()
	return c.Request.Form
}

func isForm(c *gin.Context) bool {
	return strings.HasPrefix(strings.ToLower(c.GetHeader("Content-Type")), "application/x-www-form-urlencoded")
}

func (s *Server) authorizeSubmit(c *gin.Context) {
	if !isForm(c) {
		s.fail(c, apperr.Generic())
		return
	}
	p := formParams(c)
	for _, required := range []string{"email", "password", "client_id", "redirect_uri"} {
		if _, ok := p[required]; !ok {
			s.fail(c, apperr.Generic()) // MissingServletRequestParameterException
			return
		}
	}
	cl, err := s.oauthClient(c, p.Get("client_id"))
	var oe *oauthError
	if errors.As(err, &oe) {
		writeHTML(c, http.StatusBadRequest, renderErrorPageHTML("Client Error", oe.Description))
		return
	} else if err != nil {
		s.fail(c, err)
		return
	}
	redirectURI, state := p.Get("redirect_uri"), p.Get("state")
	if !redirectAllowed(cl, redirectURI) {
		writeHTML(c, http.StatusBadRequest, renderErrorPageHTML("Redirect URI Error", "The redirect_uri is not allowed."))
		return
	}
	email := normEmail(p.Get("email"))
	form := func(msg string) string {
		return renderAuthorizeFormHTML(c.Request.URL.Path, cl.ClientName, p.Get("client_id"), redirectURI, p.Get("scope"), state,
			p.Get("code_challenge"), p.Get("code_challenge_method"), msg, email, cancelURL(redirectURI, state))
	}
	ctx := s.ctx(c)
	user, err := store.UserByEmail(ctx, s.Store.Pool, email)
	if err != nil && err != store.ErrNotFound {
		s.fail(c, err)
		return
	}
	if user == nil || !security.CheckPassword(user.Password, p.Get("password")) {
		writeHTML(c, http.StatusUnauthorized, form("Invalid email or password. Please try again."))
		return
	}
	if !user.EmailVerified {
		writeHTML(c, http.StatusForbidden, form("Your email is not verified yet. Please verify your email first."))
		return
	}

	scope := p.Get("scope")
	var scopeVal *string
	if _, ok := p["scope"]; ok {
		scopeVal = &scope
	} else {
		scopeVal = cl.Scopes
	}
	challenge, method := p.Get("code_challenge"), p.Get("code_challenge_method")
	var chPtr, mPtr *string
	if _, ok := p["code_challenge"]; ok {
		chPtr = &challenge
	}
	if method != "" || chPtr != nil {
		m := method
		if _, ok := p["code_challenge_method"]; !ok {
			m = "S256"
		}
		mPtr = &m
	} else {
		m := "S256"
		mPtr = &m
	}
	buf := make([]byte, 32)
	_, _ = rand.Read(buf)
	code := base64.RawURLEncoding.EncodeToString(buf)
	now := time.Now().UTC()
	rec := &store.OAuthCode{ID: uuid.New(), Code: code, UserID: user.ID, ClientID: cl.ClientID, RedirectURI: redirectURI,
		Scope: scopeVal, CodeChallenge: chPtr, CodeChallengeMethod: mPtr, ExpiresAt: now.Add(5 * time.Minute), CreatedAt: now}
	if err := store.InsertOAuthCode(ctx, s.Store.Pool, rec); err != nil {
		s.fail(c, err)
		return
	}
	loc := redirectURI
	if strings.Contains(loc, "?") {
		loc += "&code=" + code
	} else {
		loc += "?code=" + code
	}
	if strings.TrimSpace(state) != "" {
		loc += "&state=" + state
	}
	log.Printf("OAuth 2.1: Authorizing user %s for client %s. Redirecting to callback.", user.Email, p.Get("client_id"))
	c.Writer.Header().Set("Location", loc)
	writeStatus(c, http.StatusFound)
}

func (s *Server) tokenEndpoint(c *gin.Context) {
	ct := strings.ToLower(c.GetHeader("Content-Type"))
	params := map[string]string{}
	switch {
	case isForm(c):
		for k, v := range formParams(c) {
			if len(v) > 0 {
				params[k] = v[0]
			}
		}
	case strings.HasPrefix(ct, "application/json"):
		var raw map[string]any
		data, _ := io.ReadAll(io.LimitReader(c.Request.Body, 1<<20))
		if err := json.Unmarshal(data, &raw); err != nil || raw == nil {
			s.fail(c, apperr.Malformed())
			return
		}
		for k, v := range raw {
			switch t := v.(type) {
			case string:
				params[k] = t
			case float64, bool:
				params[k] = fmt.Sprint(t)
			case nil:
			default:
				s.fail(c, apperr.Malformed())
				return
			}
		}
	default:
		s.fail(c, apperr.Generic()) // unsupported media type is unhandled in the Java backend
		return
	}

	clientID, clientSecret := params["client_id"], params["client_secret"]
	if auth := c.GetHeader("Authorization"); strings.HasPrefix(auth, "Basic ") {
		if decoded, err := base64.StdEncoding.DecodeString(strings.TrimSpace(auth[6:])); err == nil {
			parts := strings.SplitN(string(decoded), ":", 2)
			if len(parts) >= 1 && strings.TrimSpace(clientID) == "" {
				clientID = parts[0]
			}
			if len(parts) == 2 && strings.TrimSpace(clientSecret) == "" {
				clientSecret = parts[1]
			}
		}
	}

	var resp *tokenResponse
	var err error
	switch params["grant_type"] {
	case "authorization_code":
		resp, err = s.exchangeCode(c, params, clientID, clientSecret)
	case "refresh_token":
		resp, err = s.refreshGrant(c, params, clientID, clientSecret)
	default:
		writeJSON(c, http.StatusBadRequest, oauthErrorBody{"unsupported_grant_type", "Supported grant_types are authorization_code and refresh_token"})
		return
	}
	var oe *oauthError
	switch {
	case errors.As(err, &oe):
		writeJSON(c, oe.Status, oauthErrorBody{oe.Code, oe.Description})
	case err != nil:
		log.Printf("Unhandled error in token endpoint: %v", err)
		writeJSON(c, http.StatusInternalServerError, oauthErrorBody{"server_error", "An internal error occurred during token processing"})
	default:
		c.Writer.Header().Set("Cache-Control", "no-store")
		c.Writer.Header().Set("Pragma", "no-cache")
		writeJSON(c, http.StatusOK, resp)
	}
}

func validateClientCredentials(cl *store.OAuthClient, candidate string) error {
	if cl.ClientSecret != nil && strings.TrimSpace(*cl.ClientSecret) != "" {
		if strings.TrimSpace(candidate) == "" {
			return oerrAuth("invalid_client", "Client secret is required for this client")
		}
		if candidate != *cl.ClientSecret && !security.CheckPassword(*cl.ClientSecret, candidate) {
			return oerrAuth("invalid_client", "Invalid client secret")
		}
	}
	return nil
}

func verifyPKCE(verifier, challenge, method string) bool {
	if verifier == "" || challenge == "" {
		return false
	}
	if method == "" || strings.EqualFold(method, "S256") {
		sum := sha256.Sum256([]byte(verifier))
		return base64.RawURLEncoding.EncodeToString(sum[:]) == challenge
	}
	return false
}

func (s *Server) exchangeCode(c *gin.Context, p map[string]string, clientID, clientSecret string) (*tokenResponse, error) {
	ctx := s.ctx(c)
	code := p["code"]
	if strings.TrimSpace(code) == "" {
		return nil, oerr("invalid_request", "Missing code parameter")
	}
	auth, err := store.OAuthCodeByCode(ctx, s.Store.Pool, strings.TrimSpace(code))
	if err == store.ErrNotFound {
		return nil, oerr("invalid_grant", "Invalid authorization code")
	} else if err != nil {
		return nil, err
	}
	if auth.Used {
		log.Printf("Security Alert: Reuse attempt detected for authorization code %s", code)
		return nil, oerr("invalid_grant", "Authorization code already used")
	}
	if time.Now().After(auth.ExpiresAt) {
		return nil, oerr("invalid_grant", "Authorization code expired")
	}
	if err := store.MarkOAuthCodeUsed(ctx, s.Store.Pool, auth.ID); err != nil { // single use, even if later checks fail
		return nil, err
	}
	if strings.TrimSpace(clientID) != "" && clientID != auth.ClientID {
		return nil, oerr("invalid_grant", "Client mismatch for authorization code")
	}
	client, err := s.oauthClient(c, auth.ClientID)
	if err != nil {
		return nil, err
	}
	if err := validateClientCredentials(client, clientSecret); err != nil {
		return nil, err
	}
	if ru := p["redirect_uri"]; strings.TrimSpace(ru) != "" && ru != auth.RedirectURI {
		return nil, oerr("invalid_grant", "redirect_uri mismatch")
	}
	if auth.CodeChallenge != nil && strings.TrimSpace(*auth.CodeChallenge) != "" {
		verifier := p["code_verifier"]
		if strings.TrimSpace(verifier) == "" {
			return nil, oerr("invalid_request", "code_verifier required for PKCE validation")
		}
		method := ""
		if auth.CodeChallengeMethod != nil {
			method = *auth.CodeChallengeMethod
		}
		if !verifyPKCE(verifier, *auth.CodeChallenge, method) {
			return nil, oerr("invalid_grant", "PKCE verification failed: code_verifier does not match code_challenge")
		}
	}
	user, err := store.UserByID(ctx, s.Store.Pool, auth.UserID)
	if err != nil {
		return nil, err
	}
	access := s.JWT.AccessToken(user.ID, user.Email)
	refresh := s.JWT.RefreshToken(user.Email)
	if err := store.InsertRefreshToken(ctx, s.Store.Pool, refresh, user.ID, time.Now().Add(30*24*time.Hour)); err != nil {
		return nil, err
	}
	return &tokenResponse{AccessToken: access, TokenType: "Bearer", ExpiresIn: int64(s.JWT.AccessTTL / time.Second),
		RefreshToken: refresh, Scope: auth.Scope}, nil
}

func (s *Server) refreshGrant(c *gin.Context, p map[string]string, clientID, clientSecret string) (*tokenResponse, error) {
	ctx := s.ctx(c)
	rt := p["refresh_token"]
	if strings.TrimSpace(rt) == "" {
		return nil, oerr("invalid_request", "Missing refresh_token parameter")
	}
	if strings.TrimSpace(clientID) != "" {
		client, err := s.oauthClient(c, clientID)
		if err != nil {
			return nil, err
		}
		if err := validateClientCredentials(client, clientSecret); err != nil {
			return nil, err
		}
	}
	stored, user, err := store.RefreshTokenByToken(ctx, s.Store.Pool, strings.TrimSpace(rt))
	if err == store.ErrNotFound {
		return nil, oerr("invalid_grant", "Invalid refresh token")
	} else if err != nil {
		return nil, err
	}
	if stored.Revoked || stored.ExpiresAt.Before(time.Now()) {
		return nil, oerr("invalid_grant", "Refresh token is expired or revoked")
	}
	access := s.JWT.AccessToken(user.ID, user.Email)
	if err := store.RevokeRefreshToken(ctx, s.Store.Pool, stored.ID); err != nil { // rotate
		return nil, err
	}
	next := s.JWT.RefreshToken(user.Email)
	if err := store.InsertRefreshToken(ctx, s.Store.Pool, next, user.ID, time.Now().Add(30*24*time.Hour)); err != nil {
		return nil, err
	}
	scope := "read write all"
	return &tokenResponse{AccessToken: access, TokenType: "Bearer", ExpiresIn: int64(s.JWT.AccessTTL / time.Second),
		RefreshToken: next, Scope: &scope}, nil
}

func (s *Server) revoke(c *gin.Context) {
	if token := strings.TrimSpace(formParams(c).Get("token")); token != "" {
		if rt, _, err := store.RefreshTokenByToken(s.ctx(c), s.Store.Pool, token); err == nil {
			_ = store.RevokeRefreshToken(s.ctx(c), s.Store.Pool, rt.ID)
		}
	}
	writeStatus(c, http.StatusOK)
}

// register_ is RFC 7591 dynamic client registration.
func (s *Server) register_(c *gin.Context) {
	if !strings.HasPrefix(strings.ToLower(c.GetHeader("Content-Type")), "application/json") {
		s.fail(c, apperr.Generic())
		return
	}
	body := map[string]any{}
	data, _ := io.ReadAll(io.LimitReader(c.Request.Body, 1<<20))
	if len(strings.TrimSpace(string(data))) > 0 {
		var raw map[string]any
		if err := json.Unmarshal(data, &raw); err != nil {
			s.fail(c, apperr.Malformed())
			return
		}
		if raw != nil {
			body = raw
		}
	}
	name := "ChatGPT / MCP Integration"
	if v, ok := body["client_name"]; ok && v != nil {
		name = fmt.Sprint(v)
	}
	var uris []string
	if list, ok := body["redirect_uris"].([]any); ok {
		for _, u := range list {
			if u != nil {
				uris = append(uris, fmt.Sprint(u))
			}
		}
	}
	redirectURIs := "https://claude.ai,https://chatgpt.com,https://chat.openai.com,https://todoflow.ddns.net,http://localhost"
	if len(uris) > 0 {
		redirectURIs = strings.Join(uris, ",")
	}
	clientID := "client_" + strings.ReplaceAll(uuid.NewString(), "-", "")[:16]
	secret := strings.ReplaceAll(uuid.NewString(), "-", "") + strings.ReplaceAll(uuid.NewString(), "-", "")
	scopes := "read write all"
	cl := &store.OAuthClient{ID: uuid.New(), ClientID: clientID, ClientSecret: &secret, ClientName: name,
		RedirectURIs: redirectURIs, Scopes: &scopes, CreatedAt: time.Now().UTC()}
	if err := store.InsertOAuthClient(s.ctx(c), s.Store.Pool, cl); err != nil {
		s.fail(c, err)
		return
	}
	respURIs := []string{"https://chatgpt.com", "https://chat.openai.com"}
	if len(uris) > 0 {
		respURIs = uris
	}
	writeJSON(c, http.StatusCreated, orderedMap{
		{"client_id", clientID}, {"client_secret", secret}, {"client_name", name}, {"redirect_uris", respURIs},
		{"grant_types", []string{"authorization_code", "refresh_token"}}, {"response_types", []string{"code"}},
		{"token_endpoint_auth_method", "client_secret_post"}, {"code_challenge_methods_supported", []string{"S256"}},
	})
}

// orderedMap marshals as a JSON object keeping insertion order (Java's LinkedHashMap).
type orderedMap [][2]any

func (m orderedMap) MarshalJSON() ([]byte, error) {
	var sb strings.Builder
	sb.WriteByte('{')
	for i, kv := range m {
		if i > 0 {
			sb.WriteByte(',')
		}
		k, _ := json.Marshal(kv[0])
		v := marshal(kv[1])
		sb.Write(k)
		sb.WriteByte(':')
		sb.Write(v)
	}
	sb.WriteByte('}')
	return []byte(sb.String()), nil
}

func (s *Server) discovery(c *gin.Context) {
	base := requestBase(c)
	writeJSON(c, http.StatusOK, orderedMap{
		{"issuer", base}, {"authorization_endpoint", base + "/oauth2/authorize"}, {"token_endpoint", base + "/oauth2/token"},
		{"registration_endpoint", base + "/oauth2/register"}, {"revocation_endpoint", base + "/oauth2/revoke"},
		{"response_types_supported", []string{"code"}},
		{"grant_types_supported", []string{"authorization_code", "refresh_token"}},
		{"code_challenge_methods_supported", []string{"S256"}},
		{"token_endpoint_auth_methods_supported", []string{"client_secret_post", "client_secret_basic", "none"}},
		{"scopes_supported", []string{"read", "write", "all"}},
	})
}

func (s *Server) protectedResource(c *gin.Context) {
	base := requestBase(c)
	writeJSON(c, http.StatusOK, orderedMap{
		{"resource", base + "/mcp"}, {"authorization_servers", []string{base}},
		{"scopes_supported", []string{"read", "write", "all"}},
		{"bearer_methods_supported", []string{"header"}}, {"resource_documentation", base},
	})
}
