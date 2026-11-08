package mcp

import (
	"fmt"
	"strings"
)

// validateArgs checks tool arguments against the tool's JSON schema and reports problems with the same
// wording zod (used by the Node MCP SDK) produces. Unknown keys are ignored (zod strips them).
func validateArgs(schema Obj, args any) []string {
	var issues []string
	validate(schema, args, nil, &issues)
	return issues
}

func formatPath(path []any) string {
	var sb strings.Builder
	for i, p := range path {
		switch t := p.(type) {
		case int:
			fmt.Fprintf(&sb, "[%d]", t)
		case string:
			if i > 0 {
				sb.WriteByte('.')
			}
			sb.WriteString(t)
		}
	}
	return sb.String()
}

func addIssue(issues *[]string, path []any, msg string) {
	if len(path) > 0 {
		msg += " at " + formatPath(path)
	}
	*issues = append(*issues, msg)
}

func validate(schema Obj, v any, path []any, issues *[]string) {
	typ, _ := schema.Val("type").(string)
	enumVals, hasEnum := schema.Val("enum").([]any)
	if isUndef(v) {
		addIssue(issues, path, "Required")
		return
	}
	if hasEnum {
		quoted := make([]string, len(enumVals))
		for i, e := range enumVals {
			quoted[i] = "'" + toStr(e) + "'"
		}
		s, ok := v.(string)
		if !ok {
			addIssue(issues, path, fmt.Sprintf("Expected %s, received %s", strings.Join(quoted, " | "), typeName(v)))
			return
		}
		for _, e := range enumVals {
			if e == s {
				return
			}
		}
		addIssue(issues, path, fmt.Sprintf("Invalid enum value. Expected %s, received '%s'", strings.Join(quoted, " | "), s))
		return
	}
	if typ != "" && typeName(v) != typ {
		addIssue(issues, path, fmt.Sprintf("Expected %s, received %s", typ, typeName(v)))
		return
	}
	switch typ {
	case "number":
		f := num(v)
		if min, ok := schema.Get("minimum"); ok && f < num(min) {
			addIssue(issues, path, fmt.Sprintf("Number must be greater than or equal to %s", toStr(min)))
		}
		if max, ok := schema.Get("maximum"); ok && f > num(max) {
			addIssue(issues, path, fmt.Sprintf("Number must be less than or equal to %s", toStr(max)))
		}
	case "array":
		items, _ := schema.Val("items").(Obj)
		for i, e := range v.([]any) {
			validate(items, e, append(append([]any{}, path...), i), issues)
		}
	case "object":
		props, _ := schema.Val("properties").(Obj)
		obj := v.(Obj)
		required, _ := schema.Val("required").([]any)
		for _, p := range props {
			sub, _ := p.V.(Obj)
			val := obj.Val(p.K)
			if isUndef(val) {
				isReq := false
				for _, r := range required {
					if r == p.K {
						isReq = true
					}
				}
				if !isReq {
					continue
				}
			}
			validate(sub, val, append(append([]any{}, path...), p.K), issues)
		}
	}
}
