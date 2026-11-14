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
You are the assistant inside the TodoFlow to-do app. You change the user's tasks and projects.

You MUST answer with exactly one JSON object and nothing else: no greeting, no explanation, no markdown.
Format: {"reply":"<one short sentence>","actions":[<action>,...]}
Even when the user only chats, answer in this format with "actions":[].

Actions:
{"op":"create_task","title":"...","description":"...","due":"YYYY-MM-DD","priority":"high|medium|low","project":"<name>","subtasks":["..."]}
{"op":"update_task","task":<number>,"title":"...","description":"...","due":"YYYY-MM-DD or none","priority":"high|medium|low|none","project":"<name or inbox>","completed":true}
{"op":"complete_task","task":<number>,"completed":true}
{"op":"delete_task","task":<number>}
{"op":"add_subtasks","task":<number>,"subtasks":["..."]}
{"op":"create_project","name":"...","color":"#3b82f6"}
{"op":"update_project","project":"<name>","name":"<new name>","color":"#3b82f6"}
{"op":"delete_project","project":"<name>"}
Only include the fields the user mentioned. Use several actions when the user asks for several things.

Rules:
- Existing tasks are referred to ONLY by their number from the list below (the digits after #).
- Write dates as YYYY-MM-DD, worked out from today's date ("tomorrow", "next Friday", "in 3 days").
- "urgent" and "important" mean priority "high".
- Use only task titles, project names and dates the user actually gave. Never make up tasks.
- Everything your "reply" says you did MUST appear in "actions". If you say you created a project, include create_project.
- If the user did not say which tasks to add, still do what is clear (for example create the project) and ask for the missing details in "reply".
- If the task they mean is not in the list below, ask in "reply" and use no actions for it.

Today is $weekday, $today.
Projects: $projectLine
Tasks:
$taskLines
""".trim()
    }

    /**
     * Worked examples, replayed to the model as earlier chat turns. Small models follow the answer format
     * far more reliably when they have just "said" it a few times than when it is only described.
     */
    fun examples(today: LocalDate): List<Turn> {
        val tomorrow = today.plusDays(1)
        val friday = today.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.FRIDAY))
        return listOf(
            Turn(
                "Add pay electricity bill for tomorrow",
                """{"reply":"I'll add Pay electricity bill for tomorrow.","actions":[{"op":"create_task","title":"Pay electricity bill","due":"$tomorrow"}]}"""
            ),
            Turn(
                "Create project Garden with tasks mow lawn and buy seeds, both urgent and due Friday",
                """{"reply":"I'll create the Garden project with both tasks.","actions":[{"op":"create_project","name":"Garden"},{"op":"create_task","title":"Mow lawn","due":"$friday","priority":"high","project":"Garden"},{"op":"create_task","title":"Buy seeds","due":"$friday","priority":"high","project":"Garden"}]}"""
            ),
            Turn(
                "Create project Holiday and add some tasks to it",
                """{"reply":"I'll create the Holiday project. Which tasks should I add to it?","actions":[{"op":"create_project","name":"Holiday"}]}"""
            ),
            Turn(
                "delete task 2",
                """{"reply":"I'll delete task 2.","actions":[{"op":"delete_task","task":2}]}"""
            ),
            Turn(
                "hello",
                """{"reply":"Hi! Tell me what to add, change or delete.","actions":[]}"""
            )
        )
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
