package com.example.todoflow

import android.app.Application
import com.example.todoflow.data.local.SessionManager
import com.example.todoflow.data.local.TodoDbHelper
import com.example.todoflow.data.local.TodoLocalDataSource
import com.example.todoflow.data.remote.ApiClient
import com.example.todoflow.data.remote.RealtimeClient
import com.example.todoflow.data.repository.TaskRepository
import com.example.todoflow.data.local.TaskFilterType
import com.example.todoflow.reminders.ReminderScheduler
import com.example.todoflow.sync.SyncWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
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

        // Keep due-date reminders in step with the tasks, the setting and the login state
        appScope.launch {
            combine(
                taskRepository.observeTasks(TaskFilterType.ALL),
                sessionManager.remindersEnabled,
                sessionManager.isLoggedIn
            ) { tasks, enabled, loggedIn -> tasks to (enabled && loggedIn) }
                .debounce(1500)
                .collectLatest { (tasks, enabled) ->
                    runCatching { ReminderScheduler.sync(this@TodoFlowApplication, tasks, enabled) }
                }
        }

        // Changes queued in a previous run (e.g. the app was closed while offline)
        appScope.launch {
            if (sessionManager.isLoggedIn.value && localDataSource.getPendingOps().isNotEmpty()) {
                SyncWorker.schedule(this@TodoFlowApplication)
            }
        }
    }
}
