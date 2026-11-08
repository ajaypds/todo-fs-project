package api

import (
	"net/http"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/ajaypds/todo-fs-project/backend-go/internal/apperr"
	"github.com/ajaypds/todo-fs-project/backend-go/internal/jsonx"
	"github.com/ajaypds/todo-fs-project/backend-go/internal/store"
	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
)

func (s *Server) registerComments(v1 *gin.RouterGroup) {
	g := v1.Group("/tasks/:taskId")
	g.GET("/comments", s.handle(s.listComments))
	g.POST("/comments", s.handle(s.createComment))
	g.PUT("/comments/:commentId", s.handle(s.updateComment))
	g.DELETE("/comments/:commentId", s.handle(s.deleteComment))
	g.GET("/activities", s.handle(s.taskActivities))
}

func toCommentResponse(cm *store.Comment) commentResponse {
	return commentResponse{ID: cm.ID, TaskID: cm.TaskID, UserID: cm.UserID, UserName: cm.AuthorName, UserEmail: cm.AuthorEmail,
		Content: cm.Content, CreatedAt: jsonx.From(cm.CreatedAt), UpdatedAt: jsonx.From(cm.UpdatedAt)}
}

// accessibleTask: owner, assignee, project owner, or member of the project's workspace.
func (s *Server) accessibleTask(c *gin.Context, taskID uuid.UUID) (*store.Task, error) {
	ctx := s.ctx(c)
	t, err := store.TaskByID(ctx, s.Store.Pool, taskID)
	if err == store.ErrNotFound {
		return nil, apperr.NotFound("Task not found")
	} else if err != nil {
		return nil, err
	}
	me := s.me(c).ID
	ok := t.UserID == me ||
		(t.AssignedTo != nil && *t.AssignedTo == me) ||
		(t.ProjectOwnerID != nil && *t.ProjectOwnerID == me)
	if !ok && t.ProjectWorkspaceID != nil {
		if ok, err = store.MemberExists(ctx, s.Store.Pool, *t.ProjectWorkspaceID, me); err != nil {
			return nil, err
		}
	}
	if !ok {
		return nil, apperr.NotFound("Task not found")
	}
	return t, nil
}

func validateComment(content *string) error {
	v := newCheck()
	v.notBlank("content", content, "Comment content cannot be empty")
	v.size("content", content, 0, 5000, "Comment cannot exceed 5000 characters")
	return v.err()
}

func truncateContent(content string) string {
	if utf8.RuneCountInString(content) > 60 {
		r := []rune(content)
		return string(r[:57]) + "..."
	}
	return content
}

func (s *Server) listComments(c *gin.Context) error {
	taskID, err := pathUUID(c, "taskId")
	if err != nil {
		return err
	}
	t, err := s.accessibleTask(c, taskID)
	if err != nil {
		return err
	}
	comments, err := store.CommentsByTask(s.ctx(c), s.Store.Pool, t.ID)
	if err != nil {
		return err
	}
	out := make([]commentResponse, 0, len(comments))
	for _, cm := range comments {
		out = append(out, toCommentResponse(cm))
	}
	writeJSON(c, http.StatusOK, out)
	return nil
}

func (s *Server) createComment(c *gin.Context) error {
	taskID, err := pathUUID(c, "taskId")
	if err != nil {
		return err
	}
	var req struct {
		Content jsonx.Str `json:"content"`
	}
	if err := bind(c, &req); err != nil {
		return err
	}
	if err := validateComment(req.Content.Ptr()); err != nil {
		return err
	}
	ctx := s.ctx(c)
	t, err := s.accessibleTask(c, taskID)
	if err != nil {
		return err
	}
	author, err := s.requireUser(c)
	if err != nil {
		return err
	}
	now := time.Now().UTC()
	cm := &store.Comment{ID: uuid.New(), TaskID: t.ID, UserID: author.ID, Content: strings.TrimSpace(req.Content.V),
		CreatedAt: now, UpdatedAt: now, AuthorName: author.Name(), AuthorEmail: author.Email}
	err = s.Store.Tx(ctx, func(q store.Querier) error {
		if err := store.InsertComment(ctx, q, cm); err != nil {
			return err
		}
		return store.InsertActivity(ctx, q, "TASK_COMMENTED",
			author.Name()+" commented: "+truncateContent(req.Content.V), now, author.ID, &t.ID)
	})
	if err != nil {
		return err
	}
	resp := toCommentResponse(cm)
	s.Hub.Publish("/topic/tasks/"+t.ID.String()+"/comments", resp)
	s.Hub.Publish("/topic/users/"+author.ID.String()+"/activities",
		activityEvent{"TASK_COMMENTED", author.Name() + " commented on a task"})
	writeJSON(c, http.StatusCreated, resp)
	return nil
}

