package com.example.todoflow.data.repository

import com.example.todoflow.data.local.LabelEntity
import com.example.todoflow.data.local.PendingOp
import com.example.todoflow.data.local.ProjectEntity
import com.example.todoflow.data.local.SubtaskEntity
import com.example.todoflow.data.local.TaskEntity
import com.example.todoflow.data.local.TaskFilterType
import com.example.todoflow.data.local.TodoLocalDataSource
import com.example.todoflow.data.model.CommentDto
import com.example.todoflow.data.model.CommentRequest
import com.example.todoflow.data.model.CreateLabelRequest
import com.example.todoflow.data.model.CreateProjectRequest
import com.example.todoflow.data.model.CreateSubtaskRequest
import com.example.todoflow.data.model.CreateTaskRequest
import com.example.todoflow.data.model.ReorderTasksRequest
import com.example.todoflow.data.model.TaskDto
import com.example.todoflow.data.model.UpdateSubtaskRequest
import com.example.todoflow.data.model.UpdateTaskRequest
import com.example.todoflow.data.remote.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.util.UUID

private const val MAX_REFRESH_PAGES = 50

/** Types of changes that can wait in the offline queue. */
object OpType {
    const val CREATE_TASK = "CREATE_TASK"
    const val UPDATE_TASK = "UPDATE_TASK"
    const val DELETE_TASK = "DELETE_TASK"
    const val REORDER_TASKS = "REORDER_TASKS"
    const val CREATE_PROJECT = "CREATE_PROJECT"
    const val UPDATE_PROJECT = "UPDATE_PROJECT"
    const val DELETE_PROJECT = "DELETE_PROJECT"
    const val CREATE_SUBTASK = "CREATE_SUBTASK"
    const val UPDATE_SUBTASK = "UPDATE_SUBTASK"
    const val DELETE_SUBTASK = "DELETE_SUBTASK"
}

/**
 * @property remaining operations still waiting (e.g. because the device is offline)
 * @property dropped operations the server permanently rejected and that were discarded
 */
data class FlushResult(val remaining: Int, val dropped: Int)

/**
 * Local-first repository. Every change is applied to the local database immediately and written
 * to a persistent queue; the queue is uploaded in order whenever the network allows (right away,
 * on refresh, and from a WorkManager job), so nothing is lost while offline.
 */
