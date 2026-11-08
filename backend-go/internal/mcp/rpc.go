package mcp

import (
	"context"
	_ "embed"
	"fmt"
	"math"
	"net/http"
	"strings"
	"time"
)

//go:embed tools.json
var toolsJSON []byte

const (
	latestProtocol  = "2025-11-25"
	serverName      = "todoflow"
	serverVersion   = "1.0.0"
	apiCallDeadline = 30 * time.Second
)

var supportedProtocols = []string{latestProtocol, "2025-06-18", "2025-03-26", "2024-11-05", "2024-10-07"}

// engine implements the JSON-RPC layer shared by both transports.
type engine struct {
	api     func() http.Handler
	tools   []any          // tool descriptors exactly as tools/list returns them
	schemas map[string]Obj // tool name → inputSchema
}

func newEngine(api func() http.Handler) *engine {
	parsed, err := ParseJSON(toolsJSON)
	if err != nil {
		panic("mcp: invalid embedded tools.json: " + err.Error())
	}
	e := &engine{api: api, tools: parsed.([]any), schemas: map[string]Obj{}}
	for _, t := range e.tools {
		o := t.(Obj)
		schema, _ := o.Val("inputSchema").(Obj)
		e.schemas[o.Val("name").(string)] = schema
	}
	return e
}

// message kinds
const (
	kindInvalid = iota
	kindRequest
	kindNotification
	kindResponse
)

func isIntegerNum(v any) bool {
	n, ok := v.(Num)
	if !ok {
		return false
	}
	f := parseNum(n)
	return !math.IsNaN(f) && f == math.Trunc(f)
}

func validID(v any) bool {
	if _, ok := v.(string); ok {
		return true
	}
	return isIntegerNum(v)
}

// classify validates a decoded value as a JSON-RPC 2.0 message.
func classify(v any) int {
	o, ok := v.(Obj)
	if !ok {
		return kindInvalid
	}
	if ver, _ := o.Val("jsonrpc").(string); ver != "2.0" {
		return kindInvalid
	}
	if method, has := o.Get("method"); has {
		if _, ok := method.(string); !ok {
			return kindInvalid
		}
		if p, has := o.Get("params"); has {
			if _, ok := p.(Obj); !ok {
				return kindInvalid
			}
		}
		if id, has := o.Get("id"); has {
			if !validID(id) {
				return kindInvalid
			}
			return kindRequest
		}
		return kindNotification
	}
	if id, has := o.Get("id"); has && validID(id) {
		if r, has := o.Get("result"); has {
			if _, ok := r.(Obj); ok {
				return kindResponse
			}
		}
		if e, has := o.Get("error"); has {
			if eo, ok := e.(Obj); ok {
				_, msgOK := eo.Val("message").(string)
				return map[bool]int{true: kindResponse, false: kindInvalid}[isIntegerNum(eo.Val("code")) && msgOK]
			}
		}
	}
	return kindInvalid
}

func rpcResult(id, result any) Obj {
	return Obj{{"result", result}, {"jsonrpc", "2.0"}, {"id", id}}
}

func rpcError(id any, code int, msg string) Obj {
	return Obj{{"jsonrpc", "2.0"}, {"id", id}, {"error", Obj{{"code", code}, {"message", msg}}}}
}

// transportError is the body shape used by transport-level failures: error first, id null.
func transportError(code int, msg string) Obj {
	return Obj{{"jsonrpc", "2.0"}, {"error", Obj{{"code", code}, {"message", msg}}}, {"id", nil}}
}

// isInitialize reports whether v is a well-formed initialize request.
func isInitialize(v any) bool {
	o, ok := v.(Obj)
	if !ok || classify(v) != kindRequest || o.Val("method") != "initialize" {
		return false
	}
	p, _ := o.Val("params").(Obj)
	if _, ok := p.Val("protocolVersion").(string); !ok {
		return false
	}
	if _, ok := p.Val("capabilities").(Obj); !ok {
		return false
	}
	ci, ok := p.Val("clientInfo").(Obj)
	if !ok {
		return false
	}
	_, n := ci.Val("name").(string)
	_, ver := ci.Val("version").(string)
	return n && ver
}

// handle processes one validated message. Requests yield a response; everything else yields nil.
func (e *engine) handle(ctx context.Context, apiKey string, v any) any {
	if classify(v) != kindRequest {
		return nil
	}
	o := v.(Obj)
	id := o.Val("id")
	params, _ := o.Val("params").(Obj)
	switch o.Val("method") {
	case "initialize":
		requested, _ := params.Val("protocolVersion").(string)
		version := latestProtocol
		for _, s := range supportedProtocols {
			if s == requested {
				version = requested
			}
		}
		return rpcResult(id, Obj{
			{"protocolVersion", version},
			{"capabilities", Obj{{"tools", Obj{{"listChanged", true}}}}},
			{"serverInfo", Obj{{"name", serverName}, {"version", serverVersion}}},
		})
	case "ping":
		return rpcResult(id, Obj{})
	case "tools/list":
		return rpcResult(id, Obj{{"tools", e.tools}})
	case "tools/call":
		return e.callTool(ctx, apiKey, id, o)
	}
	return rpcError(id, -32601, "Method not found")
}

func zodIssue(path []any, expected, received string) string {
	return Stringify([]any{Obj{
		{"expected", expected}, {"code", "invalid_type"}, {"path", path},
		{"message", fmt.Sprintf("Invalid input: expected %s, received %s", expected, received)},
	}}, "  ")
}

func (e *engine) callTool(ctx context.Context, apiKey string, id any, req Obj) any {
	params, hasParams := req.Get("params")
	po, isObj := params.(Obj)
	if !hasParams || !isObj {
		return rpcError(id, -32603, zodIssue([]any{"params"}, "object", typeName(orUndef(hasParams, params))))
	}
	nameV, hasName := po.Get("name")
	name, ok := nameV.(string)
	if !hasName || !ok {
		return rpcError(id, -32603, zodIssue([]any{"params", "name"}, "string", typeName(orUndef(hasName, nameV))))
	}
	args := Obj{}
	if av, has := po.Get("arguments"); has {
		ao, ok := av.(Obj)
		if !ok {
			return rpcError(id, -32603, zodIssue([]any{"params", "arguments"}, "record", typeName(av)))
		}
		args = ao
	}
	schema, known := e.schemas[name]
	if !known {
		return rpcResult(id, errorResult(fmt.Sprintf("MCP error -32602: Tool %s not found", name)))
	}
	if issues := validateArgs(schema, args); len(issues) > 0 {
		return rpcResult(id, errorResult(fmt.Sprintf("MCP error -32602: Input validation error: Invalid arguments for tool %s: %s", name, strings.Join(issues, "\n"))))
	}
	callCtx, cancel := context.WithTimeout(ctx, apiCallDeadline)
	defer cancel()
	client := &apiClient{ctx: callCtx, handler: e.api(), key: apiKey}
	return rpcResult(id, runTool(toolFns[name], client, args))
}

func orUndef(has bool, v any) any {
	if has {
		return v
	}
	return Undef{}
}

func runTool(fn toolFn, c *apiClient, args Obj) (res any) {
	defer func() {
		if r := recover(); r != nil {
			res = errorResult(fmt.Sprintf("%v", r))
		}
	}()
	return fn(c, args)
}
