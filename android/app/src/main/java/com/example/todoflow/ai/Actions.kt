package com.example.todoflow.ai

import java.time.LocalDate

/** A due date change: [date] == null clears the due date. */
data class DueSpec(val date: LocalDate?)

/** A project change for a task: [name] == null moves the task to the Inbox. */
data class ProjectSpec(val name: String?)

/**
 * What the model asked for, already checked for shape. Tasks are referenced by the number they had in
 * the title (never by id); projects by name. [ActionResolver] maps both to real entities.
 */
sealed interface AssistantAction {
    data class CreateTask(
        val title: String,
        val description: String? = null,
        val priority: Int? = null,
        val due: DueSpec? = null,
        val project: String? = null,
        val subtasks: List<String> = emptyList()
    ) : AssistantAction

    data class UpdateTask(
        val task: String,
        val title: String? = null,
        val description: String? = null,
        val priority: Int? = null,
        val due: DueSpec? = null,
        val project: ProjectSpec? = null,
        val completed: Boolean? = null
    ) : AssistantAction

    data class CompleteTask(val task: String, val completed: Boolean = true) : AssistantAction
    data class DeleteTask(val task: String) : AssistantAction
    data class AddSubtasks(val task: String, val titles: List<String>) : AssistantAction
    data class CreateProject(val name: String, val color: String? = null) : AssistantAction
    data class UpdateProject(val project: String, val name: String? = null, val color: String? = null) : AssistantAction
    /** [withTasks]: also delete the project's tasks (otherwise they move to the Inbox). */
    data class DeleteProject(val project: String, val withTasks: Boolean = false) : AssistantAction
}

/**
 * Read-only requests (the tools the model may use to look things up). The app answers them with real
 * data and sends the result back to the model; they never change anything and need no confirmation.
 */
sealed interface AssistantQuery : AssistantAction

data object ListProjects : AssistantQuery

data class ListTasks(
    val project: String? = null,
    /** all | inbox | today | upcoming | overdue */
    val view: String? = null,
    val completed: Boolean? = null,
    val search: String? = null
) : AssistantQuery

data class GetTask(val task: String) : AssistantQuery

data object ListLabels : AssistantQuery

data object DailyBriefing : AssistantQuery

/** The model's answer after parsing: a short message, the requested changes, and anything it got wrong. */
data class ParsedReply(
    val reply: String,
    val actions: List<AssistantAction>,
    val problems: List<String> = emptyList(),
    /** False when the model answered in plain prose without any JSON (it ignored the format). */
    val hadJson: Boolean = true
)
