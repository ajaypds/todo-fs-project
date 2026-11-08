package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/ajaypds/todo-fs-project/backend-go/internal/apperr"
	"github.com/ajaypds/todo-fs-project/backend-go/internal/jsonx"
	"github.com/ajaypds/todo-fs-project/backend-go/internal/store"
	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
)

func (s *Server) registerTasks(v1 *gin.RouterGroup) {
	g := v1.Group("/tasks")
	g.POST("", s.handle(s.createTask))
	g.GET("", s.handle(s.listTasks))
	g.POST("/reorder", s.handle(s.reorderTasks))
	g.GET("/:taskId", s.handle(s.getTask))
	g.PUT("/:taskId", s.handle(s.updateTask))
	g.DELETE("/:taskId", s.handle(s.deleteTask))
	g.POST("/:taskId/subtasks", s.handle(s.createSubtask))
	g.PATCH("/:taskId/subtasks/:subtaskId", s.handle(s.updateSubtask))
	g.DELETE("/:taskId/subtasks/:subtaskId", s.handle(s.deleteSubtask))
}

// recurrence is an optional RecurrenceType enum. Like Jackson it accepts the exact (case-sensitive) names
// and, by default, ordinals; anything else is a malformed body.
type recurrence struct {
	V  string
	OK bool
}

var recurrenceNames = []string{"NONE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY"}

func (r *recurrence) UnmarshalJSON(b []byte) error {
	b = bytes.TrimSpace(b)
	if bytes.Equal(b, []byte("null")) {
		return nil
	}
	var text string
	if len(b) > 0 && b[0] == '"' {
		if err := json.Unmarshal(b, &text); err != nil {
			return err
		}
		for _, n := range recurrenceNames {
			if n == text {
				r.V, r.OK = n, true
				return nil
			}
		}
		return fmt.Errorf("unknown RecurrenceType %q", text) // includes "": Jackson does not coerce "" to null for enums
	}
	if i, err := strconv.Atoi(string(b)); err == nil && i >= 0 && i < len(recurrenceNames) {
		r.V, r.OK = recurrenceNames[i], true
		return nil
	}
	return fmt.Errorf("invalid RecurrenceType")
}

type createTaskRequest struct {
	Title              jsonx.Str            `json:"title"`
	Description        jsonx.Str            `json:"description"`
	Priority           jsonx.Priority       `json:"priority"`
	DueDate            jsonx.LenientInstant `json:"dueDate"`
	ProjectID          jsonx.UUID           `json:"projectId"`
	ProjectName        jsonx.Str            `json:"projectName"`
	LabelIDs           jsonx.UUIDList       `json:"labelIds"`
	RecurrenceType     recurrence           `json:"recurrenceType"`
	RecurrenceInterval jsonx.Int            `json:"recurrenceInterval"`
	AssignedToUserID   jsonx.UUID           `json:"assignedToUserId"`
}

type updateTaskRequest struct {
	Title              jsonx.Str            `json:"title"`
	Description        jsonx.Str            `json:"description"`
	Completed          jsonx.Bool           `json:"completed"`
	Priority           jsonx.Priority       `json:"priority"`
	DueDate            jsonx.LenientInstant `json:"dueDate"`
	ClearDueDate       jsonx.Bool           `json:"clearDueDate"`
	LabelIDs           jsonx.UUIDList       `json:"labelIds"`
	RecurrenceType     recurrence           `json:"recurrenceType"`
	RecurrenceInterval jsonx.Int            `json:"recurrenceInterval"`
	AssignedToUserID   jsonx.UUID           `json:"assignedToUserId"`
	ClearAssignee      jsonx.Bool           `json:"clearAssignee"`
	ProjectID          jsonx.UUID           `json:"projectId"`
	ProjectName        jsonx.Str            `json:"projectName"`
	ClearProject       jsonx.Bool           `json:"clearProject"`
}

