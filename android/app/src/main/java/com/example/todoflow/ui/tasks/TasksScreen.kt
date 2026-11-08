package com.example.todoflow.ui.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.example.todoflow.reminders.ReminderNotifier
import com.example.todoflow.security.AppLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.todoflow.data.local.ProjectEntity
import com.example.todoflow.data.local.TaskEntity
import com.example.todoflow.data.local.TaskFilterType
import com.example.todoflow.data.local.ThemeMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.tasks.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val filterState by viewModel.filterState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val labels by viewModel.labels.collectAsState()
    val pendingOpCount by viewModel.pendingOpCount.collectAsState()
    val isLive by viewModel.realtimeConnected.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val isReordering by viewModel.isReordering.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val remindersEnabled by viewModel.remindersEnabled.collectAsState()
    val appLockEnabled by viewModel.appLockEnabled.collectAsState()

    // Notifications need a runtime permission on Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setRemindersEnabled(granted)
    }

    var isSearchVisible by remember { mutableStateOf(false) }
    var isCreateDialogOpen by remember { mutableStateOf(false) }
    var taskBeingEdited by remember { mutableStateOf<TaskEntity?>(null) }
    var commentsTask by remember { mutableStateOf<TaskEntity?>(null) }
    var isLogoutDialogOpen by remember { mutableStateOf(false) }
    var isThemeDialogOpen by remember { mutableStateOf(false) }

    // Project Dialog state (for add or edit)
    var isProjectDialogOpen by remember { mutableStateOf(false) }
    var projectToEdit by remember { mutableStateOf<ProjectEntity?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Bumped on every "Undo" so a restored row starts un-swiped
    var undoCount by remember { mutableIntStateOf(0) }

    // Deleting hides the task and offers "Undo"; the delete is only sent once the snackbar goes away
    fun requestDelete(task: TaskEntity) {
        val indexBefore = tasks.indexOfFirst { it.id == task.id }
        viewModel.hideTaskForDelete(task.id)
        // Snackbars queue up: clear any message that's showing so "Undo" appears right away
        snackbarHostState.currentSnackbarData?.dismiss()
        scope.launch {
            val result = try {
                snackbarHostState.showSnackbar(
                    message = "Task deleted",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Long
                )
            } catch (e: CancellationException) {
                viewModel.commitDelete(task.id) // screen left composition: don't leave it hidden forever
                throw e
            }
            if (result == SnackbarResult.ActionPerformed) {
                undoCount++
                viewModel.undoDelete(task.id)
                // The list keeps its scroll position, so a restored row above the top would stay hidden
                if (indexBefore in 0..listState.firstVisibleItemIndex) {
                    delay(50)
                    listState.animateScrollToItem(indexBefore)
                }
            } else {
                viewModel.commitDelete(task.id)
            }
        }
    }

    // Lazy loading on scroll: triggers when user scrolls near the end
    LaunchedEffect(listState, isLoadingMore, hasMore) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 3
        }.collect { nearEnd ->
            if (nearEnd && !isLoadingMore && hasMore && !isReordering) {
                viewModel.loadNextPage()
            }
        }
    }

    // Keep in sync with the server: live updates over a WebSocket while the app is visible, plus a
    // refresh when it comes to the foreground and a polling fallback (every 15s, or every 2 minutes
    // while the live connection is up). Everything stops when the app is backgrounded.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.startRealtime()
            try {
                launch {
                    viewModel.realtimeEvents.collectLatest {
                        delay(700) // coalesce bursts of events into one refresh
                        viewModel.refresh(silent = true)
                    }
                }
                var first = true
                while (true) {
                    viewModel.refresh(silent = !first)
                    first = false
                    delay(if (viewModel.realtimeConnected.value) 120_000 else 15_000)
                }
            } finally {
                viewModel.stopRealtime()
            }
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    val activeProject = projects.firstOrNull { it.id == filterState.selectedProjectId }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // Include the keyboard and display cutouts, not just the system bars
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "TodoFlow",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                SyncBadge(syncStatus, pendingOpCount, isLive)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = when (filterState.filterType) {
                                        TaskFilterType.ALL -> "${tasks.size} tasks"
                                        TaskFilterType.INBOX -> "Inbox • ${tasks.size} tasks"
                                        TaskFilterType.TODAY -> "Today • ${tasks.size} tasks"
                                        TaskFilterType.UPCOMING -> "Upcoming • ${tasks.size} tasks"
                                        TaskFilterType.COMPLETED -> "Done • ${tasks.size} tasks"
                                        TaskFilterType.PROJECT -> "${activeProject?.name ?: "Project"} • ${tasks.size} tasks"
                                        TaskFilterType.LABEL -> "#${labels.firstOrNull { it.id == filterState.selectedLabelId }?.name ?: "label"} • ${tasks.size} tasks"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (filterState.filterType == TaskFilterType.PROJECT && activeProject != null) {
                                    IconButton(
                                        onClick = {
                                            projectToEdit = activeProject
                                            isProjectDialogOpen = true
                                        },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit Project",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    },
                    actions = {
                        // Reorder mode toggle
                        IconButton(onClick = { viewModel.toggleReorderMode() }) {
                            Icon(
                                Icons.Default.SwapVert,
                                contentDescription = "Reorder Tasks",
                                tint = if (isReordering) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        // Settings (theme, reminders, app lock)
                        IconButton(onClick = { isThemeDialogOpen = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                        // Search
                        IconButton(onClick = {
                            isSearchVisible = !isSearchVisible
                            if (!isSearchVisible) viewModel.setSearchQuery("")
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        // Refresh
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                        // Logout
                        IconButton(onClick = { isLogoutDialogOpen = true }) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
                        }
                    }
                )

                // Search Bar
                AnimatedVisibility(visible = isSearchVisible) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search tasks...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                // Filter Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = filterState.filterType == TaskFilterType.ALL,
                        onClick = { viewModel.setFilter(TaskFilterType.ALL) },
                        label = { Text("All") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    FilterChip(
                        selected = filterState.filterType == TaskFilterType.INBOX,
                        onClick = { viewModel.setFilter(TaskFilterType.INBOX) },
                        label = { Text("Inbox") },
                        leadingIcon = { Icon(Icons.Default.Inbox, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    FilterChip(
                        selected = filterState.filterType == TaskFilterType.TODAY,
                        onClick = { viewModel.setFilter(TaskFilterType.TODAY) },
                        label = { Text("Today") },
                        leadingIcon = { Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    FilterChip(
                        selected = filterState.filterType == TaskFilterType.UPCOMING,
                        onClick = { viewModel.setFilter(TaskFilterType.UPCOMING) },
                        label = { Text("Upcoming") },
                        leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    FilterChip(
                        selected = filterState.filterType == TaskFilterType.COMPLETED,
                        onClick = { viewModel.setFilter(TaskFilterType.COMPLETED) },
                        label = { Text("Done") },
                        leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )

                    // Project Filter Chips
                    projects.forEach { proj ->
                        val isSelected = filterState.filterType == TaskFilterType.PROJECT &&
                            filterState.selectedProjectId == proj.id
                        val color = parseHexColor(proj.color)

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    // Clicking selected chip opens edit dialog
                                    projectToEdit = proj
                                    isProjectDialogOpen = true
                                } else {
                                    viewModel.setFilter(TaskFilterType.PROJECT, proj.id)
                                }
                            },
                            label = { Text(proj.name) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                            }
                        )
                    }

                    // Label Filter Chips
                    labels.forEach { label ->
                        val isSelected = filterState.filterType == TaskFilterType.LABEL &&
                            filterState.selectedLabelId == label.id
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) viewModel.setFilter(TaskFilterType.ALL)
                                else viewModel.setFilter(TaskFilterType.LABEL, labelId = label.id)
                            },
                            label = { Text("#" + label.name) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(parseHexColor(label.color))
                                )
                            }
                        )
                    }

                    // Add Project Chip
                    AssistChip(
                        onClick = {
                            projectToEdit = null
                            isProjectDialogOpen = true
                        },
                        label = { Text("Add Project") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Add Project",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }

                // Reorder Mode Banner
                AnimatedVisibility(visible = isReordering) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Reorder mode: use ▲ and ▼ to reorder tasks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            TextButton(onClick = { viewModel.toggleReorderMode() }) {
                                Text("Done", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isReordering) {
                FloatingActionButton(
                    onClick = { isCreateDialogOpen = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Task")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (tasks.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (filterState.filterType == TaskFilterType.COMPLETED) Icons.Default.CheckCircle else Icons.Default.TaskAlt,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No matching tasks found"
                        else if (filterState.filterType == TaskFilterType.COMPLETED) "No completed tasks yet"
                        else "No tasks here! Tap + to add one",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    itemsIndexed(
                        items = tasks,
                        key = { _, task -> task.id }
                    ) { index, task ->
                        SwipeableTaskRow(
                            modifier = Modifier.testTag("task_row_${task.id}"),
                            resetKey = undoCount,
                            enabled = !isReordering,
                            completed = task.completed,
                            onToggleComplete = { viewModel.toggleTask(task) },
                            onDelete = { requestDelete(task) }
                        ) {
                            TaskItemRow(
                                task = task,
                                projects = projects,
                                onToggle = { viewModel.toggleTask(task) },
                                onDelete = { requestDelete(task) },
                                onClick = { taskBeingEdited = task },
                                onOpenComments = { commentsTask = task },
                                isReorderMode = isReordering,
                                canMoveUp = index > 0,
                                canMoveDown = index < tasks.size - 1,
                                onMoveUp = { viewModel.moveTask(task.id, up = true) },
                                onMoveDown = { viewModel.moveTask(task.id, up = false) },
                                onToggleSubtask = { subtask -> viewModel.toggleSubtask(task.id, subtask) },
                                onCreateSubtask = { title -> viewModel.createSubtask(task.id, title) },
                                onDeleteSubtask = { subtaskId -> viewModel.deleteSubtask(task.id, subtaskId) }
                            )
                        }
                    }

                    // Bottom loading indicator for lazy loading on scroll
                    if (isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (isCreateDialogOpen || taskBeingEdited != null) {
        val editing = taskBeingEdited
        TaskEditorDialog(
            task = editing,
            projects = projects,
            labels = labels,
            defaultProjectId = filterState.selectedProjectId,
            onDismiss = {
                isCreateDialogOpen = false
                taskBeingEdited = null
            },
            onCreateLabel = { name, color -> viewModel.createLabel(name, color) },
            onSave = { title, desc, priority, dueDate, projId, projName, chosenLabels ->
                if (editing != null) {
                    viewModel.updateTask(editing, title, desc, priority, dueDate, projId, projName, chosenLabels)
                } else {
                    viewModel.createTask(
                        title = title,
                        description = desc,
                        priority = priority,
                        dueDate = dueDate,
                        projectId = projId,
                        projectName = projName,
                        labels = chosenLabels
                    )
                }
                isCreateDialogOpen = false
                taskBeingEdited = null
            }
        )
    }

    commentsTask?.let { target ->
        CommentsSheet(task = target, viewModel = viewModel, onDismiss = { commentsTask = null })
    }

    if (isProjectDialogOpen) {
        ProjectDialog(
            projectToEdit = projectToEdit,
            onDismiss = {
                isProjectDialogOpen = false
                projectToEdit = null
            },
            onSave = { name, color ->
                val editing = projectToEdit
                if (editing != null) {
                    viewModel.updateProject(editing.id, name, color)
                } else {
                    viewModel.createProject(name, color)
                }
                isProjectDialogOpen = false
                projectToEdit = null
            },
            onDelete = if (projectToEdit != null) {
                {
                    projectToEdit?.let { viewModel.deleteProject(it.id) }
                    isProjectDialogOpen = false
                    projectToEdit = null
                }
            } else null
        )
    }

    if (isThemeDialogOpen) {
        val context = LocalContext.current
        SettingsDialog(
            themeMode = themeMode,
            onThemeChange = { viewModel.setThemeMode(it) },
            remindersEnabled = remindersEnabled,
            onRemindersChange = { enable ->
                if (!enable) {
                    viewModel.setRemindersEnabled(false)
                } else if (ReminderNotifier.hasPermission(context)) {
                    viewModel.setRemindersEnabled(true)
                } else {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            appLockEnabled = appLockEnabled,
            appLockAvailable = AppLock.isAvailable(context),
            onAppLockChange = { viewModel.setAppLockEnabled(it) },
            onDismiss = { isThemeDialogOpen = false }
        )
    }

    if (isLogoutDialogOpen) {
        AlertDialog(
            onDismissRequest = { isLogoutDialogOpen = false },
            title = { Text("Log out?") },
            text = { Text("You will be returned to the login screen.") },
            confirmButton = {
                Button(
                    onClick = {
                        isLogoutDialogOpen = false
                        viewModel.logout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Log Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { isLogoutDialogOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private data class BadgeStyle(val container: Color, val dot: Color, val content: Color, val label: String)

/**
 * Shows the real sync state: green only when the last sync succeeded and nothing is waiting to be
 * uploaded. Local changes that haven't reached the server yet are counted as "pending".
 */
@Composable
private fun SyncBadge(status: SyncStatus, pending: Int, live: Boolean) {
    val colors = MaterialTheme.colorScheme
    val style = when {
        status == SyncStatus.SYNCING -> BadgeStyle(colors.primaryContainer, colors.primary, colors.onPrimaryContainer, "Syncing...")
        status == SyncStatus.ERROR -> BadgeStyle(colors.errorContainer, colors.error, colors.onErrorContainer, "Sync failed")
        status == SyncStatus.OFFLINE && pending > 0 ->
            BadgeStyle(colors.tertiaryContainer, colors.tertiary, colors.onTertiaryContainer, "Offline · $pending pending")
        status == SyncStatus.OFFLINE -> BadgeStyle(colors.tertiaryContainer, colors.tertiary, colors.onTertiaryContainer, "Offline")
        pending > 0 -> BadgeStyle(colors.tertiaryContainer, colors.tertiary, colors.onTertiaryContainer, "$pending pending")
        live -> BadgeStyle(Color(0xFFE8F5E9), Color(0xFF4CAF50), Color(0xFF2E7D32), "Live")
        else -> BadgeStyle(Color(0xFFE8F5E9), Color(0xFF4CAF50), Color(0xFF2E7D32), "Synced")
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(style.container)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(style.dot)
        )
        Text(
            text = style.label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = style.content
        )
    }
}
