package com.example.todoflow.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LabelDto(
    val id: String,
    val name: String,
    val color: String? = null
)

@Serializable
data class SubtaskDto(
    val id: String,
    val title: String,
    val completed: Boolean = false,
    val position: Int? = null
)

@Serializable
data class TaskDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val completed: Boolean = false,
    val priority: Int? = null,
    val dueDate: String? = null,
    val projectId: String? = null,
    val projectName: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val position: Int? = null,
    val labels: List<LabelDto> = emptyList(),
    val subtasks: List<SubtaskDto> = emptyList(),
    val commentCount: Int = 0
)

@Serializable
data class PageResponse<T>(
    val content: List<T> = emptyList(),
    val totalElements: Long? = 0,
    val totalPages: Int? = 0,
    val size: Int? = 0,
    val number: Int? = 0
)

@Serializable
data class CreateTaskRequest(
    val title: String,
    val description: String? = null,
    val priority: Int? = null,
    val dueDate: String? = null,
    val projectId: String? = null,
    val projectName: String? = null
)

@Serializable
data class UpdateTaskRequest(
    val title: String? = null,
    val description: String? = null,
    val completed: Boolean? = null,
    val priority: Int? = null,
    val dueDate: String? = null,
    val projectId: String? = null,
    val clearProject: Boolean? = null
)

@Serializable
data class ReorderTasksRequest(
    val taskIds: List<String>
)

@Serializable
data class CreateSubtaskRequest(
    val title: String
)

@Serializable
data class UpdateSubtaskRequest(
    val title: String? = null,
    val completed: Boolean? = null,
    val position: Int? = null
)

