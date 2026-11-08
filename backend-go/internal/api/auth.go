package api

import (
	"crypto/rand"
	"fmt"
	"log"
	"math/big"
	"net/http"
	"regexp"
	"strings"
	"time"

	"github.com/ajaypds/todo-fs-project/backend-go/internal/apperr"
	"github.com/ajaypds/todo-fs-project/backend-go/internal/jsonx"
	"github.com/ajaypds/todo-fs-project/backend-go/internal/security"
	"github.com/ajaypds/todo-fs-project/backend-go/internal/store"
	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
)

func (s *Server) registerAuth(v1 *gin.RouterGroup) {
	g := v1.Group("/auth")
	g.POST("/register", s.handle(s.register))
	g.POST("/login", s.handle(s.login))
	g.POST("/verify-email", s.handle(s.verifyEmail))
	g.POST("/resend-verification", s.handle(s.resendVerification))
	g.POST("/refresh", s.handle(s.refresh))
}

func normEmail(s string) string { return strings.ToLower(strings.TrimSpace(s)) }

func verificationCode() string {
	n, _ := rand.Int(rand.Reader, big.NewInt(900000))
	return fmt.Sprint(100000 + n.Int64())
}

// logVerificationCode prints the OTP banner: email delivery is console-only, exactly like the Java backend.
func logVerificationCode(email, code string) {
	banner := "\n" +
		"============================================================\n" +
		" [EMAIL VERIFICATION OTP]\n" +
		" To: " + email + "\n" +
		" Verification Code: " + code + "\n" +
		" Expires in: 15 minutes\n" +
		" (Email service is in console mode. Configure SMTP in application.yaml to send real emails)\n" +
		"============================================================"
	log.Println(banner)
}

func (s *Server) register(c *gin.Context) error {
	var req struct {
		Email    jsonx.Str `json:"email"`
		Password jsonx.Str `json:"password"`
		FullName jsonx.Str `json:"fullName"`
		Timezone jsonx.Str `json:"timezone"`
	}
	if err := bind(c, &req); err != nil {
		return err
	}
	v := newCheck()
	v.notBlank("email", req.Email.Ptr(), "Email is required")
	v.email("email", req.Email.Ptr(), "Please provide a valid email address")
	v.notBlank("password", req.Password.Ptr(), "Password is required")
	v.size("password", req.Password.Ptr(), 6, 72, "Password must be between 6 and 72 characters")
	v.notBlank("fullName", req.FullName.Ptr(), "Full name is required")
	v.size("fullName", req.FullName.Ptr(), 2, 100, "Full name must be between 2 and 100 characters")
	v.size("timezone", req.Timezone.Ptr(), 0, 50, "Timezone must not exceed 50 characters")
	if err := v.err(); err != nil {
		return err
	}

	ctx := s.ctx(c)
	email := normEmail(req.Email.V)
	if _, err := store.UserByEmail(ctx, s.Store.Pool, email); err == nil {
		return apperr.BadRequest("Email already exists")
	} else if err != store.ErrNotFound {
		return err
	}
	tz := "UTC"
	if !blank(req.Timezone.Ptr()) {
		tz = strings.TrimSpace(req.Timezone.V)
	}
	hash, err := security.HashPassword(req.Password.V)
	if err != nil {
		return err
	}
	code := verificationCode()
	now := time.Now().UTC()
	expires := now.Add(15 * time.Minute)
	fullName := strings.TrimSpace(req.FullName.V)
	user := &store.User{
		ID: uuid.New(), Email: email, Password: hash, FullName: &fullName, Timezone: tz,
		VerificationCode: &code, VerificationCodeExpiresAt: &expires, CreatedAt: now,
	}
	workspaceName := "Personal Workspace"
	if fullName != "" {
		workspaceName = fullName + "'s Workspace"
	}
	err = s.Store.Tx(ctx, func(q store.Querier) error {
		if err := store.InsertUser(ctx, q, user); err != nil {
			return err
		}
		wsID := uuid.New()
		if err := store.InsertWorkspace(ctx, q, wsID, workspaceName, user.ID, now); err != nil {
			return err
		}
		return store.InsertMember(ctx, q, uuid.New(), wsID, user.ID, "OWNER", now)
	})
	if err != nil {
		return err
	}
	logVerificationCode(email, code)
	writeJSON(c, http.StatusCreated, authResponse{
		EmailVerificationRequired: true, Email: &email, Message: strp("Verification code sent to email"),
	})
	return nil
}

