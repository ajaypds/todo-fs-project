package store

import (
	"context"
	"time"

	"github.com/google/uuid"
)

// Project mirrors the projects table.
type Project struct {
	ID          uuid.UUID
	UserID      uuid.UUID
	WorkspaceID *uuid.UUID
	Name        string
	Color       string
	CreatedAt   time.Time
}

const projectCols = `id, user_id, workspace_id, name, color, created_at`

func scanProject(row interface{ Scan(...any) error }) (*Project, error) {
	var p Project
	if err := row.Scan(&p.ID, &p.UserID, &p.WorkspaceID, &p.Name, &p.Color, &p.CreatedAt); err != nil {
		return nil, notFound(err)
	}
	return &p, nil
}

// ProjectsByUser lists a user's projects in the database's natural order (the Java code has no ORDER BY).
func ProjectsByUser(ctx context.Context, q Querier, userID uuid.UUID) ([]*Project, error) {
	rows, err := q.Query(ctx, `SELECT `+projectCols+` FROM projects WHERE user_id = $1`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []*Project
	for rows.Next() {
		p, err := scanProject(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, p)
	}
	return out, rows.Err()
}

// ProjectByID loads one project.
func ProjectByID(ctx context.Context, q Querier, id uuid.UUID) (*Project, error) {
	return scanProject(q.QueryRow(ctx, `SELECT `+projectCols+` FROM projects WHERE id = $1`, id))
}

// InsertProject creates a project (no workspace, like the Java service).
func InsertProject(ctx context.Context, q Querier, p *Project) error {
	_, err := q.Exec(ctx, `INSERT INTO projects (id, user_id, name, color, created_at) VALUES ($1,$2,$3,$4,$5)`,
		p.ID, p.UserID, p.Name, p.Color, p.CreatedAt.UTC())
	return err
}

// UpdateProject saves name and colour.
func UpdateProject(ctx context.Context, q Querier, p *Project) error {
	_, err := q.Exec(ctx, `UPDATE projects SET name = $2, color = $3 WHERE id = $1`, p.ID, p.Name, p.Color)
	return err
}

// DeleteProject removes a project (tasks keep existing with project_id set to NULL by the FK).
func DeleteProject(ctx context.Context, q Querier, id uuid.UUID) error {
	_, err := q.Exec(ctx, `DELETE FROM projects WHERE id = $1`, id)
	return err
}

// Label mirrors the labels table.
type Label struct {
	ID     uuid.UUID
	Name   string
	Color  string
	UserID uuid.UUID
}

// LabelsByUser lists a user's labels.
func LabelsByUser(ctx context.Context, q Querier, userID uuid.UUID) ([]*Label, error) {
	rows, err := q.Query(ctx, `SELECT id, name, color, user_id FROM labels WHERE user_id = $1`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []*Label
	for rows.Next() {
		var l Label
		if err := rows.Scan(&l.ID, &l.Name, &l.Color, &l.UserID); err != nil {
			return nil, err
		}
		out = append(out, &l)
	}
	return out, rows.Err()
}

// LabelsByIDs behaves like JpaRepository.findAllById: existing labels only, no ownership check.
func LabelsByIDs(ctx context.Context, q Querier, ids []uuid.UUID) ([]*Label, error) {
	if len(ids) == 0 {
		return nil, nil
	}
	rows, err := q.Query(ctx, `SELECT id, name, color, user_id FROM labels WHERE id = ANY($1)`, ids)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []*Label
	for rows.Next() {
		var l Label
		if err := rows.Scan(&l.ID, &l.Name, &l.Color, &l.UserID); err != nil {
			return nil, err
		}
		out = append(out, &l)
	}
	return out, rows.Err()
}

// InsertLabel creates a label; a missing name/color surfaces as a NOT NULL violation (HTTP 409).
func InsertLabel(ctx context.Context, q Querier, l *Label, name, color *string) error {
	_, err := q.Exec(ctx, `INSERT INTO labels (id, name, color, user_id) VALUES ($1,$2,$3,$4)`, l.ID, name, color, l.UserID)
	return err
}

// InsertProjectWithoutTimestamp reproduces how the Java backend auto-creates a project from a task's
// projectName: it never sets created_at, so the NOT NULL constraint rejects the insert (HTTP 409).
// Behaviour is kept identical on purpose; fixing it means passing time.Now() here.
func InsertProjectWithoutTimestamp(ctx context.Context, q Querier, id, userID uuid.UUID, name, color string) error {
	_, err := q.Exec(ctx, `INSERT INTO projects (id, user_id, name, color, created_at) VALUES ($1,$2,$3,$4,NULL)`,
		id, userID, name, color)
	return err
}
