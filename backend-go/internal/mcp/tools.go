package mcp

import (
	"context"
	"fmt"
	"log"
	"net/http"
	"net/http/httptest"
	"sort"
	"strings"
	"time"
)

// apiClient calls the REST API of this very process (no network hop) with the caller's credentials, so
// every tool behaves exactly like the equivalent REST request and returns the same error messages.
type apiClient struct {
	ctx     context.Context
	handler http.Handler
	key     string
}

type apiError struct{ msg string }

func (e *apiError) Error() string { return e.msg }

// do performs a request and returns the parsed JSON body (or the raw text when it isn't JSON).
// Non-2xx statuses become errors worded like axios + `response.data.message`.
func (c *apiClient) do(method, path string, body any) (any, error) {
	var rd *strings.Reader
	if body != nil {
		rd = strings.NewReader(Stringify(body, ""))
	} else {
		rd = strings.NewReader("")
	}
	req, err := http.NewRequestWithContext(c.ctx, method, path, rd)
	if err != nil {
		return nil, &apiError{err.Error()}
	}
	req.Host = "localhost"
	req.RemoteAddr = "127.0.0.1:0"
	req.Header.Set("Content-Type", "application/json")
	if c.key != "" {
		if strings.HasPrefix(c.key, "todo_live_") {
			req.Header.Set("X-API-Key", c.key)
		} else {
			req.Header.Set("Authorization", "Bearer "+c.key)
		}
	}
	rec := httptest.NewRecorder()
	c.handler.ServeHTTP(rec, req)
	raw := rec.Body.Bytes()
	var data any = string(raw)
	if parsed, err := ParseJSON(raw); err == nil {
		data = parsed
	}
	if rec.Code < 200 || rec.Code > 299 {
		if o, ok := data.(Obj); ok {
			if m := o.Val("message"); truthy(m) {
				return nil, &apiError{toStr(m)}
			}
		}
		return nil, &apiError{fmt.Sprintf("Request failed with status code %d", rec.Code)}
	}
	return data, nil
}

func (c *apiClient) get(path string) (any, error)          { return c.do(http.MethodGet, path, nil) }
func (c *apiClient) post(path string, b any) (any, error)  { return c.do(http.MethodPost, path, b) }
func (c *apiClient) put(path string, b any) (any, error)   { return c.do(http.MethodPut, path, b) }
func (c *apiClient) patch(path string, b any) (any, error) { return c.do(http.MethodPatch, path, b) }
func (c *apiClient) del(path string) (any, error)          { return c.do(http.MethodDelete, path, nil) }

// field reads a member of a decoded response (undefined when it isn't an object).
func field(v any, k string) any {
	if o, ok := v.(Obj); ok {
		return o.Val(k)
	}
	return Undef{}
}

func array(v any) []any {
	a, _ := v.([]any)
	return a
}

// toolResult is the {content:[{type:"text",text}], isError?} envelope.
func textResult(text string) any {
	return Obj{{"content", []any{Obj{{"type", "text"}, {"text", text}}}}}
}

func errorResult(text string) any {
	return Obj{{"content", []any{Obj{{"type", "text"}, {"text", text}}}}, {"isError", true}}
}

func pretty(v any) string { return Stringify(v, "  ") }

func errMsg(err error) string { return err.Error() }

func lower(v any) string { return strings.ToLower(toStr(v)) }

// resolveOrCreateProject mirrors the Node helper: errors are swallowed and yield "no project".
func resolveOrCreateProject(c *apiClient, name, id any) any {
	if truthy(id) {
		return id
	}
	nm, _ := name.(string)
	if !truthy(name) || strings.TrimSpace(nm) == "" {
		return Undef{}
	}
	projects, err := c.get("/api/v1/projects")
	if err != nil {
		log.Printf("mcp: error resolving/creating project: %v", err)
		return Undef{}
	}
	want := strings.ToLower(strings.TrimSpace(nm))
	for _, p := range array(projects) {
		if strings.ToLower(strings.TrimSpace(toStr(field(p, "name")))) == want {
			return field(p, "id")
		}
	}
	created, err := c.post("/api/v1/projects", Obj{{"name", strings.TrimSpace(nm)}, {"color", "#3b82f6"}})
	if err != nil {
		log.Printf("mcp: error resolving/creating project: %v", err)
		return Undef{}
	}
	return field(created, "id")
}

