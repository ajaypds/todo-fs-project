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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.todoflow.data.local.ProjectEntity
import com.example.todoflow.data.local.TaskFilterType
import com.example.todoflow.data.local.ThemeMode

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
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val isReordering by viewModel.isReordering.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    var isSearchVisible by remember { mutableStateOf(false) }
    var isCreateDialogOpen by remember { mutableStateOf(false) }
    var isLogoutDialogOpen by remember { mutableStateOf(false) }
    var isThemeDialogOpen by remember { mutableStateOf(false) }

    // Project Dialog state (for add or edit)
    var isProjectDialogOpen by remember { mutableStateOf(false) }
    var projectToEdit by remember { mutableStateOf<ProjectEntity?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

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

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    val activeProject = projects.firstOrNull { it.id == filterState.selectedProjectId }

    Scaffold(
        modifier = modifier.fillMaxSize(),
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
                                // Sync indicator badge
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isRefreshing) MaterialTheme.colorScheme.primaryContainer
                                            else Color(0xFFE8F5E9)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isRefreshing) MaterialTheme.colorScheme.primary
                                                else Color(0xFF4CAF50)
                                            )
                                    )
                                    Text(
                                        text = if (isRefreshing) "Syncing..." else "Synced",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isRefreshing) MaterialTheme.colorScheme.onPrimaryContainer
                                        else Color(0xFF2E7D32)
                                    )
                                }
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
                        // Theme Switcher Dialog
                        IconButton(onClick = { isThemeDialogOpen = true }) {
                            Icon(
                                imageVector = when (themeMode) {
                                    ThemeMode.LIGHT -> Icons.Default.LightMode
                                    ThemeMode.DARK -> Icons.Default.DarkMode
                                    ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                },
                                contentDescription = "Theme"
                            )
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
                        TaskItemRow(
                            task = task,
                            projects = projects,
                            onToggle = { viewModel.toggleTask(task) },
                            onDelete = { viewModel.deleteTask(task.id) },
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

    if (isCreateDialogOpen) {
        CreateTaskDialog(
            projects = projects,
            selectedProjectId = filterState.selectedProjectId,
            onDismiss = { isCreateDialogOpen = false },
            onCreate = { title, desc, priority, dueDate, projId, projName ->
                viewModel.createTask(
                    title = title,
                    description = desc,
                    priority = priority,
                    dueDate = dueDate,
                    projectId = projId,
                    projectName = projName
                )
                isCreateDialogOpen = false
            }
        )
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
        AlertDialog(
            onDismissRequest = { isThemeDialogOpen = false },
            title = { Text("Choose Theme") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = themeMode == ThemeMode.SYSTEM,
                            onClick = {
                                viewModel.setThemeMode(ThemeMode.SYSTEM)
                                isThemeDialogOpen = false
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("System Default")
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = themeMode == ThemeMode.LIGHT,
                            onClick = {
                                viewModel.setThemeMode(ThemeMode.LIGHT)
                                isThemeDialogOpen = false
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Light Mode")
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = themeMode == ThemeMode.DARK,
                            onClick = {
                                viewModel.setThemeMode(ThemeMode.DARK)
                                isThemeDialogOpen = false
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Dark Mode")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isThemeDialogOpen = false }) {
                    Text("Close")
                }
            }
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
