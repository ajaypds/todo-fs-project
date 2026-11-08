package mcp

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"math"
	"strconv"
	"strings"
	"unicode/utf8"
)

// The MCP server is a port of a Node implementation, so it reproduces JavaScript's JSON semantics:
// object keys keep their insertion order, numbers print like JS numbers and JSON.stringify(v, null, 2)
// layout is reproduced byte for byte. The value model is:
//
//	nil → null, bool, string, Num (number as it appeared on the wire), float64, []any, Obj, Undef.
type (
	// Num is a JSON number kept in its textual form.
	Num string
	// KV is one object member.
	KV struct {
		K string
		V any
	}
	// Obj is an insertion-ordered JSON object.
	Obj []KV
	// Undef marks a missing value; it is skipped in objects and printed as null inside arrays.
	Undef struct{}
)

// Get returns the member value (and whether it is present).
func (o Obj) Get(k string) (any, bool) {
	for _, kv := range o {
		if kv.K == k {
			return kv.V, true
		}
	}
	return nil, false
}

// Val returns the member or Undef.
func (o Obj) Val(k string) any {
	if v, ok := o.Get(k); ok {
		return v
	}
	return Undef{}
}

func (o *Obj) set(k string, v any) {
	for i := range *o {
		if (*o)[i].K == k {
			(*o)[i].V = v
			return
		}
	}
	*o = append(*o, KV{k, v})
}

// ParseJSON decodes a document keeping object order and number text. Trailing data is an error.
func ParseJSON(b []byte) (any, error) {
	dec := json.NewDecoder(bytes.NewReader(b))
	dec.UseNumber()
	v, err := parseValue(dec)
	if err != nil {
		return nil, err
	}
	if _, err := dec.Token(); err != io.EOF {
		return nil, fmt.Errorf("unexpected data after JSON value")
	}
	return v, nil
}

func parseValue(dec *json.Decoder) (any, error) {
	tok, err := dec.Token()
	if err != nil {
		return nil, err
	}
	switch t := tok.(type) {
	case json.Delim:
		switch t {
		case '{':
			obj := Obj{}
			for dec.More() {
				kt, err := dec.Token()
				if err != nil {
					return nil, err
				}
				v, err := parseValue(dec)
				if err != nil {
					return nil, err
				}
				obj.set(kt.(string), v)
			}
			if _, err := dec.Token(); err != nil {
				return nil, err
			}
			return obj, nil
		case '[':
			arr := []any{}
			for dec.More() {
				v, err := parseValue(dec)
				if err != nil {
					return nil, err
				}
				arr = append(arr, v)
			}
			if _, err := dec.Token(); err != nil {
				return nil, err
			}
			return arr, nil
		}
		return nil, fmt.Errorf("unexpected delimiter")
	case json.Number:
		return Num(t.String()), nil
	default: // string, bool, nil
		return t, nil
	}
}

// ---- encoding ----------------------------------------------------------------------------------------

// Stringify mimics JSON.stringify(v) (indent == "") or JSON.stringify(v, null, 2) (indent == "  ").
func Stringify(v any, indent string) string {
	var sb strings.Builder
	encode(&sb, v, indent, "")
	return sb.String()
}

func isUndef(v any) bool { _, ok := v.(Undef); return ok }

func encode(sb *strings.Builder, v any, indent, cur string) {
	switch t := v.(type) {
	case nil, Undef:
		sb.WriteString("null")
	case bool:
		sb.WriteString(strconv.FormatBool(t))
	case string:
		writeJSString(sb, t)
	case Num:
		sb.WriteString(jsNumber(parseNum(t)))
	case float64:
		sb.WriteString(jsNumber(t))
	case int:
		sb.WriteString(strconv.Itoa(t))
	case int64:
		sb.WriteString(strconv.FormatInt(t, 10))
	case []string:
		a := make([]any, len(t))
		for i, s := range t {
			a[i] = s
		}
		encode(sb, a, indent, cur)
	case []any:
		if len(t) == 0 {
			sb.WriteString("[]")
			return
		}
		next := cur + indent
		sb.WriteByte('[')
		for i, e := range t {
			if i > 0 {
				sb.WriteByte(',')
			}
			if indent != "" {
				sb.WriteString("\n" + next)
			}
			encode(sb, e, indent, next)
		}
		if indent != "" {
			sb.WriteString("\n" + cur)
		}
		sb.WriteByte(']')
	case Obj:
		n := 0
		for _, kv := range t {
			if !isUndef(kv.V) {
				n++
			}
		}
		if n == 0 {
			sb.WriteString("{}")
			return
		}
		next := cur + indent
		sb.WriteByte('{')
		first := true
		for _, kv := range t {
			if isUndef(kv.V) {
				continue
			}
			if !first {
				sb.WriteByte(',')
			}
			first = false
			if indent != "" {
				sb.WriteString("\n" + next)
			}
			writeJSString(sb, kv.K)
			sb.WriteByte(':')
			if indent != "" {
				sb.WriteByte(' ')
			}
			encode(sb, kv.V, indent, next)
		}
		if indent != "" {
			sb.WriteString("\n" + cur)
		}
		sb.WriteByte('}')
	default:
		sb.WriteString("null")
	}
}

