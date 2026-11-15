package com.example.todoflow.ai

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.todoflow.data.local.LabelEntity
import com.example.todoflow.data.local.ProjectEntity
import com.example.todoflow.data.local.SubtaskEntity
import com.example.todoflow.data.local.TaskEntity
import com.example.todoflow.data.local.TodoDbHelper
import com.example.todoflow.data.local.TodoLocalDataSource
import com.example.todoflow.data.remote.ApiProvider
import com.example.todoflow.data.repository.FakeTodoFlowApi
import com.example.todoflow.data.repository.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
@Config(sdk = [34], application = Application::class)
class QueryRunnerTest {

    private lateinit var db: TodoDbHelper
    private lateinit var local: TodoLocalDataSource
    private lateinit var runner: QueryRunner

    private val today = LocalDate.of(2026, 11, 14)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = TodoDbHelper(context, "queries-${java.util.UUID.randomUUID()}.db")
        local = TodoLocalDataSource(db)
        val repo = TaskRepository(local, ApiProvider { FakeTodoFlowApi() }, CoroutineScope(Job().apply { cancel() }))
        runner = QueryRunner(repo) { today }
        runBlocking {
            local.upsertProjects(listOf(ProjectEntity("p1", "Garden"), ProjectEntity("p2", "Work")))
            local.upsertTasks(listOf(
                TaskEntity("a", "Mow lawn", projectId = "p1", projectName = "Garden", dueDate = "2026-11-14T23:59:59Z"),
                TaskEntity("b", "Buy seeds", projectId = "p1", projectName = "Garden", dueDate = "2026-11-20T23:59:59Z", subtasks = listOf(SubtaskEntity("s1", "b", "Pick shop"), SubtaskEntity("s2", "b", "Pay", completed = true))),
                TaskEntity("c", "Inbox item"),
                TaskEntity("d", "Late report", projectId = "p2", projectName = "Work", dueDate = "2026-11-10T23:59:59Z"),
                TaskEntity("e", "Old done", completed = true, dueDate = "2026-11-01T23:59:59Z")
            ))
            local.upsertLabel(LabelEntity("l1", "urgent", "#f00"))
        }
    }

    @After
    fun tearDown() = db.close()

    private fun run(vararg q: AssistantQuery) = runBlocking { runner.run(q.toList()) }

    @Test
    fun listsProjectsWithTheirTaskCounts() {
        assertEquals("list_projects: Garden (2 tasks); Work (1 task)", run(ListProjects))
    }

    @Test
    fun listsLabels() {
        assertEquals("list_labels: urgent", run(ListLabels))
    }

    @Test
    fun filtersTasksByProjectViewSearchAndStatus() {
        fun titles(q: ListTasks): List<String> = run(q).lines().drop(1).filter { it.startsWith("• ") }.map { it.removePrefix("• ").substringBefore(" |") }
        assertEquals(setOf("Mow lawn", "Buy seeds"), titles(ListTasks(project = "garden")).toSet())
        assertEquals(listOf("Mow lawn"), titles(ListTasks(view = "today")))
        assertEquals(listOf("Buy seeds"), titles(ListTasks(view = "upcoming")))
        assertEquals(listOf("Late report"), titles(ListTasks(view = "overdue")))   // the completed old one is not overdue
        assertEquals(setOf("Inbox item", "Old done"), titles(ListTasks(view = "inbox")).toSet()) // completed tasks without a project are in the inbox too
        assertEquals(listOf("Old done"), titles(ListTasks(completed = true)))
        assertEquals(listOf("Buy seeds"), titles(ListTasks(search = "SEEDS")))
        assertEquals("list_tasks: no tasks match.", run(ListTasks(search = "zzz")))
        assertEquals("list_tasks: there is no project named \"Nope\".", run(ListTasks(project = "Nope")))
    }

    @Test
    fun getTaskShowsDetailsForATitle() {
        val out = run(GetTask("buy seeds"))
        assertTrue(out.startsWith("get_task: Buy seeds"))
        assertTrue(out.contains("Subtasks: [ ] Pick shop; [x] Pay"))
        assertTrue(run(GetTask("Nonexistent")).contains("couldn't find a task called"))
    }

    @Test
    fun resultsAreShownToTheUserWithoutToolNames() {
        assertEquals("Projects:\n• Garden (2 tasks)\n• Work (1 task)", QueryRunner.forDisplay(run(ListProjects)))
        assertEquals("You have no labels.", QueryRunner.forDisplay("list_labels: there are no labels."))
        val shown = QueryRunner.forDisplay(run(ListTasks(project = "Garden")))
        assertTrue(shown.startsWith("Tasks (2):\n• "))
        assertFalse(shown.contains("["))
        assertTrue(shown.contains("Mow lawn · due 2026-11-14"))
        assertEquals("No tasks match.", QueryRunner.forDisplay("list_tasks: no tasks match."))
    }

    @Test
    fun recognisesQuestionsAboutTheUsersData() {
        listOf("list projects", "list tasks in inbox", "what is due today?", "How many tasks do I have", "show me my overdue tasks", "any tasks tomorrow")
            .forEach { assertTrue(it, DataQuestions.looksLikeDataQuestion(it)) }
        listOf("add buy milk", "delete the Test project with its tasks", "move Task 1 to Test", "create a project and add some tasks", "hello", "mark task A as done")
            .forEach { assertFalse(it, DataQuestions.looksLikeDataQuestion(it)) }
    }

    @Test
    fun dailyBriefingGroupsOpenTasksByDueDate() {
        val out = run(DailyBriefing)
        assertTrue(out.startsWith("daily_briefing: 4 open tasks."))
        assertTrue(out.contains("Overdue (1):"))
        assertTrue(out.contains("Late report"))
        assertTrue(out.contains("Due today (1):"))
        assertTrue(out.contains("Due in the next 7 days (1):"))
        assertFalse(out.contains("Old done"))
    }
}
