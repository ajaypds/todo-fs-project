package store

import (
	"context"
	"time"

	"github.com/google/uuid"
)

// Task is a tasks row joined with the project name and assignee details used in responses.
type Task struct {
	ID                 uuid.UUID
	UserID             uuid.UUID
	Title              string
	Description        *string
	Completed          bool
	Priority           int
	DueDate            *time.Time
	RecurrenceType     *string
	RecurrenceInterval *int
	Position           int
	ProjectID          *uuid.UUID
	ProjectName        *string
	ProjectOwnerID     *uuid.UUID
	ProjectWorkspaceID *uuid.UUID
	CreatedAt          time.Time
	UpdatedAt          time.Time
	AssignedTo         *uuid.UUID
	AssigneeFullName   *string
	AssigneeEmail      *string
}

// AssigneeName mirrors User.getName() for the assignee.
func (t *Task) AssigneeName() *string {
	if t.AssignedTo == nil {
		return nil
	}
	if t.AssigneeFullName != nil && *t.AssigneeFullName != "" {
		return t.AssigneeFullName
	}
	return t.AssigneeEmail
}

const taskSelect = `SELECT t.id, t.user_id, t.title, t.description, t.completed, t.priority, t.due_date,
	t.recurrence_type, t.recurrence_interval, t.position, t.project_id, p.name, p.user_id, p.workspace_id,
	t.created_at, t.updated_at, t.assigned_to, au.full_name, au.email
	FROM tasks t LEFT JOIN projects p ON p.id = t.project_id LEFT JOIN users au ON au.id = t.assigned_to`

func scanTask(row interface{ Scan(...any) error }) (*Task, error) {
	var t Task
	err := row.Scan(&t.ID, &t.UserID, &t.Title, &t.Description, &t.Completed, &t.Priority, &t.DueDate,
		&t.RecurrenceType, &t.RecurrenceInterval, &t.Position, &t.ProjectID, &t.ProjectName, &t.ProjectOwnerID,
		&t.ProjectWorkspaceID, &t.CreatedAt, &t.UpdatedAt, &t.AssignedTo, &t.AssigneeFullName, &t.AssigneeEmail)
	if err != nil {
		return nil, notFound(err)
	}
	t.CreatedAt, t.UpdatedAt = t.CreatedAt.UTC(), t.UpdatedAt.UTC()
	if t.DueDate != nil {
		d := t.DueDate.UTC()
		t.DueDate = &d
	}
	return &t, nil
}

// TaskByID loads a task.
func TaskByID(ctx context.Context, q Querier, id uuid.UUID) (*Task, error) {
	return scanTask(q.QueryRow(ctx, taskSelect+` WHERE t.id = $1`, id))
}

// TasksPage returns one page of a user's tasks ordered by position, and the total count.
func TasksPage(ctx context.Context, q Querier, userID uuid.UUID, projectID *uuid.UUID, limit, offset int64) ([]*Task, int64, error) {
	where, args := ` WHERE t.user_id = $1`, []any{userID}
	if projectID != nil {
		where += ` AND t.project_id = $2`
		args = append(args, *projectID)
	}
	var total int64
	if err := q.QueryRow(ctx, `SELECT count(*) FROM tasks t`+where, args...).Scan(&total); err != nil {
		return nil, 0, err
	}
	n := len(args)
	rows, err := q.Query(ctx, taskSelect+where+` ORDER BY t.position ASC LIMIT $`+itoa(n+1)+` OFFSET $`+itoa(n+2),
		append(args, limit, offset)...)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()
	var out []*Task
	for rows.Next() {
		t, err := scanTask(rows)
		if err != nil {
			return nil, 0, err
		}
		out = append(out, t)
	}
	return out, total, rows.Err()
}

func itoa(n int) string {
	const digits = "0123456789"
	if n < 10 {
		return string(digits[n])
	}
	return itoa(n/10) + string(digits[n%10])
}

// CountTasks is the global task count; the Java code uses it as the position of new tasks.
func CountTasks(ctx context.Context, q Querier) (int, error) {
	var n int
	err := q.QueryRow(ctx, `SELECT count(*) FROM tasks`).Scan(&n)
	return n, err
}

