package mcp

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/gin-gonic/gin"
)

func TestStringifyMatchesJS(t *testing.T) {
	v, err := ParseJSON([]byte(`{"b":1.0,"a":[],"c":{},"d":[1,{"x":null}],"e":"<é\n>","n":1e21,"s":0.1}`))
	if err != nil {
		t.Fatal(err)
	}
	if got, want := Stringify(v, ""), `{"b":1,"a":[],"c":{},"d":[1,{"x":null}],"e":"<é\n>","n":1e+21,"s":0.1}`; got != want {
		t.Fatalf("compact:\n got %s\nwant %s", got, want)
	}
	if got, want := Stringify(Obj{{"k", []any{"v", Obj{}}}}, "  "), "{\n  \"k\": [\n    \"v\",\n    {}\n  ]\n}"; got != want {
		t.Fatalf("pretty:\n got %q\nwant %q", got, want)
	}
}

func TestValidateArgsMessages(t *testing.T) {
	e := newEngine(func() http.Handler { return http.NotFoundHandler() })
	args, _ := ParseJSON([]byte(`{"title":5,"priority":0,"recurrenceType":"X","subtasks":"a"}`))
	got := strings.Join(validateArgs(e.schemas["todoflow_create_task"], args), "\n")
	want := "Expected string, received number at title\n" +
		"Number must be greater than or equal to 1 at priority\n" +
		"Invalid enum value. Expected 'NONE' | 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY', received 'X' at recurrenceType\n" +
		"Expected array, received string at subtasks"
	if got != want {
		t.Fatalf("got:\n%s\nwant:\n%s", got, want)
	}
	nested, _ := ParseJSON([]byte(`{"tasks":[{"priority":1},{"title":"a","subtasks":[1]}]}`))
	got = strings.Join(validateArgs(e.schemas["todoflow_create_tasks_batch"], nested), "\n")
	if want := "Required at tasks[0].title\nExpected string, received number at tasks[1].subtasks[0]"; got != want {
		t.Fatalf("got %q want %q", got, want)
	}
}

// newTestServer mounts MCP over a fake REST API that records the credentials it receives.
func newTestServer(t *testing.T) (*httptest.Server, *[]string) {
	var seen []string
	api := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		seen = append(seen, r.Method+" "+r.URL.RequestURI()+" key="+r.Header.Get("X-API-Key")+" auth="+r.Header.Get("Authorization"))
		w.Header().Set("Content-Type", "application/json")
		switch {
		case r.URL.Path == "/api/v1/projects" && r.Method == http.MethodGet:
			_, _ = w.Write([]byte(`[{"id":"p1","name":"Work","color":"#fff"}]`))
		case r.URL.Path == "/api/v1/tasks/missing":
			w.WriteHeader(http.StatusNotFound)
			_, _ = w.Write([]byte(`{"message":"Task not found"}`))
		default:
			w.WriteHeader(http.StatusForbidden)
		}
	})
	gin.SetMode(gin.ReleaseMode)
	r := gin.New()
	Mount(r, func() http.Handler { return api })
	srv := httptest.NewServer(r)
	t.Cleanup(srv.Close)
	return srv, &seen
}