class TaskRepository(
    private val localDataSource: TodoLocalDataSource,
    private val apiClient: ApiClient,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    /** Called whenever work was queued so a background job can be scheduled. */
    private val onOpsQueued: () -> Unit = {}
) {

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val flushMutex = Mutex()

    private val _syncEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Human readable notices, e.g. when the server rejected a queued change. */
    val syncEvents: SharedFlow<String> = _syncEvents.asSharedFlow()

    fun observeTasks(
        filter: TaskFilterType = TaskFilterType.ALL,
        projectId: String? = null,
        labelId: String? = null
    ): Flow<List<TaskEntity>> {
        return localDataSource.observeTasks(filter, projectId, labelId)
    }

    fun observeProjects(): Flow<List<ProjectEntity>> = localDataSource.observeProjects()

    fun observeLabels(): Flow<List<LabelEntity>> = localDataSource.observeLabels()

    /** Number of local changes that have not reached the server yet. */
    fun observePendingOpCount(): Flow<Int> = localDataSource.observePendingOpCount()

    // ------------------------------------------------------------------ sync

    /**
     * Uploads queued changes first, then pulls projects, labels and tasks from the server into the
     * local cache. Local changes that are still pending are never overwritten.
     */
    suspend fun refresh(projectId: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            flushPendingOps()
            val api = apiClient.getApi()

            val projectEntities = api.getProjects().map {
                ProjectEntity(id = it.id, name = it.name, color = it.color)
            }
            localDataSource.upsertProjects(projectEntities)
            localDataSource.pruneProjectsNotIn(projectEntities.map { it.id }.toSet())

            localDataSource.replaceLabels(api.getLabels().map { LabelEntity(it.id, it.name, it.color) })

            // Unfiltered refresh pulls every page so tasks deleted elsewhere can be pruned.
            val pendingDeletes = localDataSource.pendingDeletedTaskIds()
            val pageSize = 100
            val serverIds = mutableSetOf<String>()
            var page = 0
            var complete = false
            while (page < MAX_REFRESH_PAGES) {
                val pageResponse = api.getTasks(projectId = projectId, page = page, size = pageSize)
                val dtos = pageResponse.content.filter { it.id !in pendingDeletes }
                localDataSource.upsertTasks(dtos.map { dtoToEntity(it) })
                pageResponse.content.forEach { serverIds.add(it.id) }
                val totalPages = pageResponse.totalPages ?: 0
                page++
                if (page >= totalPages || pageResponse.content.isEmpty()) {
                    complete = true
                    break
                }
            }
            if (complete && projectId == null) {
                localDataSource.pruneSyncedTasksNotIn(serverIds)
            }

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
            val pendingDeletes = localDataSource.pendingDeletedTaskIds()
            localDataSource.upsertTasks(
                pageResponse.content.filter { it.id !in pendingDeletes }.map { dtoToEntity(it) }
            )

            val totalPages = pageResponse.totalPages ?: 0
            val hasMore = (page + 1) < totalPages && pageResponse.content.isNotEmpty()
            Result.success(hasMore)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ----------------------------------------------------------- queue engine

    private suspend fun enqueue(type: String, entityId: String, parentId: String? = null, payload: String = "{}") {
        localDataSource.enqueueOp(type, entityId, parentId, payload)
        onOpsQueued()
        scope.launch { flushPendingOps() }
    }

    /**
     * Sends queued changes to the server in the order they were made. Stops at the first problem
     * that is worth retrying (offline, server error, auth); discards changes the server rejects.
     */
    suspend fun flushPendingOps(): FlushResult = flushMutex.withLock {
        var dropped = 0
        while (true) {
            val op = localDataSource.getPendingOps().firstOrNull() ?: break
            try {
                execute(op)
            } catch (e: Exception) {
                if (isRetryable(e)) {
                    localDataSource.incrementOpAttempts(op.id)
                    break
                }
                android.util.Log.w("TaskRepository", "Discarding rejected ${op.type} for ${op.entityId}", e)
                discard(op)
                dropped++
                continue
            }
            localDataSource.deleteOp(op.id)
            settle(op)
        }
        if (dropped > 0) {
            _syncEvents.tryEmit("$dropped change(s) were rejected by the server and discarded")
        }
        FlushResult(remaining = localDataSource.getPendingOps().size, dropped = dropped)
    }

    private fun isRetryable(e: Throwable): Boolean = when (e) {
        is IOException -> true
        is HttpException -> e.code() >= 500 || e.code() in listOf(401, 403, 408, 429)
        else -> false
    }

    private fun <T> Response<T>.requireSuccess(allowNotFound: Boolean = false) {
        if (!isSuccessful && !(allowNotFound && code() == 404)) throw HttpException(this)
    }

    private suspend fun execute(op: PendingOp) {
        val api = apiClient.getApi()
        when (op.type) {
            OpType.CREATE_TASK -> {
                val created = api.createTask(json.decodeFromString(CreateTaskRequest.serializer(), op.payload))
                localDataSource.remapPendingIds(op.entityId, created.id)
                localDataSource.deleteTask(op.entityId)
                val stillPending = localDataSource.hasPendingOps(created.id)
                localDataSource.upsertTask(dtoToEntity(created).copy(isSynced = !stillPending))
            }
            OpType.UPDATE_TASK -> {
                api.updateTask(
                    op.entityId,
                    json.decodeFromString(UpdateTaskRequest.serializer(), op.payload)
                ).requireSuccess()
            }
            OpType.DELETE_TASK -> api.deleteTask(op.entityId).requireSuccess(allowNotFound = true)
            OpType.REORDER_TASKS -> {
                api.reorderTasks(json.decodeFromString(ReorderTasksRequest.serializer(), op.payload)).requireSuccess()
            }
            OpType.CREATE_PROJECT -> {
                val created = api.createProject(json.decodeFromString(CreateProjectRequest.serializer(), op.payload))
                localDataSource.remapPendingIds(op.entityId, created.id)
                localDataSource.deleteProject(op.entityId)
                localDataSource.upsertProject(ProjectEntity(created.id, created.name, created.color))
            }
            OpType.UPDATE_PROJECT -> {
                val updated = api.updateProject(
                    op.entityId,
                    json.decodeFromString(CreateProjectRequest.serializer(), op.payload)
                )
                localDataSource.upsertProject(ProjectEntity(updated.id, updated.name, updated.color))
            }
            OpType.DELETE_PROJECT -> api.deleteProject(op.entityId).requireSuccess(allowNotFound = true)
            OpType.CREATE_SUBTASK -> {
                val parent = requireNotNull(op.parentId)
                val created = api.createSubtask(parent, json.decodeFromString(CreateSubtaskRequest.serializer(), op.payload))
                localDataSource.remapPendingIds(op.entityId, created.id)
                localDataSource.deleteSubtask(op.entityId)
                localDataSource.upsertSubtask(
                    SubtaskEntity(created.id, parent, created.title, created.completed, created.position ?: 0)
                )
            }
            OpType.UPDATE_SUBTASK -> {
                api.updateSubtask(
                    requireNotNull(op.parentId),
                    op.entityId,
                    json.decodeFromString(UpdateSubtaskRequest.serializer(), op.payload)
                ).requireSuccess()
            }
            OpType.DELETE_SUBTASK -> {
                api.deleteSubtask(requireNotNull(op.parentId), op.entityId).requireSuccess(allowNotFound = true)
            }
            else -> Unit
        }
    }

    /** Marks a task as in sync once nothing is waiting for it any more. */
    private suspend fun settle(op: PendingOp) {
        val taskId = when (op.type) {
            OpType.UPDATE_TASK -> op.entityId
            OpType.CREATE_SUBTASK, OpType.UPDATE_SUBTASK, OpType.DELETE_SUBTASK -> op.parentId
            else -> null
        } ?: return
        if (!localDataSource.hasPendingOps(taskId)) {
            localDataSource.markTaskSynced(taskId)
        }
    }

    /** The server permanently refused this change: remove it and undo its local effect. */
    private suspend fun discard(op: PendingOp) {
        localDataSource.deleteOp(op.id)
        when (op.type) {
            OpType.CREATE_TASK -> {
                localDataSource.removeOpsFor(op.entityId)
                localDataSource.deleteTask(op.entityId)
            }
            OpType.CREATE_PROJECT -> {
                localDataSource.removeOpsFor(op.entityId)
                localDataSource.deleteProject(op.entityId)
            }
            OpType.CREATE_SUBTASK -> {
                localDataSource.removeOpsFor(op.entityId)
                localDataSource.deleteSubtask(op.entityId)
            }
            OpType.UPDATE_TASK -> localDataSource.markTaskSynced(op.entityId) // next refresh restores the server's version
            else -> Unit
        }
    }

    private fun isTemp(id: String) = id.startsWith("temp_")

    // ----------------------------------------------------------------- tasks

    /** Optimistic toggle: the UI updates instantly and the change is queued for upload. */
    suspend fun toggleTaskCompleted(taskId: String, completed: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val newStatus = !completed
        localDataSource.updateTaskCompleted(taskId, newStatus)
        enqueue(
            OpType.UPDATE_TASK, taskId,
            payload = json.encodeToString(UpdateTaskRequest.serializer(), UpdateTaskRequest(completed = newStatus))
        )
        Result.success(Unit)
    }

    suspend fun createTask(
        title: String,
        description: String? = null,
        priority: Int = 0,
        dueDate: String? = null,
        projectId: String? = null,
        projectName: String? = null,
        labels: List<LabelEntity> = emptyList()
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
            isSynced = false,
            labels = labels
        )
        localDataSource.upsertTask(localTask)

        val request = CreateTaskRequest(
            title = title,
            description = description,
            priority = if (priority > 0) priority else null,
            dueDate = dueDate,
            projectId = projectId,
            projectName = projectName,
            labelIds = labels.map { it.id }.takeIf { it.isNotEmpty() }
        )
        enqueue(OpType.CREATE_TASK, tempId, payload = json.encodeToString(CreateTaskRequest.serializer(), request))
        Result.success(localTask)
    }

    /** Edits a task's details. Fields cleared by the user are sent as explicit "clear" flags. */
    suspend fun updateTask(
        original: TaskEntity,
        title: String,
        description: String?,
        priority: Int,
        dueDate: String?,
        projectId: String?,
        projectName: String?,
        labels: List<LabelEntity>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        localDataSource.updateTaskFields(
            taskId = original.id,
            title = title,
            description = description,
            priority = priority,
            dueDate = dueDate,
            projectId = projectId,
            projectName = projectName,
            labels = labels
        )
        val request = UpdateTaskRequest(
            title = title,
            description = description ?: if (original.description != null) "" else null,
            priority = priority,
            dueDate = dueDate,
            clearDueDate = if (dueDate == null && original.dueDate != null) true else null,
            projectId = projectId,
            clearProject = if (projectId == null && original.projectId != null) true else null,
            labelIds = labels.map { it.id }
        )
        enqueue(
            OpType.UPDATE_TASK, original.id,
            payload = json.encodeToString(UpdateTaskRequest.serializer(), request)
        )
        Result.success(Unit)
    }

    suspend fun deleteTask(taskId: String): Result<Unit> = withContext(Dispatchers.IO) {
        localDataSource.removeOpsFor(taskId) // anything still waiting for this task is moot
        localDataSource.deleteTask(taskId)
        if (!isTemp(taskId)) {
            enqueue(OpType.DELETE_TASK, taskId)
        }
        Result.success(Unit)
    }

    suspend fun reorderTasks(taskIds: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        localDataSource.updateTaskPositions(taskIds)
        enqueue(
            OpType.REORDER_TASKS, "reorder",
            payload = json.encodeToString(ReorderTasksRequest.serializer(), ReorderTasksRequest(taskIds))
        )
        Result.success(Unit)
    }

    // -------------------------------------------------------------- projects

    suspend fun createProject(name: String, color: String): Result<ProjectEntity> = withContext(Dispatchers.IO) {
        val tempId = "temp_p_" + UUID.randomUUID().toString()
        val localProj = ProjectEntity(id = tempId, name = name, color = color)
        localDataSource.upsertProject(localProj)
        enqueue(
            OpType.CREATE_PROJECT, tempId,
            payload = json.encodeToString(CreateProjectRequest.serializer(), CreateProjectRequest(name, color))
        )
        Result.success(localProj)
    }

    suspend fun updateProject(id: String, name: String, color: String): Result<ProjectEntity> = withContext(Dispatchers.IO) {
        val updated = ProjectEntity(id = id, name = name, color = color)
        localDataSource.upsertProject(updated)
        enqueue(
            OpType.UPDATE_PROJECT, id,
            payload = json.encodeToString(CreateProjectRequest.serializer(), CreateProjectRequest(name, color))
        )
        Result.success(updated)
    }

    suspend fun deleteProject(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        localDataSource.removeOpsFor(id)
        localDataSource.deleteProject(id)
        if (!isTemp(id)) {
            enqueue(OpType.DELETE_PROJECT, id)
        }
        Result.success(Unit)
    }

    // -------------------------------------------------------------- subtasks

    suspend fun toggleSubtaskCompleted(taskId: String, subtaskId: String, completed: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val newStatus = !completed
        localDataSource.updateSubtaskCompleted(subtaskId, newStatus)
        localDataSource.markTaskUnsynced(taskId)
        enqueue(
            OpType.UPDATE_SUBTASK, subtaskId, parentId = taskId,
            payload = json.encodeToString(UpdateSubtaskRequest.serializer(), UpdateSubtaskRequest(completed = newStatus))
        )
        Result.success(Unit)
    }

    suspend fun createSubtask(taskId: String, title: String): Result<SubtaskEntity> = withContext(Dispatchers.IO) {
        val tempId = "temp_sub_" + UUID.randomUUID().toString()
        val localSubtask = SubtaskEntity(id = tempId, taskId = taskId, title = title, completed = false, position = 999)
        localDataSource.upsertSubtask(localSubtask)
        localDataSource.markTaskUnsynced(taskId)
        enqueue(
            OpType.CREATE_SUBTASK, tempId, parentId = taskId,
            payload = json.encodeToString(CreateSubtaskRequest.serializer(), CreateSubtaskRequest(title = title))
        )
        Result.success(localSubtask)
    }

    suspend fun deleteSubtask(taskId: String, subtaskId: String): Result<Unit> = withContext(Dispatchers.IO) {
        localDataSource.removeOpsFor(subtaskId)
        localDataSource.deleteSubtask(subtaskId)
        if (!isTemp(subtaskId)) {
            localDataSource.markTaskUnsynced(taskId)
            enqueue(OpType.DELETE_SUBTASK, subtaskId, parentId = taskId)
        } else if (!localDataSource.hasPendingOps(taskId)) {
            localDataSource.markTaskSynced(taskId)
        }
        Result.success(Unit)
    }

    // ---------------------------------------------- labels & comments (online)

    suspend fun createLabel(name: String, color: String): Result<LabelEntity> = withContext(Dispatchers.IO) {
        try {
            val created = apiClient.getApi().createLabel(CreateLabelRequest(name, color))
            val label = LabelEntity(created.id, created.name, created.color)
            localDataSource.upsertLabel(label)
            Result.success(label)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getComments(taskId: String): Result<List<CommentDto>> = withContext(Dispatchers.IO) {
        try {
            Result.success(apiClient.getApi().getComments(taskId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addComment(taskId: String, content: String): Result<CommentDto> = withContext(Dispatchers.IO) {
        try {
            Result.success(apiClient.getApi().createComment(taskId, CommentRequest(content)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteComment(taskId: String, commentId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            apiClient.getApi().deleteComment(taskId, commentId).requireSuccess()
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
            isSynced = true,
            subtasks = dto.subtasks.map {
                SubtaskEntity(
                    id = it.id,
                    taskId = dto.id,
                    title = it.title,
                    completed = it.completed,
                    position = it.position ?: 0
                )
            },
            labels = dto.labels.map { LabelEntity(it.id, it.name, it.color) }
        )
    }
}