// ---- response building ------------------------------------------------------------------------------

func toSubtaskResponse(sub *store.Subtask) subtaskResponse {
	return subtaskResponse{ID: sub.ID, TaskID: sub.TaskID, Title: sub.Title, Completed: sub.Completed,
		Position: sub.Position, CreatedAt: jsonx.From(sub.CreatedAt)}
}

func toLabelResponses(labels []*store.Label) []labelResponse {
	out := make([]labelResponse, 0, len(labels))
	for _, l := range labels {
		out = append(out, labelResponse{ID: l.ID, Name: l.Name, Color: l.Color})
	}
	return out
}

func toTaskResponse(t *store.Task, labels []*store.Label, subs []*store.Subtask, comments int) taskResponse {
	r := taskResponse{
		ID: t.ID, Title: t.Title, Description: t.Description, Completed: t.Completed, Priority: t.Priority,
		ProjectID: t.ProjectID, ProjectName: t.ProjectName,
		CreatedAt: jsonx.From(t.CreatedAt), UpdatedAt: jsonx.From(t.UpdatedAt), Position: t.Position,
		Labels: toLabelResponses(labels), Subtasks: make([]subtaskResponse, 0, len(subs)),
		RecurrenceType: "NONE", RecurrenceInterval: 1,
		AssignedToUserID: t.AssignedTo, AssignedToName: t.AssigneeName(), AssignedToEmail: t.AssigneeEmail,
		CommentCount: comments,
	}
	if t.DueDate != nil {
		r.DueDate = jsonx.Ptr(*t.DueDate)
	}
	if t.RecurrenceType != nil {
		r.RecurrenceType = *t.RecurrenceType
	}
	if t.RecurrenceInterval != nil {
		r.RecurrenceInterval = *t.RecurrenceInterval
	}
	for _, sub := range subs {
		r.Subtasks = append(r.Subtasks, toSubtaskResponse(sub))
	}
	return r
}

// taskResponses loads labels, subtasks and comment counts for a batch of tasks.
func (s *Server) taskResponses(c *gin.Context, tasks []*store.Task) ([]taskResponse, error) {
	ctx := s.ctx(c)
	ids := make([]uuid.UUID, len(tasks))
	for i, t := range tasks {
		ids[i] = t.ID
	}
	labels, err := store.LabelsForTasks(ctx, s.Store.Pool, ids)
	if err != nil {
		return nil, err
	}
	subs, err := store.SubtasksForTasks(ctx, s.Store.Pool, ids)
	if err != nil {
		return nil, err
	}
	counts, err := store.CommentCounts(ctx, s.Store.Pool, ids)
	if err != nil {
		return nil, err
	}
	out := make([]taskResponse, len(tasks))
	for i, t := range tasks {
		out[i] = toTaskResponse(t, labels[t.ID], subs[t.ID], counts[t.ID])
	}
	return out, nil
}

func (s *Server) oneTaskResponse(c *gin.Context, t *store.Task) (taskResponse, error) {
	rs, err := s.taskResponses(c, []*store.Task{t})
	if err != nil {
		return taskResponse{}, err
	}
	return rs[0], nil
}

// ---- events (activity log + realtime) ---------------------------------------------------------------

type taskEvent struct {
	Type   string    `json:"type"`
	TaskID uuid.UUID `json:"taskId"`
	Title  *string   `json:"title"`
}

type activityEvent struct {
	Type    string `json:"type"`
	Message string `json:"message"`
}

func (s *Server) taskCreated(c *gin.Context, userID uuid.UUID, t *store.Task) {
	ctx := s.ctx(c)
	_ = store.InsertActivity(ctx, s.Store.Pool, "TASK_CREATED", "Created task: "+t.Title, time.Now(), userID, &t.ID)
	title := t.Title
	s.Hub.Publish("/topic/users/"+userID.String()+"/tasks", taskEvent{"TASK_CREATED", t.ID, &title})
	s.Hub.Publish("/topic/users/"+userID.String()+"/activities", activityEvent{"ACTIVITY_CREATED", "Created task: " + t.Title})
}

