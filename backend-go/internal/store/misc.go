package store

import (
	"context"
	"time"

	"github.com/google/uuid"
)

// Comment is a task comment joined with its author.
type Comment struct {
	ID          uuid.UUID
	TaskID      uuid.UUID
	UserID      uuid.UUID
	Content     string
	CreatedAt   time.Time
	UpdatedAt   time.Time
	AuthorName  string
	AuthorEmail string
}

const commentSelect = `SELECT c.id, c.task_id, c.user_id, c.content, c.created_at, c.updated_at, u.full_name, u.email
	FROM task_comments c JOIN users u ON u.id = c.user_id`

func scanComment(row interface{ Scan(...any) error }) (*Comment, error) {
	var c Comment
	var full *string
	if err := row.Scan(&c.ID, &c.TaskID, &c.UserID, &c.Content, &c.CreatedAt, &c.UpdatedAt, &full, &c.AuthorEmail); err != nil {
		return nil, notFound(err)
	}
	c.CreatedAt, c.UpdatedAt = c.CreatedAt.UTC(), c.UpdatedAt.UTC()
	c.AuthorName = c.AuthorEmail
	if full != nil && *full != "" {
		c.AuthorName = *full
	}
	return &c, nil
}

// CommentsByTask lists comments oldest first.
func CommentsByTask(ctx context.Context, q Querier, taskID uuid.UUID) ([]*Comment, error) {
	rows, err := q.Query(ctx, commentSelect+` WHERE c.task_id = $1 ORDER BY c.created_at ASC, c.id ASC`, taskID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []*Comment
	for rows.Next() {
		c, err := scanComment(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, c)
	}
	return out, rows.Err()
}

// CommentByID loads one comment.
func CommentByID(ctx context.Context, q Querier, id uuid.UUID) (*Comment, error) {
	return scanComment(q.QueryRow(ctx, commentSelect+` WHERE c.id = $1`, id))
}

// InsertComment creates a comment.
func InsertComment(ctx context.Context, q Querier, c *Comment) error {
	_, err := q.Exec(ctx, `INSERT INTO task_comments (id, task_id, user_id, content, created_at, updated_at) VALUES ($1,$2,$3,$4,$5,$6)`,
		c.ID, c.TaskID, c.UserID, c.Content, c.CreatedAt.UTC(), c.UpdatedAt.UTC())
	return err
}

// UpdateComment saves new content.
func UpdateComment(ctx context.Context, q Querier, id uuid.UUID, content string, at time.Time) error {
	_, err := q.Exec(ctx, `UPDATE task_comments SET content = $2, updated_at = $3 WHERE id = $1`, id, content, at.UTC())
	return err
}

// DeleteComment removes a comment.
func DeleteComment(ctx context.Context, q Querier, id uuid.UUID) error {
	_, err := q.Exec(ctx, `DELETE FROM task_comments WHERE id = $1`, id)
	return err
}

// Activity mirrors the activities table.
type Activity struct {
	ID        uuid.UUID
	Type      string
	Message   string
	CreatedAt time.Time
}

// InsertActivity records an audit entry.
func InsertActivity(ctx context.Context, q Querier, typ, message string, at time.Time, userID uuid.UUID, taskID *uuid.UUID) error {
	_, err := q.Exec(ctx, `INSERT INTO activities (id, type, message, created_at, user_id, task_id) VALUES ($1,$2,$3,$4,$5,$6)`,
		uuid.New(), typ, message, at.UTC(), userID, taskID)
	return err
}

func scanActivities(rows interface {
	Next() bool
	Scan(...any) error
	Err() error
	Close()
}) ([]*Activity, error) {
	defer rows.Close()
	var out []*Activity
	for rows.Next() {
		var a Activity
		if err := rows.Scan(&a.ID, &a.Type, &a.Message, &a.CreatedAt); err != nil {
			return nil, err
		}
		a.CreatedAt = a.CreatedAt.UTC()
		out = append(out, &a)
	}
	return out, rows.Err()
}

// ActivitiesByUser lists a user's activities, newest first.
func ActivitiesByUser(ctx context.Context, q Querier, userID uuid.UUID) ([]*Activity, error) {
	rows, err := q.Query(ctx, `SELECT id, type, message, created_at FROM activities WHERE user_id = $1 ORDER BY created_at DESC, id DESC`, userID)
	if err != nil {
		return nil, err
	}
	return scanActivities(rows)
}

// ActivitiesByTask lists a task's activities, newest first.
func ActivitiesByTask(ctx context.Context, q Querier, taskID uuid.UUID) ([]*Activity, error) {
	rows, err := q.Query(ctx, `SELECT id, type, message, created_at FROM activities WHERE task_id = $1 ORDER BY created_at DESC, id DESC`, taskID)
	if err != nil {
		return nil, err
	}
	return scanActivities(rows)
}

// APIKey mirrors api_keys.
type APIKey struct {
	ID         uuid.UUID
	UserID     uuid.UUID
	Name       string
	KeyHash    string
	KeyPrefix  string
	CreatedAt  time.Time
	LastUsedAt *time.Time
}

// InsertAPIKey stores a key hash.
func InsertAPIKey(ctx context.Context, q Querier, k *APIKey) error {
	_, err := q.Exec(ctx, `INSERT INTO api_keys (id, user_id, name, key_hash, key_prefix, created_at) VALUES ($1,$2,$3,$4,$5,$6)`,
		k.ID, k.UserID, k.Name, k.KeyHash, k.KeyPrefix, k.CreatedAt.UTC())
	return err
}

// APIKeysByUser lists keys newest first.
func APIKeysByUser(ctx context.Context, q Querier, userID uuid.UUID) ([]*APIKey, error) {
	rows, err := q.Query(ctx, `SELECT id, user_id, name, key_hash, key_prefix, created_at, last_used_at
		FROM api_keys WHERE user_id = $1 ORDER BY created_at DESC, id DESC`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []*APIKey
	for rows.Next() {
		var k APIKey
		if err := rows.Scan(&k.ID, &k.UserID, &k.Name, &k.KeyHash, &k.KeyPrefix, &k.CreatedAt, &k.LastUsedAt); err != nil {
			return nil, err
		}
		k.CreatedAt = k.CreatedAt.UTC()
		k.LastUsedAt = utcPtr(k.LastUsedAt)
		out = append(out, &k)
	}
	return out, rows.Err()
}

// DeleteAPIKey removes a user's key (no error when it doesn't exist, like deleteByIdAndUserId).
func DeleteAPIKey(ctx context.Context, q Querier, id, userID uuid.UUID) error {
	_, err := q.Exec(ctx, `DELETE FROM api_keys WHERE id = $1 AND user_id = $2`, id, userID)
	return err
}

// UserByAPIKeyHash validates a key hash, records the use, and returns the owner.
func UserByAPIKeyHash(ctx context.Context, q Querier, hash string, now time.Time) (*User, error) {
	var userID uuid.UUID
	var keyID uuid.UUID
	if err := q.QueryRow(ctx, `SELECT id, user_id FROM api_keys WHERE key_hash = $1`, hash).Scan(&keyID, &userID); err != nil {
		return nil, notFound(err)
	}
	if _, err := q.Exec(ctx, `UPDATE api_keys SET last_used_at = $2 WHERE id = $1`, keyID, now.UTC()); err != nil {
		return nil, err
	}
	return UserByID(ctx, q, userID)
}