// orDefault implements `a || b`.
func orDefault(a, b any) any {
	if truthy(a) {
		return a
	}
	return b
}

func orNull(a any) any { return orDefault(a, nil) }

type toolFn func(c *apiClient, a Obj) any

var toolFns = map[string]toolFn{
	"todoflow_create_task":        createTask,
	"todoflow_create_tasks_batch": createTasksBatch,
	"todoflow_list_tasks":         listTasks,
	"todoflow_complete_task":      completeTask,
	"todoflow_add_subtasks":       addSubtasks,
	"todoflow_list_projects":      listProjects,
	"todoflow_create_project":     createProject,
	"todoflow_get_task":           getTask,
	"todoflow_update_task":        updateTask,
	"todoflow_delete_task":        deleteTask,
	"todoflow_toggle_subtask":     toggleSubtask,
	"todoflow_add_comment":        addComment,
	"todoflow_list_comments":      listComments,
	"todoflow_list_labels":        listLabels,
	"todoflow_create_label":       createLabel,
	"todoflow_daily_briefing":     dailyBriefing,
}

func createTask(c *apiClient, a Obj) any {
	projectID := resolveOrCreateProject(c, a.Val("projectName"), a.Val("projectId"))
	payload := Obj{
		{"title", a.Val("title")},
		{"description", a.Val("description")},
		{"priority", orDefault(a.Val("priority"), 1)},
		{"dueDate", orNull(a.Val("dueDate"))},
		{"projectId", orNull(projectID)},
		{"recurrenceType", orDefault(a.Val("recurrenceType"), "NONE")},
		{"recurrenceInterval", orDefault(a.Val("recurrenceInterval"), 1)},
	}
	task, err := c.post("/api/v1/tasks", payload)
	if err != nil {
		return errorResult("Failed to create task in TodoFlow: " + errMsg(err))
	}
	created := []any{}
	subs := array(a.Val("subtasks"))
	if len(subs) > 0 && truthy(field(task, "id")) {
		for _, t := range subs {
			if _, err := c.post("/api/v1/tasks/"+toStr(field(task, "id"))+"/subtasks", Obj{{"title", t}}); err != nil {
				log.Printf("mcp: error creating subtask '%s': %v", toStr(t), err)
				continue
			}
			created = append(created, t)
		}
	}
	return textResult(pretty(Obj{
		{"success", true},
		{"message", fmt.Sprintf("Task '%s' created successfully in TodoFlow.", toStr(field(task, "title")))},
		{"task", Obj{
			{"id", field(task, "id")}, {"title", field(task, "title")}, {"priority", field(task, "priority")},
			{"dueDate", field(task, "dueDate")}, {"projectId", field(task, "projectId")}, {"subtasks", created},
		}},
	}))
}

func createTasksBatch(c *apiClient, a Obj) any {
	results := []any{}
	cache := map[string]any{}
	for _, item := range array(a.Val("tasks")) {
		it, _ := item.(Obj)
		var projectID any = Undef{}
		pn := it.Val("projectName")
		if s, ok := pn.(string); ok && strings.TrimSpace(s) != "" {
			key := strings.ToLower(strings.TrimSpace(s))
			if v, ok := cache[key]; ok {
				projectID = v
			} else {
				projectID = resolveOrCreateProject(c, pn, Undef{})
				cache[key] = projectID
			}
		}
		payload := Obj{
			{"title", it.Val("title")},
			{"description", it.Val("description")},
			{"priority", orDefault(it.Val("priority"), 1)},
			{"dueDate", orNull(it.Val("dueDate"))},
			{"projectId", orNull(projectID)},
		}
		task, err := c.post("/api/v1/tasks", payload)
		if err != nil {
			return errorResult("Failed to create batch tasks: " + errMsg(err))
		}
		subs := array(it.Val("subtasks"))
		if len(subs) > 0 && truthy(field(task, "id")) {
			for _, t := range subs {
				if _, err := c.post("/api/v1/tasks/"+toStr(field(task, "id"))+"/subtasks", Obj{{"title", t}}); err != nil {
					return errorResult("Failed to create batch tasks: " + errMsg(err))
				}
			}
		}
		results = append(results, Obj{{"id", field(task, "id")}, {"title", field(task, "title")}, {"subtasksCount", len(subs)}})
	}
	return textResult(pretty(Obj{
		{"success", true},
		{"message", fmt.Sprintf("Successfully created %d tasks in TodoFlow.", len(results))},
		{"tasks", results},
	}))
}

