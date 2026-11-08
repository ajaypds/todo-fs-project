// Package jsonx reproduces the JSON behaviour of the Spring Boot backend (Jackson):
// ISO-8601 instants, nullable fields that are always present, and lenient request parsing.
package jsonx

import (
	"fmt"
	"strings"
	"time"
)

// Instant is a point in time that marshals exactly like java.time.Instant does in Spring Boot:
// "2026-11-08T10:44:23.480510Z" - fractional seconds appear in groups of 3 digits and only when non-zero.
type Instant time.Time

// Now returns the current time with nanosecond precision (like Instant.now()).
func Now() Instant { return Instant(time.Now().UTC()) }

// From converts a time.Time (any zone) to an Instant.
func From(t time.Time) Instant { return Instant(t.UTC()) }

// Ptr returns a pointer to an Instant built from t.
func Ptr(t time.Time) *Instant { i := From(t); return &i }

// Time returns the value as time.Time (UTC).
func (i Instant) Time() time.Time { return time.Time(i).UTC() }

// String formats like Instant.toString().
func (i Instant) String() string {
	t := time.Time(i).UTC()
	s := t.Format("2006-01-02T15:04:05")
	nano := t.Nanosecond()
	switch {
	case nano == 0:
	case nano%1_000_000 == 0:
		s += fmt.Sprintf(".%03d", nano/1_000_000)
	case nano%1_000 == 0:
		s += fmt.Sprintf(".%06d", nano/1_000)
	default:
		s += fmt.Sprintf(".%09d", nano)
	}
	return s + "Z"
}

// MarshalJSON implements json.Marshaler.
func (i Instant) MarshalJSON() ([]byte, error) { return []byte(`"` + i.String() + `"`), nil }

// ParseLenientInstant mirrors LenientInstantDeserializer: an ISO instant (with Z or an offset),
// a local date-time (taken as UTC) or a plain date (start of day, UTC). Blank input yields ok=false, err=nil.
func ParseLenientInstant(text string) (t time.Time, ok bool, err error) {
	text = strings.TrimSpace(text)
	if text == "" {
		return time.Time{}, false, nil
	}
	if v, e := time.Parse(time.RFC3339Nano, text); e == nil {
		return v.UTC(), true, nil
	}
	for _, layout := range []string{
		"2006-01-02T15:04:05.999999999",
		"2006-01-02T15:04:05",
		"2006-01-02T15:04",
	} {
		if v, e := time.ParseInLocation(layout, text, time.UTC); e == nil {
			return v.UTC(), true, nil
		}
	}
	if v, e := time.ParseInLocation("2006-01-02", text, time.UTC); e == nil {
		return v.UTC(), true, nil
	}
	return time.Time{}, false, fmt.Errorf("Failed to parse date into Instant: %s", text)
}