func (s *Server) taskUpdated(c *gin.Context, userID uuid.UUID, t *store.Task) {
	ctx := s.ctx(c)
	_ = store.InsertActivity(ctx, s.Store.Pool, "TASK_UPDATED", "Updated task: "+t.Title, time.Now(), userID, &t.ID)
	title := t.Title
	s.Hub.Publish("/topic/users/"+userID.String()+"/tasks", taskEvent{"TASK_UPDATED", t.ID, &title})
	s.Hub.Publish("/topic/users/"+userID.String()+"/activities", activityEvent{"ACTIVITY_CREATED", "Updated task: " + t.Title})
}

func (s *Server) taskDeleted(c *gin.Context, userID, taskID uuid.UUID) {
	ctx := s.ctx(c)
	_ = store.InsertActivity(ctx, s.Store.Pool, "TASK_DELETED", "Deleted a task", time.Now(), userID, nil)
	s.Hub.Publish("/topic/users/"+userID.String()+"/tasks", taskEvent{"TASK_DELETED", taskID, nil})
	s.Hub.Publish("/topic/users/"+userID.String()+"/activities", activityEvent{"ACTIVITY_CREATED", "Deleted a task"})
}

// ---- shared lookups ---------------------------------------------------------------------------------

func (s *Server) ownedTask(c *gin.Context, taskID uuid.UUID) (*store.Task, error) {
	t, err := store.TaskByID(s.ctx(c), s.Store.Pool, taskID)
	if err == store.ErrNotFound {
		return nil, apperr.NotFound("Task not found")
	} else if err != nil {
		return nil, err
	}
	if t.UserID != s.me(c).ID {
		return nil, apperr.NotFound("Task not found")
	}
	return t, nil
}

// ownedProject loads a project and requires the caller to own it (404 "Project not found" otherwise).
func (s *Server) ownedProject(c *gin.Context, id uuid.UUID) (*store.Project, error) {
	p, err := store.ProjectByID(s.ctx(c), s.Store.Pool, id)
	if err == store.ErrNotFound {
		return nil, apperr.NotFound("Project not found")
	} else if err != nil {
		return nil, err
	}
	if p.UserID != s.me(c).ID {
		return nil, apperr.NotFound("Project not found")
	}
	return p, nil
}

// projectByName finds the user's project (case-insensitive) or "creates" one the way the Java code does.
func (s *Server) projectByName(c *gin.Context, userID uuid.UUID, name string) (*store.Project, error) {
	ctx := s.ctx(c)
	projects, err := store.ProjectsByUser(ctx, s.Store.Pool, userID)
	if err != nil {
		return nil, err
	}
	for _, p := range projects {
		if strings.EqualFold(p.Name, name) {
			return p, nil
		}
	}
	// Parity with the Java backend: this insert fails (created_at is never set), which becomes HTTP 409.
	if err := store.InsertProjectWithoutTimestamp(ctx, s.Store.Pool, uuid.New(), userID, name, "#6366f1"); err != nil {
		return nil, err
	}
	return nil, apperr.Generic()
}

func applyProject(t *store.Task, p *store.Project) {
	if p == nil {
		t.ProjectID, t.ProjectName, t.ProjectOwnerID, t.ProjectWorkspaceID = nil, nil, nil, nil
		return
	}
	id, name, owner := p.ID, p.Name, p.UserID
	t.ProjectID, t.ProjectName, t.ProjectOwnerID, t.ProjectWorkspaceID = &id, &name, &owner, p.WorkspaceID
}

