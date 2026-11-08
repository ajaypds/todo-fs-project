package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/ajaypds/todo-fs-project/backend-go/internal/apperr"
	"github.com/ajaypds/todo-fs-project/backend-go/internal/jsonx"
	"github.com/ajaypds/todo-fs-project/backend-go/internal/store"
	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
)

func (s *Server) registerWorkspaces(v1 *gin.RouterGroup) {
	g := v1.Group("/workspaces")
	g.GET("", s.handle(s.listWorkspaces))
	g.POST("", s.handle(s.createWorkspace))
	g.GET("/:workspaceId", s.handle(s.getWorkspace))
	g.PATCH("/:workspaceId", s.handle(s.updateWorkspace))
	g.DELETE("/:workspaceId", s.handle(s.deleteWorkspace))
	g.GET("/:workspaceId/members", s.handle(s.listMembers))
	g.POST("/:workspaceId/members", s.handle(s.addMember))
	g.PATCH("/:workspaceId/members/:targetUserId", s.handle(s.updateMemberRole))
	g.DELETE("/:workspaceId/members/:targetUserId", s.handle(s.removeMember))
}

// role is a WorkspaceRole enum (exact names, ordinals accepted like Jackson's default).
type role struct {
	V  string
	OK bool
}

var roleNames = []string{"OWNER", "ADMIN", "MEMBER", "VIEWER"}

func (r *role) UnmarshalJSON(b []byte) error {
	b = bytes.TrimSpace(b)
	if bytes.Equal(b, []byte("null")) {
		return nil
	}
	if len(b) > 0 && b[0] == '"' {
		var text string
		if err := json.Unmarshal(b, &text); err != nil {
			return err
		}
		for _, n := range roleNames {
			if n == text {
				r.V, r.OK = n, true
				return nil
			}
		}
		return fmt.Errorf("unknown WorkspaceRole %q", text)
	}
	var i int
	if err := json.Unmarshal(b, &i); err == nil && i >= 0 && i < len(roleNames) {
		r.V, r.OK = roleNames[i], true
		return nil
	}
	return fmt.Errorf("invalid WorkspaceRole")
}

func (s *Server) workspaceResp(c *gin.Context, w *store.Workspace, roleName string) (workspaceResponse, error) {
	n, err := store.CountMembers(s.ctx(c), s.Store.Pool, w.ID)
	if err != nil {
		return workspaceResponse{}, err
	}
	return workspaceResponse{ID: w.ID, Name: w.Name, OwnerID: w.OwnerID, OwnerName: w.OwnerName(),
		CurrentUserRole: roleName, MemberCount: n, CreatedAt: jsonx.From(w.CreatedAt)}, nil
}

func toMemberResponse(m *store.Member) memberResponse {
	return memberResponse{ID: m.ID, UserID: m.UserID, Email: m.Email, FullName: m.FullName, Role: m.Role, JoinedAt: jsonx.From(m.JoinedAt)}
}

// membership requires the caller to belong to the workspace (401 otherwise, like the Java service).
func (s *Server) membership(c *gin.Context, workspaceID uuid.UUID) (*store.Member, error) {
	m, err := store.MemberByWorkspaceAndUser(s.ctx(c), s.Store.Pool, workspaceID, s.me(c).ID)
	if err == store.ErrNotFound {
		return nil, apperr.Unauthorized("You are not a member of this workspace")
	}
	return m, err
}

func isAdminOrOwner(r string) bool { return r == "OWNER" || r == "ADMIN" }

func validateWorkspaceName(name *string) error {
	v := newCheck()
	v.notBlank("name", name, "Workspace name is required")
	v.size("name", name, 2, 100, "Workspace name must be between 2 and 100 characters")
	return v.err()
}

func (s *Server) listWorkspaces(c *gin.Context) error {
	u, err := s.requireUser(c)
	if err != nil {
		return err
	}
	ctx := s.ctx(c)
	list, err := store.WorkspacesForUser(ctx, s.Store.Pool, u.ID)
	if err != nil {
		return err
	}
	out := make([]workspaceResponse, 0, len(list))
	for _, w := range list {
		r := "VIEWER"
		if m, err := store.MemberByWorkspaceAndUser(ctx, s.Store.Pool, w.ID, u.ID); err == nil {
			r = m.Role
		}
		resp, err := s.workspaceResp(c, w, r)
		if err != nil {
			return err
		}
		out = append(out, resp)
	}
	writeJSON(c, http.StatusOK, out)
	return nil
}

