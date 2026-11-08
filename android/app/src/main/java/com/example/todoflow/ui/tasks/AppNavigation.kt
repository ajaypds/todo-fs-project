package com.example.todoflow.ui.tasks

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.todoflow.R
import com.example.todoflow.data.local.LabelEntity
import com.example.todoflow.data.local.ProjectEntity
import com.example.todoflow.data.local.TaskFilterType
import com.example.todoflow.data.local.ThemeMode
import com.example.todoflow.theme.Brand
import com.example.todoflow.theme.BrandDark
import com.example.todoflow.theme.BrandLight

/** The brand gradient used by the drawer header, the login hero and the centre "add" button. */
val BrandGradient: Brush = Brush.linearGradient(listOf(BrandDark, Brand, BrandLight))

/** Navigation drawer opened from the hamburger button: views, projects, labels, settings, log out. */
@Composable
fun AppDrawerContent(
    filterType: TaskFilterType,
    selectedProjectId: String?,
    selectedLabelId: String?,
    projects: List<ProjectEntity>,
    labels: List<LabelEntity>,
    syncStatus: SyncStatus,
    pendingOps: Int,
    isLive: Boolean,
    onSelectFilter: (TaskFilterType) -> Unit,
    onSelectProject: (ProjectEntity) -> Unit,
    onEditProject: (ProjectEntity) -> Unit,
    onAddProject: () -> Unit,
    onSelectLabel: (LabelEntity) -> Unit,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    appLockEnabled: Boolean,
    appLockAvailable: Boolean,
    onAppLockChange: (Boolean) -> Unit,
    onLogout: () -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BrandGradient)
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                    Text("TodoFlow", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    SyncPill(syncStatus, pendingOps, isLive)
                }
            }

            Spacer(Modifier.height(12.dp))

            val isView = { type: TaskFilterType -> filterType == type }
            DrawerRow("All tasks", Icons.AutoMirrored.Filled.List, isView(TaskFilterType.ALL)) { onSelectFilter(TaskFilterType.ALL) }
            DrawerRow("Inbox", Icons.Default.Inbox, isView(TaskFilterType.INBOX)) { onSelectFilter(TaskFilterType.INBOX) }
            DrawerRow("Today", Icons.Default.Today, isView(TaskFilterType.TODAY)) { onSelectFilter(TaskFilterType.TODAY) }
            DrawerRow("Upcoming", Icons.Default.CalendarMonth, isView(TaskFilterType.UPCOMING)) { onSelectFilter(TaskFilterType.UPCOMING) }
            DrawerRow("Completed", Icons.Default.CheckCircle, isView(TaskFilterType.COMPLETED)) { onSelectFilter(TaskFilterType.COMPLETED) }

            SectionHeader("Projects", action = { IconButton(onClick = onAddProject) { Icon(Icons.Default.Add, contentDescription = "Add Project") } })
            if (projects.isEmpty()) {
                Text(
                    "No projects yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp)
                )
            }
            projects.forEach { project ->
                val selected = filterType == TaskFilterType.PROJECT && selectedProjectId == project.id
                DrawerRow(
                    label = project.name,
                    selected = selected,
                    leading = { ColorDot(parseHexColor(project.color), 12.dp) },
                    trailing = {
                        IconButton(onClick = { onEditProject(project) }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit ${project.name}",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = { onSelectProject(project) }
                )
            }

            if (labels.isNotEmpty()) {
                SectionHeader("Labels")
                labels.forEach { label ->
                    val selected = filterType == TaskFilterType.LABEL && selectedLabelId == label.id
                    DrawerRow(
                        label = "#" + label.name,
                        selected = selected,
                        leading = { ColorDot(parseHexColor(label.color), 12.dp) },
                        onClick = { onSelectLabel(label) }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            SectionHeader("Settings")
            DrawerSettings(
                themeMode = themeMode,
                onThemeChange = onThemeChange,
                appLockEnabled = appLockEnabled,
                appLockAvailable = appLockAvailable,
                onAppLockChange = onAppLockChange,
            )
            Spacer(Modifier.height(8.dp))
            DrawerRow("Log out", Icons.AutoMirrored.Filled.ExitToApp, false, tint = MaterialTheme.colorScheme.error) { onLogout() }
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 16.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )
        if (action != null) action() else Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun DrawerRow(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit,
) = DrawerRow(
    label = label,
    selected = selected,
    leading = {
        Icon(
            icon,
            contentDescription = null,
            tint = if (tint != Color.Unspecified) tint
            else if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    },
    labelColor = tint,
    onClick = onClick
)

@Composable
private fun DrawerRow(
    label: String,
    selected: Boolean,
    leading: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    labelColor: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = if (trailing != null) 4.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) { leading() }
        Text(
            label,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = when {
                labelColor != Color.Unspecified -> labelColor
                selected -> colors.onPrimaryContainer
                else -> colors.onSurface
            }
        )
        trailing?.invoke()
    }
}

@Composable
private fun ColorDot(color: Color, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun SyncPill(status: SyncStatus, pending: Int, live: Boolean) {
    val label = when {
        status == SyncStatus.SYNCING -> "Syncing…"
        status == SyncStatus.ERROR -> "Sync failed"
        status == SyncStatus.OFFLINE && pending > 0 -> "Offline · $pending pending"
        status == SyncStatus.OFFLINE -> "Offline"
        pending > 0 -> "$pending pending"
        live -> "Live"
        else -> "Synced"
    }
    val dot = when {
        status == SyncStatus.ERROR -> Color(0xFFFF8A80)
        status == SyncStatus.OFFLINE || pending > 0 -> Color(0xFFFFD54F)
        status == SyncStatus.SYNCING -> Color.White
        else -> Color(0xFF69F0AE)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ColorDot(dot, 8.dp)
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Bottom navigation with a raised, gradient "add task" button in the middle:
 * Home · Today · (+) · Upcoming · Done.
 */
@Composable
fun AppBottomBar(
    filterType: TaskFilterType,
    onSelectFilter: (TaskFilterType) -> Unit,
    onAddTask: () -> Unit,
    addEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), clip = false)
                .background(colors.surface, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BarItem("Home", Icons.Outlined.Home, Icons.Filled.Home, filterType == TaskFilterType.ALL, Modifier.weight(1f)) { onSelectFilter(TaskFilterType.ALL) }
                BarItem("Today", Icons.Outlined.Today, Icons.Filled.Today, filterType == TaskFilterType.TODAY, Modifier.weight(1f)) { onSelectFilter(TaskFilterType.TODAY) }
                Spacer(Modifier.weight(1f)) // room for the centre button
                BarItem("Upcoming", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth, filterType == TaskFilterType.UPCOMING, Modifier.weight(1f)) { onSelectFilter(TaskFilterType.UPCOMING) }
                BarItem("Done", Icons.Outlined.CheckCircle, Icons.Filled.CheckCircle, filterType == TaskFilterType.COMPLETED, Modifier.weight(1f)) { onSelectFilter(TaskFilterType.COMPLETED) }
            }
        }
        if (addEnabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-22).dp)
                    .size(60.dp)
                    .shadow(10.dp, CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(BrandGradient)
                    .clickable(onClick = onAddTask)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Add Task"
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }
    }
}

@Composable
private fun BarItem(
    label: String,
    icon: ImageVector,
    selectedIcon: ImageVector,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (selected) colors.primaryContainer else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = if (selected) selectedIcon else icon,
                contentDescription = null,
                tint = if (selected) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** App settings shown inside the navigation drawer: theme and app lock. */
@Composable
fun DrawerSettings(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    appLockEnabled: Boolean,
    appLockAvailable: Boolean,
    onAppLockChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark").forEach { (mode, label) ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { onThemeChange(mode) },
                        label = { Text(label) }
                    )
                }
            }
        }
        SettingSwitch(
            title = "App lock",
            description = if (appLockAvailable) {
                "Require fingerprint, face or screen lock to open the app"
            } else {
                "Set up a screen lock or fingerprint on this device to use this"
            },
            checked = appLockEnabled,
            enabled = appLockAvailable || appLockEnabled,
            onCheckedChange = onAppLockChange
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
