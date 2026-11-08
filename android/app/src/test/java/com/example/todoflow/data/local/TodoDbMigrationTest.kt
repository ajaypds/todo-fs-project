package com.example.todoflow.data.local

import android.content.ContentValues
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TodoDbMigrationTest {

    /** Upgrading an installed app (database v2) must keep cached tasks and unsynced changes. */
    @Test
    fun upgradingFromVersion2KeepsExistingDataAndAddsTheNewTables() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Removes the main file plus any -wal / -shm / -journal files left by other tests
        android.database.sqlite.SQLiteDatabase.deleteDatabase(context.getDatabasePath(TodoDbHelper.DATABASE_NAME))

        // Build a version 2 database exactly as the previous app version created it
        val old = context.openOrCreateDatabase(TodoDbHelper.DATABASE_NAME, android.content.Context.MODE_PRIVATE, null)
        old.execSQL(
            "CREATE TABLE tasks (id TEXT PRIMARY KEY, title TEXT NOT NULL, description TEXT, " +
                "completed INTEGER NOT NULL DEFAULT 0, priority INTEGER NOT NULL DEFAULT 0, due_date TEXT, " +
                "project_id TEXT, project_name TEXT, position INTEGER NOT NULL DEFAULT 0, " +
                "comment_count INTEGER NOT NULL DEFAULT 0, updated_at TEXT, is_synced INTEGER NOT NULL DEFAULT 1)"
        )
        old.execSQL("CREATE TABLE projects (id TEXT PRIMARY KEY, name TEXT NOT NULL, color TEXT)")
        old.execSQL(
            "CREATE TABLE subtasks (id TEXT PRIMARY KEY, task_id TEXT NOT NULL, title TEXT NOT NULL, " +
                "completed INTEGER NOT NULL DEFAULT 0, position INTEGER NOT NULL DEFAULT 0)"
        )
        old.insert("tasks", null, ContentValues().apply {
            put("id", "t1"); put("title", "Existing task"); put("is_synced", 0)
        })
        old.insert("projects", null, ContentValues().apply { put("id", "p1"); put("name", "Home") })
        old.version = 2
        old.close()

        val upgraded = TodoDbHelper(context).writableDatabase

        assertEquals(TodoDbHelper.DATABASE_VERSION, upgraded.version)
        upgraded.rawQuery("SELECT title, is_synced FROM tasks WHERE id = 't1'", null).use {
            assertTrue(it.moveToFirst())
            assertEquals("Existing task", it.getString(0))
            assertEquals(0, it.getInt(1)) // still marked as not uploaded
        }
        upgraded.rawQuery("SELECT name FROM projects WHERE id = 'p1'", null).use {
            assertTrue(it.moveToFirst())
        }
        for (table in listOf("labels", "task_labels", "pending_ops")) {
            upgraded.rawQuery("SELECT COUNT(*) FROM $table", null).use { assertTrue(it.moveToFirst()) }
        }
        upgraded.close()
    }
}
