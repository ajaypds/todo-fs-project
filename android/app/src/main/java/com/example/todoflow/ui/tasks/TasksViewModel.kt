package com.example.todoflow.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.todoflow.data.local.LabelEntity
import com.example.todoflow.data.local.ProjectEntity
import com.example.todoflow.data.model.CommentDto
import com.example.todoflow.data.remote.RealtimeClient
import com.example.todoflow.data.local.SessionManager
import com.example.todoflow.data.local.SubtaskEntity
import com.example.todoflow.data.local.TaskEntity
import com.example.todoflow.data.local.TaskFilterType
import com.example.todoflow.data.local.ThemeMode
import com.example.todoflow.data.repository.TaskRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

enum class SyncStatus { SYNCING, SYNCED, OFFLINE, ERROR }

data class TasksFilterState(
    val filterType: TaskFilterType = TaskFilterType.ALL,
    val selectedProjectId: String? = null,
    val selectedLabelId: String? = null
)

class TasksViewModel(
    private val taskRepository: TaskRepository,
    private val sessionManager: SessionManager,
    private val realtimeClient: RealtimeClient
) : ViewModel() {

    /** True while live updates from the server are connected. */
    val realtimeConnected: StateFlow<Boolean> = realtimeClient.connected

    /** Emits whenever the server reports a task change (created, updated or deleted). */
    val realtimeEvents = realtimeClient.events

    fun startRealtime() = realtimeClient.start(viewModelScope)

    fun stopRealtime() = realtimeClient.stop()

    override fun onCleared() {
        realtimeClient.stop()
        super.onCleared()
    }

    val themeMode: StateFlow<ThemeMode> = sessionManager.themeMode
    val appLockEnabled: StateFlow<Boolean> = sessionManager.appLockEnabled

    fun setAppLockEnabled(enabled: Boolean) = sessionManager.setAppLockEnabled(enabled)

    init {
        // Notices from the offline queue (e.g. a change the server rejected)
        viewModelScope.launch {
            taskRepository.syncEvents.collect { _statusMessage.value = it }
        }
    }

    private val _filterState = MutableStateFlow(TasksFilterState(TaskFilterType.ALL))
    val filterState: StateFlow<TasksFilterState> = _filterState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _syncStatus = MutableStateFlow(SyncStatus.SYNCING)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private var refreshJob: Job? = null
    private var rerunRequested = false

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _isReordering = MutableStateFlow(false)
    val isReordering: StateFlow<Boolean> = _isReordering.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var currentPage = 0

    val projects: StateFlow<List<ProjectEntity>> = taskRepository.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val labels: StateFlow<List<LabelEntity>> = taskRepository.observeLabels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Local changes that haven't reached the server yet. */
    val pendingOpCount: StateFlow<Int> = taskRepository.observePendingOpCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Tasks swiped away that can still be restored with "Undo". */
    private val _hiddenTaskIds = MutableStateFlow<Set<String>>(emptySet())

    @OptIn(ExperimentalCoroutinesApi::class)
    val tasks: StateFlow<List<TaskEntity>> = combine(
        _filterState.flatMapLatest { filter ->
            taskRepository.observeTasks(filter.filterType, filter.selectedProjectId, filter.selectedLabelId)
        },
        _searchQuery,
        _hiddenTaskIds
    ) { rawTasks, query, hidden ->
        val visible = if (hidden.isEmpty()) rawTasks else rawTasks.filter { it.id !in hidden }
        if (query.isBlank()) {
            visible
        } else {
            val lower = query.trim().lowercase()
            visible.filter {
                it.title.lowercase().contains(lower) ||
                    (it.description?.lowercase()?.contains(lower) == true) ||
                    (it.projectName?.lowercase()?.contains(lower) == true) ||
                    it.labels.any { l -> l.name.lowercase().contains(lower) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setThemeMode(mode: ThemeMode) {
        sessionManager.setThemeMode(mode)
    }

    fun toggleReorderMode() {
        _isReordering.value = !_isReordering.value
    }

    fun setFilter(type: TaskFilterType, projectId: String? = null, labelId: String? = null) {
        currentPage = 0
        _hasMore.value = true
        _filterState.value = TasksFilterState(type, projectId, labelId)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Syncs with the server. [silent] refreshes (periodic / on resume) don't show the "Syncing"
     * indicator and only surface errors that need user attention.
     */
    fun refresh(silent: Boolean = false) {
        if (refreshJob?.isActive == true) {
            // A server event or tap arrived mid-refresh: run once more afterwards so nothing is missed
            rerunRequested = true
            return
        }
        refreshJob = viewModelScope.launch {
            do {
                rerunRequested = false
                performRefresh(silent)
            } while (rerunRequested)
        }
    }

    private suspend fun performRefresh(silent: Boolean) {
        if (!silent) _isRefreshing.value = true
        _syncStatus.value = SyncStatus.SYNCING
        currentPage = 0
        _hasMore.value = true
        val result = taskRepository.refresh(_filterState.value.selectedProjectId)
        _isRefreshing.value = false
        val error = result.exceptionOrNull()
        if (error == null) {
            _syncStatus.value = SyncStatus.SYNCED
            return
        }
        android.util.Log.e("TasksViewModel", "Refresh failed", error)
        when {
            error is IOException -> {
                _syncStatus.value = SyncStatus.OFFLINE
                if (!silent) _statusMessage.value = "Offline mode: Showing cached tasks"
            }
            error is HttpException && (error.code() == 401 || error.code() == 403) -> {
                _syncStatus.value = SyncStatus.ERROR
                // If the session was ended the app is already switching to the login screen,
                // which explains why. Otherwise the server rejected us for another reason.
                if (sessionManager.isLoggedIn.value) {
                    _statusMessage.value =
                        "Couldn't verify your session (HTTP ${error.code()}). Showing cached tasks"
                }
            }
            error is HttpException -> {
                _syncStatus.value = SyncStatus.ERROR
                if (!silent) _statusMessage.value = "Server error (HTTP ${error.code()}). Showing cached tasks"
            }
            else -> {
                _syncStatus.value = SyncStatus.ERROR
                if (!silent) {
                    _statusMessage.value =
                        "Sync failed: ${error.localizedMessage ?: error.javaClass.simpleName}"
                }
            }
        }
    }

    fun loadNextPage() {
        if (_isLoadingMore.value || !_hasMore.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            val nextPage = currentPage + 1
            val result = taskRepository.loadTasksPage(
                page = nextPage,
                size = 20,
                projectId = _filterState.value.selectedProjectId
            )
            if (result.isSuccess) {
                currentPage = nextPage
                _hasMore.value = result.getOrDefault(false)
            } else {
                _hasMore.value = false
            }
            _isLoadingMore.value = false
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun toggleTask(task: TaskEntity) {
        viewModelScope.launch {
            val result = taskRepository.toggleTaskCompleted(task.id, task.completed)
            if (result.isFailure) {
                _statusMessage.value = "Failed to update task: ${result.exceptionOrNull()?.localizedMessage ?: "Network error"}"
            }
        }
    }

    fun toggleSubtask(taskId: String, subtask: SubtaskEntity) {
        viewModelScope.launch {
            val result = taskRepository.toggleSubtaskCompleted(taskId, subtask.id, subtask.completed)
            if (result.isFailure) {
                _statusMessage.value = "Failed to update subtask: ${result.exceptionOrNull()?.localizedMessage ?: "Network error"}"
            }
        }
    }

    fun createSubtask(taskId: String, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            taskRepository.createSubtask(taskId, title.trim())
        }
    }

    fun deleteSubtask(taskId: String, subtaskId: String) {
        viewModelScope.launch {
            taskRepository.deleteSubtask(taskId, subtaskId)
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            taskRepository.deleteTask(taskId)
        }
    }

    fun moveTask(taskId: String, up: Boolean) {
        val currentTasks = tasks.value.toMutableList()
        val index = currentTasks.indexOfFirst { it.id == taskId }
        if (index == -1) return
        val targetIndex = if (up) index - 1 else index + 1
        if (targetIndex < 0 || targetIndex >= currentTasks.size) return

        val item = currentTasks.removeAt(index)
        currentTasks.add(targetIndex, item)
        val ids = currentTasks.map { it.id }

        viewModelScope.launch {
            taskRepository.reorderTasks(ids)
        }
    }

    fun createTask(
        title: String,
        description: String? = null,
        priority: Int = 0,
        dueDate: String? = null,
        projectId: String? = null,
        projectName: String? = null,
        labels: List<LabelEntity> = emptyList()
    ) {
        viewModelScope.launch {
            taskRepository.createTask(
                title = title,
                description = description,
                priority = priority,
                dueDate = dueDate,
                projectId = projectId,
                projectName = projectName,
                labels = labels
            )
        }
    }

    fun updateTask(
        original: TaskEntity,
        title: String,
        description: String?,
        priority: Int,
        dueDate: String?,
        projectId: String?,
        projectName: String?,
        labels: List<LabelEntity>
    ) {
        viewModelScope.launch {
            taskRepository.updateTask(original, title, description, priority, dueDate, projectId, projectName, labels)
        }
    }

    /** Creates a label (needs a connection). Returns the new label, or null with a message shown. */
    suspend fun createLabel(name: String, color: String): LabelEntity? {
        val result = taskRepository.createLabel(name.trim(), color)
        result.exceptionOrNull()?.let {
            _statusMessage.value = "Couldn't create label: connect to the internet and try again"
        }
        return result.getOrNull()
    }

    /** Hides a task right away; call [commitDelete] when the undo window ends, or [undoDelete]. */
    fun hideTaskForDelete(taskId: String) {
        _hiddenTaskIds.value = _hiddenTaskIds.value + taskId
    }

    fun undoDelete(taskId: String) {
        _hiddenTaskIds.value = _hiddenTaskIds.value - taskId
    }

    fun commitDelete(taskId: String) {
        viewModelScope.launch {
            taskRepository.deleteTask(taskId)
            _hiddenTaskIds.value = _hiddenTaskIds.value - taskId
        }
    }

    // Comments are loaded on demand (not cached offline)
    val currentUserEmail: String? get() = sessionManager.userEmail

    suspend fun loadComments(taskId: String): Result<List<CommentDto>> = taskRepository.getComments(taskId)

    suspend fun addComment(taskId: String, content: String): Result<CommentDto> {
        val result = taskRepository.addComment(taskId, content.trim())
        if (result.isSuccess) refresh(silent = true) // updates the comment count
        return result
    }

    suspend fun deleteComment(taskId: String, commentId: String): Result<Unit> {
        val result = taskRepository.deleteComment(taskId, commentId)
        if (result.isSuccess) refresh(silent = true)
        return result
    }

    fun createProject(name: String, color: String) {
        viewModelScope.launch {
            taskRepository.createProject(name, color)
        }
    }

    fun updateProject(id: String, name: String, color: String) {
        viewModelScope.launch {
            taskRepository.updateProject(id, name, color)
        }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            if (_filterState.value.selectedProjectId == id) {
                setFilter(TaskFilterType.ALL)
            }
            taskRepository.deleteProject(id)
        }
    }

    fun logout() {
        viewModelScope.launch {
            taskRepository.clearCache()
            sessionManager.clearSession()
        }
    }
}
