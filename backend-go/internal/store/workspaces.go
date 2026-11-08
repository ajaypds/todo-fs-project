package store

import (
	"context"
	"time"

	"github.com/google/uuid"
)

// Workspace is a workspaces row with its owner's display fields.
type Workspace struct {
	ID            uuid.UUID
	Name          string
	OwnerID       uuid.UUID
	OwnerFullName *string
	OwnerEmail    string
	CreatedAt     time.Time
}

// OwnerName is full name, else email (as in WorkspaceResponse.ownerName).
func (w *Workspace) OwnerName() string {
	if w.OwnerFullName != nil {
		return *w.OwnerFullName
	}
	return w.OwnerEmail
}

// Member is a workspace_members row joined with the user.
type Member struct {
	ID          uuid.UUID
	WorkspaceID uuid.UUID
	UserID      uuid.UUID
	Role        string
	JoinedAt    time.Time
	Email       string
	FullName    *string
}

const workspaceSelect = `SELECT w.id, w.name, w.owner_id, o.full_name, o.email, w.created_at
	FROM workspaces w JOIN users o ON o.id = w.owner_id`

func scanWorkspace(row interface{ Scan(...any) error }) (*Workspace, error) {
	var w Workspace
	if err := row.Scan(&w.ID, &w.Name, &w.OwnerID, &w.OwnerFullName, &w.OwnerEmail, &w.CreatedAt); err != nil {
		return nil, notFound(err)
	}
	w.CreatedAt = w.CreatedAt.UTC()
	return &w, nil
}