// ownedComment loads a comment and checks it belongs to the task in the URL.
func (s *Server) commentForTask(c *gin.Context, taskID uuid.UUID) (*store.Comment, error) {
	id, err := pathUUID(c, "commentId")
	if err != nil {
		return nil, err
	}
	cm, err := store.CommentByID(s.ctx(c), s.Store.Pool, id)
	if err == store.ErrNotFound {
		return nil, apperr.NotFound("Comment not found")
	} else if err != nil {
		return nil, err
	}
	if cm.TaskID != taskID {
		return nil, apperr.NotFound("Comment not found for this task")
	}
	return cm, nil
}

func (s *Server) updateComment(c *gin.Context) error {
	taskID, err := pathUUID(c, "taskId")
	if err != nil {
		return err
	}
	if _, err := pathUUID(c, "commentId"); err != nil {
		return err
	}
	var req struct {
		Content jsonx.Str `json:"content"`
	}
	if err := bind(c, &req); err != nil {
		return err
	}
	if err := validateComment(req.Content.Ptr()); err != nil {
		return err
	}
	cm, err := s.commentForTask(c, taskID)
	if err != nil {
		return err
	}
	if cm.UserID != s.me(c).ID {
		// AccessDeniedException is swallowed by the catch-all handler in the Java backend (HTTP 500).
		return apperr.Generic()
	}
	now := time.Now().UTC()
	content := strings.TrimSpace(req.Content.V)
	if err := store.UpdateComment(s.ctx(c), s.Store.Pool, cm.ID, content, now); err != nil {
		return err
	}
	cm.Content, cm.UpdatedAt = content, now
	writeJSON(c, http.StatusOK, toCommentResponse(cm))
	return nil
}

func (s *Server) deleteComment(c *gin.Context) error {
	taskID, err := pathUUID(c, "taskId")
	if err != nil {
		return err
	}
	if _, err := pathUUID(c, "commentId"); err != nil {
		return err
	}
	ctx := s.ctx(c)
	cm, err := s.commentForTask(c, taskID)
	if err != nil {
		return err
	}
	me := s.me(c).ID
	task, err := store.TaskByID(ctx, s.Store.Pool, cm.TaskID)
	if err != nil {
		return err
	}
	allowed := cm.UserID == me || task.UserID == me
	if !allowed && task.ProjectWorkspaceID != nil {
		if m, err := store.MemberByWorkspaceAndUser(ctx, s.Store.Pool, *task.ProjectWorkspaceID, me); err == nil {
			allowed = m.Role == "OWNER" || m.Role == "ADMIN"
		} else if err != store.ErrNotFound {
			return err
		}
	}
	if !allowed {
		return apperr.Generic() // AccessDeniedException -> catch-all handler
	}
	if err := store.DeleteComment(ctx, s.Store.Pool, cm.ID); err != nil {
		return err
	}
	writeStatus(c, http.StatusNoContent)
	return nil
}

func (s *Server) taskActivities(c *gin.Context) error {
	taskID, err := pathUUID(c, "taskId")
	if err != nil {
		return err
	}
	t, err := s.accessibleTask(c, taskID)
	if err != nil {
		return err
	}
	acts, err := store.ActivitiesByTask(s.ctx(c), s.Store.Pool, t.ID)
	if err != nil {
		return err
	}
	writeJSON(c, http.StatusOK, toActivityResponses(acts))
	return nil
}

func toActivityResponses(acts []*store.Activity) []activityResponse {
	out := make([]activityResponse, 0, len(acts))
	for _, a := range acts {
		out = append(out, activityResponse{ID: a.ID, Type: a.Type, Message: a.Message, CreatedAt: jsonx.From(a.CreatedAt)})
	}
	return out
}

func (s *Server) registerActivities(v1 *gin.RouterGroup) {
	v1.GET("/activities", s.handle(func(c *gin.Context) error {
		u, err := s.requireUser(c)
		if err != nil {
			return err
		}
		acts, err := store.ActivitiesByUser(s.ctx(c), s.Store.Pool, u.ID)
		if err != nil {
			return err
		}
		writeJSON(c, http.StatusOK, toActivityResponses(acts))
		return nil
	}))
}
