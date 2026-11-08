package com.example.todoflow.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.todoflow.data.local.LabelEntity
import com.example.todoflow.data.local.ProjectEntity
import com.example.todoflow.data.local.TaskEntity
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset

private val LABEL_COLORS = listOf(
    "#6366f1", "#ef4444", "#f97316", "#eab308", "#22c55e", "#06b6d4", "#3b82f6", "#a855f7", "#ec4899"
)

/** Due dates are stored as "yyyy-MM-dd" plus an end-of-day marker, matching the quick options. */
internal fun dueDateFor(date: LocalDate): String = date.toString() + "T23:59:59Z"

/**
 * Create a new task ([task] == null) or edit an existing one: title, description, priority,
 * due date (quick options or a date picker), project and labels.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorDialog(
    task: TaskEntity?,
    projects: List<ProjectEntity>,
    labels: List<LabelEntity>,
    defaultProjectId: String?,
    onDismiss: () -> Unit,
    onCreateLabel: suspend (name: String, color: String) -> LabelEntity?,
    onSave: (
        title: String,
        description: String?,
        priority: Int,
        dueDate: String?,
        projectId: String?,
        projectName: String?,
        labels: List<LabelEntity>
    ) -> Unit
) {
    val isEditing = task != null
    var title by remember { mutableStateOf(task?.title.orEmpty()) }
    var description by remember { mutableStateOf(task?.description.orEmpty()) }
    var priority by remember { mutableIntStateOf(task?.priority ?: 0) }
    var chosenProjectId by remember { mutableStateOf(if (isEditing) task?.projectId else defaultProjectId) }
    var dueDate by remember { mutableStateOf(task?.dueDate) }
    var isProjectDropdownExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showNewLabel by remember { mutableStateOf(false) }

    // Labels created from this dialog are kept locally so they show up immediately
    var extraLabels by remember { mutableStateOf<List<LabelEntity>>(emptyList()) }
    var selectedLabelIds by remember { mutableStateOf(task?.labels?.map { it.id }?.toSet() ?: emptySet()) }
    val allLabels = (labels + extraLabels).distinctBy { it.id }

    val chosenProject = projects.firstOrNull { it.id == chosenProjectId }
    val dueDatePart = dueDate?.substringBefore("T")
    val today = LocalDate.now()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Task" else "New Task", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("What needs to be done?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Priority", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val priorities = listOf(
                        Triple(0, "None", MaterialTheme.colorScheme.outline),
                        Triple(3, "P3", Color(0xFF2196F3)),
                        Triple(2, "P2", Color(0xFFFF9800)),
                        Triple(1, "P1", Color(0xFFF44336))
                    )
                    priorities.forEach { (p, label, color) ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(label) },
                            leadingIcon = if (p > 0) {
                                {
                                    Icon(
                                        Icons.Default.Flag,
                                        contentDescription = null,
                                        tint = color,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null
                        )
                    }
                }

                Text("Due Date", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = dueDate == null,
                        onClick = { dueDate = null },
                        label = { Text("No date") }
                    )
                    FilterChip(
                        selected = dueDatePart == today.toString(),
                        onClick = { dueDate = dueDateFor(today) },
                        label = { Text("Today") }
                    )
                    FilterChip(
                        selected = dueDatePart == today.plusDays(1).toString(),
                        onClick = { dueDate = dueDateFor(today.plusDays(1)) },
                        label = { Text("Tomorrow") }
                    )
                    val isCustom = dueDatePart != null &&
                        dueDatePart != today.toString() && dueDatePart != today.plusDays(1).toString()
                    FilterChip(
                        selected = isCustom,
                        onClick = { showDatePicker = true },
                        label = { Text(if (isCustom) formatDueDate(dueDate.orEmpty()) else "Pick date") },
                        leadingIcon = {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                }

                if (projects.isNotEmpty()) {
                    Text("Project", style = MaterialTheme.typography.labelMedium)
                    ExposedDropdownMenuBox(
                        expanded = isProjectDropdownExpanded,
                        onExpandedChange = { isProjectDropdownExpanded = !isProjectDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = chosenProject?.name ?: "Inbox (No Project)",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isProjectDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = isProjectDropdownExpanded,
                            onDismissRequest = { isProjectDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Inbox (No Project)") },
                                onClick = {
                                    chosenProjectId = null
                                    isProjectDropdownExpanded = false
                                }
                            )
                            projects.forEach { proj ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(parseHexColor(proj.color))
                                            )
                                            Text(proj.name)
                                        }
                                    },
                                    onClick = {
                                        chosenProjectId = proj.id
                                        isProjectDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Text("Labels", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    allLabels.forEach { label ->
                        val selected = label.id in selectedLabelIds
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedLabelIds = if (selected) selectedLabelIds - label.id else selectedLabelIds + label.id
                            },
                            label = { Text(label.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                    AssistChip(
                        onClick = { showNewLabel = true },
                        label = { Text("New label") },
                        leadingIcon = {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(
                            title.trim(),
                            description.trim().ifEmpty { null },
                            priority,
                            dueDate,
                            chosenProjectId,
                            chosenProject?.name,
                            allLabels.filter { it.id in selectedLabelIds }
                        )
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text(if (isEditing) "Save" else "Add Task")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDatePicker) {
        val initialMillis = try {
            dueDatePart?.let { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        } catch (_: Exception) {
            null
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val picked = java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        dueDate = dueDateFor(picked)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showNewLabel) {
        NewLabelDialog(
            onDismiss = { showNewLabel = false },
            onCreate = { name, color ->
                onCreateLabel(name, color)?.let { created ->
                    extraLabels = extraLabels + created
                    selectedLabelIds = selectedLabelIds + created.id
                    showNewLabel = false
                }
            }
        )
    }
}

@Composable
private fun NewLabelDialog(
    onDismiss: () -> Unit,
    onCreate: suspend (name: String, color: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(LABEL_COLORS.first()) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("New Label") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Label name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LABEL_COLORS.forEach { hex ->
                        val swatch = parseHexColor(hex)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(
                                    width = if (hex == color) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape
                                )
                                .clickable { color = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && !isSaving,
                onClick = {
                    isSaving = true
                    scope.launch {
                        onCreate(name.trim(), color)
                        isSaving = false
                    }
                }
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
        }
    )
}

/** Small colored chip used to show a label on a task row. */
@Composable
fun LabelChip(label: LabelEntity, modifier: Modifier = Modifier) {
    val color = parseHexColor(label.color)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "#" + label.name,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
