package com.example.todoflow.data.local

data class SubtaskEntity(
    val id: String,
    val taskId: String,
    val title: String,
    val completed: Boolean = false,
    val position: Int = 0
)

data class LabelEntity(
    val id: String,
    val name: String,
    val color: String? = null
)

/** A change made while offline (or not yet confirmed) that still has to be sent to the server. */
data class PendingOp(
    val id: Long,
    val type: String,
    val entityId: String,
    val parentId: String?,
    val payload: String,
    val attempts: Int = 0
)

data class TaskEntity(
    val id: String,
    val title: String,
    val description: String? = null,
    val completed: Boolean = false,
    val priority: Int = 0,
    val dueDate: String? = null,
    val projectId: String? = null,
    val projectName: String? = null,
    val position: Int = 0,
    val commentCount: Int = 0,
    val updatedAt: String? = null,
    val isSynced: Boolean = true,
    val subtasks: List<SubtaskEntity> = emptyList(),
    val labels: List<LabelEntity> = emptyList()
)

data class ProjectEntity(
    val id: String,
    val name: String,
    val color: String? = null
)