// WorkspacesForUser lists the workspaces a user belongs to, oldest first.
func WorkspacesForUser(ctx context.Context, q Querier, userID uuid.UUID) ([]*Workspace, error) {
	rows, err := q.Query(ctx, workspaceSelect+` JOIN workspace_members m ON m.workspace_id = w.id
		WHERE m.user_id = $1 ORDER BY w.created_at ASC, w.id ASC`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []*Workspace
	for rows.Next() {
		w, err := scanWorkspace(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, w)
	}
	return out, rows.Err()
}

// WorkspaceByID loads a workspace.
func WorkspaceByID(ctx context.Context, q Querier, id uuid.UUID) (*Workspace, error) {
	return scanWorkspace(q.QueryRow(ctx, workspaceSelect+` WHERE w.id = $1`, id))
}

// InsertWorkspace creates a workspace.
func InsertWorkspace(ctx context.Context, q Querier, id uuid.UUID, name string, ownerID uuid.UUID, at time.Time) error {
	_, err := q.Exec(ctx, `INSERT INTO workspaces (id, name, owner_id, created_at) VALUES ($1,$2,$3,$4)`, id, name, ownerID, at.UTC())
	return err
}

// RenameWorkspace updates the name.
func RenameWorkspace(ctx context.Context, q Querier, id uuid.UUID, name string) error {
	_, err := q.Exec(ctx, `UPDATE workspaces SET name = $2 WHERE id = $1`, id, name)
	return err
}

// DeleteWorkspace removes a workspace; members cascade.
func DeleteWorkspace(ctx context.Context, q Querier, id uuid.UUID) error {
	_, err := q.Exec(ctx, `DELETE FROM workspaces WHERE id = $1`, id)
	return err
}

const memberSelect = `SELECT m.id, m.workspace_id, m.user_id, m.role, m.joined_at, u.email, u.full_name
	FROM workspace_members m JOIN users u ON u.id = m.user_id`

func scanMember(row interface{ Scan(...any) error }) (*Member, error) {
	var m Member
	if err := row.Scan(&m.ID, &m.WorkspaceID, &m.UserID, &m.Role, &m.JoinedAt, &m.Email, &m.FullName); err != nil {
		return nil, notFound(err)
	}
	m.JoinedAt = m.JoinedAt.UTC()
	return &m, nil
}

// MemberByWorkspaceAndUser finds a membership.
func MemberByWorkspaceAndUser(ctx context.Context, q Querier, workspaceID, userID uuid.UUID) (*Member, error) {
	return scanMember(q.QueryRow(ctx, memberSelect+` WHERE m.workspace_id = $1 AND m.user_id = $2`, workspaceID, userID))
}

// MembersOfWorkspace lists members oldest first.
func MembersOfWorkspace(ctx context.Context, q Querier, workspaceID uuid.UUID) ([]*Member, error) {
	rows, err := q.Query(ctx, memberSelect+` WHERE m.workspace_id = $1 ORDER BY m.joined_at ASC, m.id ASC`, workspaceID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []*Member
	for rows.Next() {
		m, err := scanMember(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, m)
	}
	return out, rows.Err()
}

// CountMembers counts a workspace's members.
func CountMembers(ctx context.Context, q Querier, workspaceID uuid.UUID) (int64, error) {
	var n int64
	err := q.QueryRow(ctx, `SELECT count(*) FROM workspace_members WHERE workspace_id = $1`, workspaceID).Scan(&n)
	return n, err
}

// InsertMember adds a member.
func InsertMember(ctx context.Context, q Querier, id, workspaceID, userID uuid.UUID, role string, at time.Time) error {
	_, err := q.Exec(ctx, `INSERT INTO workspace_members (id, workspace_id, user_id, role, joined_at) VALUES ($1,$2,$3,$4,$5)`,
		id, workspaceID, userID, role, at.UTC())
	return err
}

// SetMemberRole changes a member's role.
func SetMemberRole(ctx context.Context, q Querier, id uuid.UUID, role string) error {
	_, err := q.Exec(ctx, `UPDATE workspace_members SET role = $2 WHERE id = $1`, id, role)
	return err
}

// DeleteMember removes a membership.
func DeleteMember(ctx context.Context, q Querier, id uuid.UUID) error {
	_, err := q.Exec(ctx, `DELETE FROM workspace_members WHERE id = $1`, id)
	return err
}

// MemberExists reports whether the user belongs to the workspace.
func MemberExists(ctx context.Context, q Querier, workspaceID, userID uuid.UUID) (bool, error) {
	var ok bool
	err := q.QueryRow(ctx, `SELECT EXISTS(SELECT 1 FROM workspace_members WHERE workspace_id = $1 AND user_id = $2)`, workspaceID, userID).Scan(&ok)
	return ok, err
}

// OAuthClient mirrors oauth_clients.
type OAuthClient struct {
	ID           uuid.UUID
	ClientID     string
	ClientSecret *string
	ClientName   string
	RedirectURIs string
	Scopes       *string
	CreatedAt    time.Time
}

// OAuthCode mirrors oauth_authorization_codes joined with its user.
type OAuthCode struct {
	ID                  uuid.UUID
	Code                string
	UserID              uuid.UUID
	ClientID            string
	RedirectURI         string
	Scope               *string
	CodeChallenge       *string
	CodeChallengeMethod *string
	ExpiresAt           time.Time
	Used                bool
	CreatedAt           time.Time
}

// OAuthClientByClientID finds a client.
func OAuthClientByClientID(ctx context.Context, q Querier, clientID string) (*OAuthClient, error) {
	var c OAuthClient
	err := q.QueryRow(ctx, `SELECT id, client_id, client_secret, client_name, redirect_uris, scopes, created_at
		FROM oauth_clients WHERE client_id = $1`, clientID).
		Scan(&c.ID, &c.ClientID, &c.ClientSecret, &c.ClientName, &c.RedirectURIs, &c.Scopes, &c.CreatedAt)
	if err != nil {
		return nil, notFound(err)
	}
	return &c, nil
}

// InsertOAuthClient registers a client.
func InsertOAuthClient(ctx context.Context, q Querier, c *OAuthClient) error {
	_, err := q.Exec(ctx, `INSERT INTO oauth_clients (id, client_id, client_secret, client_name, redirect_uris, scopes, created_at)
		VALUES ($1,$2,$3,$4,$5,$6,$7)`, c.ID, c.ClientID, c.ClientSecret, c.ClientName, c.RedirectURIs, c.Scopes, c.CreatedAt.UTC())
	return err
}

// InsertOAuthCode stores an authorization code.
func InsertOAuthCode(ctx context.Context, q Querier, c *OAuthCode) error {
	_, err := q.Exec(ctx, `INSERT INTO oauth_authorization_codes (id, code, user_id, client_id, redirect_uri, scope,
		code_challenge, code_challenge_method, expires_at, used, created_at) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)`,
		c.ID, c.Code, c.UserID, c.ClientID, c.RedirectURI, c.Scope, c.CodeChallenge, c.CodeChallengeMethod,
		c.ExpiresAt.UTC(), c.Used, c.CreatedAt.UTC())
	return err
}

// OAuthCodeByCode finds a code.
func OAuthCodeByCode(ctx context.Context, q Querier, code string) (*OAuthCode, error) {
	var c OAuthCode
	var used *bool
	err := q.QueryRow(ctx, `SELECT id, code, user_id, client_id, redirect_uri, scope, code_challenge,
		code_challenge_method, expires_at, used, created_at FROM oauth_authorization_codes WHERE code = $1`, code).
		Scan(&c.ID, &c.Code, &c.UserID, &c.ClientID, &c.RedirectURI, &c.Scope, &c.CodeChallenge,
			&c.CodeChallengeMethod, &c.ExpiresAt, &used, &c.CreatedAt)
	if err != nil {
		return nil, notFound(err)
	}
	c.Used = used != nil && *used
	c.ExpiresAt = c.ExpiresAt.UTC()
	return &c, nil
}

// MarkOAuthCodeUsed flags a code as consumed.
func MarkOAuthCodeUsed(ctx context.Context, q Querier, id uuid.UUID) error {
	_, err := q.Exec(ctx, `UPDATE oauth_authorization_codes SET used = TRUE WHERE id = $1`, id)
	return err
}
