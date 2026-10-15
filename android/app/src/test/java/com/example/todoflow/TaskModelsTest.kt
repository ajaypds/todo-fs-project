package com.example.todoflow

import com.example.todoflow.data.model.TaskDto
import com.example.todoflow.data.model.PageResponse
import com.example.todoflow.ui.tasks.formatDueDate
import com.example.todoflow.ui.tasks.parseHexColor
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TaskModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun testTaskDtoSerializationAndDeserialization() {
        val jsonPayload = """
            {
                "id": "123e4567-e89b-12d3-a456-426614174000",
                "title": "Build Android App",
                "completed": false,
                "priority": 1,
                "dueDate": "2026-10-15T12:00:00Z",
                "projectName": "Mobile Launch",
                "labels": [{"id": "lbl-1", "name": "urgent"}],
                "subtasks": []
            }
        """.trimIndent()

        val task = json.decodeFromString<TaskDto>(jsonPayload)
        assertEquals("123e4567-e89b-12d3-a456-426614174000", task.id)
        assertEquals("Build Android App", task.title)
        assertEquals(false, task.completed)
        assertEquals(1, task.priority)
        assertEquals("Mobile Launch", task.projectName)
        assertEquals(1, task.labels.size)
    }

    @Test
    fun testPageResponseDeserialization() {
        val jsonPayload = """
            {
                "content": [
                    {"id": "t1", "title": "First Task", "completed": false},
                    {"id": "t2", "title": "Second Task", "completed": true}
                ],
                "totalElements": 2,
                "totalPages": 1
            }
        """.trimIndent()

        val page = json.decodeFromString<PageResponse<TaskDto>>(jsonPayload)
        assertEquals(2, page.content.size)
        assertEquals("First Task", page.content[0].title)
        assertEquals(true, page.content[1].completed)
    }

    @Test
    fun testFormatDueDate() {
        val formatted = formatDueDate("2026-10-15T10:00:00Z")
        assertEquals("Oct 15", formatted)
    }

    @Test
    fun testParseHexColor() {
        val color = parseHexColor("#FF0000")
        assertNotNull(color)
    }

    @Test
    fun testSubtaskSerialization() {
        val jsonPayload = """
            {
                "id": "123e4567-e89b-12d3-a456-426614174000",
                "title": "Build Android App",
                "completed": false,
                "subtasks": [
                    {
                        "id": "sub-1",
                        "title": "Setup SQLite",
                        "completed": true,
                        "position": 0
                    },
                    {
                        "id": "sub-2",
                        "title": "Add UI Accordion",
                        "completed": false,
                        "position": 1
                    }
                ]
            }
        """.trimIndent()

        val task = json.decodeFromString<TaskDto>(jsonPayload)
        assertEquals(2, task.subtasks.size)
        assertEquals("Setup SQLite", task.subtasks[0].title)
        assertEquals(true, task.subtasks[0].completed)
        assertEquals("Add UI Accordion", task.subtasks[1].title)
        assertEquals(false, task.subtasks[1].completed)
    }
}

