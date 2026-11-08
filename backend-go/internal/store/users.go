package store

import (
	"context"
	"strings"
	"time"

	"github.com/google/uuid"
)

// User mirrors the users table.
type User struct {
	ID                        uuid.UUID
	Email                     string
	Password                  string
	FullName                  *string
	Timezone                  string
	EmailVerified             bool
	VerificationCode          *string
	VerificationCodeExpiresAt *time.Time
	CreatedAt                 time.Time
}

// Name is the display name: the full name when set and not blank, otherwise the email.
func (u User) Name() string {
	if u.FullName != nil && strings.TrimSpace(*u.FullName) != "" {
		return *u.FullName
	}
	return u.Email
}

const userCols = `id, email, password, full_name, timezone, email_verified, verification_code, verification_code_expires_at, created_at`

func scanUser(row interface{ Scan(...any) error }) (*User, error) {
	var u User
	err := row.Scan(&u.ID, &u.Email, &u.Password, &u.FullName, &u.Timezone, &u.EmailVerified,
		&u.VerificationCode, &u.VerificationCodeExpiresAt, &u.CreatedAt)
	if err != nil {
		return nil, notFound(err)
	}
	u.CreatedAt = u.CreatedAt.UTC()
	return &u, nil
}

// UserByEmail finds a user by (already normalised) email.
func UserByEmail(ctx context.Context, q Querier, email string) (*User, error) {
	return scanUser(q.QueryRow(ctx, `SELECT `+userCols+` FROM users WHERE email = $1`, email))
}

// UserByID finds a user by id.
func UserByID(ctx context.Context, q Querier, id uuid.UUID) (*User, error) {
	return scanUser(q.QueryRow(ctx, `SELECT `+userCols+` FROM users WHERE id = $1`, id))
}

// InsertUser creates a user.
func InsertUser(ctx context.Context, q Querier, u *User) error {
	_, err := q.Exec(ctx, `INSERT INTO users (id, email, password, full_name, timezone, email_verified,
		verification_code, verification_code_expires_at, created_at) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)`,
		u.ID, u.Email, u.Password, u.FullName, u.Timezone, u.EmailVerified,
		u.VerificationCode, utcPtr(u.VerificationCodeExpiresAt), u.CreatedAt.UTC())
	return err
}

// MarkVerified flags the email as verified and clears the code.
func MarkVerified(ctx context.Context, q Querier, id uuid.UUID) error {
	_, err := q.Exec(ctx, `UPDATE users SET email_verified = TRUE, verification_code = NULL,
		verification_code_expires_at = NULL WHERE id = $1`, id)
	return err
}

// SetVerificationCode stores a new code.
func SetVerificationCode(ctx context.Context, q Querier, id uuid.UUID, code string, expires time.Time) error {
	_, err := q.Exec(ctx, `UPDATE users SET verification_code = $2, verification_code_expires_at = $3 WHERE id = $1`,
		id, code, expires.UTC())
	return err
}

// SetTimezone updates the user's timezone.
func SetTimezone(ctx context.Context, q Querier, id uuid.UUID, tz string) error {
	_, err := q.Exec(ctx, `UPDATE users SET timezone = $2 WHERE id = $1`, id, tz)
	return err
}

func utcPtr(t *time.Time) *time.Time {
	if t == nil {
		return nil
	}
	u := t.UTC()
	return &u
}

// RefreshToken mirrors refresh_tokens.
type RefreshToken struct {
	ID        uuid.UUID
	Token     string
	UserID    uuid.UUID
	ExpiresAt time.Time
	Revoked   bool
}

// InsertRefreshToken stores a refresh token.
func InsertRefreshToken(ctx context.Context, q Querier, token string, userID uuid.UUID, expires time.Time) error {
	_, err := q.Exec(ctx, `INSERT INTO refresh_tokens (id, token, user_id, expires_at, revoked) VALUES ($1,$2,$3,$4,FALSE)`,
		uuid.New(), token, userID, expires.UTC())
	return err
}

// RefreshTokenByToken loads a token with its owner.
func RefreshTokenByToken(ctx context.Context, q Querier, token string) (*RefreshToken, *User, error) {
	var rt RefreshToken
	row := q.QueryRow(ctx, `SELECT r.id, r.token, r.user_id, r.expires_at, r.revoked FROM refresh_tokens r WHERE r.token = $1`, token)
	if err := row.Scan(&rt.ID, &rt.Token, &rt.UserID, &rt.ExpiresAt, &rt.Revoked); err != nil {
		return nil, nil, notFound(err)
	}
	rt.ExpiresAt = rt.ExpiresAt.UTC()
	u, err := UserByID(ctx, q, rt.UserID)
	if err != nil {
		return nil, nil, err
	}
	return &rt, u, nil
}

// RevokeRefreshToken marks a token revoked.
func RevokeRefreshToken(ctx context.Context, q Querier, id uuid.UUID) error {
	_, err := q.Exec(ctx, `UPDATE refresh_tokens SET revoked = TRUE WHERE id = $1`, id)
	return err
}