func (s *Server) applyAssignee(c *gin.Context, t *store.Task, id uuid.UUID) error {
	u, err := store.UserByID(s.ctx(c), s.Store.Pool, id)
	if err == store.ErrNotFound {
		t.AssignedTo, t.AssigneeFullName, t.AssigneeEmail = nil, nil, nil
		return nil
	} else if err != nil {
		return err
	}
	t.AssignedTo, t.AssigneeFullName, t.AssigneeEmail = &u.ID, u.FullName, &u.Email
	return nil
}

func labelIDs(labels []*store.Label) []uuid.UUID {
	ids := make([]uuid.UUID, len(labels))
	for i, l := range labels {
		ids[i] = l.ID
	}
	return ids
}

// ---- handlers ---------------------------------------------------------------------------------------

func (s *Server) createTask(c *gin.Context) error {
	var req createTaskRequest
	if err := bind(c, &req); err != nil {
		return err
	}
	v := newCheck()
	v.notBlank("title", req.Title.Ptr(), "must not be blank")
	v.size("title", req.Title.Ptr(), 0, 500, "size must be between 0 and 500")
	if err := v.err(); err != nil {
		return err
	}
	ctx := s.ctx(c)
	user, err := store.UserByID(ctx, s.Store.Pool, s.me(c).ID)
	if err == store.ErrNotFound {
		return apperr.NotFound("User not found")
	} else if err != nil {
		return err
	}
	position, err := store.CountTasks(ctx, s.Store.Pool)
	if err != nil {
		return err
	}
	var project *store.Project
	if req.ProjectID.OK {
		if project, err = s.ownedProject(c, req.ProjectID.V); err != nil {
			return err
		}
	} else if !blank(req.ProjectName.Ptr()) {
		if project, err = s.projectByName(c, user.ID, strings.TrimSpace(req.ProjectName.V)); err != nil {
			return err
		}
	}
	var labels []*store.Label
	if req.LabelIDs.OK {
		if labels, err = store.LabelsByIDs(ctx, s.Store.Pool, req.LabelIDs.V); err != nil {
			return err
		}
	}
	rt := "NONE"
	if req.RecurrenceType.OK {
		rt = req.RecurrenceType.V
	}
	interval := 1
	if req.RecurrenceInterval.OK && req.RecurrenceInterval.V > 0 {
		interval = req.RecurrenceInterval.V
	}
	priority := 4
	if req.Priority.OK {
		priority = req.Priority.V
	}
	now := time.Now().UTC()
	task := &store.Task{
		ID: uuid.New(), UserID: user.ID, Title: req.Title.V, Description: req.Description.Ptr(), Priority: priority,
		RecurrenceType: &rt, RecurrenceInterval: &interval, Position: position,
		CreatedAt: now, UpdatedAt: time.Now().UTC(),
	}
	if req.DueDate.OK {
		d := req.DueDate.V.Time()
		task.DueDate = &d
	}
	applyProject(task, project)
	if req.AssignedToUserID.OK {
		if err := s.applyAssignee(c, task, req.AssignedToUserID.V); err != nil {
			return err
		}
	}
	err = s.Store.Tx(ctx, func(q store.Querier) error { return store.InsertTask(ctx, q, task, labelIDs(labels)) })
	if err != nil {
		return err
	}
	s.taskCreated(c, user.ID, task)
	writeJSON(c, http.StatusCreated, toTaskResponse(task, labels, nil, 0))
	return nil
}

