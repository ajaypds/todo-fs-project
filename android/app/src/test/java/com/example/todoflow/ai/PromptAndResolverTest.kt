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
        val lines = prompt.substringAfter("Tasks:\n").lines()
        assertEquals("- Call mum | due 2026-11-15", lines[0])
        assertEquals("- Pay rent | due 2026-11-20 | priority high | project Home", lines[1])
        assertEquals("- Read a book", lines[2])
        assertEquals("- Old thing | done", lines[3])
    }

    @Test
    fun workedExamplesUseRealDatesAndTheDocumentedFormat() {
        val ex = PromptBuilder.examples(today)
        assertTrue(ex[0].assistant.contains(""""due":"2026-11-15""""))   // tomorrow
        assertTrue(ex[1].assistant.contains(""""due":"2026-11-20""""))   // next Friday
        // A vague "add some tasks" request creates the project and asks, instead of inventing tasks
        val vague = ActionParser.parse(ex[2].assistant)
        assertEquals(listOf(AssistantAction.CreateProject("Beta")), vague.actions)
        assertTrue(vague.reply.contains("Which tasks"))
        // Every example is a valid answer for the parser
        ex.forEach { assertTrue(it.user, ActionParser.parse(it.assistant).hadJson) }
        assertEquals(1, ActionParser.parse(ex[0].assistant).actions.size)
        assertTrue(ActionParser.parse(ex[9].assistant).actions.isEmpty())
    }

    @Test
    fun promptListsTheToolsAndTellsTheModelItMayAnswerFromTheLists() {
        val prompt = PromptBuilder.system(today, tasks, projects)
        listOf("list_projects", "list_tasks", "get_task", "list_labels", "daily_briefing",
            "create_task", "update_task", "complete_task", "delete_task", "add_subtasks",
            "create_project", "update_project", "delete_project").forEach { assertTrue(it, prompt.contains(""""op":"$it""""))}
        assertTrue(prompt.contains("you MUST use a look-up tool"))
        assertTrue(prompt.contains("Never answer such a question from memory"))
        assertTrue(prompt.contains("exact, complete title"))
        assertTrue(prompt.contains("never just \"1\""))
        assertTrue(prompt.contains("NOT done until the user applies"))
    }

    @Test
    fun dataQuestionExamplesUseLookUpToolsAndNeverContainUserDataLikeNames() {
        val ex = PromptBuilder.examples(today)
        assertEquals(listOf(ListProjects), ActionParser.parse(ex[3].assistant).actions)
        assertEquals(listOf(ListTasks(view = "inbox")), ActionParser.parse(ex[4].assistant).actions)
        assertEquals(listOf(ListTasks(view = "today")), ActionParser.parse(ex[5].assistant).actions)
        // the examples never state facts about the user's data, so there is nothing for the model to copy as "data"
        assertTrue(ex.none { it.assistant.contains("Your projects") })
    }

    @Test
    fun titlesMapToTheRealTasksAndFindingIsCaseInsensitive() {
        val parsed = ParsedReply("", listOf(AssistantAction.DeleteTask("Pay rent"), AssistantAction.CompleteTask("call MUM")))
        val res = ActionResolver.resolve(parsed, projects, tasks)
        assertEquals("t-later", res.proposals[0].task?.id)
        assertTrue(res.proposals[0].destructive)
        assertEquals("t-soon", res.proposals[1].task?.id)
        assertFalse(res.proposals[1].destructive)
    }

    @Test
    fun theUsersTaskNamedTask1IsFoundByItsTitleNotByAPosition() {
        val list = listOf(TaskEntity("m", "Buy milk"), TaskEntity("1", "Task 1"), TaskEntity("2", "Task 2"), TaskEntity("3", "Task 3"))
        val res = ActionResolver.resolve(
            ParsedReply("", listOf(AssistantAction.UpdateTask("Task 1", project = ProjectSpec("Test")), AssistantAction.UpdateTask("Task 3", project = ProjectSpec("Test")))),
            projects, list
        )
        assertEquals(listOf("1", "3"), res.proposals.map { it.task!!.id })
    }

    @Test
    fun ambiguousUnknownAndDuplicateTitlesAreExplainedNotGuessed() {
        val list = listOf(
            TaskEntity("a", "Buy milk", completed = true), TaskEntity("b", "Buy milk"),         // one open, one done: the open one
            TaskEntity("c", "Call dad"), TaskEntity("d", "Call dad"),                           // two open: ambiguous
            TaskEntity("e", "Water the garden plants"), TaskEntity("f", "Water the herbs")
        )
        assertEquals("b", ActionResolver.findTask(list, "buy milk").task?.id)
        assertTrue(ActionResolver.findTask(list, "Call dad").problem!!.contains("More than one task is called"))
        assertEquals("e", ActionResolver.findTask(list, "garden plants").task?.id)               // unique partial match
        assertTrue(ActionResolver.findTask(list, "Water").problem!!.contains("More than one task matches"))
        assertTrue(ActionResolver.findTask(list, "Nonexistent").problem!!.contains("couldn't find a task called"))
        assertTrue(ActionResolver.findTask(list, "1").problem!!.contains("couldn't find"))        // no title ends with 1
    }

    @Test
    fun aBareNumberMeansTheOneTaskWhoseTitleEndsWithIt() {
        val list = listOf(
            TaskEntity("1", "Task 1"), TaskEntity("11", "Task 11"), TaskEntity("2", "Task 2"),
            TaskEntity("c1", "Chapter 2"), TaskEntity("done", "Old 3", completed = true), TaskEntity("open", "Open 3")
        )
        assertEquals("1", ActionResolver.findTask(list, "1").task?.id)          // "Task 1", not "Task 11"
        assertEquals("11", ActionResolver.findTask(list, "11").task?.id)
        assertEquals("open", ActionResolver.findTask(list, "3").task?.id)       // the open one wins over the finished one
        assertTrue(ActionResolver.findTask(list, "2").problem!!.contains("More than one task ends with"))
        assertTrue(ActionResolver.findTask(list, "9").problem!!.contains("couldn't find"))
    }

    @Test
    fun unknownTasksAndProjectsBecomeProblems() {
        val parsed = ParsedReply(
            "", listOf(
                AssistantAction.DeleteTask("Nope"),
                AssistantAction.UpdateProject("Ghost", name = "Boo"),
                AssistantAction.DeleteProject("Nope")
            )
        )
        val res = ActionResolver.resolve(parsed, projects, tasks)
        assertTrue(res.proposals.isEmpty())
        assertEquals(3, res.problems.size)
    }

    @Test
    fun summariesDescribeEachChange() {
        val parsed = ParsedReply(
            "", listOf(
                AssistantAction.CreateTask("Buy milk", due = DueSpec(LocalDate.of(2026, 11, 15)), priority = 1, project = "Garden", subtasks = listOf("a", "b")),
                AssistantAction.CreateProject("Garden"),
                AssistantAction.UpdateTask("Call mum", due = DueSpec(null), completed = true)
            )
        )
        val res = ActionResolver.resolve(parsed, projects, tasks)
        assertEquals("Create task “Buy milk” · due Sun 15 Nov · high priority · project Garden (new) · 2 subtasks", res.proposals[0].summary)
        assertEquals("Create project “Garden”", res.proposals[1].summary)
        assertEquals("Update “Call mum”: remove due date, mark done", res.proposals[2].summary)
    }

    @Test
    fun deletingAProjectSaysWhatHappensToItsTasks() {
        val inHome = listOf(TaskEntity("a", "One", projectId = "p1"), TaskEntity("b", "Two", projectId = "p1"), TaskEntity("c", "Other", projectId = "p2"))
        fun summary(withTasks: Boolean, project: String = "Home") = ActionResolver.resolve(
            ParsedReply("", listOf(AssistantAction.DeleteProject(project, withTasks))), projects, inHome
        ).proposals.single().summary
        assertEquals("Delete project “Home” and its 2 tasks", summary(true))
        assertEquals("Delete project “Home” (its 2 tasks move to the Inbox)", summary(false))
        assertEquals("Delete project “Work” (its 1 task moves to the Inbox)", summary(false, "Work"))
    }

    @Test
    fun aProjectCreatedEarlierInTheSameAnswerIsKnownToLaterActions() {
        val parsed = ParsedReply("", listOf(AssistantAction.CreateProject("Garden"), AssistantAction.DeleteProject("garden")))
        val res = ActionResolver.resolve(parsed, projects, emptyList())
        assertEquals(2, res.proposals.size)
        assertTrue(res.problems.isEmpty())
    }
}