func (s *Server) createWorkspace(c *gin.Context) error {
	var req struct {
		Name jsonx.Str `json:"name"`
	}
	if err := bind(c, &req); err != nil {
		return err
	}
	if err := validateWorkspaceName(req.Name.Ptr()); err != nil {
		return err
	}
	u, err := s.requireUser(c)
	if err != nil {
		return err
	}
	ctx := s.ctx(c)
	now := time.Now().UTC()
	id := uuid.New()
	name := strings.TrimSpace(req.Name.V)
	err = s.Store.Tx(ctx, func(q store.Querier) error {
		if err := store.InsertWorkspace(ctx, q, id, name, u.ID, now); err != nil {
			return err
		}
		return store.InsertMember(ctx, q, uuid.New(), id, u.ID, "OWNER", now)
	})
	if err != nil {
		return err
	}
	owner := u.Email
	if u.FullName != nil {
		owner = *u.FullName
	}
	writeJSON(c, http.StatusCreated, workspaceResponse{ID: id, Name: name, OwnerID: u.ID, OwnerName: owner,
		CurrentUserRole: "OWNER", MemberCount: 1, CreatedAt: jsonx.From(now)})
	return nil
}

func (s *Server) getWorkspace(c *gin.Context) error {
	id, err := pathUUID(c, "workspaceId")
	if err != nil {
		return err
	}
	m, err := s.membership(c, id)
	if err != nil {
		return err
	}
	w, err := store.WorkspaceByID(s.ctx(c), s.Store.Pool, id)
	if err != nil {
		return err
	}
	resp, err := s.workspaceResp(c, w, m.Role)
	if err != nil {
		return err
	}
	writeJSON(c, http.StatusOK, resp)
	return nil
}

func (s *Server) updateWorkspace(c *gin.Context) error {
	id, err := pathUUID(c, "workspaceId")
	if err != nil {
		return err
	}
	var req struct {
		Name jsonx.Str `json:"name"`
	}
	if err := bind(c, &req); err != nil {
		return err
	}
	if err := validateWorkspaceName(req.Name.Ptr()); err != nil {
		return err
	}
	m, err := s.membership(c, id)
	if err != nil {
		return err
	}
	if !isAdminOrOwner(m.Role) {
		return apperr.Unauthorized("Only workspace owners or admins can update workspace settings")
	}
	ctx := s.ctx(c)
	name := strings.TrimSpace(req.Name.V)
	if err := store.RenameWorkspace(ctx, s.Store.Pool, id, name); err != nil {
		return err
	}
	w, err := store.WorkspaceByID(ctx, s.Store.Pool, id)
	if err != nil {
		return err
	}
	resp, err := s.workspaceResp(c, w, m.Role)
	if err != nil {
		return err
	}
	writeJSON(c, http.StatusOK, resp)
	return nil
}

func (s *Server) deleteWorkspace(c *gin.Context) error {
	id, err := pathUUID(c, "workspaceId")
	if err != nil {
		return err
	}
	m, err := s.membership(c, id)
	if err != nil {
		return err
	}
	if m.Role != "OWNER" {
		return apperr.Unauthorized("Only the workspace owner can delete the workspace")
	}
	ctx := s.ctx(c)
	mine, err := store.WorkspacesForUser(ctx, s.Store.Pool, s.me(c).ID)
	if err != nil {
		return err
	}
	if len(mine) <= 1 {
		return apperr.BadRequest("You cannot delete your only workspace")
	}
	if err := store.DeleteWorkspace(ctx, s.Store.Pool, id); err != nil {
		return err
	}
	writeStatus(c, http.StatusNoContent)
	return nil
}

func (s *Server) listMembers(c *gin.Context) error {
	id, err := pathUUID(c, "workspaceId")
	if err != nil {
		return err
	}
	if _, err := s.membership(c, id); err != nil {
		return err
	}
	members, err := store.MembersOfWorkspace(s.ctx(c), s.Store.Pool, id)
	if err != nil {
		return err
	}
	out := make([]memberResponse, 0, len(members))
	for _, m := range members {
		out = append(out, toMemberResponse(m))
	}
	writeJSON(c, http.StatusOK, out)
	return nil
}