// InsertTask creates a task and its labels.
func InsertTask(ctx context.Context, q Querier, t *Task, labelIDs []uuid.UUID) error {
	_, err := q.Exec(ctx, `INSERT INTO tasks (id, user_id, title, description, completed, priority, due_date,
		recurrence_type, recurrence_interval, position, project_id, created_at, updated_at, assigned_to)
		VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14)`,
		t.ID, t.UserID, t.Title, t.Description, t.Completed, t.Priority, t.DueDate, t.RecurrenceType,
		t.RecurrenceInterval, t.Position, t.ProjectID, t.CreatedAt.UTC(), t.UpdatedAt.UTC(), t.AssignedTo)
	if err != nil {
		return err
	}
	return SetTaskLabels(ctx, q, t.ID, labelIDs)
}

// UpdateTask writes every editable column.
func UpdateTask(ctx context.Context, q Querier, t *Task) error {
	_, err := q.Exec(ctx, `UPDATE tasks SET title=$2, description=$3, completed=$4, priority=$5, due_date=$6,
		recurrence_type=$7, recurrence_interval=$8, position=$9, project_id=$10, updated_at=$11, assigned_to=$12
		WHERE id=$1`,
		t.ID, t.Title, t.Description, t.Completed, t.Priority, t.DueDate, t.RecurrenceType, t.RecurrenceInterval,
		t.Position, t.ProjectID, t.UpdatedAt.UTC(), t.AssignedTo)
	return err
}

// SetTaskPositions sets position = index for each id, in one transaction-friendly loop.
func SetTaskPosition(ctx context.Context, q Querier, id uuid.UUID, position int) error {
	_, err := q.Exec(ctx, `UPDATE tasks SET position = $2 WHERE id = $1`, id, position)
	return err
}

// TasksByIDs behaves like findAllById (existing rows only).
func TasksByIDs(ctx context.Context, q Querier, ids []uuid.UUID) ([]*Task, error) {
	if len(ids) == 0 {
		return nil, nil
	}
	rows, err := q.Query(ctx, taskSelect+` WHERE t.id = ANY($1)`, ids)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []*Task
	for rows.Next() {
		t, err := scanTask(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, t)
	}
	return out, rows.Err()
}

// DeleteTask removes a task; subtasks, comments, labels links and activities cascade in the database.
func DeleteTask(ctx context.Context, q Querier, id uuid.UUID) error {
	_, err := q.Exec(ctx, `DELETE FROM tasks WHERE id = $1`, id)
	return err
}

// SetTaskLabels replaces a task's labels.
func SetTaskLabels(ctx context.Context, q Querier, taskID uuid.UUID, labelIDs []uuid.UUID) error {
	if _, err := q.Exec(ctx, `DELETE FROM task_labels WHERE task_id = $1`, taskID); err != nil {
		return err
	}
	for _, id := range labelIDs {
		if _, err := q.Exec(ctx, `INSERT INTO task_labels (task_id, label_id) VALUES ($1,$2) ON CONFLICT DO NOTHING`, taskID, id); err != nil {
			return err
		}
	}
	return nil
}

// LabelsForTasks loads labels for several tasks at once.
func LabelsForTasks(ctx context.Context, q Querier, taskIDs []uuid.UUID) (map[uuid.UUID][]*Label, error) {
	out := map[uuid.UUID][]*Label{}
	if len(taskIDs) == 0 {
		return out, nil
	}
	rows, err := q.Query(ctx, `SELECT tl.task_id, l.id, l.name, l.color, l.user_id FROM task_labels tl
		JOIN labels l ON l.id = tl.label_id WHERE tl.task_id = ANY($1)`, taskIDs)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	for rows.Next() {
		var tid uuid.UUID
		var l Label
		if err := rows.Scan(&tid, &l.ID, &l.Name, &l.Color, &l.UserID); err != nil {
			return nil, err
		}
		out[tid] = append(out[tid], &l)
	}
	return out, rows.Err()
}