func writeJSString(sb *strings.Builder, s string) {
	sb.WriteByte('"')
	for i := 0; i < len(s); {
		r, size := utf8.DecodeRuneInString(s[i:])
		switch {
		case r == '"':
			sb.WriteString(`\"`)
		case r == '\\':
			sb.WriteString(`\\`)
		case r == '\b':
			sb.WriteString(`\b`)
		case r == '\f':
			sb.WriteString(`\f`)
		case r == '\n':
			sb.WriteString(`\n`)
		case r == '\r':
			sb.WriteString(`\r`)
		case r == '\t':
			sb.WriteString(`\t`)
		case r < 0x20:
			fmt.Fprintf(sb, `\u%04x`, r)
		default:
			sb.WriteString(s[i : i+size])
		}
		i += size
	}
	sb.WriteByte('"')
}

func parseNum(n Num) float64 {
	f, err := strconv.ParseFloat(string(n), 64)
	if err != nil {
		return math.NaN()
	}
	return f
}

// jsNumber formats like Number.prototype.toString.
func jsNumber(f float64) string {
	if math.IsNaN(f) || math.IsInf(f, 0) {
		return "null"
	}
	if f == 0 {
		return "0"
	}
	abs := math.Abs(f)
	if abs >= 1e-6 && abs < 1e21 {
		return strconv.FormatFloat(f, 'f', -1, 64)
	}
	s := strconv.FormatFloat(f, 'e', -1, 64)
	// Go: 1e-07 / 1e+21; JS: 1e-7 / 1e+21
	if i := strings.IndexByte(s, 'e'); i >= 0 {
		mant, exp := s[:i], s[i+1:]
		sign := exp[:1]
		digits := strings.TrimLeft(exp[1:], "0")
		if digits == "" {
			digits = "0"
		}
		s = mant + "e" + sign + digits
	}
	return s
}

// ---- JavaScript helpers used by the tool implementations ----------------------------------------------

// truthy implements JS truthiness for the value model.
func truthy(v any) bool {
	switch t := v.(type) {
	case nil, Undef:
		return false
	case bool:
		return t
	case string:
		return t != ""
	case Num:
		f := parseNum(t)
		return f != 0 && !math.IsNaN(f)
	case float64:
		return t != 0 && !math.IsNaN(t)
	}
	return true
}

// toStr implements template-literal conversion `${v}`.
func toStr(v any) string {
	switch t := v.(type) {
	case nil:
		return "null"
	case Undef:
		return "undefined"
	case string:
		return t
	case bool:
		return strconv.FormatBool(t)
	case Num:
		return jsNumber(parseNum(t))
	case float64:
		return jsNumber(t)
	case []any:
		parts := make([]string, len(t))
		for i, e := range t {
			if e == nil || isUndef(e) {
				continue
			}
			parts[i] = toStr(e)
		}
		return strings.Join(parts, ",")
	case Obj:
		return "[object Object]"
	}
	return fmt.Sprint(v)
}

// num converts a number-like value to float64 (NaN when it isn't one).
func num(v any) float64 {
	switch t := v.(type) {
	case Num:
		return parseNum(t)
	case float64:
		return t
	}
	return math.NaN()
}

// typeName returns the JS-ish type zod reports in "received ...".
func typeName(v any) string {
	switch v.(type) {
	case nil:
		return "null"
	case Undef:
		return "undefined"
	case string:
		return "string"
	case bool:
		return "boolean"
	case Num, float64:
		return "number"
	case []any:
		return "array"
	case Obj:
		return "object"
	}
	return "unknown"
}
