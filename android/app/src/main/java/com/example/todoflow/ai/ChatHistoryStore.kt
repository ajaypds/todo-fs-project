package com.example.todoflow.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class StoredChange(val summary: String, val destructive: Boolean = false)

@Serializable
data class StoredMessage(
    val role: String, // "user" or "assistant"
    val text: String,
    val changes: List<StoredChange> = emptyList(),
    val problems: List<String> = emptyList(),
    val status: String = "NONE",
    val raw: String = "",
    val note: String? = null
)

@Serializable
data class StoredChat(val id: Long, val title: String, val updatedAt: Long, val messages: List<StoredMessage>)

/**
 * The assistant's past chats, kept in one small JSON file on the device (never uploaded). Only the
 * newest [MAX_CHATS] are kept. A damaged file is treated as empty instead of crashing the app.
 */
class ChatHistoryStore(private val file: File) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Synchronized
    fun load(): List<StoredChat> = readAll().sortedByDescending { it.updatedAt }

    @Synchronized
    fun save(chat: StoredChat) {
        val all = readAll().filter { it.id != chat.id } + chat
        write(all.sortedByDescending { it.updatedAt }.take(MAX_CHATS))
    }

    @Synchronized
    fun delete(id: Long) = write(readAll().filter { it.id != id })

    @Synchronized
    fun clear() {
        file.delete()
    }

    private fun readAll(): List<StoredChat> = try {
        if (file.exists()) json.decodeFromString<List<StoredChat>>(file.readText()) else emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun write(chats: List<StoredChat>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(chats))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    companion object {
        const val MAX_CHATS = 50
    }
}
