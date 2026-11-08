// Code generated from GeminiTaskService.java text blocks; DO NOT EDIT by hand.

package ai

// PromptParseTask is the parseTask prompt (printf verbs match the Java .formatted(...) arguments).
const PromptParseTask = `Extract task details from the following input.

Current date and time (%s): %s

Interpret relative dates such as:
- today
- tomorrow
- next week
- this Friday
- next Monday

relative to the current date and timezone above.

Return ONLY valid JSON.

Required fields:
- title
- description
- priority
- dueDate

Rules:
- dueDate must be standard ISO-8601 UTC Instant format ending with Z (e.g. 2026-10-09T00:00:00Z) or null if no date specified
- Use %s timezone when interpreting relative dates
- Priority must be one of: LOW, MEDIUM, HIGH
- Do not invent information not present in the input
- If a date is not specified, return null for dueDate

Priority must be returned as a number:
1 = LOW
2 = MEDIUM
3 = HIGH
4 = URGENT

Priority Rules:
- URGENT (4): immediate action required, critical issues, deadlines within hours
- HIGH (3): important tasks with near deadlines
- MEDIUM (2): normal work tasks
- LOW (1): optional or non-urgent tasks

The priority field MUST contain only the integer value 1, 2, 3, or 4.
Do NOT return LOW, MEDIUM, HIGH, or URGENT as text.

Description Rules:
- description must never be empty
- If the user does not provide a separate description,
  generate a short description from the title
- description should be 1-2 sentences maximum

Input: %s
`

// PromptProductivity is the productivity prompt (printf verbs match the Java .formatted(...) arguments).
const PromptProductivity = `You are a productivity coach. Analyze the provided tasks.
Return JSON:
{
  "summary": "...",
  "recommendations": [
    "...",
    "...",
    "..."
  ]
}

Focus on:
- overdue tasks
- workload
- prioritization
- planning

Tasks: %s
`

// PromptDecompose is the decompose prompt (printf verbs match the Java .formatted(...) arguments).
const PromptDecompose = `You are an expert productivity assistant and project manager.
Break down the given task into 3 to 5 concrete, actionable, and sequential subtasks (checklist steps).

Rules:
- Return 3 to 5 subtasks.
- Each subtask title must be clear, concise, and start with an action verb (e.g., 'Research...', 'Draft...', 'Design...', 'Review...', 'Set up...').
- The subtasks should follow a logical sequence from start to finish.
- Return ONLY valid JSON adhering to the specified schema.

JSON schema:
{
  "subtasks": [
    { "title": "..." },
    { "title": "..." },
    { "title": "..." }
  ]
}

Task Details:
%s
`

// PromptDailyPlan is the dailyPlan prompt (printf verbs match the Java .formatted(...) arguments).
const PromptDailyPlan = `You are an elite productivity strategist and executive coach specializing in the Eisenhower Matrix method and daily time blocking.

Current date & time (%s): %s

Analyze the user's tasks and create an actionable, structured Daily Plan.

1. Eisenhower Matrix Classification:
Evaluate each task's URGENCY (time sensitivity, imminent due dates, critical blockers) and IMPORTANCE (strategic value, high priority rating, long-term impact):
- Q1: Do First (Urgent & Important): High urgency and high importance (critical deadlines today/tomorrow, high priority bugs, critical deliverables).
- Q2: Schedule / Deep Work (Not Urgent, but Important): Strategic goals, design docs, learning, roadmap planning, high priority tasks without immediate panic deadlines.
- Q3: Delegate / Quick Batch (Urgent, but Not Important): Administrative tasks, quick low-impact requests, chores with near deadlines but low priority.
- Q4: Eliminate / Backlog (Not Urgent & Not Important): Low-priority tasks, nice-to-haves, busywork with distant or no deadlines.

For each classified task, provide:
- taskId: EXACT id from the task input
- title: task title
- rationale: 1 brief sentence explaining why it fits this quadrant
- urgencyScore: integer 1-10
- importanceScore: integer 1-10

2. Daily Time Blocks:
Synthesize 2-3 structured focus time blocks for today (e.g., Morning Focus, Afternoon Execution, End of Day Wrap-Up).
Assign relevant taskIds and taskTitles to the appropriate time blocks based on cognitive load and urgency.

3. Executive Summary & Coaching Advice:
- summary: 2-3 sentences summarizing the day's primary objective and realistic workload.
- coachingTip: 1 actionable tactical advice for today (e.g., "Tackle Q1 items before noon while cognitive energy is high, then protect 90 minutes for Q2 deep work.").

Return ONLY valid JSON matching this schema:
{
  "summary": "...",
  "coachingTip": "...",
  "timeBlocks": [
    {
      "timePeriod": "Morning Focus (9:00 AM - 12:00 PM)",
      "focusTheme": "High Priority & Urgent Execution",
      "taskIds": ["..."],
      "taskTitles": ["..."]
    }
  ],
  "matrix": {
    "doFirst": [
      {
        "taskId": "...",
        "title": "...",
        "rationale": "...",
        "urgencyScore": 9,
        "importanceScore": 9
      }
    ],
    "schedule": [],
    "delegate": [],
    "eliminate": []
  }
}

Input Tasks:
%s
`