// Subtask mirrors the subtasks table.
type Subtask struct {
	ID        uuid.UUID
	TaskID    uuid.UUID
	Title     string
	Completed bool
	Position  int
	CreatedAt time.Time
	UpdatedAt time.Time
}

const subtaskCols = `id, task_id, title, completed, position, created_at, updated_at`

func scanSubtask(row interface{ Scan(...any) error }) (*Subtask, error) {
	var s Subtask
	if err := row.Scan(&s.ID, &s.TaskID, &s.Title, &s.Completed, &s.Position, &s.CreatedAt, &s.UpdatedAt); err != nil {
		return nil, notFound(err)
	}
	s.CreatedAt, s.UpdatedAt = s.CreatedAt.UTC(), s.UpdatedAt.UTC()
	return &s, nil
}

// SubtasksForTasks loads subtasks ordered by position then creation time.
func SubtasksForTasks(ctx context.Context, q Querier, taskIDs []uuid.UUID) (map[uuid.UUID][]*Subtask, error) {
	out := map[uuid.UUID][]*Subtask{}
	if len(taskIDs) == 0 {
		return out, nil
	}
	rows, err := q.Query(ctx, `SELECT `+subtaskCols+` FROM subtasks WHERE task_id = ANY($1) ORDER BY position ASC, created_at ASC, id ASC`, taskIDs)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	for rows.Next() {
		s, err := scanSubtask(rows)
		if err != nil {
			return nil, err
		}
		out[s.TaskID] = append(out[s.TaskID], s)
	}
	return out, rows.Err()
}

// SubtaskByIDAndTask finds a subtask within its task.
func SubtaskByIDAndTask(ctx context.Context, q Querier, id, taskID uuid.UUID) (*Subtask, error) {
	return scanSubtask(q.QueryRow(ctx, `SELECT `+subtaskCols+` FROM subtasks WHERE id = $1 AND task_id = $2`, id, taskID))
}

// CountSubtasks counts a task's subtasks.
func CountSubtasks(ctx context.Context, q Querier, taskID uuid.UUID) (int, error) {
	var n int
	err := q.QueryRow(ctx, `SELECT count(*) FROM subtasks WHERE task_id = $1`, taskID).Scan(&n)
	return n, err
}

// InsertSubtask creates a subtask.
func InsertSubtask(ctx context.Context, q Querier, s *Subtask) error {
	_, err := q.Exec(ctx, `INSERT INTO subtasks (id, task_id, title, completed, position, created_at, updated_at)
		VALUES ($1,$2,$3,$4,$5,$6,$7)`, s.ID, s.TaskID, s.Title, s.Completed, s.Position, s.CreatedAt.UTC(), s.UpdatedAt.UTC())
	return err
}

// UpdateSubtask saves title, completion, position.
func UpdateSubtask(ctx context.Context, q Querier, s *Subtask) error {
	_, err := q.Exec(ctx, `UPDATE subtasks SET title=$2, completed=$3, position=$4, updated_at=$5 WHERE id=$1`,
		s.ID, s.Title, s.Completed, s.Position, s.UpdatedAt.UTC())
	return err
}

// DeleteSubtask removes a subtask.
func DeleteSubtask(ctx context.Context, q Querier, id uuid.UUID) error {
	_, err := q.Exec(ctx, `DELETE FROM subtasks WHERE id = $1`, id)
	return err
}

// CommentCounts returns the number of comments per task.
func CommentCounts(ctx context.Context, q Querier, taskIDs []uuid.UUID) (map[uuid.UUID]int, error) {
	out := map[uuid.UUID]int{}
	if len(taskIDs) == 0 {
		return out, nil
	}
	rows, err := q.Query(ctx, `SELECT task_id, count(*) FROM task_comments WHERE task_id = ANY($1) GROUP BY task_id`, taskIDs)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	for rows.Next() {
		var id uuid.UUID
		var n int
		if err := rows.Scan(&id, &n); err != nil {
			return nil, err
		}
		out[id] = n
	}
	return out, rows.Err()
}
