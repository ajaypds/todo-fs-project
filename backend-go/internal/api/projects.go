package api

import (
	"net/http"
	"strings"
	"time"

	"github.com/ajaypds/todo-fs-project/backend-go/internal/apperr"
	"github.com/ajaypds/todo-fs-project/backend-go/internal/jsonx"
	"github.com/ajaypds/todo-fs-project/backend-go/internal/store"
	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
)

func (s *Server) registerProjects(v1 *gin.RouterGroup) {
	g := v1.Group("/projects")
	g.POST("", s.handle(s.createProject))
	g.GET("", s.handle(s.listProjects))
	g.PUT("/:projectId", s.handle(s.updateProject))
	g.DELETE("/:projectId", s.handle(s.deleteProject))
}

func (s *Server) registerLabels(v1 *gin.RouterGroup) {
	g := v1.Group("/labels")
	g.POST("", s.handle(s.createLabel))
	g.GET("", s.handle(s.listLabels))
}

type projectRequest struct {
	Name  jsonx.Str `json:"name"`
	Color jsonx.Str `json:"color"`
}

func (r *projectRequest) validate() error {
	v := newCheck()
	v.notBlank("name", r.Name.Ptr(), "must not be blank")
	v.notBlank("color", r.Color.Ptr(), "must not be blank")
	return v.err()
}

func toProjectResponse(p *store.Project) projectResponse {
	return projectResponse{ID: p.ID, Name: p.Name, Color: p.Color}
}

// requireUser reproduces userRepository.findById(...).orElseThrow(ResourceNotFoundException("User not found")).
func (s *Server) requireUser(c *gin.Context) (*store.User, error) {
	u, err := store.UserByID(s.ctx(c), s.Store.Pool, s.me(c).ID)
	if err == store.ErrNotFound {
		return nil, apperr.NotFound("User not found")
	}
	return u, err
}

func (s *Server) createProject(c *gin.Context) error {
	var req projectRequest
	if err := bind(c, &req); err != nil {
		return err
	}
	if err := req.validate(); err != nil {
		return err
	}
	u, err := s.requireUser(c)
	if err != nil {
		return err
	}
	p := &store.Project{ID: uuid.New(), UserID: u.ID, Name: req.Name.V, Color: req.Color.V, CreatedAt: time.Now().UTC()}
	if err := store.InsertProject(s.ctx(c), s.Store.Pool, p); err != nil {
		return err
	}
	writeJSON(c, http.StatusCreated, toProjectResponse(p))
	return nil
}

func (s *Server) listProjects(c *gin.Context) error {
	u, err := s.requireUser(c)
	if err != nil {
		return err
	}
	projects, err := store.ProjectsByUser(s.ctx(c), s.Store.Pool, u.ID)
	if err != nil {
		return err
	}
	out := make([]projectResponse, 0, len(projects))
	for _, p := range projects {
		out = append(out, toProjectResponse(p))
	}
	writeJSON(c, http.StatusOK, out)
	return nil
}

func (s *Server) updateProject(c *gin.Context) error {
	id, err := pathUUID(c, "projectId")
	if err != nil {
		return err
	}
	var req projectRequest
	if err := bind(c, &req); err != nil {
		return err
	}
	if err := req.validate(); err != nil {
		return err
	}
	u, err := s.requireUser(c)
	if err != nil {
		return err
	}
	ctx := s.ctx(c)
	p, err := store.ProjectByID(ctx, s.Store.Pool, id)
	if err == store.ErrNotFound {
		return apperr.NotFound("Project not found")
	} else if err != nil {
		return err
	}
	if p.UserID != u.ID {
		return apperr.Unauthorized("You are not allowed to update this project")
	}
	if !blank(req.Name.Ptr()) {
		p.Name = req.Name.V
	}
	if !blank(req.Color.Ptr()) {
		p.Color = req.Color.V
	}
	if err := store.UpdateProject(ctx, s.Store.Pool, p); err != nil {
		return err
	}
	writeJSON(c, http.StatusOK, toProjectResponse(p))
	return nil
}

func (s *Server) deleteProject(c *gin.Context) error {
	id, err := pathUUID(c, "projectId")
	if err != nil {
		return err
	}
	u, err := s.requireUser(c)
	if err != nil {
		return err
	}
	ctx := s.ctx(c)
	p, err := store.ProjectByID(ctx, s.Store.Pool, id)
	if err == store.ErrNotFound {
		return apperr.NotFound("Project not found")
	} else if err != nil {
		return err
	}
	if p.UserID != u.ID {
		return apperr.Unauthorized("You are not allowed to delete this project")
	}
	if err := store.DeleteProject(ctx, s.Store.Pool, p.ID); err != nil {
		return err
	}
	writeStatus(c, http.StatusNoContent)
	return nil
}

func (s *Server) createLabel(c *gin.Context) error {
	var req struct {
		Name  jsonx.Str `json:"name"`
		Color jsonx.Str `json:"color"`
	}
	if err := bind(c, &req); err != nil {
		return err
	}
	u, err := store.UserByID(s.ctx(c), s.Store.Pool, s.me(c).ID)
	if err != nil {
		return apperr.Generic() // orElseThrow()
	}
	l := &store.Label{ID: uuid.New(), UserID: u.ID}
	// A missing name/color violates NOT NULL and becomes HTTP 409, as in the Java backend.
	if err := store.InsertLabel(s.ctx(c), s.Store.Pool, l, req.Name.Ptr(), req.Color.Ptr()); err != nil {
		return err
	}
	l.Name, l.Color = req.Name.V, req.Color.V
	writeJSON(c, http.StatusOK, labelResponse{ID: l.ID, Name: l.Name, Color: l.Color})
	return nil
}

func (s *Server) listLabels(c *gin.Context) error {
	u, err := store.UserByID(s.ctx(c), s.Store.Pool, s.me(c).ID)
	if err != nil {
		return apperr.Generic()
	}
	labels, err := store.LabelsByUser(s.ctx(c), s.Store.Pool, u.ID)
	if err != nil {
		return err
	}
	writeJSON(c, http.StatusOK, toLabelResponses(labels))
	return nil
}

var _ = strings.TrimSpace
