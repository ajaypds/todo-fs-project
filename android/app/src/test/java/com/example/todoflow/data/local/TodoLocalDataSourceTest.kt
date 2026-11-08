package com.example.todoflow.data.local

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TodoLocalDataSourceTest {

    private lateinit var dbHelper: TodoDbHelper
    private lateinit var local: TodoLocalDataSource

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(TodoDbHelper.DATABASE_NAME)
        dbHelper = TodoDbHelper(context)
        local = TodoLocalDataSource(dbHelper)
    }

    @After
    fun tearDown() = dbHelper.close()

    private fun due(offsetDays: Long) = LocalDate.now().plusDays(offsetDays).toString() + "T23:59:59Z"

    private fun titles(filter: TaskFilterType, label: String? = null) =
        runBlocking { local.getTasks(filter, labelId = label) }.map { it.title }.toSet()

    @Test
    fun todayShowsDueTodayAndOverdueButNotFinishedOrFutureTasks() = runBlocking {
        local.upsertTasks(
            listOf(
                TaskEntity("1", "overdue", dueDate = due(-3)),
                TaskEntity("2", "today", dueDate = due(0)),
                TaskEntity("3", "tomorrow", dueDate = due(1)),
                TaskEntity("4", "no date"),
                TaskEntity("5", "done today", dueDate = due(0), completed = true)
            )
        )
        assertEquals(setOf("overdue", "today"), titles(TaskFilterType.TODAY))
    }

    @Test
    fun upcomingShowsOnlyFutureUnfinishedTasks() = runBlocking {
        local.upsertTasks(
            listOf(
                TaskEntity("1", "today", dueDate = due(0)),
                TaskEntity("2", "tomorrow", dueDate = due(1)),
                TaskEntity("3", "next week", dueDate = due(7)),
                TaskEntity("4", "future but done", dueDate = due(2), completed = true)
            )
        )
        assertEquals(setOf("tomorrow", "next week"), titles(TaskFilterType.UPCOMING))
    }

    @Test
    fun labelsAreStoredWithTasksAndCanBeFilteredBy() = runBlocking {
        val urgent = LabelEntity("l1", "urgent", "#ef4444")
        val home = LabelEntity("l2", "home", "#22c55e")
        local.upsertTasks(
            listOf(
                TaskEntity("1", "pay bill", labels = listOf(urgent)),
                TaskEntity("2", "clean", labels = listOf(home, urgent)),
                TaskEntity("3", "nothing")
            )
        )
        assertEquals(setOf("pay bill", "clean"), titles(TaskFilterType.LABEL, "l1"))
        assertEquals(setOf("clean"), titles(TaskFilterType.LABEL, "l2"))
        val clean = local.getTasks(TaskFilterType.ALL).first { it.title == "clean" }
        assertEquals(setOf("home", "urgent"), clean.labels.map { it.name }.toSet())
    }

    @Test
    fun serverDataNeverOverwritesALocalChangeThatIsStillWaitingToUpload() = runBlocking {
        local.upsertTask(TaskEntity("t1", "my local edit", isSynced = false))
        local.upsertTasks(listOf(TaskEntity("t1", "older server title", isSynced = true)))

        val task = local.getTasks(TaskFilterType.ALL).single()
        assertEquals("my local edit", task.title)
        assertFalse(task.isSynced)
    }

    @Test
    fun serverDataUpdatesTasksThatAreInSync() = runBlocking {
        local.upsertTasks(listOf(TaskEntity("t1", "old", isSynced = true)))
        local.upsertTasks(listOf(TaskEntity("t1", "new", isSynced = true)))
        assertEquals("new", local.getTasks(TaskFilterType.ALL).single().title)
    }

    @Test
    fun subtasksRemovedOnTheServerDisappearFromSyncedTasks() = runBlocking {
        val sub = SubtaskEntity("s1", "t1", "step")
        local.upsertTasks(listOf(TaskEntity("t1", "task", subtasks = listOf(sub))))
        assertEquals(1, local.getTasks(TaskFilterType.ALL).single().subtasks.size)

        local.upsertTasks(listOf(TaskEntity("t1", "task", subtasks = emptyList())))
        assertTrue(local.getTasks(TaskFilterType.ALL).single().subtasks.isEmpty())
    }

    @Test
    fun pruneKeepsUnsyncedTasksAndTemporaryProjects() = runBlocking {
        local.upsertTasks(listOf(TaskEntity("keep", "on server"), TaskEntity("gone", "deleted elsewhere")))
        local.upsertTask(TaskEntity("temp_1", "not uploaded yet", isSynced = false))
        local.upsertProjects(listOf(ProjectEntity("p1", "Real"), ProjectEntity("p2", "Deleted elsewhere")))
        local.upsertProject(ProjectEntity("temp_p_1", "Not uploaded yet"))

        local.pruneSyncedTasksNotIn(setOf("keep"))
        local.pruneProjectsNotIn(setOf("p1"))

        assertEquals(setOf("keep", "temp_1"), local.getTasks(TaskFilterType.ALL).map { it.id }.toSet())
        assertEquals(setOf("p1", "temp_p_1"), local.getProjects().map { it.id }.toSet())
    }

    @Test
    fun remappingRewritesQueuedOperationsAndCachedRows() = runBlocking {
        local.upsertProject(ProjectEntity("temp_p_1", "Trip"))
        local.upsertTask(TaskEntity("t1", "Book", projectId = "temp_p_1"))
        local.enqueueOp("CREATE_TASK", "temp_9", null, """{"projectId":"temp_p_1"}""")
        local.enqueueOp("UPDATE_SUBTASK", "temp_sub_1", "temp_9", "{}")

        local.remapPendingIds("temp_p_1", "proj-77")
        local.remapPendingIds("temp_9", "task-5")

        val ops = local.getPendingOps()
        assertEquals("""{"projectId":"proj-77"}""", ops[0].payload)
        assertEquals("task-5", ops[0].entityId)
        assertEquals("task-5", ops[1].parentId)
        assertEquals("proj-77", local.getTasks(TaskFilterType.ALL).single().projectId)
    }

    @Test
    fun pendingOpsAreReportedPerEntityAndCanBeRemoved() = runBlocking {
        val first = local.enqueueOp("UPDATE_TASK", "t1", null, "{}")
        local.enqueueOp("CREATE_SUBTASK", "temp_sub", "t1", "{}")

        assertTrue(local.hasPendingOps("t1"))
        assertTrue(local.hasPendingOps("t1", exceptOpId = first)) // the subtask op still refers to it
        assertFalse(local.hasPendingOps("other"))

        local.removeOpsFor("t1")
        assertTrue(local.getPendingOps().isEmpty())
    }

    @Test
    fun clearAllWipesCachedDataAndTheQueue() = runBlocking {
        local.upsertTask(TaskEntity("t1", "x", labels = listOf(LabelEntity("l1", "a"))))
        local.enqueueOp("DELETE_TASK", "t1", null, "{}")
        local.clearAll()

        assertTrue(local.getTasks(TaskFilterType.ALL).isEmpty())
        assertTrue(local.getPendingOps().isEmpty())
    }
}
