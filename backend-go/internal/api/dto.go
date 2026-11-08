package api

import (
	"github.com/ajaypds/todo-fs-project/backend-go/internal/jsonx"
	"github.com/google/uuid"
)

// Response structs list fields in the same order as the Java DTOs (Jackson uses declaration order);
// nullable values are pointers without omitempty so they appear as null.

type authResponse struct {
	Token                     *string `json:"token"`
	RefreshToken              *string `json:"refreshToken"`
	EmailVerificationRequired bool    `json:"emailVerificationRequired"`
	Email                     *string `json:"email"`
	Message                   *string `json:"message"`
}

type userResponse struct {
	ID            uuid.UUID     `json:"id"`
	Email         string        `json:"email"`
	FullName      *string       `json:"fullName"`
	Timezone      string        `json:"timezone"`
	EmailVerified bool          `json:"emailVerified"`
	CreatedAt     jsonx.Instant `json:"createdAt"`
}

type projectResponse struct {
	ID    uuid.UUID `json:"id"`
	Name  string    `json:"name"`
	Color string    `json:"color"`
}

type labelResponse struct {
	ID    uuid.UUID `json:"id"`
	Name  string    `json:"name"`
	Color string    `json:"color"`
}

type subtaskResponse struct {
	ID        uuid.UUID     `json:"id"`
	TaskID    uuid.UUID     `json:"taskId"`
	Title     string        `json:"title"`
	Completed bool          `json:"completed"`
	Position  int           `json:"position"`
	CreatedAt jsonx.Instant `json:"createdAt"`
}

type taskResponse struct {
	ID                 uuid.UUID         `json:"id"`
	Title              string            `json:"title"`
	Description        *string           `json:"description"`
	Completed          bool              `json:"completed"`
	Priority           int               `json:"priority"`
	DueDate            *jsonx.Instant    `json:"dueDate"`
	ProjectID          *uuid.UUID        `json:"projectId"`
	ProjectName        *string           `json:"projectName"`
	CreatedAt          jsonx.Instant     `json:"createdAt"`
	UpdatedAt          jsonx.Instant     `json:"updatedAt"`
	Position           int               `json:"position"`
	Labels             []labelResponse   `json:"labels"`
	Subtasks           []subtaskResponse `json:"subtasks"`
	RecurrenceType     string            `json:"recurrenceType"`
	RecurrenceInterval int               `json:"recurrenceInterval"`
	AssignedToUserID   *uuid.UUID        `json:"assignedToUserId"`
	AssignedToName     *string           `json:"assignedToName"`
	AssignedToEmail    *string           `json:"assignedToEmail"`
	CommentCount       int               `json:"commentCount"`
}

type sortInfo struct {
	Sorted   bool `json:"sorted"`
	Empty    bool `json:"empty"`
	Unsorted bool `json:"unsorted"`
}

type pageable struct {
	PageNumber int      `json:"pageNumber"`
	PageSize   int      `json:"pageSize"`
	Sort       sortInfo `json:"sort"`
	Offset     int64    `json:"offset"`
	Paged      bool     `json:"paged"`
	Unpaged    bool     `json:"unpaged"`
}

// pageResponse reproduces the JSON of Spring Data's PageImpl.
type pageResponse struct {
	Content          []taskResponse `json:"content"`
	Pageable         pageable       `json:"pageable"`
	TotalPages       int64          `json:"totalPages"`
	TotalElements    int64          `json:"totalElements"`
	Last             bool           `json:"last"`
	Size             int            `json:"size"`
	Number           int            `json:"number"`
	Sort             sortInfo       `json:"sort"`
	NumberOfElements int            `json:"numberOfElements"`
	First            bool           `json:"first"`
	Empty            bool           `json:"empty"`
}

type activityResponse struct {
	ID        uuid.UUID     `json:"id"`
	Type      string        `json:"type"`
	Message   string        `json:"message"`
	CreatedAt jsonx.Instant `json:"createdAt"`
}

type commentResponse struct {
	ID        uuid.UUID     `json:"id"`
	TaskID    uuid.UUID     `json:"taskId"`
	UserID    uuid.UUID     `json:"userId"`
	UserName  string        `json:"userName"`
	UserEmail string        `json:"userEmail"`
	Content   string        `json:"content"`
	CreatedAt jsonx.Instant `json:"createdAt"`
	UpdatedAt jsonx.Instant `json:"updatedAt"`
}

type workspaceResponse struct {
	ID              uuid.UUID     `json:"id"`
	Name            string        `json:"name"`
	OwnerID         uuid.UUID     `json:"ownerId"`
	OwnerName       string        `json:"ownerName"`
	CurrentUserRole string        `json:"currentUserRole"`
	MemberCount     int64         `json:"memberCount"`
	CreatedAt       jsonx.Instant `json:"createdAt"`
}

type memberResponse struct {
	ID       uuid.UUID     `json:"id"`
	UserID   uuid.UUID     `json:"userId"`
	Email    string        `json:"email"`
	FullName *string       `json:"fullName"`
	Role     string        `json:"role"`
	JoinedAt jsonx.Instant `json:"joinedAt"`
}

type apiKeyResponse struct {
	ID         uuid.UUID      `json:"id"`
	Name       string         `json:"name"`
	KeyPrefix  string         `json:"keyPrefix"`
	APIKey     *string        `json:"apiKey"`
	CreatedAt  jsonx.Instant  `json:"createdAt"`
	LastUsedAt *jsonx.Instant `json:"lastUsedAt"`
}

func strp(s string) *string { return &s }
