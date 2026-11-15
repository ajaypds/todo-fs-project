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

    fun resolve(
        parsed: ParsedReply,
        promptTasks: List<TaskEntity>,
        projects: List<ProjectEntity>,
        /** Every task (the prompt only lists some), used to count a project's tasks. */
        allTasks: List<TaskEntity> = promptTasks
    ): Resolution {
        val problems = parsed.problems.toMutableList()
        val proposals = mutableListOf<Proposal>()

        fun taskFor(ref: Int): TaskEntity? =
            promptTasks.getOrNull(ref - 1).also { if (it == null) problems += "There is no task with code ${TaskCodes.encode(ref)}" }

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
                is AssistantAction.UpdateTask -> taskFor(action.ref)?.let { task ->
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
                is AssistantAction.CompleteTask -> taskFor(action.ref)?.let { task ->
                    val text = if (action.completed) "Mark “${task.title}” as done" else "Reopen “${task.title}”"
                    proposals += Proposal(action, task, text, false)
                }
                is AssistantAction.DeleteTask -> taskFor(action.ref)?.let { task ->
                    proposals += Proposal(action, task, "Delete task “${task.title}”", true)
                }
                is AssistantAction.AddSubtasks -> taskFor(action.ref)?.let { task ->
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

    fun priorityWord(value: Int): String? = when (value) {
        1 -> "high"
        2 -> "medium"
        3 -> "low"
        else -> null
    }

    private fun suffix(bits: List<String>) = if (bits.isEmpty()) "" else " · " + bits.joinToString(" · ")
}
