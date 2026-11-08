package com.example.todoflow

import android.app.Application
import com.example.todoflow.data.local.SessionManager
import com.example.todoflow.data.local.TodoDbHelper
import com.example.todoflow.data.local.TodoLocalDataSource
import com.example.todoflow.data.remote.ApiClient
import com.example.todoflow.data.repository.TaskRepository
import com.example.todoflow.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TodoFlowApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        taskRepository = TaskRepository(
            localDataSource = localDataSource,
            apiClient = apiClient,
            scope = appScope,
            onOpsQueued = { SyncWorker.schedule(this) }
        )

        // Changes queued in a previous run (e.g. the app was closed while offline)
        appScope.launch {
            if (sessionManager.isLoggedIn.value && localDataSource.getPendingOps().isNotEmpty()) {
                SyncWorker.schedule(this@TodoFlowApplication)
            }
        }
    }
}
