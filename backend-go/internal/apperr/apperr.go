// Package apperr defines the error responses of the API, matching GlobalExceptionHandler.
package apperr

import (
	"errors"
	"net/http"
	"strings"

	"github.com/ajaypds/todo-fs-project/backend-go/internal/jsonx"
	"github.com/jackc/pgx/v5/pgconn"
)

// Error is a handled error that becomes an ApiErrorResponse {timestamp, status, message}.
type Error struct {
	Status  int
	Message string
}

func (e *Error) Error() string { return e.Message }

// BadRequest -> 400 with the given message.
func BadRequest(msg string) *Error { return &Error{Status: http.StatusBadRequest, Message: msg} }

// NotFound -> 404.
func NotFound(msg string) *Error { return &Error{Status: http.StatusNotFound, Message: msg} }

// Unauthorized -> 401 (the Java backend uses this for "not allowed" too).
func Unauthorized(msg string) *Error { return &Error{Status: http.StatusUnauthorized, Message: msg} }

// Malformed is the 400 returned for unreadable request bodies.
func Malformed() *Error {
	return BadRequest("Malformed request body or invalid field format")
}

// Generic is the catch-all 500 used for anything the Java backend didn't handle specifically.
func Generic() *Error {
	return &Error{Status: http.StatusInternalServerError, Message: "Something went wrong"}
}

// Validation carries per-field messages and renders as a plain {"field":"message"} map with status 400.
type Validation map[string]string

func (v Validation) Error() string { return "validation failed" }

type body struct {
	Timestamp jsonx.Instant `json:"timestamp"`
	Status    int           `json:"status"`
	Message   string        `json:"message"`
}

func respond(status int, msg string) (int, any) {
	return status, body{Timestamp: jsonx.Now(), Status: status, Message: msg}
}

// Resolve maps any error to the response the Java backend would produce (status and JSON body).
// Database constraint violations become 409, like DataIntegrityViolationException.
func Resolve(err error) (int, any) {
	var ae *Error
	if errors.As(err, &ae) {
		return respond(ae.Status, ae.Message)
	}
	var v Validation
	if errors.As(err, &v) {
		return http.StatusBadRequest, map[string]string(v)
	}
	var pg *pgconn.PgError
	if errors.As(err, &pg) && isIntegrityCode(pg.Code) {
		msg := "Database constraint violation"
		text := strings.ToLower(pg.Message + " " + pg.Detail + " " + pg.ConstraintName + " " + pg.TableName + " " + pg.ColumnName)
		if strings.Contains(text, "email") || pg.TableName == "users" {
			msg = "Email already exists"
		}
		return respond(http.StatusConflict, msg)
	}
	return respond(http.StatusInternalServerError, "Something went wrong")
}

// isIntegrityCode: Spring maps SQLSTATE classes 22 (data exception, e.g. value too long), 23 (constraint),
// 27 and 44 to DataIntegrityViolationException.
func isIntegrityCode(code string) bool {
	for _, p := range []string{"22", "23", "27", "44"} {
		if strings.HasPrefix(code, p) {
			return true
		}
	}
	return false
}
