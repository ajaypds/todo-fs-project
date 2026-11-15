package com.example.todoflow.ai

import com.example.todoflow.data.local.TaskEntity
import com.example.todoflow.data.local.TaskFilterType
import com.example.todoflow.data.repository.TaskRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Answers the model's read-only look-ups ("list_projects", "list_tasks", ...) from the app's own data: the
 * same data the screens show, so it works offline and is never out of date. The text goes back to the model.
 */
class QueryRunner(
    private val repository: TaskRepository,
    private val today: () -> LocalDate = { LocalDate.now() }
) {

    suspend fun run(queries: List<AssistantQuery>): String {
        val sb = StringBuilder()
        for (q in queries) {
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append(
                when (q) {
                    ListProjects -> listProjects()
                    ListLabels -> listLabels()
                    DailyBriefing -> briefing()
                    is ListTasks -> listTasks(q)
                    is GetTask -> getTask(q)
                }
            )
        }
        return sb.toString()
    }

    private suspend fun listProjects(): String {
        val projects = repository.observeProjects().first()
        if (projects.isEmpty()) return "list_projects: there are no projects."
        val tasks = repository.observeTasks(TaskFilterType.ALL).first()
        return "list_projects: " + projects.joinToString("; ") { p ->
            val n = tasks.count { it.projectId == p.id }
            "${p.name} ($n task${if (n == 1) "" else "s"})"
        }
    }

    private suspend fun listLabels(): String {
        val labels = repository.observeLabels().first()
        return if (labels.isEmpty()) "list_labels: there are no labels." else "list_labels: " + labels.joinToString(", ") { it.name }
    }

    private suspend fun listTasks(q: ListTasks): String {
        val projects = repository.observeProjects().first()
        var tasks = repository.observeTasks(TaskFilterType.ALL).first()
        q.project?.let { name ->
            val project = projects.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: return "list_tasks: there is no project named \"$name\"."
            tasks = tasks.filter { it.projectId == project.id }
        }
        val now = today()
        tasks = when (q.view) {
            "inbox" -> tasks.filter { it.projectId.isNullOrBlank() }
            "today" -> tasks.filter { dueOf(it) == now }
            "upcoming" -> tasks.filter { dueOf(it)?.isAfter(now) == true }
            "overdue" -> tasks.filter { !it.completed && dueOf(it)?.isBefore(now) == true }
            else -> tasks
        }
        q.completed?.let { c -> tasks = tasks.filter { it.completed == c } }
        q.search?.lowercase()?.let { s -> tasks = tasks.filter { it.title.lowercase().contains(s) || it.description.orEmpty().lowercase().contains(s) } }
        if (tasks.isEmpty()) return "list_tasks: no tasks match."
        val shown = tasks.take(MAX_LISTED)
        val lines = shown.joinToString("\n") { "• ${PromptBuilder.describe(it)}" }
        val more = if (tasks.size > shown.size) "\n(${tasks.size - shown.size} more not shown)" else ""
        return "list_tasks (${tasks.size}):\n$lines$more"
    }

    private suspend fun getTask(q: GetTask): String {
        val match = ActionResolver.findTask(repository.observeTasks(TaskFilterType.ALL).first(), q.task)
        val task = match.task ?: return "get_task: " + (match.problem ?: "no such task.")
        val sb = StringBuilder("get_task: ${PromptBuilder.describe(task)}")
        task.description?.takeIf { it.isNotBlank() }?.let { sb.append("\nDescription: ${it.take(300)}") }
        if (task.subtasks.isNotEmpty()) {
            sb.append("\nSubtasks: " + task.subtasks.joinToString("; ") { (if (it.completed) "[x] " else "[ ] ") + it.title })
        }
        if (task.labels.isNotEmpty()) sb.append("\nLabels: " + task.labels.joinToString(", ") { it.name })
        return sb.toString()
    }

    private suspend fun briefing(): String {
        val now = today()
        val open = repository.observeTasks(TaskFilterType.ALL).first().filter { !it.completed }
        fun block(title: String, list: List<TaskEntity>) =
            "$title (${list.size})" + if (list.isEmpty()) "" else ":\n" + list.take(10).joinToString("\n") { "• ${PromptBuilder.describe(it)}" }
        val overdue = open.filter { dueOf(it)?.isBefore(now) == true }
        val dueToday = open.filter { dueOf(it) == now }
        val week = open.filter { d -> dueOf(d)?.let { it.isAfter(now) && !it.isAfter(now.plusDays(7)) } == true }
        return "daily_briefing: ${open.size} open tasks.\n" + block("Overdue", overdue) + "\n" + block("Due today", dueToday) + "\n" + block("Due in the next 7 days", week)
    }

    private fun dueOf(t: TaskEntity): LocalDate? = try {
        t.dueDate?.substringBefore("T")?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }
    } catch (_: Exception) {
        null
    }

    companion object {
        const val MAX_LISTED = 40

        /** The look-up result as the user sees it: no codes or tool names, one bullet per item. */
        fun forDisplay(text: String): String = text.lines().flatMap { raw ->
            var line = raw
            when {
                line.startsWith("list_projects: ") && !line.contains("there are no projects") ->
                    return@flatMap listOf("Projects:") + line.removePrefix("list_projects: ").split("; ").map { "• $it" }
                line.startsWith("list_projects: ") -> line = "You have no projects."
                line.startsWith("list_labels: ") && !line.contains("there are no labels") -> line = "Labels: " + line.removePrefix("list_labels: ")
                line.startsWith("list_labels: ") -> line = "You have no labels."
                line.startsWith("list_tasks (") -> line = "Tasks (" + line.removePrefix("list_tasks (")
                line.startsWith("list_tasks: ") -> line = line.removePrefix("list_tasks: ").replaceFirstChar { it.uppercase() }
                line.startsWith("get_task: ") -> line = line.removePrefix("get_task: ").let { if (it.startsWith("I couldn't") || it.startsWith("More than")) it else "• $it" }
                line.startsWith("daily_briefing: ") -> line = "Daily briefing: " + line.removePrefix("daily_briefing: ")
            }
            listOf(line.replace(" | ", " · "))
        }.joinToString("\n")
    }
}

/** Spots questions about the user's own data, which must be answered from the app's data and never from memory. */
object DataQuestions {
    private val asks = Regex("""\b(list|show|display|what|which|how many|how much|do i have|have i got|are there|is there|any|count|tell me|view|see|find|search|give me)\b""", RegexOption.IGNORE_CASE)
    private val subjects = Regex("""\b(tasks?|projects?|labels?|inbox|due|overdue|today|tomorrow|upcoming|deadlines?|briefing|completed|done|subtasks?)\b""", RegexOption.IGNORE_CASE)
    private val changes = Regex("""\b(create|add|new|delete|remove|move|rename|make|set|mark|change|update|edit|complete|finish|reopen|put|assign|schedule)\b""", RegexOption.IGNORE_CASE)

    fun looksLikeDataQuestion(text: String): Boolean = asks.containsMatchIn(text) && subjects.containsMatchIn(text) && !changes.containsMatchIn(text)
}
