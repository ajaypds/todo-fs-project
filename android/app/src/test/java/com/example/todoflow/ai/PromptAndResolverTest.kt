package com.example.todoflow.ai

import com.example.todoflow.data.local.ProjectEntity
import com.example.todoflow.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PromptAndResolverTest {

    private val today = LocalDate.of(2026, 11, 14) // a Saturday
    private val projects = listOf(ProjectEntity("p1", "Home"), ProjectEntity("p2", "Work"))
    private val tasks = listOf(
        TaskEntity("t-done", "Old thing", completed = true),
        TaskEntity("t-later", "Pay rent", dueDate = "2026-11-20T23:59:59Z", priority = 1, projectName = "Home"),
        TaskEntity("t-soon", "Call mum", dueDate = "2026-11-15T23:59:59Z"),
        TaskEntity("t-none", "Read a book")
    )

    @Test
    fun promptHasTodaysDateProjectsAndNumberedOpenTasksFirst() {
        val prompt = PromptBuilder.system(today, tasks, projects)
        assertTrue(prompt.contains("Today is Saturday, 2026-11-14"))
        assertTrue(prompt.contains("Projects: Home, Work"))
        // Open tasks by due date, undated after, completed last
        val lines = prompt.lines().filter { it.startsWith("#") }
        assertEquals("#1 Call mum | due 2026-11-15", lines[0])
        assertEquals("#2 Pay rent | due 2026-11-20 | priority high | project Home", lines[1])
        assertEquals("#3 Read a book", lines[2])
        assertEquals("#4 Old thing | done", lines[3])
    }

    @Test
    fun numbersInThePromptMapBackToTheSameTasks() {
        val shown = PromptBuilder.contextTasks(tasks)
        val parsed = ParsedReply("", listOf(AssistantAction.DeleteTask(2), AssistantAction.CompleteTask(1)))
        val res = ActionResolver.resolve(parsed, shown, projects)
        assertEquals("t-later", res.proposals[0].task?.id)
        assertTrue(res.proposals[0].destructive)
        assertEquals("t-soon", res.proposals[1].task?.id)
        assertFalse(res.proposals[1].destructive)
    }

    @Test
    fun unknownTaskNumbersAndProjectsBecomeProblems() {
        val parsed = ParsedReply(
            "", listOf(
                AssistantAction.DeleteTask(99),
                AssistantAction.UpdateProject("Ghost", name = "Boo"),
                AssistantAction.DeleteProject("Nope")
            )
        )
        val res = ActionResolver.resolve(parsed, PromptBuilder.contextTasks(tasks), projects)
        assertTrue(res.proposals.isEmpty())
        assertEquals(3, res.problems.size)
    }

    @Test
    fun summariesDescribeEachChange() {
        val parsed = ParsedReply(
            "", listOf(
                AssistantAction.CreateTask("Buy milk", due = DueSpec(LocalDate.of(2026, 11, 15)), priority = 1, project = "Garden", subtasks = listOf("a", "b")),
                AssistantAction.CreateProject("Garden"),
                AssistantAction.UpdateTask(1, due = DueSpec(null), completed = true)
            )
        )
        val res = ActionResolver.resolve(parsed, PromptBuilder.contextTasks(tasks), projects)
        assertEquals("Create task “Buy milk” · due Sun 15 Nov · high priority · project Garden (new) · 2 subtasks", res.proposals[0].summary)
        assertEquals("Create project “Garden”", res.proposals[1].summary)
        assertEquals("Update “Call mum”: remove due date, mark done", res.proposals[2].summary)
    }

    @Test
    fun aProjectCreatedEarlierInTheSameAnswerIsKnownToLaterActions() {
        val parsed = ParsedReply("", listOf(AssistantAction.CreateProject("Garden"), AssistantAction.DeleteProject("garden")))
        val res = ActionResolver.resolve(parsed, emptyList(), projects)
        assertEquals(2, res.proposals.size)
        assertTrue(res.problems.isEmpty())
    }
}
