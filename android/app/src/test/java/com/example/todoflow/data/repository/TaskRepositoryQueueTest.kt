package com.example.todoflow.data.repository

import androidx.test.core.app.ApplicationProvider
import com.example.todoflow.data.local.LabelEntity
import com.example.todoflow.data.local.TaskEntity
import com.example.todoflow.data.local.TaskFilterType
import com.example.todoflow.data.local.TodoDbHelper
import com.example.todoflow.data.local.TodoLocalDataSource
import com.example.todoflow.data.model.TaskDto
import com.example.todoflow.data.remote.ApiProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Behaviour of the offline queue against real SQLite (Robolectric) and a fake server. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskRepositoryQueueTest {

    private lateinit var dbName: String
    private lateinit var dbHelper: TodoDbHelper
    private lateinit var local: TodoLocalDataSource
    private lateinit var api: FakeTodoFlowApi
    private lateinit var repo: TaskRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        dbName = "queue-test-${java.util.UUID.randomUUID()}.db" // own file: tests can't affect each other
        dbHelper = TodoDbHelper(context, dbName)
        local = TodoLocalDataSource(dbHelper)
        api = FakeTodoFlowApi()
        // A cancelled scope: queued work is never auto-flushed, so each test flushes explicitly
        val deadScope = CoroutineScope(Job().apply { cancel() })
        repo = TaskRepository(local, ApiProvider { api }, deadScope)
    }

    @After
    fun tearDown() {
        dbHelper.close()
    }

    private fun tasks() = runBlocking { local.getTasks(TaskFilterType.ALL) }
    private fun pending() = runBlocking { local.getPendingOps() }

    @Test
    fun offlineCreateIsShownImmediatelyThenUploadedAndTheTempIdIsReplaced() = runBlocking {
        api.offline = true
        repo.createTask(title = "Buy milk", priority = 2)

        val shown = tasks().single()
        assertTrue(shown.id.startsWith("temp_"))
        assertFalse(shown.isSynced)
        assertEquals(1, repo.observePendingOpCount().first())

        // Still offline: nothing is lost, the change stays queued
        val stuck = repo.flushPendingOps()
        assertEquals(1, stuck.remaining)
        assertEquals(1, pending().size)

        api.offline = false
        val done = repo.flushPendingOps()
        assertEquals(0, done.remaining)
        assertEquals(0, repo.observePendingOpCount().first())

        val synced = tasks().single()
        assertEquals(api.serverTasks.single().id, synced.id)
        assertTrue(synced.isSynced)
        assertEquals("Buy milk", api.createdTasks.single().title)
        assertEquals(2, api.createdTasks.single().priority)
    }

    @Test
    fun changesMadeToAnOfflineCreatedTaskFollowItToTheRealId() = runBlocking {
        api.offline = true
        val temp = repo.createTask(title = "Draft report").getOrThrow()
        repo.toggleTaskCompleted(temp.id, completed = false)

        api.offline = false
        assertEquals(0, repo.flushPendingOps().remaining)

        val realId = api.serverTasks.single().id
        assertEquals(realId, api.updates.single().first)
        assertEquals(true, api.updates.single().second.completed)
        assertTrue(tasks().single { it.id == realId }.completed)
    }

    @Test
    fun deletingATaskThatWasNeverUploadedCancelsItsQueuedCreate() = runBlocking {
        api.offline = true
        val temp = repo.createTask(title = "Oops").getOrThrow()
        repo.deleteTask(temp.id)

        assertTrue(pending().isEmpty())
        assertTrue(tasks().isEmpty())
        api.offline = false
        repo.flushPendingOps()
        assertTrue(api.createdTasks.isEmpty())
        assertTrue(api.deletedTasks.isEmpty())
    }

    @Test
    fun aTemporaryProjectIdInAQueuedTaskIsRewrittenToTheRealProjectId() = runBlocking {
        api.offline = true
        val project = repo.createProject("Trip", "#ff0000").getOrThrow()
        repo.createTask(title = "Book tickets", projectId = project.id, projectName = project.name)

        api.offline = false
        assertEquals(0, repo.flushPendingOps().remaining)

        val realProject = api.serverProjects.single()
        assertEquals(realProject.id, api.createdTasks.single().projectId)
        assertEquals(realProject.id, tasks().single().projectId)
    }

    @Test
    fun serverErrorsKeepTheChangeQueuedForLater() = runBlocking {
        val dto = TaskDto(id = "t1", title = "Existing")
        api.serverTasks += dto
        repo.refresh()
        api.httpFailures["updateTask"] = 503
        repo.toggleTaskCompleted("t1", completed = false)

        val result = repo.flushPendingOps()
        assertEquals(1, result.remaining)
        assertEquals(0, result.dropped)
        assertEquals(1, pending().single().attempts)

        api.httpFailures.clear()
        assertEquals(0, repo.flushPendingOps().remaining)
        assertEquals(true, api.updates.single().second.completed)
    }

    @Test
    fun aChangeTheServerRejectsIsDiscardedWithANotice() = runBlocking {
        api.serverTasks += TaskDto(id = "t1", title = "Existing")
        repo.refresh()
        api.httpFailures["updateTask"] = 400
        repo.toggleTaskCompleted("t1", completed = false)

        val notice = async(start = CoroutineStart.UNDISPATCHED) { repo.syncEvents.first() }
        val result = repo.flushPendingOps()

        assertEquals(0, result.remaining)
        assertEquals(1, result.dropped)
        assertTrue(notice.await().contains("rejected"))
        assertTrue(tasks().single().isSynced) // no longer waiting on anything
    }

    @Test
    fun refreshDoesNotBringBackATaskWhoseDeleteIsStillWaiting() = runBlocking {
        api.serverTasks += TaskDto(id = "t1", title = "Doomed")
        api.serverTasks += TaskDto(id = "t2", title = "Keeper")
        repo.refresh()

        api.httpFailures["deleteTask"] = 503
        repo.deleteTask("t1")
        repo.refresh() // server still has t1

        assertEquals(listOf("t2"), tasks().map { it.id })
        assertEquals(1, pending().size)

        api.httpFailures.clear()
        repo.flushPendingOps()
        assertEquals(listOf("t1"), api.deletedTasks)
    }

    @Test
    fun refreshRemovesTasksDeletedElsewhereButKeepsUnsyncedLocalOnes() = runBlocking {
        api.serverTasks += TaskDto(id = "t1", title = "Stays")
        api.serverTasks += TaskDto(id = "t2", title = "Deleted on web")
        repo.refresh()
        assertEquals(setOf("t1", "t2"), tasks().map { it.id }.toSet())

        api.offline = true
        repo.createTask(title = "Created on the train") // unsynced
        api.offline = false
        api.serverTasks.removeAll { it.id == "t2" }
        api.httpFailures["createTask"] = 503 // keep it unsynced during the refresh
        repo.refresh()

        val titles = tasks().map { it.title }.toSet()
        assertEquals(setOf("Stays", "Created on the train"), titles)
    }

    @Test
    fun editingATaskSendsClearFlagsForFieldsTheUserRemoved() = runBlocking {
        api.serverTasks += TaskDto(id = "t1", title = "Plan", dueDate = "2026-11-20T23:59:59Z", projectId = "p1")
        api.serverProjects += com.example.todoflow.data.model.ProjectDto("p1", "Home", null)
        repo.refresh()
        val original = tasks().single()

        repo.updateTask(
            original = original,
            title = "Plan v2",
            description = null,
            priority = 0,
            dueDate = null,
            projectId = null,
            projectName = null,
            labels = emptyList()
        )
        assertEquals("Plan v2", tasks().single().title) // optimistic
        repo.flushPendingOps()

        val request = api.updates.single().second
        assertEquals("Plan v2", request.title)
        assertEquals(true, request.clearDueDate)
        assertEquals(true, request.clearProject)
        assertEquals(emptyList<String>(), request.labelIds)
        assertNull(api.serverTasks.single().dueDate)
    }

    @Test
    fun labelsSelectedInTheEditorAreSentAndShownOnTheTask() = runBlocking {
        val label = repo.run { api.serverLabels += com.example.todoflow.data.model.LabelDto("l1", "urgent", "#ef4444"); LabelEntity("l1", "urgent", "#ef4444") }
        repo.createTask(title = "Pay bill", labels = listOf(label))
        assertEquals(listOf("urgent"), tasks().single().labels.map { it.name })

        repo.flushPendingOps()
        assertEquals(listOf("l1"), api.createdTasks.single().labelIds)
        assertEquals(listOf("urgent"), tasks().single().labels.map { it.name })
    }

    @Test
    fun reorderIsQueuedAndSentWhenBackOnline() = runBlocking {
        api.serverTasks += TaskDto(id = "a", title = "A", position = 0)
        api.serverTasks += TaskDto(id = "b", title = "B", position = 1)
        repo.refresh()
        api.offline = true
        repo.reorderTasks(listOf("b", "a"))
        assertEquals(listOf("b", "a"), tasks().map { it.id })

        api.offline = false
        repo.flushPendingOps()
        assertEquals(listOf(listOf("b", "a")), api.reorders)
    }

    @Test
    fun queuedChangesSurviveReopeningTheDatabase() = runBlocking {
        api.offline = true
        repo.createTask(title = "Persist me")
        dbHelper.close()

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val reopenedHelper = TodoDbHelper(context, dbName)
        try {
            val reopened = TodoLocalDataSource(reopenedHelper)
            val ops = reopened.getPendingOps()
            assertEquals(1, ops.size)
            assertEquals("CREATE_TASK", ops.single().type)
            assertNotNull(reopened.getTasks(TaskFilterType.ALL).singleOrNull { it.title == "Persist me" })
        } finally {
            reopenedHelper.close()
        }
    }
}
