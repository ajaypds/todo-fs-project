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
        val taskLines = if (shown.isEmpty()) "(no tasks yet)" else shown.joinToString("\n") { "- ${describe(it)}" }
        return """
You are the assistant inside the TodoFlow to-do app. You look things up and change the user's tasks and projects using the tools below. You can do nothing else (no labels, comments, settings or anything outside the app); if asked, say you can't.

You MUST answer with exactly one JSON object and nothing else: no greeting, no explanation, no markdown.
Format: {"reply":"<one short sentence>","actions":[<action>,...]}
Even when the user only chats or asks a question, answer in this format.

LOOK-UP tools (the app runs them at once and sends you the result; use them when the lists below are not enough):
{"op":"list_projects"}
{"op":"list_tasks","project":"<name>","view":"all|inbox|today|upcoming|overdue","completed":false,"search":"<text>"}
{"op":"get_task","task":"<title>"}
{"op":"list_labels"}
{"op":"daily_briefing"}

CHANGE tools (the user reviews them before anything happens):
{"op":"create_task","title":"...","description":"...","due":"YYYY-MM-DD","priority":"high|medium|low","project":"<name>","subtasks":["..."]}
{"op":"update_task","task":"<title>","title":"...","description":"...","due":"YYYY-MM-DD or none","priority":"high|medium|low|none","project":"<name or inbox>","completed":true}
{"op":"complete_task","task":"<title>","completed":true}
{"op":"delete_task","task":"<title>"}
{"op":"add_subtasks","task":"<title>","subtasks":["..."]}
{"op":"create_project","name":"...","color":"#3b82f6"}
{"op":"update_project","project":"<name>","name":"<new name>","color":"#3b82f6"}
{"op":"delete_project","project":"<name>","with_tasks":true}
Only include the fields the user mentioned. Use several actions when the user asks for several things.

Rules:
- For ANY question about the user's projects, tasks, labels or deadlines ("list...", "show...", "what is...", "how many...", "do I have...") you MUST use a look-up tool. Never answer such a question from memory, from the lists below or from earlier replies: the app shows the tool's result to the user.
- The lists below are only there so you can copy exact task titles and project names for changes.
- Refer to an existing task by its exact, complete title as written in the list below. If the user says "Task 1", write "task":"Task 1", never just "1". Never use list positions or ids.
- Write dates as YYYY-MM-DD, worked out from today's date ("tomorrow", "next Friday", "in 3 days").
- "urgent" and "important" mean priority "high".
- delete_project: add "with_tasks":true ONLY when the user also wants the project's tasks deleted; otherwise leave it out (the tasks then move to the Inbox).
- Use only task titles, project names and dates the user actually gave. Never make up tasks.
- Your "reply" may only say something was done if the matching action is in "actions". Changes are NOT done until the user applies them, so say "I'll ..." not "I have ...".
- If the user did not say which tasks to add, still do what is clear (for example create the project) and ask for the missing details in "reply".
- If the task they mean is not in the list below, use a look-up tool or ask in "reply".

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
        // Names are deliberately generic: small models copy example content when they are unsure
        return listOf(
            Turn(
                "Add pay electricity bill for tomorrow",
                """{"reply":"I'll add Pay electricity bill for tomorrow.","actions":[{"op":"create_task","title":"Pay electricity bill","due":"$tomorrow"}]}"""
            ),
            Turn(
                "Create project Alpha with tasks first job and second job, both urgent and due Friday",
                """{"reply":"I'll create the Alpha project with both tasks.","actions":[{"op":"create_project","name":"Alpha"},{"op":"create_task","title":"First job","due":"$friday","priority":"high","project":"Alpha"},{"op":"create_task","title":"Second job","due":"$friday","priority":"high","project":"Alpha"}]}"""
            ),
            Turn(
                "Create project Beta and add some tasks to it",
                """{"reply":"I'll create the Beta project. Which tasks should I add to it?","actions":[{"op":"create_project","name":"Beta"}]}"""
            ),
            Turn(
                "list my projects",
                """{"reply":"Let me look that up.","actions":[{"op":"list_projects"}]}"""
            ),
            Turn(
                "show the tasks in my inbox",
                """{"reply":"Let me look that up.","actions":[{"op":"list_tasks","view":"inbox"}]}"""
            ),
            Turn(
                "what is due today?",
                """{"reply":"Let me look that up.","actions":[{"op":"list_tasks","view":"today"}]}"""
            ),
            Turn(
                "Delete project Beta with all its tasks",
                """{"reply":"I'll delete the Beta project and all its tasks.","actions":[{"op":"delete_project","project":"Beta","with_tasks":true}]}"""
            ),
            Turn(
                "delete the task Sample task",
                """{"reply":"I'll delete Sample task.","actions":[{"op":"delete_task","task":"Sample task"}]}"""
            ),
            Turn(
                "Move Task 7 and Task 8 to project Beta",
                """{"reply":"I'll move Task 7 and Task 8 to Beta.","actions":[{"op":"update_task","task":"Task 7","project":"Beta"},{"op":"update_task","task":"Task 8","project":"Beta"}]}"""
            ),
            Turn(
                "hello",
                """{"reply":"Hi! Tell me what to add, change or delete, or ask about your tasks and projects.","actions":[]}"""
            )
        )
    }


    internal fun describe(t: TaskEntity): String {
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