func (s *Server) listTasks(c *gin.Context) error {
	projectID, err := queryUUID(c, "projectId")
	if err != nil {
		return err
	}
	page, size := 0, 1000
	if raw, ok := c.GetQuery("page"); ok && raw != "" {
		if page, err = strconv.Atoi(strings.TrimSpace(raw)); err != nil {
			return apperr.Generic()
		}
	}
	if raw, ok := c.GetQuery("size"); ok && raw != "" {
		if size, err = strconv.Atoi(strings.TrimSpace(raw)); err != nil {
			return apperr.Generic()
		}
	}
	ctx := s.ctx(c)
	userID := s.me(c).ID
	if _, err := store.UserByID(ctx, s.Store.Pool, userID); err == store.ErrNotFound {
		return apperr.NotFound("User not found")
	} else if err != nil {
		return err
	}
	if projectID != nil {
		if _, err := s.ownedProject(c, *projectID); err != nil {
			return err
		}
	}
	if page < 0 || size < 1 { // PageRequest.of(...) rejects these
		return apperr.Generic()
	}
	offset := int64(page) * int64(size)
	tasks, total, err := store.TasksPage(ctx, s.Store.Pool, userID, projectID, int64(size), offset)
	if err != nil {
		return err
	}
	content, err := s.taskResponses(c, tasks)
	if err != nil {
		return err
	}
	totalPages := (total + int64(size) - 1) / int64(size)
	unsorted := sortInfo{Sorted: false, Empty: true, Unsorted: true}
	writeJSON(c, http.StatusOK, pageResponse{
		Content:          content,
		Pageable:         pageable{PageNumber: page, PageSize: size, Sort: unsorted, Offset: offset, Paged: true, Unpaged: false},
		TotalPages:       totalPages,
		TotalElements:    total,
		Last:             int64(page)+1 >= totalPages,
		Size:             size,
		Number:           page,
		Sort:             unsorted,
		NumberOfElements: len(content),
		First:            page == 0,
		Empty:            len(content) == 0,
	})
	return nil
}

func (s *Server) getTask(c *gin.Context) error {
	id, err := pathUUID(c, "taskId")
	if err != nil {
		return err
	}
	t, err := s.ownedTask(c, id)
	if err != nil {
		return err
	}
	resp, err := s.oneTaskResponse(c, t)
	if err != nil {
		return err
	}
	writeJSON(c, http.StatusOK, resp)
	return nil
}

func (s *Server) updateTask(c *gin.Context) error {
	id, err := pathUUID(c, "taskId")
	if err != nil {
		return err
	}
	var req updateTaskRequest
	if err := bind(c, &req); err != nil {
		return err
	}
	ctx := s.ctx(c)
	task, err := s.ownedTask(c, id)
	if err != nil {
		return err
	}
	wasNotCompleted := !task.Completed

	if req.Title.OK {
		task.Title = req.Title.V
	}
	if req.Description.OK {
		task.Description = req.Description.Ptr()
	}
	if req.Completed.OK {
		task.Completed = req.Completed.V
	}
	if req.Priority.OK {
		task.Priority = req.Priority.V
	}
	if req.ClearDueDate.OK && req.ClearDueDate.V {
		task.DueDate = nil
	} else if req.DueDate.OK {
		d := req.DueDate.V.Time()
		task.DueDate = &d
	}
	if req.RecurrenceType.OK {
		rt := req.RecurrenceType.V
		task.RecurrenceType = &rt
	}
	if req.RecurrenceInterval.OK && req.RecurrenceInterval.V > 0 {
		iv := req.RecurrenceInterval.V
		task.RecurrenceInterval = &iv
	}
	var newLabels []*store.Label
	setLabels := false
	if req.LabelIDs.OK {
		if newLabels, err = store.LabelsByIDs(ctx, s.Store.Pool, req.LabelIDs.V); err != nil {
			return err
		}
		setLabels = true
	}
	if req.ClearAssignee.OK && req.ClearAssignee.V {
		task.AssignedTo, task.AssigneeFullName, task.AssigneeEmail = nil, nil, nil
	} else if req.AssignedToUserID.OK {
		if err := s.applyAssignee(c, task, req.AssignedToUserID.V); err != nil {
			return err
		}
	}
	if req.ClearProject.OK && req.ClearProject.V {
		applyProject(task, nil)
	} else if req.ProjectID.OK {
		p, err := s.ownedProject(c, req.ProjectID.V)
		if err != nil {
			return err
		}
		applyProject(task, p)
	} else if !blank(req.ProjectName.Ptr()) {
		owner := task.UserID
		p, err := s.projectByName(c, owner, strings.TrimSpace(req.ProjectName.V))
		if err != nil {
			return err
		}
		applyProject(task, p)
	}
	task.UpdatedAt = time.Now().UTC()
	err = s.Store.Tx(ctx, func(q store.Querier) error {
		if err := store.UpdateTask(ctx, q, task); err != nil {
			return err
		}
		if setLabels {
			return store.SetTaskLabels(ctx, q, task.ID, labelIDs(newLabels))
		}
		return nil
	})
	if err != nil {
		return err
	}
	s.taskUpdated(c, task.UserID, task)

	if req.Completed.OK && req.Completed.V && wasNotCompleted && task.RecurrenceType != nil && *task.RecurrenceType != "NONE" {
		if err := s.spawnNextRecurring(c, task); err != nil {
			return err
		}
	}

	var resp taskResponse
	if setLabels {
		subs, err := store.SubtasksForTasks(ctx, s.Store.Pool, []uuid.UUID{task.ID})
		if err != nil {
			return err
		}
		counts, err := store.CommentCounts(ctx, s.Store.Pool, []uuid.UUID{task.ID})
		if err != nil {
			return err
		}
		resp = toTaskResponse(task, newLabels, subs[task.ID], counts[task.ID])
	} else if resp, err = s.oneTaskResponse(c, task); err != nil {
		return err
	}
	writeJSON(c, http.StatusOK, resp)
	return nil
}

