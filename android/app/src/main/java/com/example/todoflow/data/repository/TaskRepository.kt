package com.example.todoflow.data.repository

import com.example.todoflow.data.local.ProjectEntity
import com.example.todoflow.data.local.TaskEntity
import com.example.todoflow.data.local.TaskFilterType
import com.example.todoflow.data.local.TodoLocalDataSource
import com.example.todoflow.data.model.CreateTaskRequest
import com.example.todoflow.data.model.TaskDto
import com.example.todoflow.data.model.UpdateTaskRequest
import com.example.todoflow.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class TaskRepository(
    private val localDataSource: TodoLocalDataSource,
    private val apiClient: ApiClient
) {

    fun observeTasks(filter: TaskFilterType = TaskFilterType.ALL, projectId: String? = null): Flow<List<TaskEntity>> {
        return localDataSource.observeTasks(filter, projectId)
    }

    fun observeProjects(): Flow<List<ProjectEntity>> {
        return localDataSource.observeProjects()
    }

    /**
     * Stale-while-revalidate background refresh.
     * Fetches fresh tasks and projects from server and updates local SQLite cache in transactions.
     */
    suspend fun refresh(projectId: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val api = apiClient.getApi()

            // 1. Fetch & cache projects
            val projects = api.getProjects()
            val projectEntities = projects.map {
                ProjectEntity(id = it.id, name = it.name, color = it.color)
            }
            localDataSource.upsertProjects(projectEntities)

            // 2. Fetch & cache first batch of tasks
            val pageResponse = api.getTasks(projectId = projectId, page = 0, size = 30)
            val taskEntities = pageResponse.content.map { dtoToEntity(it) }
            localDataSource.upsertTasks(taskEntities)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Lazy loading for pagination on scroll.
     * Fetches the specified page and appends to local cache.
     * Returns true if there are more pages to load.
     */
    suspend fun loadTasksPage(page: Int, size: Int = 20, projectId: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val api = apiClient.getApi()
            val pageResponse = api.getTasks(projectId = projectId, page = page, size = size)
            val taskEntities = pageResponse.content.map { dtoToEntity(it) }
            localDataSource.upsertTasks(taskEntities)

            val totalPages = pageResponse.totalPages ?: 0
            val hasMore = (page + 1) < totalPages && pageResponse.content.isNotEmpty()
            Result.success(hasMore)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Optimistic toggle: Instant UI update in <1ms, syncs with backend in background.
     */
    suspend fun toggleTaskCompleted(taskId: String, completed: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val newStatus = !completed
        // 1. Optimistic local update
        localDataSource.updateTaskCompleted(taskId, newStatus)

        // 2. Background sync
        try {
            val api = apiClient.getApi()
            api.updateTask(taskId, UpdateTaskRequest(completed = newStatus))
            Result.success(Unit)
        } catch (e: Exception) {
            // Revert on error
            localDataSource.updateTaskCompleted(taskId, completed)
            Result.failure(e)
        }
    }

    /**
     * Optimistic task creation: Inserts into SQLite immediately, syncs to backend.
     */
    suspend fun createTask(
        title: String,
        description: String? = null,
        priority: Int = 0,
        dueDate: String? = null,
        projectId: String? = null,
        projectName: String? = null
    ): Result<TaskEntity> = withContext(Dispatchers.IO) {
        val tempId = "temp_" + UUID.randomUUID().toString()
        val localTask = TaskEntity(
            id = tempId,
            title = title,
            description = description,
            completed = false,
            priority = priority,
            dueDate = dueDate,
            projectId = projectId,
            projectName = projectName,
            isSynced = false
        )

        // Instant local display
        localDataSource.upsertTask(localTask)

        try {
            val api = apiClient.getApi()
            val request = CreateTaskRequest(
                title = title,
                description = description,
                priority = if (priority > 0) priority else null,
                dueDate = dueDate,
                projectId = projectId,
                projectName = projectName
            )
            val created = api.createTask(request)
            val confirmedEntity = dtoToEntity(created)

            // Replace temp task with confirmed entity
            localDataSource.deleteTask(tempId)
            localDataSource.upsertTask(confirmedEntity)

            Result.success(confirmedEntity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTask(taskId: String): Result<Unit> = withContext(Dispatchers.IO) {
        // Immediate local removal
        localDataSource.deleteTask(taskId)
        try {
            val api = apiClient.getApi()
            api.deleteTask(taskId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reorderTasks(taskIds: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        // 1. Instant local SQLite update
        localDataSource.updateTaskPositions(taskIds)
        // 2. Background sync
        try {
            val api = apiClient.getApi()
            api.reorderTasks(com.example.todoflow.data.model.ReorderTasksRequest(taskIds))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createProject(name: String, color: String): Result<ProjectEntity> = withContext(Dispatchers.IO) {
        val tempId = "temp_p_" + UUID.randomUUID().toString()
        val localProj = ProjectEntity(id = tempId, name = name, color = color)
        localDataSource.upsertProject(localProj)

        try {
            val api = apiClient.getApi()
            val created = api.createProject(com.example.todoflow.data.model.CreateProjectRequest(name, color))
            val confirmed = ProjectEntity(id = created.id, name = created.name, color = created.color)
            localDataSource.deleteProject(tempId)
            localDataSource.upsertProject(confirmed)
            Result.success(confirmed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProject(id: String, name: String, color: String): Result<ProjectEntity> = withContext(Dispatchers.IO) {
        val updated = ProjectEntity(id = id, name = name, color = color)
        localDataSource.upsertProject(updated)

        try {
            val api = apiClient.getApi()
            val res = api.updateProject(id, com.example.todoflow.data.model.CreateProjectRequest(name, color))
            val confirmed = ProjectEntity(id = res.id, name = res.name, color = res.color)
            localDataSource.upsertProject(confirmed)
            Result.success(confirmed)
        } catch (e: Exception) {
            // Keep local project or handle error
            Result.failure(e)
        }
    }

    suspend fun deleteProject(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        localDataSource.deleteProject(id)
        try {
            val api = apiClient.getApi()
            api.deleteProject(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearCache() {
        localDataSource.clearAll()
    }

    private fun dtoToEntity(dto: TaskDto): TaskEntity {
        return TaskEntity(
            id = dto.id,
            title = dto.title,
            description = dto.description,
            completed = dto.completed,
            priority = dto.priority ?: 0,
            dueDate = dto.dueDate,
            projectId = dto.projectId,
            projectName = dto.projectName,
            position = dto.position ?: 0,
            commentCount = dto.commentCount,
            updatedAt = dto.updatedAt,
            isSynced = true
        )
    }
}
