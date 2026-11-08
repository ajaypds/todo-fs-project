package api

import (
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/ajaypds/todo-fs-project/backend-go/internal/ai"
	"github.com/ajaypds/todo-fs-project/backend-go/internal/apperr"
	"github.com/ajaypds/todo-fs-project/backend-go/internal/jsonx"
	"github.com/gin-gonic/gin"
)

func (s *Server) registerAI(v1 *gin.RouterGroup) {
	g := v1.Group("/ai")
	g.POST("/parse-task", s.handle(s.parseTask))
	g.POST("/productivity-coach", s.handle(s.productivityCoach))
	g.POST("/decompose-task", s.handle(s.decomposeTask))
	g.POST("/daily-planner", s.handle(s.dailyPlanner))
}

// zoneOrDefault mirrors ZoneId.of(tz) with a fallback for blank or invalid input.
func zoneOrDefault(tz *string, fallback string) (id string, loc *time.Location) {
	if tz != nil && strings.TrimSpace(*tz) != "" && validZoneID(*tz) {
		return *tz, zoneFor(*tz)
	}
	return fallback, zoneFor(fallback)
}

// aiFailed hides model/network errors behind the catch-all 500 (the Java backend doesn't handle them either).
func aiFailed(err error) error { _ = err; return apperr.Generic() }

type parsedTask struct {
	Title       *string `json:"title"`
	Description *string `json:"description"`
	Priority    *string `json:"priority"`
	DueDate     *string `json:"dueDate"`
}

func (s *Server) parseTask(c *gin.Context) error {
	var req struct {
		Input    jsonx.Str `json:"input"`
		Timezone jsonx.Str `json:"timezone"`
	}
	if err := bind(c, &req); err != nil {
		return err
	}
	zoneID, loc := zoneOrDefault(req.Timezone.Ptr(), "UTC")
	input := "null"
	if req.Input.OK {
		input = req.Input.V
	}
	prompt := fmt.Sprintf(ai.PromptParseTask, zoneID, ai.FormatZoned(time.Now().In(loc), zoneID), zoneID, input)

	var raw struct {
		Title       jsonx.Str `json:"title"`
		Description jsonx.Str `json:"description"`
		Priority    jsonx.Str `json:"priority"`
		DueDate     jsonx.Str `json:"dueDate"`
	}
	if err := s.AI.Entity(s.ctx(c), prompt, ai.SchemaParsedTask, &raw); err != nil {
		return aiFailed(err)
	}
	writeJSON(c, http.StatusOK, parsedTask{raw.Title.Ptr(), raw.Description.Ptr(), raw.Priority.Ptr(), raw.DueDate.Ptr()})
	return nil
}

type taskSnapshot struct {
	Title     *string `json:"title"`
	Priority  *string `json:"priority"`
	Completed *bool   `json:"completed"`
	DueDate   *string `json:"dueDate"`
}

func (s *Server) productivityCoach(c *gin.Context) error {
	var req struct {
		Tasks []struct {
			Title     jsonx.Str  `json:"title"`
			Priority  jsonx.Str  `json:"priority"`
			Completed jsonx.Bool `json:"completed"`
			DueDate   jsonx.Str  `json:"dueDate"`
		} `json:"tasks"`
	}
	if err := bind(c, &req); err != nil {
		return err
	}
	snap := struct {
		Tasks []taskSnapshot `json:"tasks"`
	}{}
	if req.Tasks != nil {
		snap.Tasks = make([]taskSnapshot, 0, len(req.Tasks))
	}
	for _, t := range req.Tasks {
		ts := taskSnapshot{Title: t.Title.Ptr(), Priority: t.Priority.Ptr(), DueDate: t.DueDate.Ptr()}
		if t.Completed.OK {
			v := t.Completed.V
			ts.Completed = &v
		}
		snap.Tasks = append(snap.Tasks, ts)
	}
	prompt := fmt.Sprintf(ai.PromptProductivity, string(marshal(snap)))

	out := struct {
		Summary         *string  `json:"summary"`
		Recommendations []string `json:"recommendations"`
	}{}
	if err := s.AI.Entity(s.ctx(c), prompt, ai.SchemaInsights, &out); err != nil {
		return aiFailed(err)
	}
	writeJSON(c, http.StatusOK, out)
	return nil
}