// dateMillis is `new Date(s).getTime()` (NaN → ok=false).
func dateMillis(v any) (int64, bool) {
	s, ok := v.(string)
	if !ok {
		return 0, false
	}
	for _, layout := range []string{time.RFC3339Nano, "2006-01-02T15:04:05", "2006-01-02T15:04", "2006-01-02"} {
		layoutTZ := layout
		if t, err := time.Parse(layoutTZ, s); err == nil {
			return t.UnixMilli(), true
		}
	}
	return 0, false
}

func startOfToday() int64 {
	now := time.Now()
	y, m, d := now.Date()
	return time.Date(y, m, d, 0, 0, 0, 0, now.Location()).UnixMilli()
}

const dayMs = 24 * 60 * 60 * 1000

func listTasks(c *apiClient, a Obj) any {
	taskURL := "/api/v1/tasks?size=100"
	if pn, ok := a.Val("projectName").(string); ok && strings.TrimSpace(pn) != "" {
		projects, err := c.get("/api/v1/projects")
		if err != nil {
			return errorResult("Failed to list tasks from TodoFlow: " + errMsg(err))
		}
		var matchID any
		found := false
		for _, p := range array(projects) {
			if strings.ToLower(strings.TrimSpace(toStr(field(p, "name")))) == strings.ToLower(strings.TrimSpace(pn)) {
				matchID, found = field(p, "id"), true
				break
			}
		}
		if !found {
			return textResult(pretty(Obj{{"count", 0}, {"tasks", []any{}}, {"message", fmt.Sprintf("Project '%s' was not found.", pn)}}))
		}
		taskURL = "/api/v1/tasks?projectId=" + toStr(matchID) + "&size=100"
	}
	data, err := c.get(taskURL)
	if err != nil {
		return errorResult("Failed to list tasks from TodoFlow: " + errMsg(err))
	}
	tasks := array(field(data, "content"))

	if comp, ok := a.Val("completed").(bool); ok {
		var out []any
		for _, t := range tasks {
			if b, isBool := field(t, "completed").(bool); isBool && b == comp {
				out = append(out, t)
			}
		}
		tasks = out
	}
	if s, ok := a.Val("search").(string); ok && strings.TrimSpace(s) != "" {
		q := strings.ToLower(s)
		var out []any
		for _, t := range tasks {
			title, ok1 := field(t, "title").(string)
			desc, ok2 := field(t, "description").(string)
			if (ok1 && strings.Contains(strings.ToLower(title), q)) || (ok2 && strings.Contains(strings.ToLower(desc), q)) {
				out = append(out, t)
			}
		}
		tasks = out
	}

	todayStart := startOfToday()
	todayEnd := todayStart + dayMs
	view, _ := a.Val("view").(string)
	if view == "today" || view == "upcoming" || view == "overdue" {
		var out []any
		for _, t := range tasks {
			due, ok := dateMillis(field(t, "dueDate"))
			if !truthy(field(t, "dueDate")) {
				continue
			}
			keep := false
			switch view {
			case "today":
				keep = ok && due < todayEnd
			case "upcoming":
				keep = ok && due >= todayEnd
			case "overdue":
				done, _ := field(t, "completed").(bool)
				keep = !done && ok && due < todayStart
			}
			if keep {
				out = append(out, t)
			}
		}
		tasks = out
	}

	formatted := []any{}
	for _, t := range tasks {
		var subs any = Undef{}
		if sl, ok := field(t, "subtasks").([]any); ok {
			m := make([]any, 0, len(sl))
			for _, st := range sl {
				m = append(m, Obj{{"id", field(st, "id")}, {"title", field(st, "title")}, {"completed", field(st, "completed")}})
			}
			subs = m
		}
		formatted = append(formatted, Obj{
			{"id", field(t, "id")}, {"title", field(t, "title")}, {"completed", field(t, "completed")},
			{"priority", field(t, "priority")}, {"dueDate", field(t, "dueDate")}, {"projectId", field(t, "projectId")},
			{"subtasks", subs},
		})
	}
	return textResult(pretty(Obj{{"count", len(formatted)}, {"tasks", formatted}}))
}

