package com.example.todoflow.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.todoflow.data.local.ProjectEntity
import com.example.todoflow.data.local.SessionManager
import com.example.todoflow.data.local.TaskEntity
import com.example.todoflow.data.local.TaskFilterType
import com.example.todoflow.data.local.ThemeMode
import com.example.todoflow.data.repository.TaskRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TasksFilterState(
    val filterType: TaskFilterType = TaskFilterType.ALL,
    val selectedProjectId: String? = null
)

class TasksViewModel(
    private val taskRepository: TaskRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = sessionManager.themeMode

    private val _filterState = MutableStateFlow(TasksFilterState(TaskFilterType.ALL))
    val filterState: StateFlow<TasksFilterState> = _filterState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

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

    @OptIn(ExperimentalCoroutinesApi::class)
    val tasks: StateFlow<List<TaskEntity>> = combine(
        _filterState.flatMapLatest { filter ->
            taskRepository.observeTasks(filter.filterType, filter.selectedProjectId)
        },
        _searchQuery
    ) { rawTasks, query ->
        if (query.isBlank()) {
            rawTasks
        } else {
            val lower = query.trim().lowercase()
            rawTasks.filter {
                it.title.lowercase().contains(lower) ||
                    (it.description?.lowercase()?.contains(lower) == true) ||
                    (it.projectName?.lowercase()?.contains(lower) == true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Automatic background refresh on start (stale-while-revalidate)
        refresh()
    }

    fun setThemeMode(mode: ThemeMode) {
        sessionManager.setThemeMode(mode)
    }

    fun toggleReorderMode() {
        _isReordering.value = !_isReordering.value
    }

    fun setFilter(type: TaskFilterType, projectId: String? = null) {
        currentPage = 0
        _hasMore.value = true
        _filterState.value = TasksFilterState(type, projectId)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            currentPage = 0
            _hasMore.value = true
            val result = taskRepository.refresh(_filterState.value.selectedProjectId)
            _isRefreshing.value = false
            if (result.isFailure) {
                _statusMessage.value = "Offline mode: Showing cached tasks"
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
            taskRepository.toggleTaskCompleted(task.id, task.completed)
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
        projectName: String? = null
    ) {
        viewModelScope.launch {
            taskRepository.createTask(
                title = title,
                description = description,
                priority = priority,
                dueDate = dueDate,
                projectId = projectId,
                projectName = projectName
            )
        }
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