func (s *Server) deleteTask(c *gin.Context) error {
	id, err := pathUUID(c, "taskId")
	if err != nil {
		return err
	}
	t, err := s.ownedTask(c, id)
	if err != nil {
		return err
	}
	s.taskDeleted(c, s.me(c).ID, id) // the Java code publishes the event before deleting
	if err := store.DeleteTask(s.ctx(c), s.Store.Pool, t.ID); err != nil {
		return err
	}
	writeStatus(c, http.StatusOK)
	return nil
}

func (s *Server) reorderTasks(c *gin.Context) error {
	var req struct {
		TaskIDs jsonx.UUIDList `json:"taskIds"`
	}
	if err := bind(c, &req); err != nil {
		return err
	}
	if !req.TaskIDs.OK { // findAllById(null) throws IllegalArgumentException
		return apperr.Generic()
	}
	ctx := s.ctx(c)
	tasks, err := store.TasksByIDs(ctx, s.Store.Pool, req.TaskIDs.V)
	if err != nil {
		return err
	}
	byID := map[uuid.UUID]*store.Task{}
	for _, t := range tasks {
		if t.UserID != s.me(c).ID {
			return apperr.NotFound("Task not found")
		}
		byID[t.ID] = t
	}
	for _, id := range req.TaskIDs.V {
		if _, ok := byID[id]; !ok { // .orElseThrow() on a missing id
			return apperr.Generic()
		}
	}
	// Like Hibernate's dirty checking: the last index of a repeated id wins, and only tasks whose
	// position really changes are written (in the order they were loaded).
	newPos := map[uuid.UUID]int{}
	for i, id := range req.TaskIDs.V {
		newPos[id] = i
	}
	err = s.Store.Tx(ctx, func(q store.Querier) error {
		for _, t := range tasks {
			if pos := newPos[t.ID]; pos != t.Position {
				if err := store.SetTaskPosition(ctx, q, t.ID, pos); err != nil {
					return err
				}
			}
		}
		return nil
	})
	if err != nil {
		return err
	}
	writeStatus(c, http.StatusOK)
	return nil
}

// ---- subtasks ---------------------------------------------------------------------------------------

