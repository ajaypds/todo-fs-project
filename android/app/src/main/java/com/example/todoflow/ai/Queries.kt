package com.example.todoflow.ai

import com.example.todoflow.data.local.TaskEntity
import com.example.todoflow.data.local.TaskFilterType
import com.example.todoflow.data.repository.TaskRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * The tasks the model can refer to, each by a letter code. It starts with the tasks listed in the prompt
 * and grows when a look-up returns tasks that weren't listed there, so every code the model sees can be
 * mapped back to a real task.
 */
class TaskIndex(initial: List<TaskEntity>) {
    private val list = initial.toMutableList()
    val tasks: List<TaskEntity> get() = list

    fun codeFor(task: TaskEntity): String {
        var i = list.indexOfFirst { it.id == task.id }
        if (i < 0) {
            list += task
            i = list.size - 1
        }
        return TaskCodes.encode(i + 1)
    }
}

/**
 * Answers the model's read-only look-ups ("list_projects", "list_tasks", ...) from the app's own data: the
 * same data the screens show, so it works offline and is never out of date. The text goes back to the model.
 */
class QueryRunner(
    private val repository: TaskRepository,
    private val today: () -> LocalDate = { LocalDate.now() }
) {

    suspend fun run(queries: List<AssistantQuery>, index: TaskIndex): String {
        val sb = StringBuilder()
        for (q in queries) {
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append(
                when (q) {
                    ListProjects -> listProjects()
                    ListLabels -> listLabels()
                    DailyBriefing -> briefing(index)
                    is ListTasks -> listTasks(q, index)
                    is GetTask -> getTask(q, index)
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

    private suspend fun listTasks(q: ListTasks, index: TaskIndex): String {
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
        val lines = shown.joinToString("\n") { "[${index.codeFor(it)}] ${PromptBuilder.describe(it)}" }
        val more = if (tasks.size > shown.size) "\n(${tasks.size - shown.size} more not shown)" else ""
        return "list_tasks (${tasks.size}):\n$lines$more"
    }

    private suspend fun getTask(q: GetTask, index: TaskIndex): String {
        val task = index.tasks.getOrNull(q.ref - 1) ?: return "get_task: there is no task with code ${TaskCodes.encode(q.ref)}."
        val sb = StringBuilder("get_task [${TaskCodes.encode(q.ref)}]: ${PromptBuilder.describe(task)}")
        task.description?.takeIf { it.isNotBlank() }?.let { sb.append("\nDescription: ${it.take(300)}") }
        if (task.subtasks.isNotEmpty()) {
            sb.append("\nSubtasks: " + task.subtasks.joinToString("; ") { (if (it.completed) "[x] " else "[ ] ") + it.title })
        }
        if (task.labels.isNotEmpty()) sb.append("\nLabels: " + task.labels.joinToString(", ") { it.name })
        return sb.toString()
    }

    private suspend fun briefing(index: TaskIndex): String {
        val now = today()
        val open = repository.observeTasks(TaskFilterType.ALL).first().filter { !it.completed }
        fun block(title: String, list: List<TaskEntity>) =
            "$title (${list.size})" + if (list.isEmpty()) "" else ":\n" + list.take(10).joinToString("\n") { "[${index.codeFor(it)}] ${PromptBuilder.describe(it)}" }
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
    }
}
