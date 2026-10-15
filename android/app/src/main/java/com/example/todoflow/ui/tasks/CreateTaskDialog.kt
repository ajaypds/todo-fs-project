package com.example.todoflow.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.todoflow.data.local.ProjectEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskDialog(
    projects: List<ProjectEntity>,
    selectedProjectId: String? = null,
    onDismiss: () -> Unit,
    onCreate: (title: String, description: String?, priority: Int, dueDate: String?, projectId: String?, projectName: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableIntStateOf(0) }
    var chosenProjectId by remember { mutableStateOf(selectedProjectId) }
    var dueDateChoice by remember { mutableStateOf<String?>(null) } // null, "today", "tomorrow"
    var isProjectDropdownExpanded by remember { mutableStateOf(false) }

    val chosenProject = projects.firstOrNull { it.id == chosenProjectId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Task", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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

                // Priority Selection
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

                // Due Date Quick Options
                Text("Due Date", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = dueDateChoice == null,
                        onClick = { dueDateChoice = null },
                        label = { Text("No date") }
                    )
                    FilterChip(
                        selected = dueDateChoice == "today",
                        onClick = { dueDateChoice = "today" },
                        label = { Text("Today") }
                    )
                    FilterChip(
                        selected = dueDateChoice == "tomorrow",
                        onClick = { dueDateChoice = "tomorrow" },
                        label = { Text("Tomorrow") }
                    )
                }

                // Project Selector
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
                                            val color = parseHexColor(proj.color)
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val computedDueDate = when (dueDateChoice) {
                            "today" -> LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + "T23:59:59Z"
                            "tomorrow" -> LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE) + "T23:59:59Z"
                            else -> null
                        }
                        onCreate(
                            title.trim(),
                            description.trim().ifEmpty { null },
                            priority,
                            computedDueDate,
                            chosenProjectId,
                            chosenProject?.name
                        )
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("Add Task")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun parseHexColor(hex: String?): Color {
    if (hex.isNullOrBlank()) return Color(0xFF3B82F6)
    return try {
        val clean = hex.removePrefix("#")
        val colorInt = when (clean.length) {
            6 -> ("FF" + clean).toLong(16)
            8 -> clean.toLong(16)
            else -> 0xFF3B82F6
        }
        Color(colorInt)
    } catch (_: Exception) {
        Color(0xFF3B82F6)
    }
}
