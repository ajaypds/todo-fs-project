package com.example.todoflow.ui.tasks

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Swipe right to complete (or reopen) a task, swipe left to delete it. Completing slides back;
 * deleting hands off to [onDelete], which is expected to offer an undo. Each swipe fires its
 * action exactly once.
 */
@Composable
fun SwipeableTaskRow(
    enabled: Boolean,
    completed: Boolean,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    resetKey: Any = Unit,
    content: @Composable () -> Unit
) {
    // Material keeps the swipe state with rememberSaveable and LazyColumn restores it for an item
    // that left and re-entered the list (e.g. after "Undo"), which would bring the row back still
    // swiped away. Changing [resetKey] starts a fresh state.
    key(resetKey) {
        SwipeableTaskRowContent(enabled, completed, onToggleComplete, onDelete, modifier, content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTaskRowContent(
    enabled: Boolean,
    completed: Boolean,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    val currentToggle by rememberUpdatedState(onToggleComplete)
    val currentDelete by rememberUpdatedState(onDelete)
    var lastActionAt by remember { mutableStateOf<Long?>(null) }

    // Material may call confirmValueChange several times for one gesture, so each action is
    // debounced: one swipe = one action. Returning false makes the row slide back by itself.
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            val now = SystemClock.elapsedRealtime()
            val previous = lastActionAt
            val isNewGesture = previous == null || now - previous > ACTION_DEBOUNCE_MS
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (isNewGesture) {
                        lastActionAt = now
                        currentToggle()
                    }
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    if (isNewGesture) {
                        lastActionAt = now
                        currentDelete()
                    }
                    true
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = enabled,
        enableDismissFromEndToStart = enabled,
        backgroundContent = {
            val direction = state.dismissDirection
            val color = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Color(0xFF2E7D32)
                SwipeToDismissBoxValue.EndToStart -> Color(0xFFC62828)
                SwipeToDismissBoxValue.Settled -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                }
            ) {
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Icon(
                        imageVector = if (completed) Icons.Default.Replay else Icons.Default.Check,
                        contentDescription = if (completed) "Reopen" else "Complete",
                        tint = Color.White
                    )
                    SwipeToDismissBoxValue.EndToStart -> Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.White
                    )
                    SwipeToDismissBoxValue.Settled -> Unit
                }
            }
        }
    ) {
        content()
    }
}

private const val ACTION_DEBOUNCE_MS = 800L