func (s *Server) createSubtask(c *gin.Context) error {
	taskID, err := pathUUID(c, "taskId")
	if err != nil {
		return err
	}
	var req struct {
		Title jsonx.Str `json:"title"`
	}
	if err := bind(c, &req); err != nil {
		return err
	}
	v := newCheck()
	v.notBlank("title", req.Title.Ptr(), "must not be blank")
	v.size("title", req.Title.Ptr(), 0, 500, "size must be between 0 and 500")
	if err := v.err(); err != nil {
		return err
	}
	ctx := s.ctx(c)
	task, err := s.ownedTask(c, taskID)
	if err != nil {
		return err
	}
	n, err := store.CountSubtasks(ctx, s.Store.Pool, taskID)
	if err != nil {
		return err
	}
	now := time.Now().UTC()
	sub := &store.Subtask{ID: uuid.New(), TaskID: taskID, Title: req.Title.V, Position: n, CreatedAt: now, UpdatedAt: time.Now().UTC()}
	if err := store.InsertSubtask(ctx, s.Store.Pool, sub); err != nil {
		return err
	}
	s.taskUpdated(c, task.UserID, task)
	writeJSON(c, http.StatusOK, toSubtaskResponse(sub))
	return nil
}

func (s *Server) updateSubtask(c *gin.Context) error {
	taskID, err := pathUUID(c, "taskId")
	if err != nil {
		return err
	}
	subID, err := pathUUID(c, "subtaskId")
	if err != nil {
		return err
	}
	var req struct {
		Title     jsonx.Str  `json:"title"`
		Completed jsonx.Bool `json:"completed"`
		Position  jsonx.Int  `json:"position"`
	}
	if err := bind(c, &req); err != nil {
		return err
	}
	ctx := s.ctx(c)
	task, err := s.ownedTask(c, taskID)
	if err != nil {
		return err
	}
	sub, err := store.SubtaskByIDAndTask(ctx, s.Store.Pool, subID, taskID)
	if err == store.ErrNotFound {
		return apperr.NotFound("Subtask not found")
	} else if err != nil {
		return err
	}
	if req.Title.OK {
		sub.Title = req.Title.V
	}
	if req.Completed.OK {
		sub.Completed = req.Completed.V
	}
	if req.Position.OK {
		sub.Position = req.Position.V
	}
	sub.UpdatedAt = time.Now().UTC()
	if err := store.UpdateSubtask(ctx, s.Store.Pool, sub); err != nil {
		return err
	}
	s.taskUpdated(c, task.UserID, task)
	writeJSON(c, http.StatusOK, toSubtaskResponse(sub))
	return nil
}

func (s *Server) deleteSubtask(c *gin.Context) error {
	taskID, err := pathUUID(c, "taskId")
	if err != nil {
		return err
	}
	subID, err := pathUUID(c, "subtaskId")
	if err != nil {
		return err
	}
	ctx := s.ctx(c)
	task, err := s.ownedTask(c, taskID)
	if err != nil {
		return err
	}
	sub, err := store.SubtaskByIDAndTask(ctx, s.Store.Pool, subID, taskID)
	if err == store.ErrNotFound {
		return apperr.NotFound("Subtask not found")
	} else if err != nil {
		return err
	}
	if err := store.DeleteSubtask(ctx, s.Store.Pool, sub.ID); err != nil {
		return err
	}
	s.taskUpdated(c, task.UserID, task)
	writeStatus(c, http.StatusOK)
	return nil
}

// ---- recurring tasks --------------------------------------------------------------------------------

// zoneFor mirrors ZoneId.of with a UTC fallback.
func zoneFor(id string) *time.Location {
	if !validZoneID(id) {
		return time.UTC
	}
	if id == "Z" || id == "UTC" || id == "UT" || id == "GMT" {
		return time.UTC
	}
	if loc, err := time.LoadLocation(id); err == nil {
		return loc
	}
	// fixed offset such as +05:30, UTC+5, GMT-03:00
	off := id
	for _, p := range []string{"UTC", "GMT", "UT"} {
		off = strings.TrimPrefix(off, p)
	}
	sign := 1
	if strings.HasPrefix(off, "-") {
		sign = -1
	}
	digits := strings.NewReplacer("+", "", "-", "", ":", "").Replace(off)
	if len(digits) <= 2 {
		digits += "00"
	}
	if len(digits) == 3 {
		digits = "0" + digits
	}
	h, _ := strconv.Atoi(digits[:2])
	m, _ := strconv.Atoi(digits[2:4])
	return time.FixedZone(id, sign*(h*3600+m*60))
}