func (s *Server) decomposeTask(c *gin.Context) error {
	var req struct {
		Title       jsonx.Str `json:"title"`
		Description jsonx.Str `json:"description"`
	}
	if err := bind(c, &req); err != nil {
		return err
	}
	v := newCheck()
	v.notBlank("title", req.Title.Ptr(), "Task title is required")
	if err := v.err(); err != nil {
		return err
	}
	info := "Task Title: " + req.Title.V
	if !blank(req.Description.Ptr()) {
		info += "\nTask Description: " + req.Description.V
	}
	prompt := fmt.Sprintf(ai.PromptDecompose, info)

	type item struct {
		Title *string `json:"title"`
	}
	out := struct {
		Subtasks []item `json:"subtasks"`
	}{Subtasks: []item{}}
	if err := s.AI.Entity(s.ctx(c), prompt, ai.SchemaDecompose, &out); err != nil {
		return aiFailed(err)
	}
	if out.Subtasks == nil {
		out.Subtasks = []item{}
	}
	writeJSON(c, http.StatusOK, out)
	return nil
}

type matrixItem struct {
	TaskID          *string `json:"taskId"`
	Title           *string `json:"title"`
	Rationale       *string `json:"rationale"`
	UrgencyScore    *int    `json:"urgencyScore"`
	ImportanceScore *int    `json:"importanceScore"`
}

type timeBlock struct {
	TimePeriod *string  `json:"timePeriod"`
	FocusTheme *string  `json:"focusTheme"`
	TaskIDs    []string `json:"taskIds"`
	TaskTitles []string `json:"taskTitles"`
}

type eisenhower struct {
	DoFirst   []matrixItem `json:"doFirst"`
	Schedule  []matrixItem `json:"schedule"`
	Delegate  []matrixItem `json:"delegate"`
	Eliminate []matrixItem `json:"eliminate"`
}

func emptyMatrix() *eisenhower {
	return &eisenhower{DoFirst: []matrixItem{}, Schedule: []matrixItem{}, Delegate: []matrixItem{}, Eliminate: []matrixItem{}}
}

type dailyPlan struct {
	Summary     *string     `json:"summary"`
	CoachingTip *string     `json:"coachingTip"`
	TimeBlocks  []timeBlock `json:"timeBlocks"`
	Matrix      *eisenhower `json:"matrix"`
}

func (s *Server) dailyPlanner(c *gin.Context) error {
	var req struct {
		Tasks []struct {
			ID                    jsonx.Str `json:"id"`
			Title                 jsonx.Str `json:"title"`
			Description           jsonx.Str `json:"description"`
			Priority              jsonx.Int `json:"priority"`
			DueDate               jsonx.Str `json:"dueDate"`
			ProjectName           jsonx.Str `json:"projectName"`
			SubtaskCount          jsonx.Int `json:"subtaskCount"`
			CompletedSubtaskCount jsonx.Int `json:"completedSubtaskCount"`
		} `json:"tasks"`
		UserTimezone jsonx.Str `json:"userTimezone"`
	}
	if err := bind(c, &req); err != nil {
		return err
	}
	if len(req.Tasks) == 0 {
		writeJSON(c, http.StatusOK, dailyPlan{
			Summary:     strp("You have no active tasks currently on your plate. Enjoy the downtime or capture new goals!"),
			CoachingTip: strp("When you add new tasks, come back here to organize your day with the Eisenhower Matrix."),
			TimeBlocks:  []timeBlock{},
			Matrix:      emptyMatrix(),
		})
		return nil
	}
	type snap struct {
		ID                    *string `json:"id"`
		Title                 *string `json:"title"`
		Description           *string `json:"description"`
		Priority              *int    `json:"priority"`
		DueDate               *string `json:"dueDate"`
		ProjectName           *string `json:"projectName"`
		SubtaskCount          *int    `json:"subtaskCount"`
		CompletedSubtaskCount *int    `json:"completedSubtaskCount"`
	}
	intp := func(n jsonx.Int) *int {
		if !n.OK {
			return nil
		}
		v := n.V
		return &v
	}
	snaps := make([]snap, 0, len(req.Tasks))
	for _, t := range req.Tasks {
		snaps = append(snaps, snap{t.ID.Ptr(), t.Title.Ptr(), t.Description.Ptr(), intp(t.Priority), t.DueDate.Ptr(),
			t.ProjectName.Ptr(), intp(t.SubtaskCount), intp(t.CompletedSubtaskCount)})
	}
	zoneID, loc := zoneOrDefault(req.UserTimezone.Ptr(), "Asia/Kolkata")
	prompt := fmt.Sprintf(ai.PromptDailyPlan, zoneID, ai.FormatZoned(time.Now().In(loc), zoneID), string(marshal(snaps)))

	plan := dailyPlan{TimeBlocks: []timeBlock{}}
	if err := s.AI.Entity(s.ctx(c), prompt, ai.SchemaDailyPlan, &plan); err != nil {
		return aiFailed(err)
	}
	if plan.Matrix == nil {
		plan.Matrix = emptyMatrix()
	}
	if plan.TimeBlocks == nil {
		plan.TimeBlocks = []timeBlock{}
	}
	writeJSON(c, http.StatusOK, plan)
	return nil
}