func completeTask(c *apiClient, a Obj) any {
	completed := true
	if b, ok := a.Val("completed").(bool); ok {
		completed = b
	}
	// The REST API exposes task updates as PUT (there is no PATCH /tasks/{id}).
	updated, err := c.put("/api/v1/tasks/"+toStr(a.Val("taskId")), Obj{{"completed", completed}})
	if err != nil {
		return errorResult("Failed to update task completion: " + errMsg(err))
	}
	state := "active"
	if completed {
		state = "completed"
	}
	return textResult(fmt.Sprintf("Task '%s' is now marked as %s.", toStr(field(updated, "title")), state))
}

func addSubtasks(c *apiClient, a Obj) any {
	added := 0
	for _, t := range array(a.Val("subtasks")) {
		if _, err := c.post("/api/v1/tasks/"+toStr(a.Val("taskId"))+"/subtasks", Obj{{"title", t}}); err != nil {
			return errorResult("Failed to add subtasks: " + errMsg(err))
		}
		added++
	}
	return textResult(fmt.Sprintf("Added %d subtasks to task checklist.", added))
}

func listProjects(c *apiClient, a Obj) any {
	projects, err := c.get("/api/v1/projects")
	if err != nil {
		return errorResult("Failed to list projects: " + errMsg(err))
	}
	return textResult(pretty(projects))
}

func createProject(c *apiClient, a Obj) any {
	p, err := c.post("/api/v1/projects", Obj{{"name", a.Val("name")}, {"color", orDefault(a.Val("color"), "#3b82f6")}})
	if err != nil {
		return errorResult("Failed to create project: " + errMsg(err))
	}
	return textResult(fmt.Sprintf("Project '%s' created successfully with ID: %s.", toStr(field(p, "name")), toStr(field(p, "id"))))
}

func getTask(c *apiClient, a Obj) any {
	task, err := c.get("/api/v1/tasks/" + toStr(a.Val("taskId")))
	if err != nil {
		return errorResult(fmt.Sprintf("Failed to get task '%s': %s", toStr(a.Val("taskId")), errMsg(err)))
	}
	return textResult(pretty(task))
}

func updateTask(c *apiClient, a Obj) any {
	resolved := a.Val("projectId")
	if !truthy(resolved) && truthy(a.Val("projectName")) {
		resolved = resolveOrCreateProject(c, a.Val("projectName"), Undef{})
	}
	payload := Obj{}
	for _, k := range []string{"title", "description", "priority", "dueDate", "completed"} {
		if v, ok := a.Get(k); ok {
			payload = append(payload, KV{k, v})
		}
	}
	if b, _ := a.Val("clearProject").(bool); b {
		payload = append(payload, KV{"clearProject", true})
	} else if truthy(resolved) {
		payload = append(payload, KV{"projectId", resolved})
	}
	updated, err := c.put("/api/v1/tasks/"+toStr(a.Val("taskId")), payload)
	if err != nil {
		return errorResult(fmt.Sprintf("Failed to update task '%s': %s", toStr(a.Val("taskId")), errMsg(err)))
	}
	return textResult(fmt.Sprintf("Task '%s' updated successfully.\n", toStr(field(updated, "title"))) + pretty(updated))
}

func deleteTask(c *apiClient, a Obj) any {
	if _, err := c.del("/api/v1/tasks/" + toStr(a.Val("taskId"))); err != nil {
		return errorResult(fmt.Sprintf("Failed to delete task '%s': %s", toStr(a.Val("taskId")), errMsg(err)))
	}
	return textResult(fmt.Sprintf("Task '%s' has been permanently deleted.", toStr(a.Val("taskId"))))
}

