package com.example.todoflow.ai

import com.example.todoflow.data.local.ProjectEntity
import com.example.todoflow.data.local.TaskEntity
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** One exchange of the chat, replayed to the model so follow-ups ("make it high priority") work. */
data class Turn(val user: String, val assistant: String)

/** The system prompt: what the model may do, the JSON it must answer with, and the user's current data. */
object PromptBuilder {

    const val MAX_TASKS_IN_PROMPT = 60
    private const val MAX_TITLE = 70

    /**
     * The tasks listed in the prompt, in order: the model refers to them by position (1-based), and
     * [ActionResolver] uses the same list to map the numbers back. Open tasks come first.
     */
    fun contextTasks(tasks: List<TaskEntity>): List<TaskEntity> =
        tasks.sortedWith(compareBy<TaskEntity> { it.completed }.thenBy { it.dueDate ?: "9999" })
            .take(MAX_TASKS_IN_PROMPT)

    fun system(today: LocalDate, tasks: List<TaskEntity>, projects: List<ProjectEntity>): String {
        val weekday = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val projectLine = if (projects.isEmpty()) "(none)" else projects.joinToString(", ") { it.name }
        val shown = contextTasks(tasks)
        val taskLines = if (shown.isEmpty()) "(no tasks yet)" else shown.mapIndexed { i, t -> "#${i + 1} ${describe(t)}" }.joinToString("\n")
        return """
You are the assistant inside the TodoFlow to-do app. Turn the user's message into changes to their tasks and projects.

Answer with ONE JSON object and nothing else (no markdown, no explanation):
{"reply": "<one short friendly sentence>", "actions": [ ... ]}
Use "actions": [] when the user is only chatting or asking something.

Allowed actions:
{"op":"create_task","title":"...","description":"...","due":"YYYY-MM-DD","priority":"high|medium|low","project":"<project name>","subtasks":["..."]}
{"op":"update_task","task":<number>,"title":"...","description":"...","due":"YYYY-MM-DD or none","priority":"high|medium|low|none","project":"<project name or inbox>","completed":true}
{"op":"complete_task","task":<number>,"completed":true}
{"op":"delete_task","task":<number>}
{"op":"add_subtasks","task":<number>,"subtasks":["..."]}
{"op":"create_project","name":"...","color":"#3b82f6"}
{"op":"update_project","project":"<project name>","name":"<new name>","color":"#3b82f6"}
{"op":"delete_project","project":"<project name>"}
Leave out every field the user did not mention.

Rules:
- Refer to existing tasks ONLY by their number in the list below (the digits after #). Never invent a number.
- Work out dates yourself from today's date and write them as YYYY-MM-DD ("tomorrow", "next Friday", "in 3 days").
- "urgent" or "important" means priority "high".
- If the request is unclear or the task is not in the list, ask in "reply" and use no actions.
- You can do several actions in one answer.

Today is $weekday, $today.
Projects: $projectLine
Tasks:
$taskLines
""".trim()
    }

    private fun describe(t: TaskEntity): String {
        val parts = mutableListOf(t.title.replace('\n', ' ').take(MAX_TITLE))
        t.dueDate?.substringBefore("T")?.takeIf { it.isNotBlank() }?.let { parts += "due $it" }
        when (t.priority) {
            1 -> parts += "priority high"
            2 -> parts += "priority medium"
            3 -> parts += "priority low"
        }
        t.projectName?.takeIf { it.isNotBlank() }?.let { parts += "project $it" }
        if (t.completed) parts += "done"
        return parts.joinToString(" | ")
    }
}
