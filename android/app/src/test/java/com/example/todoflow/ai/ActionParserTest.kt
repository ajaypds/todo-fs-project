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
            {"reply":"ok","actions":[{"op":"delete_task","task":"#2"}]}
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
        assertTrue(r.actions.isEmpty())
        assertTrue(r.problems.isEmpty())
    }

    @Test
    fun acceptsABareActionOrAnArrayOfActions() {
        assertEquals(
            AssistantAction.CompleteTask(1, true),
            ActionParser.parse("""{"op":"complete_task","task":1}""").actions.single()
        )
        val two = ActionParser.parse("""[{"op":"create_project","name":"Home","color":"#112233"},{"op":"delete_project","project":"Old"}]""")
        assertEquals(2, two.actions.size)
        assertEquals(AssistantAction.CreateProject("Home", "#112233"), two.actions[0])
    }

    @Test
    fun updateTaskSeparatesUnchangedClearedAndNewValues() {
        val r = ActionParser.parse("""{"actions":[{"op":"update_task","task":3,"due":"none","priority":"low","project":"inbox","completed":true}]}""")
        val a = r.actions.single() as AssistantAction.UpdateTask
        assertEquals(3, a.ref)
        assertEquals(DueSpec(null), a.due)          // clear the due date
        assertEquals(3, a.priority)                  // low = P3
        assertEquals(ProjectSpec(null), a.project)   // back to the inbox
        assertEquals(true, a.completed)
        assertEquals(null, a.title)                  // not mentioned: untouched
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
        assertTrue(r.problems.any { it.contains("task number") })
        assertTrue(r.problems.any { it.contains("someday") })
    }

    @Test
    fun skipsBracesInsideStringsWhenFindingTheJson() {
        val r = ActionParser.parse("""text {"reply":"use } and { carefully","actions":[]} more""")
        assertEquals("use } and { carefully", r.reply)
    }
}