func toggleSubtask(c *apiClient, a Obj) any {
	payload := Obj{}
	if v, ok := a.Val("completed").(bool); ok {
		payload = append(payload, KV{"completed", v})
	} else {
		payload = append(payload, KV{"completed", true})
	}
	if truthy(a.Val("title")) {
		payload = append(payload, KV{"title", a.Val("title")})
	}
	updated, err := c.patch("/api/v1/tasks/"+toStr(a.Val("taskId"))+"/subtasks/"+toStr(a.Val("subtaskId")), payload)
	if err != nil {
		return errorResult("Failed to update subtask: " + errMsg(err))
	}
	state := "incomplete"
	if truthy(field(updated, "completed")) {
		state = "completed"
	}
	return textResult(fmt.Sprintf("Subtask '%s' is now marked as %s.", toStr(field(updated, "title")), state))
}

func addComment(c *apiClient, a Obj) any {
	comment, err := c.post("/api/v1/tasks/"+toStr(a.Val("taskId"))+"/comments", Obj{{"content", a.Val("content")}})
	if err != nil {
		return errorResult("Failed to add comment: " + errMsg(err))
	}
	return textResult(fmt.Sprintf("Added comment to task (Comment ID: %s).", toStr(field(comment, "id"))))
}

func listComments(c *apiClient, a Obj) any {
	comments, err := c.get("/api/v1/tasks/" + toStr(a.Val("taskId")) + "/comments")
	if err != nil {
		return errorResult("Failed to list comments: " + errMsg(err))
	}
	return textResult(pretty(comments))
}

func listLabels(c *apiClient, a Obj) any {
	labels, err := c.get("/api/v1/labels")
	if err != nil {
		return errorResult("Failed to list labels: " + errMsg(err))
	}
	return textResult(pretty(labels))
}

func createLabel(c *apiClient, a Obj) any {
	l, err := c.post("/api/v1/labels", Obj{{"name", a.Val("name")}, {"color", orDefault(a.Val("color"), "#3b82f6")}})
	if err != nil {
		return errorResult("Failed to create label: " + errMsg(err))
	}
	return textResult(fmt.Sprintf("Label '%s' created successfully with ID: %s.", toStr(field(l, "name")), toStr(field(l, "id"))))
}

func dailyBriefing(c *apiClient, a Obj) any {
	data, err := c.get("/api/v1/tasks?size=100")
	if err != nil {
		return errorResult("Failed to generate daily briefing: " + errMsg(err))
	}
	tasks := array(field(data, "content"))
	start := startOfToday()
	endToday := start + dayMs
	endWeek := start + 7*dayMs

	type summary struct {
		prio float64
		obj  Obj
	}
	var overdue, today, upcoming []summary
	completedCount, totalActive := 0, 0
	for _, t := range tasks {
		if truthy(field(t, "completed")) {
			completedCount++
			continue
		}
		totalActive++
		if !truthy(field(t, "dueDate")) {
			continue
		}
		due, ok := dateMillis(field(t, "dueDate"))
		pending := 0
		for _, s := range array(field(t, "subtasks")) {
			if !truthy(field(s, "completed")) {
				pending++
			}
		}
		prio := num(field(t, "priority"))
		if !truthy(field(t, "priority")) {
			prio = 1
		}
		s := summary{prio, Obj{
			{"id", field(t, "id")}, {"title", field(t, "title")}, {"priority", field(t, "priority")},
			{"dueDate", field(t, "dueDate")}, {"projectName", orDefault(field(t, "projectName"), "Inbox")},
			{"subtasksPending", pending},
		}}
		switch {
		case !ok:
		case due < start:
			overdue = append(overdue, s)
		case due < endToday:
			today = append(today, s)
		case due < endWeek:
			upcoming = append(upcoming, s)
		}
	}
	byPrio := func(l []summary) {
		sort.SliceStable(l, func(i, j int) bool { return l[i].prio > l[j].prio })
	}
	byPrio(today)
	byPrio(overdue)
	objs := func(l []summary) []any {
		out := make([]any, len(l))
		for i, s := range l {
			out[i] = s.obj
		}
		return out
	}
	return textResult(pretty(Obj{
		{"overview", Obj{
			{"totalActiveTasks", totalActive}, {"overdueCount", len(overdue)}, {"dueTodayCount", len(today)},
			{"upcomingCount", len(upcoming)}, {"completedCount", completedCount},
		}},
		{"overdueTasks", objs(overdue)},
		{"dueToday", objs(today)},
		{"upcomingThisWeek", objs(upcoming)},
	}))
}