func rpc(t *testing.T, srv *httptest.Server, sid, key, body string) (int, string, http.Header) {
	t.Helper()
	req, _ := http.NewRequest(http.MethodPost, srv.URL+"/mcp", strings.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json, text/event-stream")
	if sid != "" {
		req.Header.Set("Mcp-Session-Id", sid)
	}
	if key != "" {
		req.Header.Set("X-API-Key", key)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	var sb strings.Builder
	buf := make([]byte, 4096)
	for {
		n, err := resp.Body.Read(buf)
		sb.Write(buf[:n])
		if err != nil {
			break
		}
	}
	return resp.StatusCode, sb.String(), resp.Header
}

const initBody = `{"jsonrpc":"2.0","id":0,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"t","version":"1"}}}`

func TestStreamableFlow(t *testing.T) {
	srv, seen := newTestServer(t)

	if st, body, hd := rpc(t, srv, "", "", initBody); st != 401 || !strings.Contains(hd.Get("WWW-Authenticate"), "resource_metadata=") || !strings.Contains(body, "-32001") {
		t.Fatalf("unauthenticated init: %d %s %v", st, body, hd)
	}
	st, body, hd := rpc(t, srv, "", "todo_live_abc", initBody)
	sid := hd.Get("Mcp-Session-Id")
	if st != 200 || sid == "" || body != `{"result":{"protocolVersion":"2025-03-26","capabilities":{"tools":{"listChanged":true}},"serverInfo":{"name":"todoflow","version":"1.0.0"}},"jsonrpc":"2.0","id":0}` {
		t.Fatalf("init: %d %s", st, body)
	}
	if st, _, _ := rpc(t, srv, sid, "", `{"jsonrpc":"2.0","method":"notifications/initialized"}`); st != 202 {
		t.Fatalf("notification status %d", st)
	}
	if st, body, _ := rpc(t, srv, sid, "", `{"jsonrpc":"2.0","id":1,"method":"tools/list"}`); st != 200 || strings.Count(body, `"name":"todoflow_`) != 16 {
		t.Fatalf("tools/list: %d %.200s", st, body)
	}
	if _, body, _ := rpc(t, srv, sid, "", `{"jsonrpc":"2.0","id":2,"method":"nope"}`); !strings.Contains(body, `"code":-32601`) {
		t.Fatalf("unknown method: %s", body)
	}
	// Tool call: credentials of the session are forwarded; API errors surface their message.
	_, body, _ = rpc(t, srv, sid, "", `{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"todoflow_get_task","arguments":{"taskId":"missing"}}}`)
	if !strings.Contains(body, `Failed to get task 'missing': Task not found`) || !strings.Contains(body, `"isError":true`) {
		t.Fatalf("get_task error: %s", body)
	}
	_, body, _ = rpc(t, srv, sid, "", `{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"todoflow_list_labels","arguments":{}}}`)
	if !strings.Contains(body, "Failed to list labels: Request failed with status code 403") {
		t.Fatalf("labels: %s", body)
	}
	if len(*seen) == 0 || !strings.Contains((*seen)[0], "key=todo_live_abc") {
		t.Fatalf("api key not forwarded: %v", *seen)
	}
	_, body, _ = rpc(t, srv, sid, "", `{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"todoflow_get_task","arguments":{}}}`)
	if !strings.Contains(body, "Input validation error: Invalid arguments for tool todoflow_get_task: Required at taskId") {
		t.Fatalf("validation: %s", body)
	}
	if st, _, _ := rpc(t, srv, "no-such-session", "x", `{}`); st != 404 {
		t.Fatalf("unknown session: %d", st)
	}
}

func TestBearerTokenForwarding(t *testing.T) {
	srv, seen := newTestServer(t)
	_, _, hd := rpc(t, srv, "", "eyJ.jwt.token", initBody)
	sid := hd.Get("Mcp-Session-Id")
	rpc(t, srv, sid, "", `{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"todoflow_list_projects","arguments":{}}}`)
	if len(*seen) != 1 || !strings.Contains((*seen)[0], "auth=Bearer eyJ.jwt.token") {
		t.Fatalf("expected bearer forwarding, got %v", *seen)
	}
}

func TestDiscoveryEndpoints(t *testing.T) {
	srv, _ := newTestServer(t)
	for path, want := range map[string]string{"/health": `"status":"UP"`, "/": `"TodoFlow Remote MCP Server"`, "/mcp": `"transport":"streamableHttp"`} {
		resp, err := http.Get(srv.URL + path)
		if err != nil {
			t.Fatal(err)
		}
		buf := make([]byte, 2048)
		n, _ := resp.Body.Read(buf)
		resp.Body.Close()
		if resp.StatusCode != 200 || !strings.Contains(string(buf[:n]), want) {
			t.Fatalf("%s: %d %s", path, resp.StatusCode, buf[:n])
		}
	}
}
