package com.example.todoflow

import android.app.Application
import com.example.todoflow.data.local.SessionManager
import com.example.todoflow.data.local.TodoDbHelper
import com.example.todoflow.data.local.TodoLocalDataSource
import com.example.todoflow.data.remote.ApiClient
import com.example.todoflow.data.repository.TaskRepository

class TodoFlowApplication : Application() {

    lateinit var sessionManager: SessionManager
        private set

    lateinit var dbHelper: TodoDbHelper
        private set

    lateinit var localDataSource: TodoLocalDataSource
        private set

    lateinit var apiClient: ApiClient
        private set

    lateinit var taskRepository: TaskRepository
        private set

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager(this)
        dbHelper = TodoDbHelper(this)
        localDataSource = TodoLocalDataSource(dbHelper)
        apiClient = ApiClient(sessionManager)
        taskRepository = TaskRepository(localDataSource, apiClient)
    }
}
