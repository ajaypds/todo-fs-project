package com.example.todoflow.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.todoflow.ai.AssistantAction
import com.example.todoflow.ai.AssistantMessage
import com.example.todoflow.ai.AssistantViewModel
import com.example.todoflow.ai.ChatMessage
import com.example.todoflow.ai.ModelCatalog
import com.example.todoflow.ai.Proposal
import com.example.todoflow.ai.ProposalStatus
import com.example.todoflow.ai.RuntimeState
import com.example.todoflow.ai.UserMessage

private val SUGGESTIONS = listOf(
    "Add “buy milk” for tomorrow",
    "Create a project Home with three chores",
    "Make my first task high priority",
    "Delete the completed tasks"
)

/** Full-screen chat with the on-device model: describe what you want, review the changes, apply them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantDialog(
    viewModel: AssistantViewModel,
    onDismiss: () -> Unit,
    onOpenModels: () -> Unit,
    /** Closes the chat and shows the full task list, so the result of "Apply" is visible. */
    onShowTasks: () -> Unit = onDismiss
) {
    val messages by viewModel.messages.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val runtime by viewModel.runtimeState.collectAsState()
    val active by viewModel.activeModel.collectAsState()
    val installed by viewModel.installed.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val hasModel = active != null && installed.any { it.fileName == active }

    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1 + if (busy) 1 else 0)
    }

    Dialog(
        onDismissRequest = onDismiss,
        // The chat handles status bar, navigation bar and keyboard itself (one inset padding, no double counting)
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                    Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                        Text("AI assistant", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = statusLine(hasModel, active, runtime, busy),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.newChat() }, enabled = !busy && messages.isNotEmpty()) {
                        Icon(Icons.Default.AddComment, contentDescription = "New chat")
                    }
                    IconButton(onClick = onOpenModels) { Icon(Icons.Default.Memory, contentDescription = "Manage models") }
                }
                HorizontalDivider()

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (messages.isEmpty()) {
                        EmptyState(
                            hasModel = hasModel,
                            onChooseModel = onOpenModels,
                            onSuggestion = { input = it },
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
                        ) {
                            items(messages, key = { it.id }) { message ->
                                when (message) {
                                    is UserMessage -> UserBubble(message.text)
                                    is AssistantMessage -> AssistantBubble(
                                        message,
                                        onApply = { viewModel.apply(message.id) },
                                        onDiscard = { viewModel.discard(message.id) },
                                        onShowTasks = onShowTasks
                                    )
                                }
                            }
                            if (busy) {
                                item(key = "thinking") {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Text("Thinking…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                // Input
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val send = {
                        if (input.isNotBlank() && !busy) {
                            viewModel.send(input)
                            input = ""
                        }
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Tell me what to do…") },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = send,
                        enabled = input.isNotBlank() && !busy,
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                if (input.isNotBlank() && !busy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(26.dp)
                            )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (input.isNotBlank() && !busy) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun statusLine(hasModel: Boolean, active: String?, runtime: RuntimeState, busy: Boolean): String = when {
    !hasModel -> "No model installed"
    runtime is RuntimeState.Loading -> "Loading ${runtime.model}…"
    runtime is RuntimeState.Failed -> "Could not load the model: ${runtime.message}"
    runtime is RuntimeState.Ready -> "${runtime.model} · ${runtime.backend} · on this device"
    else -> "${ModelCatalog.displayName(active.orEmpty())} · on this device"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyState(hasModel: Boolean, onChooseModel: () -> Unit, onSuggestion: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Manage your tasks by chatting", style = MaterialTheme.typography.titleMedium)
        Text(
            "Create, edit and delete tasks and projects in plain words. The model runs on your phone: your messages never leave it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!hasModel) {
            Button(onClick = onChooseModel) { Text("Set up a model") }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SUGGESTIONS.forEach { s -> AssistChip(onClick = { onSuggestion(s) }, label = { Text(s) }) }
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
            modifier = Modifier.widthIn(max = 300.dp)
        ) { SelectionContainer { Text(text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) } }
    }
}

@Composable
private fun AssistantBubble(message: AssistantMessage, onApply: () -> Unit, onDiscard: () -> Unit, onShowTasks: () -> Unit) {
    var showDetails by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectionContainer { Text(message.text) }
                message.proposals.forEach { ProposalRow(it) }
                message.problems.forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                when (message.status) {
                    ProposalStatus.PENDING -> {
                        val destructive = message.proposals.any { it.destructive }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onApply,
                                colors = if (destructive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                            ) { Text(if (destructive) "Apply (includes delete)" else "Apply") }
                            TextButton(onClick = onDiscard) { Text("Discard") }
                        }
                    }
                    ProposalStatus.APPLYING -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Applying…", style = MaterialTheme.typography.bodySmall)
                    }
                    ProposalStatus.APPLIED -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val failed = message.resultNote?.contains("Could not apply") == true
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                if (failed) Icons.Default.Close else Icons.Default.Check,
                                contentDescription = null,
                                tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            SelectionContainer {
                                Text(
                                    message.resultNote.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        TextButton(onClick = onShowTasks) { Text("Show my tasks") }
                    }
                    ProposalStatus.DISCARDED -> Text("Discarded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ProposalStatus.NONE -> Unit
                }
                if (message.raw.isNotBlank()) {
                    TextButton(onClick = { showDetails = !showDetails }) {
                        Text(if (showDetails) "Hide model output" else "Show model output", style = MaterialTheme.typography.labelMedium)
                    }
                    if (showDetails) {
                        SelectionContainer {
                            Text(message.raw.trim(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProposalRow(p: Proposal) {
    val icon: ImageVector = when (p.action) {
        is AssistantAction.CreateTask, is AssistantAction.CreateProject, is AssistantAction.AddSubtasks -> Icons.Default.Add
        is AssistantAction.DeleteTask, is AssistantAction.DeleteProject -> Icons.Default.Delete
        is AssistantAction.CompleteTask -> Icons.Default.Check
        is AssistantAction.UpdateTask, is AssistantAction.UpdateProject -> Icons.Default.Edit
    }
    val tint = if (p.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(p.summary, style = MaterialTheme.typography.bodyMedium, fontWeight = if (p.destructive) FontWeight.SemiBold else FontWeight.Normal)
    }
}
