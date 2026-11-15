package com.example.todoflow.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.time.LocalDate

/**
 * Turns the model's raw text into [AssistantAction]s. Small models wrap JSON in prose or code fences,
 * emit `<think>` blocks and make small mistakes, so this is deliberately forgiving about the wrapper and
 * strict about the content: anything it can't make sense of is reported in [ParsedReply.problems]
 * instead of being guessed.
 */
object ActionParser {

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }
    private val thinkBlock = Regex("<think>.*?(</think>|$)", RegexOption.DOT_MATCHES_ALL)
    private val fence = Regex("```[a-zA-Z]*")
    private val hexColor = Regex("^#[0-9a-fA-F]{6}$")

    fun parse(raw: String): ParsedReply {
        val cleaned = raw.replace(thinkBlock, "").replace(fence, "").trim()
        val element = extractJson(cleaned)
            ?: return ParsedReply(reply = cleaned, actions = emptyList(), hadJson = false) // no JSON at all

        val root: JsonObject = when (element) {
            is JsonObject -> if (element.containsKey("op")) JsonObject(mapOf("actions" to JsonArray(listOf(element)))) else element
            is JsonArray -> JsonObject(mapOf("actions" to element))
            else -> return ParsedReply(reply = cleaned, actions = emptyList(), hadJson = false)
        }

        val problems = mutableListOf<String>()
        val actions = mutableListOf<AssistantAction>()
        val list = root["actions"]
        if (list is JsonArray) {
            list.forEachIndexed { i, item ->
                val obj = item as? JsonObject
                if (obj == null) {
                    problems += "Action ${i + 1} is not an object"
                } else {
                    parseAction(obj, problems)?.let { actions += it }
                }
            }
        } else if (list != null && list !is JsonNull) {
            problems += "\"actions\" must be a list"
        }
        val reply = root.str("reply") ?: ""
        return ParsedReply(reply = reply, actions = actions, problems = problems)
    }

    private fun parseAction(o: JsonObject, problems: MutableList<String>): AssistantAction? {
        val op = o.str("op")?.trim()?.lowercase()
        fun missing(what: String): AssistantAction? { problems += "\"$op\" needs $what"; return null }
        return when (op) {
            "create_task" -> {
                val title = o.str("title")?.trim()
                if (title.isNullOrEmpty()) return missing("a title")
                AssistantAction.CreateTask(
                    title = title,
                    description = o.str("description")?.trim()?.ifEmpty { null },
                    priority = o.priority(problems),
                    due = o.due(problems),
                    project = o.str("project")?.trim()?.takeIf { it.isNotEmpty() && !isInbox(it) },
                    subtasks = o.strings("subtasks")
                )
            }
            "update_task" -> {
                val ref = o.ref(problems, op) ?: return null
                AssistantAction.UpdateTask(
                    ref = ref,
                    title = o.str("title")?.trim()?.ifEmpty { null },
                    description = o.str("description")?.trim(),
                    priority = o.priority(problems),
                    due = o.due(problems),
                    project = o.str("project")?.trim()?.let { ProjectSpec(if (it.isEmpty() || isInbox(it)) null else it) },
                    completed = o.bool("completed")
                )
            }
            "complete_task" -> {
                val ref = o.ref(problems, op) ?: return null
                AssistantAction.CompleteTask(ref, o.bool("completed") ?: true)
            }
            "delete_task" -> AssistantAction.DeleteTask(o.ref(problems, op) ?: return null)
            "add_subtasks" -> {
                val ref = o.ref(problems, op) ?: return null
                val titles = o.strings("subtasks")
                if (titles.isEmpty()) return missing("subtasks")
                AssistantAction.AddSubtasks(ref, titles)
            }
            "create_project" -> {
                val name = o.str("name")?.trim()
                if (name.isNullOrEmpty()) return missing("a name")
                AssistantAction.CreateProject(name, o.str("color")?.trim()?.takeIf { hexColor.matches(it) })
            }
            "update_project" -> {
                val project = o.str("project")?.trim()
                if (project.isNullOrEmpty()) return missing("a project name")
                AssistantAction.UpdateProject(
                    project,
                    o.str("name")?.trim()?.ifEmpty { null },
                    o.str("color")?.trim()?.takeIf { hexColor.matches(it) }
                )
            }
            "delete_project" -> {
                val project = o.str("project")?.trim()
                if (project.isNullOrEmpty()) return missing("a project name")
                AssistantAction.DeleteProject(project, withTasks = o.bool("with_tasks") ?: o.bool("withTasks") ?: o.bool("delete_tasks") ?: false)
            }
            "list_projects" -> ListProjects
            "list_labels" -> ListLabels
            "daily_briefing" -> DailyBriefing
            "list_tasks" -> ListTasks(
                project = o.str("project")?.trim()?.takeIf { it.isNotEmpty() },
                view = o.str("view")?.trim()?.lowercase()?.takeIf { it in setOf("all", "inbox", "today", "upcoming", "overdue") },
                completed = o.bool("completed"),
                search = o.str("search")?.trim()?.takeIf { it.isNotEmpty() }
            )
            "get_task" -> GetTask(o.ref(problems, op) ?: return null)
            else -> {
                problems += "Unknown action \"${op ?: "?"}\""
                null
            }
        }
    }

    // ---- field helpers ------------------------------------------------------------------------------

    private fun isInbox(name: String) = name.equals("inbox", true) || name.equals("none", true) || name.equals("no project", true)

    private fun JsonObject.str(key: String): String? {
        val v = this[key]
        return if (v is JsonPrimitive && v !is JsonNull) v.contentOrNull else null
    }

    private fun JsonObject.bool(key: String): Boolean? {
        val v = this[key] as? JsonPrimitive ?: return null
        return v.booleanOrNull ?: when (v.contentOrNull?.lowercase()) {
            "yes" -> true
            "no" -> false
            else -> null
        }
    }

    private fun JsonObject.strings(key: String): List<String> {
        val v = this[key]
        return when (v) {
            is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { s -> s.isNotEmpty() } }
            is JsonPrimitive -> listOfNotNull(v.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && v !is JsonNull })
            else -> emptyList()
        }
    }

    /**
     * The task code the model wrote ("A", "[B]", "#C"). Plain numbers are refused on purpose: the user's own
     * "Task 1" is not list position 1, and guessing would change the wrong task.
     */
    private fun JsonObject.ref(problems: MutableList<String>, op: String?): Int? {
        val v = (this["task"] ?: this["ref"] ?: this["id"]) as? JsonPrimitive
        if (v == null || v is JsonNull) {
            problems += "\"$op\" needs a task code"
            return null
        }
        val text = v.contentOrNull?.trim()?.removePrefix("#")?.removePrefix("[")?.removeSuffix("]")?.trim().orEmpty()
        val code = TaskCodes.decode(text)
        if (code == null) {
            problems += "\"$text\" is not a task code (use the letter in brackets, like A)"
        }
        return code
    }

    /** Priority words map onto the app's levels: high = 1 (P1), medium = 2, low = 3, none = 0. */
    private fun JsonObject.priority(problems: MutableList<String>): Int? {
        val v = this["priority"] as? JsonPrimitive ?: return null
        if (v is JsonNull) return null
        v.intOrNull?.let { return it.takeIf { n -> n in 0..3 } }
        return when (v.contentOrNull?.trim()?.lowercase()) {
            "urgent", "high", "important", "p1", "critical" -> 1
            "medium", "normal", "p2" -> 2
            "low", "p3" -> 3
            "none", "no", "" -> 0
            else -> { problems += "Unknown priority \"${v.contentOrNull}\""; null }
        }
    }

    private fun JsonObject.due(problems: MutableList<String>): DueSpec? {
        val v = this["due"] ?: this["due_date"] ?: this["dueDate"] ?: return null
        if (v is JsonNull) return null
        val text = (v as? JsonPrimitive)?.contentOrNull?.trim() ?: return null
        if (text.isEmpty() || text.equals("none", true) || text.equals("null", true) || text.equals("no date", true)) return DueSpec(null)
        return try {
            DueSpec(LocalDate.parse(text.substringBefore("T").take(10)))
        } catch (_: Exception) {
            problems += "Could not read the date \"$text\""
            null
        }
    }

    /** First balanced JSON object or array in [text], or null. */
    private fun extractJson(text: String): JsonElement? {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '{' || c == '[') {
                val end = matchingEnd(text, i)
                if (end > i) {
                    try {
                        return json.parseToJsonElement(text.substring(i, end + 1))
                    } catch (_: Exception) {
                        // not valid JSON: keep looking after this character
                    }
                }
            }
            i++
        }
        return null
    }

    private fun matchingEnd(text: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{', '[' -> depth++
                '}', ']' -> { depth--; if (depth == 0) return i }
            }
        }
        return -1
    }
}