// issueTokens creates an access + refresh token pair and stores the refresh token (valid 7 days).
func (s *Server) issueTokens(c *gin.Context, u *store.User) (access, refresh string, err error) {
	access = s.JWT.AccessToken(u.ID, u.Email)
	refresh = s.JWT.RefreshToken(u.Email)
	err = store.InsertRefreshToken(s.ctx(c), s.Store.Pool, refresh, u.ID, time.Now().Add(7*24*time.Hour))
	return
}

func (s *Server) login(c *gin.Context) error {
	var req struct {
		Email    jsonx.Str `json:"email"`
		Password jsonx.Str `json:"password"`
	}
	if err := bind(c, &req); err != nil {
		return err
	}
	v := newCheck()
	v.notBlank("email", req.Email.Ptr(), "must not be blank")
	v.email("email", req.Email.Ptr(), "must be a well-formed email address")
	v.notBlank("password", req.Password.Ptr(), "must not be blank")
	if err := v.err(); err != nil {
		return err
	}
	u, err := store.UserByEmail(s.ctx(c), s.Store.Pool, normEmail(req.Email.V))
	if err == store.ErrNotFound {
		return apperr.Unauthorized("Invalid credentials")
	} else if err != nil {
		return err
	}
	if !security.CheckPassword(u.Password, req.Password.V) {
		return apperr.Unauthorized("Invalid credentials")
	}
	if !u.EmailVerified {
		return apperr.Unauthorized("Email not verified. Please verify your email before logging in.")
	}
	access, refresh, err := s.issueTokens(c, u)
	if err != nil {
		return err
	}
	writeJSON(c, http.StatusOK, authResponse{Token: &access, RefreshToken: &refresh})
	return nil
}

func (s *Server) verifyEmail(c *gin.Context) error {
	var req struct {
		Email jsonx.Str `json:"email"`
		Code  jsonx.Str `json:"code"`
	}
	if err := bind(c, &req); err != nil {
		return err
	}
	v := newCheck()
	v.notBlank("email", req.Email.Ptr(), "Email is required")
	v.notBlank("code", req.Code.Ptr(), "Verification code is required")
	if err := v.err(); err != nil {
		return err
	}
	ctx := s.ctx(c)
	u, err := store.UserByEmail(ctx, s.Store.Pool, normEmail(req.Email.V))
	if err == store.ErrNotFound {
		return apperr.BadRequest("User not found with provided email")
	} else if err != nil {
		return err
	}
	if u.EmailVerified {
		return apperr.BadRequest("Email is already verified")
	}
	if u.VerificationCode == nil || *u.VerificationCode != strings.TrimSpace(req.Code.V) {
		return apperr.BadRequest("Invalid verification code")
	}
	if u.VerificationCodeExpiresAt == nil || u.VerificationCodeExpiresAt.Before(time.Now()) {
		return apperr.BadRequest("Verification code has expired. Please request a new one.")
	}
	if err := store.MarkVerified(ctx, s.Store.Pool, u.ID); err != nil {
		return err
	}
	access, refresh, err := s.issueTokens(c, u)
	if err != nil {
		return err
	}
	writeJSON(c, http.StatusOK, authResponse{
		Token: &access, RefreshToken: &refresh, Email: &u.Email, Message: strp("Email verified successfully"),
	})
	return nil
}

func (s *Server) resendVerification(c *gin.Context) error {
	var req struct {
		Email jsonx.Str `json:"email"`
	}
	if err := bind(c, &req); err != nil {
		return err
	}
	v := newCheck()
	v.notBlank("email", req.Email.Ptr(), "Email is required")
	if err := v.err(); err != nil {
		return err
	}
	ctx := s.ctx(c)
	email := normEmail(req.Email.V)
	u, err := store.UserByEmail(ctx, s.Store.Pool, email)
	if err == store.ErrNotFound {
		return apperr.BadRequest("User not found with provided email")
	} else if err != nil {
		return err
	}
	if u.EmailVerified {
		return apperr.BadRequest("Email is already verified")
	}
	code := verificationCode()
	if err := store.SetVerificationCode(ctx, s.Store.Pool, u.ID, code, time.Now().Add(15*time.Minute)); err != nil {
		return err
	}
	logVerificationCode(email, code)
	writeJSON(c, http.StatusOK, authResponse{
		EmailVerificationRequired: true, Email: &email,
		Message: strp("A new verification code has been generated and sent to email"),
	})
	return nil
}

