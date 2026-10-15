package com.example.todoflow.data.remote

import com.example.todoflow.data.model.AuthResponse
import com.example.todoflow.data.model.CreateProjectRequest
import com.example.todoflow.data.model.CreateTaskRequest
import com.example.todoflow.data.model.LoginRequest
import com.example.todoflow.data.model.PageResponse
import com.example.todoflow.data.model.ProjectDto
import com.example.todoflow.data.model.TaskDto
import com.example.todoflow.data.model.UpdateTaskRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface TodoFlowApi {

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @GET("api/v1/tasks")
    suspend fun getTasks(
        @Query("projectId") projectId: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 100
    ): PageResponse<TaskDto>

    @GET("api/v1/tasks/{taskId}")
    suspend fun getTask(@Path("taskId") taskId: String): TaskDto

    @POST("api/v1/tasks")
    suspend fun createTask(@Body request: CreateTaskRequest): TaskDto

    @PUT("api/v1/tasks/{taskId}")
    suspend fun updateTask(
        @Path("taskId") taskId: String,
        @Body request: UpdateTaskRequest
    ): TaskDto

    @DELETE("api/v1/tasks/{taskId}")
    suspend fun deleteTask(@Path("taskId") taskId: String): Response<Unit>

    @POST("api/v1/tasks/reorder")
    suspend fun reorderTasks(@Body request: com.example.todoflow.data.model.ReorderTasksRequest): Response<Unit>

    @GET("api/v1/projects")
    suspend fun getProjects(): List<ProjectDto>

    @POST("api/v1/projects")
    suspend fun createProject(@Body request: CreateProjectRequest): ProjectDto

    @PUT("api/v1/projects/{projectId}")
    suspend fun updateProject(
        @Path("projectId") projectId: String,
        @Body request: CreateProjectRequest
    ): ProjectDto

    @DELETE("api/v1/projects/{projectId}")
    suspend fun deleteProject(@Path("projectId") projectId: String): Response<Unit>
}
