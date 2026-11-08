package com.example.todoflow

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.example.todoflow.data.local.SessionManager
import com.example.todoflow.data.local.TodoDbHelper
import com.example.todoflow.data.local.TodoLocalDataSource
import com.example.todoflow.data.remote.ApiClient
import com.example.todoflow.data.remote.RealtimeClient
import com.example.todoflow.data.repository.TaskRepository
import androidx.work.WorkManager
import com.example.todoflow.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TodoFlowApplication : Application(), Configuration.Provider {

    // Scheduling problems (e.g. the system refusing more jobs) must never take the app down: WorkManager
    // reports them on its own threads, where an uncaught exception would crash the process.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setSchedulingExceptionHandler { e -> Log.w("TodoFlow", "Could not schedule background work", e) }
            .setInitializationExceptionHandler { e -> Log.w("TodoFlow", "WorkManager initialisation failed", e) }
            .setMinimumLoggingLevel(Log.INFO)
            .build()

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

    lateinit var realtimeClient: RealtimeClient
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

        realtimeClient = RealtimeClient(sessionManager, apiClient)

        // Due-date reminders were removed: drop any reminder jobs an earlier version scheduled
        appScope.launch { runCatching { WorkManager.getInstance(this@TodoFlowApplication).cancelAllWorkByTag("due_reminder") } }

        // Changes queued in a previous run (e.g. the app was closed while offline)
        appScope.launch {
            if (sessionManager.isLoggedIn.value && localDataSource.getPendingOps().isNotEmpty()) {
                SyncWorker.schedule(this@TodoFlowApplication)
            }
        }
    }
}