func (s *Server) refresh(c *gin.Context) error {
	var req struct {
		RefreshToken jsonx.Str `json:"refreshToken"`
	}
	if err := bind(c, &req); err != nil {
		return err
	}
	if !req.RefreshToken.OK {
		return apperr.Unauthorized("Invalid refresh token")
	}
	rt, u, err := store.RefreshTokenByToken(s.ctx(c), s.Store.Pool, req.RefreshToken.V)
	if err == store.ErrNotFound {
		return apperr.Unauthorized("Invalid refresh token")
	} else if err != nil {
		return err
	}
	if rt.Revoked {
		return apperr.Unauthorized("Refresh token revoked")
	}
	if rt.ExpiresAt.Before(time.Now()) {
		return apperr.Unauthorized("Refresh token expired")
	}
	access := s.JWT.AccessToken(u.ID, u.Email)
	token := rt.Token
	writeJSON(c, http.StatusOK, authResponse{Token: &access, RefreshToken: &token})
	return nil
}

// ---- users ------------------------------------------------------------------------------------------

func (s *Server) registerUsers(v1 *gin.RouterGroup) {
	g := v1.Group("/users")
	g.GET("/me", s.handle(s.getMe))
	g.PATCH("/me/timezone", s.handle(s.updateTimezone))
}

func toUserResponse(u *store.User) userResponse {
	return userResponse{ID: u.ID, Email: u.Email, FullName: u.FullName, Timezone: u.Timezone,
		EmailVerified: u.EmailVerified, CreatedAt: jsonx.From(u.CreatedAt)}
}

func (s *Server) getMe(c *gin.Context) error {
	u, err := store.UserByID(s.ctx(c), s.Store.Pool, s.me(c).ID)
	if err != nil {
		return apperr.Generic() // orElseThrow() -> NoSuchElementException
	}
	writeJSON(c, http.StatusOK, toUserResponse(u))
	return nil
}

var zoneOffsetRe = regexp.MustCompile(`^[+-](0?\d|1[0-8])(:?[0-5]\d(:?[0-5]\d)?)?$`)

// validZoneID mirrors ZoneId.of: region ids, "Z", fixed offsets, and UTC/GMT/UT with an optional offset.
func validZoneID(id string) bool {
	if id == "" || id == "Local" {
		return false
	}
	if id == "Z" || zoneOffsetRe.MatchString(id) {
		return true
	}
	for _, prefix := range []string{"UTC", "GMT", "UT"} {
		if id == prefix {
			return true
		}
		if strings.HasPrefix(id, prefix) && zoneOffsetRe.MatchString(id[len(prefix):]) {
			return true
		}
	}
	_, err := time.LoadLocation(id)
	return err == nil
}

func (s *Server) updateTimezone(c *gin.Context) error {
	var req struct {
		Timezone jsonx.Str `json:"timezone"`
	}
	if err := bind(c, &req); err != nil {
		return err
	}
	v := newCheck()
	v.notBlank("timezone", req.Timezone.Ptr(), "Timezone must not be blank")
	if err := v.err(); err != nil {
		return err
	}
	if !validZoneID(req.Timezone.V) {
		return apperr.BadRequest("Invalid timezone identifier: " + req.Timezone.V)
	}
	ctx := s.ctx(c)
	u, err := store.UserByID(ctx, s.Store.Pool, s.me(c).ID)
	if err != nil {
		return apperr.Generic()
	}
	if err := store.SetTimezone(ctx, s.Store.Pool, u.ID, req.Timezone.V); err != nil {
		return err
	}
	u.Timezone = req.Timezone.V
	writeJSON(c, http.StatusOK, toUserResponse(u))
	return nil
}
