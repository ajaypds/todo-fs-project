package com.example.todoflow.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.todoflow.data.local.LabelEntity
import com.example.todoflow.data.local.SessionManager
import com.example.todoflow.data.local.TaskEntity
import com.example.todoflow.data.local.TaskFilterType
import com.example.todoflow.data.local.TodoDbHelper
import com.example.todoflow.data.local.TodoLocalDataSource
import com.example.todoflow.data.remote.ApiClient
import com.example.todoflow.data.remote.ApiProvider
import com.example.todoflow.data.remote.RealtimeClient
import com.example.todoflow.data.repository.FakeTodoFlowApi
import com.example.todoflow.data.repository.TaskRepository
import com.example.todoflow.data.local.ThemeMode
import com.example.todoflow.data.model.CommentDto
import com.example.todoflow.ui.auth.LockScreen
import com.example.todoflow.ui.tasks.CommentsSheet
import com.example.todoflow.ui.tasks.SettingsDialog
import com.example.todoflow.ui.tasks.SwipeableTaskRow
import com.example.todoflow.ui.tasks.TaskEditorDialog
import com.example.todoflow.ui.tasks.TaskItemRow
import com.example.todoflow.ui.tasks.TasksScreen
import com.example.todoflow.ui.tasks.TasksViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Renders the new screens with Robolectric to catch crashes that compiling can't, and checks the
 * main interactions (editing, swiping, undo).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TasksUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var dbHelper: TodoDbHelper
    private lateinit var local: TodoLocalDataSource

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        dbHelper = TodoDbHelper(context, "ui-test-${java.util.UUID.randomUUID()}.db") // own file per test
        local = TodoLocalDataSource(dbHelper)
    }

    @After
    fun tearDown() = dbHelper.close()

    private fun task(id: String, title: String, labels: List<LabelEntity> = emptyList(), comments: Int = 0) =
        TaskEntity(id = id, title = title, labels = labels, commentCount = comments)

    @Test
    fun taskEditorValidatesAndReturnsTheEnteredDetails() {
        var saved: List<Any?>? = null
        val urgent = LabelEntity("l1", "urgent", "#ef4444")
        compose.setContent {
            MaterialTheme {
                TaskEditorDialog(
                    task = null,
                    projects = emptyList(),
                    labels = listOf(urgent),
                    defaultProjectId = null,
                    onDismiss = {},
                    onCreateLabel = { _, _ -> null },
                    onSave = { title, desc, priority, due, projectId, projectName, labels ->
                        saved = listOf(title, desc, priority, due, projectId, projectName, labels.map { it.id })
                    }
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("New Task").assertIsDisplayed()
        compose.onNodeWithText("Add Task").assertIsNotEnabled()

        compose.onNodeWithText("What needs to be done?").performTextInput("Pay rent")
        compose.onNodeWithText("Add Task").assertIsEnabled()
        compose.onNodeWithText("urgent").performScrollTo().performClick()   // select the label
        compose.onNodeWithText("P1").performClick()       // high priority
        compose.onNodeWithText("Add Task").performClick()

        val result = saved
        assertTrue("onSave was not called", result != null)
        assertEquals("Pay rent", result!![0])
        assertEquals(1, result[2])
        assertEquals(listOf("l1"), result[6])
    }

    @Test
    fun editingShowsTheExistingValuesAndOffersSave() {
        compose.setContent {
            MaterialTheme {
                TaskEditorDialog(
                    task = TaskEntity(
                        id = "t1",
                        title = "Call mom",
                        description = "About the trip",
                        priority = 2,
                        dueDate = "2030-01-15T23:59:59Z"
                    ),
                    projects = emptyList(),
                    labels = emptyList(),
                    defaultProjectId = null,
                    onDismiss = {},
                    onCreateLabel = { _, _ -> null },
                    onSave = { _, _, _, _, _, _, _ -> }
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Edit Task").assertIsDisplayed()
        compose.onNodeWithText("Call mom").assertIsDisplayed()
        compose.onNodeWithText("Jan 15").assertExists() // custom due date chip
        compose.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun taskRowShowsLabelsAndCommentCount() {
        compose.setContent {
            MaterialTheme {
                TaskItemRow(
                    task = task("t1", "Plan trip", labels = listOf(LabelEntity("l1", "travel", "#06b6d4")), comments = 3),
                    projects = emptyList(),
                    onToggle = {},
                    onDelete = {}
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Plan trip").assertIsDisplayed()
        compose.onNodeWithText("#travel").assertIsDisplayed()
        compose.onNodeWithText("3").assertIsDisplayed()
    }

    private fun setSwipeContent(onToggle: () -> Unit, onDelete: () -> Unit) {
        compose.setContent {
            MaterialTheme {
                SwipeableTaskRow(
                    enabled = true,
                    completed = false,
                    onToggleComplete = onToggle,
                    onDelete = onDelete
                ) {
                    Text("swipe me", modifier = Modifier.fillMaxWidth().padding(48.dp))
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun swipingRightCompletesExactlyOnce() {
        var toggled = 0
        var deleted = 0
        setSwipeContent(onToggle = { toggled++ }, onDelete = { deleted++ })

        compose.onNodeWithText("swipe me").performTouchInput { swipeRight() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        assertEquals(1, toggled)
        assertEquals(0, deleted)
    }

    @Test
    fun swipingLeftDeletesExactlyOnce() {
        var toggled = 0
        var deleted = 0
        setSwipeContent(onToggle = { toggled++ }, onDelete = { deleted++ })

        compose.onNodeWithText("swipe me").performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        assertEquals(0, toggled)
        assertEquals(1, deleted)
    }

    @Test
    fun aRowCanBeSwipedRightAndThenLeft() {
        var toggled = 0
        var deleted = 0
        setSwipeContent(onToggle = { toggled++ }, onDelete = { deleted++ })

        compose.onNodeWithText("swipe me").performTouchInput { swipeRight() }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertEquals(1, toggled)

        // Robolectric's wall clock is frozen; let a couple of seconds pass like in real use
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(2))
        compose.onNodeWithText("swipe me").performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertEquals(1, toggled)
        assertEquals(1, deleted)
    }

    private fun buildViewModel(api: FakeTodoFlowApi): TasksViewModel {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val deadScope = CoroutineScope(Job().apply { cancel() }) // never auto-flush the queue
        val repository = TaskRepository(local, ApiProvider { api }, deadScope)
        val session = SessionManager(context)
        return TasksViewModel(repository, session, RealtimeClient(session, ApiClient(session)))
    }

    @Test
    fun taskScreenListsTasksAndSwipeDeleteOffersUndo() {
        runBlocking {
            local.upsertTasks(
                listOf(
                    task("t1", "Write report", labels = listOf(LabelEntity("l1", "work", "#3b82f6"))),
                    task("t2", "Buy groceries")
                )
            )
        }
        val api = FakeTodoFlowApi().apply { offline = true } // no server needed for this test
        val viewModel = buildViewModel(api)

        compose.setContent { MaterialTheme { TasksScreen(viewModel = viewModel) } }
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()

        compose.onNodeWithText("Write report").assertIsDisplayed()
        compose.onNodeWithText("Buy groceries").assertIsDisplayed()
        // The label shows up in the filter row and on the task itself
        compose.onAllNodesWithText("#work").assertCountEquals(2)

        // Swipe the first task away: it disappears and an Undo snackbar appears
        compose.onNodeWithTag("task_row_t1").performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithText("Write report").assertDoesNotExist()
        compose.onNodeWithText("Task deleted").assertExists()

        // Undo brings it back and nothing was queued for deletion
        compose.onNodeWithText("Undo").performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithText("Write report").assertIsDisplayed()
        assertTrue(runBlocking { local.getPendingOps() }.none { it.type == "DELETE_TASK" })
    }

    @Test
    fun bottomBarSwitchesViewsAndCentreButtonOpensTheEditor() {
        runBlocking { local.upsertTasks(listOf(task("t1", "Water plants"))) }
        val viewModel = buildViewModel(FakeTodoFlowApi().apply { offline = true })

        compose.setContent { MaterialTheme { TasksScreen(viewModel = viewModel) } }
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()

        compose.onAllNodesWithText("All tasks")[0].assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Completed")[0].assertIsDisplayed()

        compose.onNodeWithContentDescription("Add Task").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("New Task").assertIsDisplayed()
    }

    @Test
    fun hamburgerOpensTheDrawerWithViewsAndLogout() {
        val viewModel = buildViewModel(FakeTodoFlowApi().apply { offline = true })
        compose.setContent { MaterialTheme { TasksScreen(viewModel = viewModel) } }
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Menu").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNodeWithText("Inbox").assertIsDisplayed()
        compose.onNodeWithText("Log out").assertExists()
    }

    @Test
    fun taskScreenDeletesForRealWhenTheUndoWindowEnds() {
        runBlocking { local.upsertTasks(listOf(task("t1", "Temporary"), task("t2", "Stays"))) }
        val viewModel = buildViewModel(FakeTodoFlowApi().apply { offline = true })

        compose.setContent { MaterialTheme { TasksScreen(viewModel = viewModel) } }
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()

        compose.onNodeWithTag("task_row_t1").performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithText("Task deleted").assertExists()

        // Let the snackbar time out
        compose.mainClock.advanceTimeBy(12_000)
        compose.waitForIdle()
        Thread.sleep(300) // the delete itself runs on an IO thread
        compose.waitForIdle()

        val remaining = runBlocking { local.getTasks(TaskFilterType.ALL) }.map { it.title }
        assertEquals(listOf("Stays"), remaining)
        assertTrue(runBlocking { local.getPendingOps() }.any { it.type == "DELETE_TASK" && it.entityId == "t1" })
    }

    @Test
    fun settingsDialogOffersThemeRemindersAndAppLock() {
        var reminders: Boolean? = null
        var lock: Boolean? = null
        compose.setContent {
            MaterialTheme {
                SettingsDialog(
                    themeMode = ThemeMode.SYSTEM,
                    onThemeChange = {},
                    remindersEnabled = false,
                    onRemindersChange = { reminders = it },
                    appLockEnabled = false,
                    appLockAvailable = true,
                    onAppLockChange = { lock = it },
                    onDismiss = {}
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("Dark").assertExists()
        compose.onNodeWithText("App lock").assertExists()
        // Switches: reminders first, app lock second (scroll them into view in the small test window)
        compose.onAllNodes(isToggleable())[0].performScrollTo().performClick()
        compose.onAllNodes(isToggleable())[1].performScrollTo().performClick()
        assertEquals(true, reminders)
        assertEquals(true, lock)
    }

    @Test
    fun appLockSwitchIsDisabledWhenTheDeviceHasNoScreenLock() {
        compose.setContent {
            MaterialTheme {
                SettingsDialog(
                    themeMode = ThemeMode.LIGHT,
                    onThemeChange = {},
                    remindersEnabled = false,
                    onRemindersChange = {},
                    appLockEnabled = false,
                    appLockAvailable = false,
                    onAppLockChange = {},
                    onDismiss = {}
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Set up a screen lock", substring = true).assertExists()
        compose.onAllNodes(isToggleable())[1].assertIsNotEnabled()
    }

    @Test
    fun lockScreenOffersUnlock() {
        var unlocked = 0
        compose.setContent { MaterialTheme { LockScreen(onUnlock = { unlocked++ }) } }
        compose.waitForIdle()
        compose.onNodeWithText("TodoFlow is locked").assertIsDisplayed()
        compose.onNodeWithText("Unlock").performClick()
        assertEquals(1, unlocked)
    }

    @Test
    fun commentsSheetListsAddsAndShowsNewComments() {
        val api = FakeTodoFlowApi()
        api.serverComments += CommentDto(id = "c0", taskId = "t1", userName = "Asha", userEmail = "asha@example.com", content = "First!")
        val viewModel = buildViewModel(api)
        val target = task("t1", "Plan trip")

        compose.setContent { MaterialTheme { CommentsSheet(task = target, viewModel = viewModel, onDismiss = {}) } }
        compose.waitForIdle()
        Thread.sleep(300) // comments load on an IO thread
        compose.waitForIdle()

        compose.onNodeWithText("Comments").assertExists()
        compose.onNodeWithText("First!").assertExists()

        compose.onNodeWithText("Add a comment").performTextInput("Looks good")
        compose.onNodeWithContentDescription("Send").performClick()
        Thread.sleep(300)
        compose.waitForIdle()

        compose.onNodeWithText("Looks good").assertExists()
        assertEquals(listOf("First!", "Looks good"), api.serverComments.map { it.content })
    }

    @Test
    fun commentsAreNotAvailableForATaskThatHasNotSyncedYet() {
        val viewModel = buildViewModel(FakeTodoFlowApi())
        compose.setContent {
            MaterialTheme { CommentsSheet(task = task("temp_1", "Brand new"), viewModel = viewModel, onDismiss = {}) }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Comments are available once this task has synced.").assertExists()
    }
}
