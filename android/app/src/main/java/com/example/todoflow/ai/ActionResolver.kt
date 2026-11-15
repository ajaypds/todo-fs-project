package com.example.todoflow.ai

import com.example.todoflow.data.local.ProjectEntity
import com.example.todoflow.data.local.TaskEntity
import java.time.format.DateTimeFormatter
import java.util.Locale

/** An action the user can review: the action itself, the task it targets (if any) and a readable summary. */
data class Proposal(
    val action: AssistantAction,
    val task: TaskEntity?,
    val summary: String,
    val destructive: Boolean
)

data class Resolution(val proposals: List<Proposal>, val problems: List<String>)

/** Maps the model's task numbers and project names to real entities and describes each change in words. */
object ActionResolver {

    private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

    fun resolve(parsed: ParsedReply, projects: List<ProjectEntity>, allTasks: List<TaskEntity>): Resolution {
        val problems = parsed.problems.toMutableList()
        val proposals = mutableListOf<Proposal>()

        fun taskFor(name: String): TaskEntity? = findTask(allTasks, name).also { if (it.task == null) problems += it.problem!! }.task

        // Projects created earlier in the same answer count as known
        val knownProjects = projects.map { it.name.lowercase() }.toMutableSet()

        for (action in parsed.actions) {
            when (action) {
                is AssistantQuery -> Unit // answered by the app before this point
                is AssistantAction.CreateTask -> {
                    val bits = mutableListOf<String>()
                    action.due?.let { bits += it.date?.format(dateFormat)?.let { d -> "due $d" } ?: "no due date" }
                    action.priority?.let { priorityWord(it)?.let { w -> bits += "$w priority" } }
                    action.project?.let { bits += "project $it" + if (it.lowercase() !in knownProjects) " (new)" else "" }
                    if (action.subtasks.isNotEmpty()) bits += "${action.subtasks.size} subtask${if (action.subtasks.size == 1) "" else "s"}"
                    proposals += Proposal(action, null, "Create task “${action.title}”" + suffix(bits), false)
                }
                is AssistantAction.UpdateTask -> taskFor(action.task)?.let { task ->
                    val bits = mutableListOf<String>()
                    action.title?.let { bits += "rename to “$it”" }
                    if (action.description != null) bits += "new description"
                    action.due?.let { bits += it.date?.format(dateFormat)?.let { d -> "due $d" } ?: "remove due date" }
                    action.priority?.let { bits += (priorityWord(it) ?: "no") + " priority" }
                    action.project?.let { bits += it.name?.let { n -> "move to $n" } ?: "move to Inbox" }
                    action.completed?.let { bits += if (it) "mark done" else "reopen" }
                    if (bits.isEmpty()) {
                        problems += "Nothing to change for “${task.title}”"
                    } else {
                        proposals += Proposal(action, task, "Update “${task.title}”: ${bits.joinToString(", ")}", false)
                    }
                }
                is AssistantAction.CompleteTask -> taskFor(action.task)?.let { task ->
                    val text = if (action.completed) "Mark “${task.title}” as done" else "Reopen “${task.title}”"
                    proposals += Proposal(action, task, text, false)
                }
                is AssistantAction.DeleteTask -> taskFor(action.task)?.let { task ->
                    proposals += Proposal(action, task, "Delete task “${task.title}”", true)
                }
                is AssistantAction.AddSubtasks -> taskFor(action.task)?.let { task ->
                    val n = action.titles.size
                    proposals += Proposal(action, task, "Add $n subtask${if (n == 1) "" else "s"} to “${task.title}”: ${action.titles.joinToString(", ")}", false)
                }
                is AssistantAction.CreateProject -> {
                    knownProjects += action.name.lowercase()
                    proposals += Proposal(action, null, "Create project “${action.name}”", false)
                }
                is AssistantAction.UpdateProject -> {
                    if (action.project.lowercase() !in knownProjects) {
                        problems += "There is no project “${action.project}”"
                    } else {
                        val bits = listOfNotNull(action.name?.let { "rename to “$it”" }, action.color?.let { "new colour" })
                        if (bits.isEmpty()) problems += "Nothing to change for project “${action.project}”"
                        else proposals += Proposal(action, null, "Update project “${action.project}”: ${bits.joinToString(", ")}", false)
                    }
                }
                is AssistantAction.DeleteProject -> {
                    if (action.project.lowercase() !in knownProjects) problems += "There is no project “${action.project}”"
                    else {
                        val id = projects.firstOrNull { it.name.equals(action.project, ignoreCase = true) }?.id
                        val count = if (id == null) 0 else allTasks.count { it.projectId == id }
                        val plural = if (count == 1) "task" else "tasks"
                        val verb = if (count == 1) "moves" else "move"
                        val text = when {
                            action.withTasks -> "Delete project “${action.project}” and its $count $plural"
                            count > 0 -> "Delete project “${action.project}” (its $count $plural $verb to the Inbox)"
                            else -> "Delete project “${action.project}”"
                        }
                        proposals += Proposal(action, null, text, true)
                    }
                }
            }
        }
        return Resolution(proposals, problems)
    }

    /** Result of looking a task up by the title the model used. */
    data class TaskMatch(val task: TaskEntity?, val problem: String? = null)

    /**
     * Exact title first (case-insensitive); with duplicates an open task wins over finished ones; a bare number
     * ("1") means the one task whose title ends with that number; as a last
     * resort a title that contains the words. Anything ambiguous or unknown is explained, never guessed.
     */
    fun findTask(tasks: List<TaskEntity>, name: String): TaskMatch {
        val wanted = name.trim()
        val exact = tasks.filter { it.title.trim().equals(wanted, ignoreCase = true) }
        val open = exact.filter { !it.completed }
        return when {
            exact.size == 1 -> TaskMatch(exact.first())
            exact.size > 1 && open.size == 1 -> TaskMatch(open.first())
            exact.size > 1 -> TaskMatch(null, "More than one task is called “$wanted”. Say which one (for example its project or due date).")
            wanted.all { it.isDigit() } && wanted.isNotEmpty() -> {
                // The model often drops the word: the user said "Task 1" and it wrote "1". Accept that only when
                // exactly one task title ends with that number as a separate word ("Task 1", not "Task 11").
                val numbered = tasks.filter { Regex("(^|\\s)$wanted$").containsMatchIn(it.title.trim()) }
                val numberedOpen = numbered.filter { !it.completed }
                when {
                    numbered.size == 1 -> TaskMatch(numbered.first())
                    numbered.size > 1 && numberedOpen.size == 1 -> TaskMatch(numberedOpen.first())
                    numbered.isEmpty() -> TaskMatch(null, "I couldn't find a task called “$wanted”.")
                    else -> TaskMatch(null, "More than one task ends with “$wanted”: " + numbered.take(4).joinToString(", ") { "“${it.title}”" })
                }
            }
            else -> {
                val partial = if (wanted.length < 3) emptyList() else tasks.filter { it.title.contains(wanted, ignoreCase = true) }
                when {
                    partial.size == 1 -> TaskMatch(partial.first())
                    partial.isEmpty() -> TaskMatch(null, "I couldn't find a task called “$wanted”.")
                    else -> TaskMatch(null, "More than one task matches “$wanted”: " + partial.take(4).joinToString(", ") { "“${it.title}”" })
                }
            }
        }
    }

    fun priorityWord(value: Int): String? = when (value) {
        1 -> "high"
        2 -> "medium"
        3 -> "low"
        else -> null
    }

    private fun suffix(bits: List<String>) = if (bits.isEmpty()) "" else " · " + bits.joinToString(" · ")
}
