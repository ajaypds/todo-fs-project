package com.example.todoflow.data.repository

import com.example.todoflow.data.model.AuthResponse
import com.example.todoflow.data.model.CommentDto
import com.example.todoflow.data.model.CommentRequest
import com.example.todoflow.data.model.CreateLabelRequest
import com.example.todoflow.data.model.CreateProjectRequest
import com.example.todoflow.data.model.CreateSubtaskRequest
import com.example.todoflow.data.model.CreateTaskRequest
import com.example.todoflow.data.model.LabelDto
import com.example.todoflow.data.model.LoginRequest
import com.example.todoflow.data.model.PageResponse
import com.example.todoflow.data.model.ProjectDto
import com.example.todoflow.data.model.ReorderTasksRequest
import com.example.todoflow.data.model.SubtaskDto
import com.example.todoflow.data.model.TaskDto
import com.example.todoflow.data.model.UpdateSubtaskRequest
import com.example.todoflow.data.model.UpdateTaskRequest
import com.example.todoflow.data.remote.TodoFlowApi
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** In-memory server for tests, with switches to simulate being offline or the server failing. */
class FakeTodoFlowApi : TodoFlowApi {

    var offline = false

    /** Throws an HTTP error for these calls (method name -> status code), e.g. "updateTask" to 400. */
    val httpFailures = mutableMapOf<String, Int>()

    val serverTasks = mutableListOf<TaskDto>()
    val serverProjects = mutableListOf<ProjectDto>()
    val serverLabels = mutableListOf<LabelDto>()
    val serverComments = mutableListOf<CommentDto>()

    // What the app sent, in order
    val createdTasks = mutableListOf<CreateTaskRequest>()
    val updates = mutableListOf<Pair<String, UpdateTaskRequest>>()
    val deletedTasks = mutableListOf<String>()
    val reorders = mutableListOf<List<String>>()
    val createdProjects = mutableListOf<CreateProjectRequest>()

    private var counter = 0
    private fun nextId(prefix: String) = "$prefix-${++counter}"

    private fun gate(method: String) {
        if (offline) throw IOException("offline")
        httpFailures[method]?.let { code ->
            throw HttpException(Response.error<Any>(code, "".toResponseBody()))
        }
    }

    private fun <T> status(method: String, body: T): Response<T> {
        if (offline) throw IOException("offline")
        httpFailures[method]?.let { code -> return Response.error(code, "".toResponseBody()) }
        return Response.success(body)
    }

    override suspend fun login(request: LoginRequest): AuthResponse = AuthResponse(token = "t")

    override suspend fun getTasks(projectId: String?, page: Int, size: Int): PageResponse<TaskDto> {
        gate("getTasks")
        val filtered = serverTasks.filter { projectId == null || it.projectId == projectId }
        val from = page * size
        val content = filtered.drop(from).take(size)
        val pages = if (filtered.isEmpty()) 1 else (filtered.size + size - 1) / size
        return PageResponse(content, filtered.size.toLong(), pages, size, page)
    }

    override suspend fun getTask(taskId: String): TaskDto = serverTasks.first { it.id == taskId }

    override suspend fun createTask(request: CreateTaskRequest): TaskDto {
        gate("createTask")
        createdTasks += request
        val dto = TaskDto(
            id = nextId("task"),
            title = request.title,
            description = request.description,
            priority = request.priority,
            dueDate = request.dueDate,
            projectId = request.projectId,
            projectName = request.projectName,
            labels = serverLabels.filter { it.id in (request.labelIds ?: emptyList()) }
        )
        serverTasks += dto
        return dto
    }

    override suspend fun updateTask(taskId: String, request: UpdateTaskRequest): Response<ResponseBody> {
        val response = status("updateTask", "{}".toResponseBody())
        if (response.isSuccessful) {
            updates += taskId to request
            val index = serverTasks.indexOfFirst { it.id == taskId }
            if (index >= 0) {
                val old = serverTasks[index]
                serverTasks[index] = old.copy(
                    title = request.title ?: old.title,
                    completed = request.completed ?: old.completed,
                    dueDate = if (request.clearDueDate == true) null else request.dueDate ?: old.dueDate
                )
            }
        }
        return response
    }

    override suspend fun deleteTask(taskId: String): Response<Unit> {
        val response = status("deleteTask", Unit)
        if (response.isSuccessful) {
            deletedTasks += taskId
            serverTasks.removeAll { it.id == taskId }
        }
        return response
    }

    override suspend fun createSubtask(taskId: String, request: CreateSubtaskRequest): SubtaskDto {
        gate("createSubtask")
        return SubtaskDto(id = nextId("sub"), title = request.title)
    }

    override suspend fun updateSubtask(taskId: String, subtaskId: String, request: UpdateSubtaskRequest): Response<ResponseBody> =
        status("updateSubtask", "{}".toResponseBody())

    override suspend fun deleteSubtask(taskId: String, subtaskId: String): Response<Unit> =
        status("deleteSubtask", Unit)

    override suspend fun reorderTasks(request: ReorderTasksRequest): Response<Unit> {
        val response = status("reorderTasks", Unit)
        if (response.isSuccessful) reorders += request.taskIds
        return response
    }

    override suspend fun getProjects(): List<ProjectDto> {
        gate("getProjects")
        return serverProjects.toList()
    }

    override suspend fun createProject(request: CreateProjectRequest): ProjectDto {
        gate("createProject")
        createdProjects += request
        return ProjectDto(nextId("proj"), request.name, request.color).also { serverProjects += it }
    }

    override suspend fun updateProject(projectId: String, request: CreateProjectRequest): ProjectDto {
        gate("updateProject")
        return ProjectDto(projectId, request.name, request.color)
    }

    override suspend fun deleteProject(projectId: String): Response<Unit> = status("deleteProject", Unit)

    override suspend fun getLabels(): List<LabelDto> {
        gate("getLabels")
        return serverLabels.toList()
    }

    override suspend fun createLabel(request: CreateLabelRequest): LabelDto {
        gate("createLabel")
        return LabelDto(nextId("label"), request.name, request.color).also { serverLabels += it }
    }

    override suspend fun getComments(taskId: String): List<CommentDto> {
        gate("getComments")
        return serverComments.filter { it.taskId == taskId }
    }

    override suspend fun createComment(taskId: String, request: CommentRequest): CommentDto {
        gate("createComment")
        return CommentDto(
            id = nextId("comment"),
            taskId = taskId,
            userName = "Me",
            userEmail = "me@example.com",
            content = request.content
        ).also { serverComments += it }
    }

    override suspend fun deleteComment(taskId: String, commentId: String): Response<Unit> {
        val response = status("deleteComment", Unit)
        if (response.isSuccessful) serverComments.removeAll { it.id == commentId }
        return response
    }
}
