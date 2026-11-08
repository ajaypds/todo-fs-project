package com.example.todoflow.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.todoflow.data.local.TaskEntity
import com.example.todoflow.data.model.CommentDto
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Comments for a task: list, add, and delete your own. Needs a connection (not cached offline). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(
    task: TaskEntity,
    viewModel: TasksViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var comments by remember { mutableStateOf<List<CommentDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    val notUploadedYet = task.id.startsWith("temp_")

    suspend fun load() {
        isLoading = true
        viewModel.loadComments(task.id)
            .onSuccess {
                comments = it
                error = null
            }
            .onFailure { error = "Couldn't load comments. Check your connection." }
        isLoading = false
    }

    LaunchedEffect(task.id) {
        if (notUploadedYet) isLoading = false else load()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Comments", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                task.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )

            when {
                notUploadedYet -> Text(
                    "Comments are available once this task has synced.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                isLoading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                }
                error != null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                comments.isEmpty() -> Text("No comments yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(comments, key = { it.id }) { comment ->
                        val mine = comment.userEmail != null &&
                            comment.userEmail.equals(viewModel.currentUserEmail, ignoreCase = true)
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        comment.userName ?: comment.userEmail ?: "Someone",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        formatCommentTime(comment.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (mine) {
                                    IconButton(onClick = {
                                        scope.launch {
                                            viewModel.deleteComment(task.id, comment.id)
                                                .onSuccess { comments = comments.filterNot { it.id == comment.id } }
                                                .onFailure { error = "Couldn't delete the comment." }
                                        }
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete comment", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            Text(comment.content, style = MaterialTheme.typography.bodyMedium)
                            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }

            if (!notUploadedYet) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text("Add a comment") },
                        maxLines = 3,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        enabled = draft.isNotBlank() && !isSending,
                        onClick = {
                            isSending = true
                            scope.launch {
                                viewModel.addComment(task.id, draft)
                                    .onSuccess {
                                        comments = comments + it
                                        draft = ""
                                        error = null
                                    }
                                    .onFailure { error = "Couldn't post the comment. Check your connection." }
                                isSending = false
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

private val commentTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")

private fun formatCommentTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        commentTimeFormat.format(Instant.parse(iso).atZone(ZoneId.systemDefault()))
    } catch (_: Exception) {
        iso.substringBefore("T")
    }
}
