package com.example.todoflow.ai

import java.time.LocalDate

/** A due date change: [date] == null clears the due date. */
data class DueSpec(val date: LocalDate?)

/** A project change for a task: [name] == null moves the task to the Inbox. */
data class ProjectSpec(val name: String?)

/**
 * What the model asked for, already checked for shape. Tasks are referenced by the number they had in
 * the prompt (never by id); projects by name. [ActionResolver] maps both to real entities.
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
        val ref: Int,
        val title: String? = null,
        val description: String? = null,
        val priority: Int? = null,
        val due: DueSpec? = null,
        val project: ProjectSpec? = null,
        val completed: Boolean? = null
    ) : AssistantAction

    data class CompleteTask(val ref: Int, val completed: Boolean = true) : AssistantAction
    data class DeleteTask(val ref: Int) : AssistantAction
    data class AddSubtasks(val ref: Int, val titles: List<String>) : AssistantAction
    data class CreateProject(val name: String, val color: String? = null) : AssistantAction
    data class UpdateProject(val project: String, val name: String? = null, val color: String? = null) : AssistantAction
    data class DeleteProject(val project: String) : AssistantAction
}

/** The model's answer after parsing: a short message, the requested changes, and anything it got wrong. */
data class ParsedReply(
    val reply: String,
    val actions: List<AssistantAction>,
    val problems: List<String> = emptyList()
)
