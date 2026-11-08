package api

import (
	"regexp"
	"strings"
	"unicode/utf8"

	"github.com/ajaypds/todo-fs-project/backend-go/internal/apperr"
)

// check collects bean-validation style messages per field. When a field breaks several rules the Java
// backend reports a random one (its violations live in a HashSet); here the last rule listed wins, deterministically.
type check struct{ errs apperr.Validation }

func newCheck() *check { return &check{errs: apperr.Validation{}} }

func (v *check) fail(field, msg string) { v.errs[field] = msg }

// notBlank: null, empty or whitespace-only fails.
func (v *check) notBlank(field string, val *string, msg string) {
	if val == nil || strings.TrimSpace(*val) == "" {
		v.fail(field, msg)
	}
}

// size: length in characters, null passes.
func (v *check) size(field string, val *string, min, max int, msg string) {
	if val == nil {
		return
	}
	n := utf8.RuneCountInString(*val)
	if n < min || n > max {
		v.fail(field, msg)
	}
}

// email: null and empty pass (like @Email); other values must be well formed.
func (v *check) email(field string, val *string, msg string) {
	if val == nil || *val == "" {
		return
	}
	if !validEmail(*val) {
		v.fail(field, msg)
	}
}

func (v *check) err() error {
	if len(v.errs) == 0 {
		return nil
	}
	return v.errs
}

const atom = "[a-z0-9!#$%&'*+/=?^_`{|}~\u0080-\U0010FFFF-]"

var (
	localPartRe = regexp.MustCompile(`(?i)^(?:` + atom + `+|"(?:[a-z0-9!#$%&'*.(),<>\[\]:; @+/=?^_` + "`" + `{|}~\x{0080}-\x{10FFFF}-]|\\\\|\\")+")(?:\.(?:` + atom + `+|"(?:[a-z0-9!#$%&'*.(),<>\[\]:; @+/=?^_` + "`" + `{|}~\x{0080}-\x{10FFFF}-]|\\\\|\\")+"))*$`)
	domainRe    = regexp.MustCompile(`(?i)^` + atom + `+(?:\.` + atom + `+)*$`)
	ipDomainRe  = regexp.MustCompile(`(?i)^\[(?:[0-9]{1,3}(?:\.[0-9]{1,3}){3}|ipv6:[0-9a-f:.]+)\]$`)
)

// validEmail follows Hibernate Validator's @Email rules.
func validEmail(s string) bool {
	at := strings.LastIndex(s, "@")
	if at < 0 {
		return false
	}
	local, domain := s[:at], s[at+1:]
	if len(local) > 64 || !localPartRe.MatchString(local) {
		return false
	}
	if strings.HasSuffix(domain, ".") || len(domain) > 255 {
		return false
	}
	return domainRe.MatchString(domain) || ipDomainRe.MatchString(domain)
}

func blank(s *string) bool { return s == nil || strings.TrimSpace(*s) == "" }
