// Package ai calls Google's Gemini API the way Spring AI's ChatClient does in the Java backend:
// the prompt is sent as a single user message with JSON-format instructions appended, and the
// model's reply is parsed as JSON into the response DTO.
package ai

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// Client talks to the Gemini generateContent REST endpoint.
type Client struct {
	APIKey  string
	Model   string
	BaseURL string
	HTTP    *http.Client
}

// New creates a client; baseURL defaults to Google's API host.
func New(apiKey, model, baseURL string) *Client {
	if baseURL == "" {
		baseURL = "https://generativelanguage.googleapis.com"
	}
	return &Client{APIKey: apiKey, Model: model, BaseURL: strings.TrimRight(baseURL, "/"),
		HTTP: &http.Client{Timeout: 120 * time.Second}}
}

// formatInstructions mirrors Spring AI's BeanOutputConverter text.
func formatInstructions(schema string) string {
	return "Your response should be in JSON format.\n" +
		"Do not include any explanations, only provide a RFC8259 compliant JSON response following this format without deviation.\n" +
		"Do not include markdown code blocks in your response.\n" +
		"Remove the ```json markdown surrounding the output including the trailing \"```\".\n" +
		"Here is the JSON Schema instance your output must adhere to:\n" +
		"```" + schema + "```\n"
}

// Entity sends prompt (plus format instructions) and decodes the JSON answer into out.
func (c *Client) Entity(ctx context.Context, prompt, schema string, out any) error {
	text, err := c.generate(ctx, prompt+"\n"+formatInstructions(schema))
	if err != nil {
		return err
	}
	return json.Unmarshal([]byte(cleanJSON(text)), out)
}

// cleanJSON strips ```json fences like BeanOutputConverter does.
func cleanJSON(text string) string {
	text = strings.TrimSpace(text)
	if strings.HasPrefix(text, "```") {
		text = strings.TrimPrefix(text, "```json")
		text = strings.TrimPrefix(text, "```")
		text = strings.TrimSuffix(strings.TrimSpace(text), "```")
	}
	return strings.TrimSpace(text)
}

type generateRequest struct {
	Contents []content `json:"contents"`
}

type content struct {
	Role  string `json:"role"`
	Parts []part `json:"parts"`
}

type part struct {
	Text string `json:"text"`
}

type generateResponse struct {
	Candidates []struct {
		Content content `json:"content"`
	} `json:"candidates"`
}

func (c *Client) generate(ctx context.Context, prompt string) (string, error) {
	if c.APIKey == "" {
		return "", fmt.Errorf("GEMINI_API_KEY is not configured")
	}
	body, _ := json.Marshal(generateRequest{Contents: []content{{Role: "user", Parts: []part{{Text: prompt}}}}})
	endpoint := fmt.Sprintf("%s/v1beta/models/%s:generateContent?key=%s", c.BaseURL, url.PathEscape(c.Model), url.QueryEscape(c.APIKey))

	var lastErr error
	for attempt := 0; attempt < 3; attempt++ { // spring-retry style: retry transient failures
		if attempt > 0 {
			select {
			case <-time.After(time.Duration(attempt) * 2 * time.Second):
			case <-ctx.Done():
				return "", ctx.Err()
			}
		}
		req, _ := http.NewRequestWithContext(ctx, http.MethodPost, endpoint, bytes.NewReader(body))
		req.Header.Set("Content-Type", "application/json")
		resp, err := c.HTTP.Do(req)
		if err != nil {
			lastErr = err
			continue
		}
		data, _ := io.ReadAll(io.LimitReader(resp.Body, 8<<20))
		resp.Body.Close()
		if resp.StatusCode == http.StatusTooManyRequests || resp.StatusCode >= 500 {
			lastErr = fmt.Errorf("gemini returned HTTP %d", resp.StatusCode)
			continue
		}
		if resp.StatusCode != http.StatusOK {
			return "", fmt.Errorf("gemini returned HTTP %d: %s", resp.StatusCode, truncate(string(data), 300))
		}
		var gr generateResponse
		if err := json.Unmarshal(data, &gr); err != nil {
			return "", err
		}
		if len(gr.Candidates) == 0 {
			return "", fmt.Errorf("gemini returned no candidates")
		}
		var sb strings.Builder
		for _, p := range gr.Candidates[0].Content.Parts {
			sb.WriteString(p.Text)
		}
		return sb.String(), nil
	}
	return "", lastErr
}

func truncate(s string, n int) string {
	if len(s) > n {
		return s[:n]
	}
	return s
}

// Schemas handed to the model (same fields as the Java DTOs).
const (
	SchemaParsedTask = `{"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","properties":{"description":{"type":"string"},"dueDate":{"type":"string"},"priority":{"type":"string"},"title":{"type":"string"}},"additionalProperties":false}`

	SchemaInsights = `{"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","properties":{"recommendations":{"type":"array","items":{"type":"string"}},"summary":{"type":"string"}},"additionalProperties":false}`

	SchemaDecompose = `{"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","properties":{"subtasks":{"type":"array","items":{"type":"object","properties":{"title":{"type":"string"}},"additionalProperties":false}}},"additionalProperties":false}`

	SchemaDailyPlan = `{"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","properties":{"coachingTip":{"type":"string"},"matrix":{"type":"object","properties":{"delegate":{"type":"array","items":{"$ref":"#/$defs/MatrixTaskItem"}},"doFirst":{"type":"array","items":{"$ref":"#/$defs/MatrixTaskItem"}},"eliminate":{"type":"array","items":{"$ref":"#/$defs/MatrixTaskItem"}},"schedule":{"type":"array","items":{"$ref":"#/$defs/MatrixTaskItem"}}},"additionalProperties":false},"summary":{"type":"string"},"timeBlocks":{"type":"array","items":{"type":"object","properties":{"focusTheme":{"type":"string"},"taskIds":{"type":"array","items":{"type":"string"}},"taskTitles":{"type":"array","items":{"type":"string"}},"timePeriod":{"type":"string"}},"additionalProperties":false}}},"additionalProperties":false,"$defs":{"MatrixTaskItem":{"type":"object","properties":{"importanceScore":{"type":"integer","format":"int32"},"rationale":{"type":"string"},"taskId":{"type":"string"},"title":{"type":"string"},"urgencyScore":{"type":"integer","format":"int32"}},"additionalProperties":false}}}`
)

// FormatZoned renders a time like java.time.ZonedDateTime.toString(), e.g. 2026-11-08T16:30:12.123+05:30[Asia/Kolkata].
func FormatZoned(t time.Time, zoneID string) string {
	s := t.Format("2006-01-02T15:04:05")
	switch n := t.Nanosecond(); {
	case n == 0:
	case n%1_000_000 == 0:
		s += fmt.Sprintf(".%03d", n/1_000_000)
	case n%1_000 == 0:
		s += fmt.Sprintf(".%06d", n/1_000)
	default:
		s += fmt.Sprintf(".%09d", n)
	}
	_, off := t.Zone()
	if off == 0 {
		s += "Z"
	} else {
		sign := "+"
		if off < 0 {
			sign, off = "-", -off
		}
		s += fmt.Sprintf("%s%02d:%02d", sign, off/3600, off%3600/60)
	}
	return s + "[" + zoneID + "]"
}