// addMonths adds months like ZonedDateTime.plusMonths: the day-of-month is clamped to the target month's length.
func addMonths(t time.Time, n int) time.Time {
	y, m, d := t.Date()
	total := int(m) - 1 + n
	ty, tm := y+total/12, total%12
	if tm < 0 {
		tm += 12
		ty--
	}
	last := time.Date(ty, time.Month(tm+2), 0, 0, 0, 0, 0, time.UTC).Day()
	if d > last {
		d = last
	}
	return time.Date(ty, time.Month(tm+1), d, t.Hour(), t.Minute(), t.Second(), t.Nanosecond(), t.Location())
}

func nextDueDate(base time.Time, typ string, interval int, loc *time.Location) time.Time {
	if interval < 1 {
		interval = 1
	}
	step := func(t time.Time) time.Time {
		switch typ {
		case "DAILY":
			return t.AddDate(0, 0, interval)
		case "WEEKLY":
			return t.AddDate(0, 0, 7*interval)
		case "MONTHLY":
			return addMonths(t, interval)
		case "YEARLY":
			return addMonths(t, 12*interval)
		}
		return t
	}
	next := step(base.In(loc))
	if typ == "NONE" || typ == "" {
		return next.UTC()
	}
	now := time.Now()
	for next.Before(now) {
		next = step(next)
	}
	return next.UTC()
}

func (s *Server) spawnNextRecurring(c *gin.Context, task *store.Task) error {
	ctx := s.ctx(c)
	position, err := store.CountTasks(ctx, s.Store.Pool)
	if err != nil {
		return err
	}
	loc := time.UTC
	if owner, err := store.UserByID(ctx, s.Store.Pool, task.UserID); err == nil && strings.TrimSpace(owner.Timezone) != "" {
		loc = zoneFor(owner.Timezone)
	}
	base := time.Now()
	if task.DueDate != nil {
		base = *task.DueDate
	}
	interval := 1
	if task.RecurrenceInterval != nil && *task.RecurrenceInterval > 0 {
		interval = *task.RecurrenceInterval
	}
	due := nextDueDate(base, *task.RecurrenceType, interval, loc)
	now := time.Now().UTC()
	next := &store.Task{
		ID: uuid.New(), UserID: task.UserID, Title: task.Title, Description: task.Description,
		Priority: task.Priority, DueDate: &due, RecurrenceType: task.RecurrenceType, RecurrenceInterval: task.RecurrenceInterval,
		Position: position, ProjectID: task.ProjectID, CreatedAt: now, UpdatedAt: now,
	}
	labels, err := store.LabelsForTasks(ctx, s.Store.Pool, []uuid.UUID{task.ID})
	if err != nil {
		return err
	}
	subs, err := store.SubtasksForTasks(ctx, s.Store.Pool, []uuid.UUID{task.ID})
	if err != nil {
		return err
	}
	err = s.Store.Tx(ctx, func(q store.Querier) error {
		if err := store.InsertTask(ctx, q, next, labelIDs(labels[task.ID])); err != nil {
			return err
		}
		for _, sub := range subs[task.ID] {
			clone := &store.Subtask{ID: uuid.New(), TaskID: next.ID, Title: sub.Title, Position: sub.Position,
				CreatedAt: now, UpdatedAt: now}
			if err := store.InsertSubtask(ctx, q, clone); err != nil {
				return err
			}
		}
		return nil
	})
	if err != nil {
		return err
	}
	s.taskCreated(c, next.UserID, next)
	return nil
}