func (s *Server) addMember(c *gin.Context) error {
	wsID, err := pathUUID(c, "workspaceId")
	if err != nil {
		return err
	}
	var req struct {
		Email jsonx.Str `json:"email"`
		Role  role      `json:"role"`
	}
	if err := bind(c, &req); err != nil {
		return err
	}
	v := newCheck()
	v.notBlank("email", req.Email.Ptr(), "Email is required")
	v.email("email", req.Email.Ptr(), "Valid email is required")
	if !req.Role.OK {
		v.fail("role", "Role is required")
	}
	if err := v.err(); err != nil {
		return err
	}
	requester, err := s.membership(c, wsID)
	if err != nil {
		return err
	}
	if !isAdminOrOwner(requester.Role) {
		return apperr.Unauthorized("Only workspace owners or admins can invite new members")
	}
	if req.Role.V == "OWNER" {
		return apperr.BadRequest("A workspace can only have one primary owner")
	}
	ctx := s.ctx(c)
	target, err := store.UserByEmail(ctx, s.Store.Pool, normEmail(req.Email.V))
	if err == store.ErrNotFound {
		return apperr.NotFound("User with email '" + req.Email.V + "' not found")
	} else if err != nil {
		return err
	}
	if exists, err := store.MemberExists(ctx, s.Store.Pool, wsID, target.ID); err != nil {
		return err
	} else if exists {
		return apperr.BadRequest("User is already a member of this workspace")
	}
	now := time.Now().UTC()
	m := &store.Member{ID: uuid.New(), WorkspaceID: wsID, UserID: target.ID, Role: req.Role.V, JoinedAt: now,
		Email: target.Email, FullName: target.FullName}
	if err := store.InsertMember(ctx, s.Store.Pool, m.ID, wsID, target.ID, m.Role, now); err != nil {
		return err
	}
	writeJSON(c, http.StatusCreated, toMemberResponse(m))
	return nil
}

func (s *Server) updateMemberRole(c *gin.Context) error {
	wsID, err := pathUUID(c, "workspaceId")
	if err != nil {
		return err
	}
	targetID, err := pathUUID(c, "targetUserId")
	if err != nil {
		return err
	}
	var req struct {
		Role role `json:"role"`
	}
	if err := bind(c, &req); err != nil {
		return err
	}
	v := newCheck()
	if !req.Role.OK {
		v.fail("role", "Role is required")
	}
	if err := v.err(); err != nil {
		return err
	}
	requester, err := s.membership(c, wsID)
	if err != nil {
		return err
	}
	if !isAdminOrOwner(requester.Role) {
		return apperr.Unauthorized("Only workspace owners or admins can manage roles")
	}
	if req.Role.V == "OWNER" {
		return apperr.BadRequest("Cannot assign OWNER role via role update")
	}
	ctx := s.ctx(c)
	target, err := store.MemberByWorkspaceAndUser(ctx, s.Store.Pool, wsID, targetID)
	if err == store.ErrNotFound {
		return apperr.NotFound("Member not found in workspace")
	} else if err != nil {
		return err
	}
	if target.Role == "OWNER" {
		return apperr.BadRequest("Cannot modify the owner's role")
	}
	if requester.Role == "ADMIN" && target.Role == "ADMIN" && s.me(c).ID != targetID {
		return apperr.Unauthorized("Admins cannot modify other admins' roles")
	}
	if err := store.SetMemberRole(ctx, s.Store.Pool, target.ID, req.Role.V); err != nil {
		return err
	}
	target.Role = req.Role.V
	writeJSON(c, http.StatusOK, toMemberResponse(target))
	return nil
}

func (s *Server) removeMember(c *gin.Context) error {
	wsID, err := pathUUID(c, "workspaceId")
	if err != nil {
		return err
	}
	targetID, err := pathUUID(c, "targetUserId")
	if err != nil {
		return err
	}
	requester, err := s.membership(c, wsID)
	if err != nil {
		return err
	}
	ctx := s.ctx(c)
	target, err := store.MemberByWorkspaceAndUser(ctx, s.Store.Pool, wsID, targetID)
	if err == store.ErrNotFound {
		return apperr.NotFound("Member not found in workspace")
	} else if err != nil {
		return err
	}
	if target.Role == "OWNER" {
		return apperr.BadRequest("Cannot remove the workspace owner")
	}
	isSelf := s.me(c).ID == targetID
	if !isSelf && !isAdminOrOwner(requester.Role) {
		return apperr.Unauthorized("You do not have permission to remove this member")
	}
	if !isSelf && requester.Role == "ADMIN" && target.Role == "ADMIN" {
		return apperr.Unauthorized("Admins cannot remove other admins")
	}
	if err := store.DeleteMember(ctx, s.Store.Pool, target.ID); err != nil {
		return err
	}
	writeStatus(c, http.StatusNoContent)
	return nil
}
