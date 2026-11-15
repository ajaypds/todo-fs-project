package com.example.todoflow.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ActionParserTest {

    @Test
    fun readsPlainJsonWithAReplyAndOneAction() {
        val r = ActionParser.parse("""{"reply":"Added it","actions":[{"op":"create_task","title":"Buy milk","due":"2026-11-15","priority":"high","project":"Home","subtasks":["Oat","Whole"]}]}""")
        assertEquals("Added it", r.reply)
        val a = r.actions.single() as AssistantAction.CreateTask
        assertEquals("Buy milk", a.title)
        assertEquals(LocalDate.of(2026, 11, 15), a.due?.date)
        assertEquals(1, a.priority)
        assertEquals("Home", a.project)
        assertEquals(listOf("Oat", "Whole"), a.subtasks)
        assertTrue(r.problems.isEmpty())
    }

    @Test
    fun toleratesCodeFencesThinkBlocksAndSurroundingProse() {
        val raw = """
            <think>The user wants a task. {"not":"this"}</think>
            Sure! Here you go:
            ```json
            {"reply":"ok","actions":[{"op":"delete_task","task":"#B"}]}
            ```
            Hope that helps.
        """.trimIndent()
        val r = ActionParser.parse(raw)
        assertEquals(AssistantAction.DeleteTask(2), r.actions.single())
    }

    @Test
    fun plainTextWithoutJsonIsJustAConversation() {
        val r = ActionParser.parse("I can help you manage tasks.")
        assertEquals("I can help you manage tasks.", r.reply)
        assertEquals(false, r.hadJson)
        assertTrue(r.actions.isEmpty())
        assertTrue(r.problems.isEmpty())
    }

    @Test
    fun acceptsABareActionOrAnArrayOfActions() {
        assertEquals(
            AssistantAction.CompleteTask(1, true),
            ActionParser.parse("""{"op":"complete_task","task":"A"}""").actions.single()
        )
        val two = ActionParser.parse("""[{"op":"create_project","name":"Home","color":"#112233"},{"op":"delete_project","project":"Old"}]""")
        assertEquals(2, two.actions.size)
        assertEquals(AssistantAction.CreateProject("Home", "#112233"), two.actions[0])
    }

    @Test
    fun updateTaskSeparatesUnchangedClearedAndNewValues() {
        val r = ActionParser.parse("""{"actions":[{"op":"update_task","task":"c","due":"none","priority":"low","project":"inbox","completed":true}]}""")
        val a = r.actions.single() as AssistantAction.UpdateTask
        assertEquals(3, a.ref)
        assertEquals(DueSpec(null), a.due)          // clear the due date
        assertEquals(3, a.priority)                  // low = P3
        assertEquals(ProjectSpec(null), a.project)   // back to the inbox
        assertEquals(true, a.completed)
        assertEquals(null, a.title)                  // not mentioned: untouched
    }

    @Test
    fun deleteProjectCanAskForItsTasksToGoToo() {
        fun del(json: String) = ActionParser.parse(json).actions.single() as AssistantAction.DeleteProject
        assertEquals(true, del("""{"actions":[{"op":"delete_project","project":"Test","with_tasks":true}]}""").withTasks)
        assertEquals(false, del("""{"actions":[{"op":"delete_project","project":"Test"}]}""").withTasks)
    }

    @Test
    fun priorityWordsMapOntoAppLevels() {
        fun p(word: String) = (ActionParser.parse("""{"actions":[{"op":"create_task","title":"x","priority":"$word"}]}""").actions.single() as AssistantAction.CreateTask).priority
        assertEquals(1, p("urgent"))
        assertEquals(1, p("high"))
        assertEquals(2, p("medium"))
        assertEquals(3, p("low"))
        assertEquals(0, p("none"))
    }

    @Test
    fun reportsProblemsInsteadOfGuessing() {
        val r = ActionParser.parse("""{"actions":[{"op":"create_task"},{"op":"explode"},{"op":"update_task","title":"x"},{"op":"create_task","title":"ok","due":"someday"}]}""")
        assertEquals(1, r.actions.size) // only the valid create_task survives
        assertEquals(4, r.problems.size)
        assertTrue(r.problems.any { it.contains("title") })
        assertTrue(r.problems.any { it.contains("explode") })
        assertTrue(r.problems.any { it.contains("task code") })
        assertTrue(r.problems.any { it.contains("someday") })
    }

    @Test
    fun taskCodesAreLettersAndPlainNumbersAreRefused() {
        fun ref(code: String) = ActionParser.parse("""{"actions":[{"op":"delete_task","task":$code}]}""")
        assertEquals(AssistantAction.DeleteTask(1), ref("\"A\"").actions.single())
        assertEquals(AssistantAction.DeleteTask(2), ref("\"[b]\"").actions.single())
        assertEquals(AssistantAction.DeleteTask(27), ref("\"AA\"").actions.single())
        // "Task 1" in the user's words is not position 1: a number is never guessed into a task
        val numeric = ref("3")
        assertTrue(numeric.actions.isEmpty())
        assertTrue(numeric.problems.single().contains("not a task code"))
    }

    @Test
    fun taskCodesRoundTrip() {
        (1..800).forEach { assertEquals(it, TaskCodes.decode(TaskCodes.encode(it))) }
        assertEquals("A", TaskCodes.encode(1))
        assertEquals("Z", TaskCodes.encode(26))
        assertEquals("AA", TaskCodes.encode(27))
    }

    @Test
    fun readOnlyLookUpsAreParsedAsQueries() {
        val r = ActionParser.parse("""{"reply":"Checking","actions":[{"op":"list_projects"},{"op":"list_tasks","project":"Home","view":"today","completed":false,"search":"milk"},{"op":"get_task","task":"C"},{"op":"list_labels"},{"op":"daily_briefing"}]}""")
        assertEquals(
            listOf(ListProjects, ListTasks("Home", "today", false, "milk"), GetTask(3), ListLabels, DailyBriefing),
            r.actions
        )
        assertTrue(r.actions.all { it is AssistantQuery })
        // an unknown view is dropped, not guessed
        assertEquals(null, (ActionParser.parse("""{"actions":[{"op":"list_tasks","view":"someday"}]}""").actions.single() as ListTasks).view)
    }

    @Test
    fun skipsBracesInsideStringsWhenFindingTheJson() {
        val r = ActionParser.parse("""text {"reply":"use } and { carefully","actions":[]} more""")
        assertEquals("use } and { carefully", r.reply)
    }
}
