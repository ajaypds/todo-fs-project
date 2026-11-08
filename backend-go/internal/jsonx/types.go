package jsonx

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"math"
	"strconv"
	"strings"

	"github.com/google/uuid"
)

// The request types below follow Jackson's default coercion rules used by Spring Boot, so that clients
// which worked against the Java backend keep working: numbers/bools are accepted as strings, numeric
// strings as numbers, JSON null is treated like an absent field, unknown fields are ignored.

func isNull(b []byte) bool { return bytes.Equal(bytes.TrimSpace(b), []byte("null")) }

// Str is an optional string.
type Str struct {
	V  string
	OK bool
}

func (s *Str) UnmarshalJSON(b []byte) error {
	if isNull(b) {
		return nil
	}
	b = bytes.TrimSpace(b)
	switch b[0] {
	case '"':
		if err := json.Unmarshal(b, &s.V); err != nil {
			return err
		}
	case '{', '[':
		return fmt.Errorf("cannot deserialize string from %c", b[0])
	default: // numbers and booleans are coerced to their text form
		s.V = string(b)
	}
	s.OK = true
	return nil
}

// Ptr returns nil when absent.
func (s Str) Ptr() *string {
	if !s.OK {
		return nil
	}
	v := s.V
	return &v
}

// Int is an optional integer.
type Int struct {
	V  int
	OK bool
}

func (n *Int) UnmarshalJSON(b []byte) error {
	if isNull(b) {
		return nil
	}
	b = bytes.TrimSpace(b)
	if b[0] == '"' {
		var text string
		if err := json.Unmarshal(b, &text); err != nil {
			return err
		}
		text = strings.TrimSpace(text)
		if text == "" {
			return nil // empty string coerces to null
		}
		f, err := strconv.ParseFloat(text, 64)
		if err != nil {
			return fmt.Errorf("not a number: %q", text)
		}
		n.V, n.OK = int(f), true
		return nil
	}
	if b[0] == '{' || b[0] == '[' || b[0] == 't' || b[0] == 'f' {
		return fmt.Errorf("cannot deserialize integer")
	}
	f, err := strconv.ParseFloat(string(b), 64)
	if err != nil || math.IsInf(f, 0) || f > math.MaxInt32 || f < math.MinInt32 {
		return fmt.Errorf("invalid integer")
	}
	n.V, n.OK = int(f), true // floats are truncated, like Jackson's ACCEPT_FLOAT_AS_INT
	return nil
}

// Bool is an optional boolean.
type Bool struct {
	V  bool
	OK bool
}

func (v *Bool) UnmarshalJSON(b []byte) error {
	if isNull(b) {
		return nil
	}
	b = bytes.TrimSpace(b)
	switch {
	case bytes.Equal(b, []byte("true")):
		v.V, v.OK = true, true
	case bytes.Equal(b, []byte("false")):
		v.V, v.OK = false, true
	case b[0] == '"':
		var text string
		if err := json.Unmarshal(b, &text); err != nil {
			return err
		}
		switch strings.TrimSpace(text) {
		case "true", "True", "TRUE":
			v.V, v.OK = true, true
		case "false", "False", "FALSE":
			v.V, v.OK = false, true
		case "":
		default:
			return fmt.Errorf("not a boolean: %q", text)
		}
	case b[0] == '{' || b[0] == '[':
		return fmt.Errorf("cannot deserialize boolean")
	default:
		f, err := strconv.ParseFloat(string(b), 64)
		if err != nil {
			return err
		}
		v.V, v.OK = f != 0, true
	}
	return nil
}

// UUID is an optional UUID.
type UUID struct {
	V  uuid.UUID
	OK bool
}

func (u *UUID) UnmarshalJSON(b []byte) error {
	if isNull(b) {
		return nil
	}
	var text string
	if err := json.Unmarshal(b, &text); err != nil {
		return err
	}
	if strings.TrimSpace(text) == "" {
		return nil
	}
	id, err := ParseUUID(text)
	if err != nil {
		return err
	}
	u.V, u.OK = id, true
	return nil
}

// ParseUUID parses like java.util.UUID.fromString (hyphenated form).
func ParseUUID(text string) (uuid.UUID, error) {
	id, err := uuid.Parse(strings.TrimSpace(text))
	if err != nil || strings.Count(text, "-") != 4 {
		return uuid.Nil, fmt.Errorf("invalid UUID: %q", text)
	}
	return id, nil
}

// UUIDList is an optional list of UUIDs (null differs from an empty list).
type UUIDList struct {
	V  []uuid.UUID
	OK bool
}

func (l *UUIDList) UnmarshalJSON(b []byte) error {
	if isNull(b) {
		return nil
	}
	var raw []UUID
	if err := json.Unmarshal(b, &raw); err != nil {
		return err
	}
	l.V = make([]uuid.UUID, 0, len(raw))
	for _, u := range raw {
		if u.OK {
			l.V = append(l.V, u.V)
		}
	}
	l.OK = true
	return nil
}

// Priority parses like LenientPriorityDeserializer: 1-4 as is, other numbers -> 4, words mapped to a level.
type Priority struct {
	V  int
	OK bool
}

func (p *Priority) UnmarshalJSON(b []byte) error {
	if isNull(b) {
		return nil
	}
	b = bytes.TrimSpace(b)
	p.OK = true
	if b[0] != '"' {
		if i, err := strconv.ParseInt(string(b), 10, 64); err == nil { // an integer token
			if i >= 1 && i <= 4 {
				p.V = int(i)
			} else {
				p.V = 4
			}
			return nil
		}
		if b[0] == '{' || b[0] == '[' {
			return fmt.Errorf("cannot deserialize priority")
		}
		p.V = priorityFromText(string(b)) // floats / booleans go through the text path
		return nil
	}
	var text string
	if err := json.Unmarshal(b, &text); err != nil {
		return err
	}
	p.V = priorityFromText(text)
	return nil
}

func priorityFromText(text string) int {
	text = strings.ToLower(strings.TrimSpace(text))
	if text == "" {
		return 4
	}
	if n, err := strconv.Atoi(text); err == nil {
		if n >= 1 && n <= 4 {
			return n
		}
		return 4
	}
	switch text {
	case "urgent", "critical", "p1", "highest":
		return 1
	case "high", "p2":
		return 2
	case "medium", "normal", "moderate", "p3":
		return 3
	default: // "low", "p4", "lowest" and anything unknown
		return 4
	}
}

// LenientInstant is an optional timestamp parsed with ParseLenientInstant.
type LenientInstant struct {
	V  Instant
	OK bool
}

func (l *LenientInstant) UnmarshalJSON(b []byte) error {
	if isNull(b) {
		return nil
	}
	var text string
	if err := json.Unmarshal(b, &text); err != nil {
		// non-string tokens: Jackson hands their text to the deserializer
		text = string(bytes.TrimSpace(b))
	}
	t, ok, err := ParseLenientInstant(text)
	if err != nil {
		return err
	}
	if ok {
		l.V, l.OK = From(t), true
	}
	return nil
}

// Decode reads one JSON object into dst. An empty body, null, or a non-object is an error.
func Decode(r io.Reader, dst any) error {
	data, err := io.ReadAll(io.LimitReader(r, 10<<20))
	if err != nil {
		return err
	}
	data = bytes.TrimSpace(data)
	if len(data) == 0 || isNull(data) || data[0] != '{' {
		return fmt.Errorf("body is not a JSON object")
	}
	return json.Unmarshal(data, dst)
}
