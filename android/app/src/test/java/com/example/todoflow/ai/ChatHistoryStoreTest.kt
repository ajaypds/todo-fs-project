package com.example.todoflow.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChatHistoryStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun chat(id: Long, title: String = "chat $id", updatedAt: Long = id) = StoredChat(
        id, title, updatedAt,
        listOf(StoredMessage("user", "hi"), StoredMessage("assistant", "hello", changes = listOf(StoredChange("Create task X", false)), status = "APPLIED", raw = "{}", note = "1 change applied"))
    )

    @Test
    fun savesLoadsNewestFirstAndKeepsAllFields() {
        val store = ChatHistoryStore(tmp.newFile("chats.json"))
        store.save(chat(1))
        store.save(chat(2))
        val loaded = store.load()
        assertEquals(listOf(2L, 1L), loaded.map { it.id })
        assertEquals(chat(2), loaded.first())
    }

    @Test
    fun savingTheSameChatAgainReplacesIt() {
        val store = ChatHistoryStore(tmp.newFile("chats.json"))
        store.save(chat(1, title = "first"))
        store.save(chat(1, title = "renamed", updatedAt = 5))
        assertEquals(listOf("renamed"), store.load().map { it.title })
    }

    @Test
    fun deleteRemovesAndOnlyTheNewestChatsAreKept() {
        val store = ChatHistoryStore(tmp.newFile("chats.json"))
        (1L..60L).forEach { store.save(chat(it)) }
        assertEquals(ChatHistoryStore.MAX_CHATS, store.load().size)
        assertEquals(60L, store.load().first().id)
        store.delete(60L)
        assertTrue(store.load().none { it.id == 60L })
    }

    @Test
    fun aMissingOrDamagedFileIsJustAnEmptyHistory() {
        val missing = ChatHistoryStore(java.io.File(tmp.root, "none.json"))
        assertTrue(missing.load().isEmpty())
        val damaged = tmp.newFile("bad.json").apply { writeText("{not json") }
        val store = ChatHistoryStore(damaged)
        assertTrue(store.load().isEmpty())
        store.save(chat(1)) // and it recovers
        assertEquals(1, store.load().size)
    }
}
