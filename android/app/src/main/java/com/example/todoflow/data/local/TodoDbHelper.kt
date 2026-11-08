package com.example.todoflow.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class TodoDbHelper(
    context: Context,
    databaseName: String = DATABASE_NAME
) : SQLiteOpenHelper(context, databaseName, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "todoflow.db"
        const val DATABASE_VERSION = 3

        const val TABLE_TASKS = "tasks"
        const val COL_TASK_ID = "id"
        const val COL_TASK_TITLE = "title"
        const val COL_TASK_DESCRIPTION = "description"
        const val COL_TASK_COMPLETED = "completed"
        const val COL_TASK_PRIORITY = "priority"
        const val COL_TASK_DUE_DATE = "due_date"
        const val COL_TASK_PROJECT_ID = "project_id"
        const val COL_TASK_PROJECT_NAME = "project_name"
        const val COL_TASK_POSITION = "position"
        const val COL_TASK_COMMENT_COUNT = "comment_count"
        const val COL_TASK_UPDATED_AT = "updated_at"
        const val COL_TASK_IS_SYNCED = "is_synced"

        const val TABLE_SUBTASKS = "subtasks"
        const val COL_SUBTASK_ID = "id"
        const val COL_SUBTASK_TASK_ID = "task_id"
        const val COL_SUBTASK_TITLE = "title"
        const val COL_SUBTASK_COMPLETED = "completed"
        const val COL_SUBTASK_POSITION = "position"

        const val TABLE_PROJECTS = "projects"
        const val COL_PROJECT_ID = "id"
        const val COL_PROJECT_NAME = "name"
        const val COL_PROJECT_COLOR = "color"

        const val TABLE_LABELS = "labels"
        const val COL_LABEL_ID = "id"
        const val COL_LABEL_NAME = "name"
        const val COL_LABEL_COLOR = "color"

        const val TABLE_TASK_LABELS = "task_labels"
        const val COL_TL_TASK_ID = "task_id"
        const val COL_TL_LABEL_ID = "label_id"

        const val TABLE_PENDING_OPS = "pending_ops"
        const val COL_OP_ID = "id"
        const val COL_OP_TYPE = "type"
        const val COL_OP_ENTITY_ID = "entity_id"
        const val COL_OP_PARENT_ID = "parent_id"
        const val COL_OP_PAYLOAD = "payload"
        const val COL_OP_ATTEMPTS = "attempts"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // Enable WAL (Write-Ahead Logging) for concurrent reads and writes
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_TASKS (
                $COL_TASK_ID TEXT PRIMARY KEY,
                $COL_TASK_TITLE TEXT NOT NULL,
                $COL_TASK_DESCRIPTION TEXT,
                $COL_TASK_COMPLETED INTEGER NOT NULL DEFAULT 0,
                $COL_TASK_PRIORITY INTEGER NOT NULL DEFAULT 0,
                $COL_TASK_DUE_DATE TEXT,
                $COL_TASK_PROJECT_ID TEXT,
                $COL_TASK_PROJECT_NAME TEXT,
                $COL_TASK_POSITION INTEGER NOT NULL DEFAULT 0,
                $COL_TASK_COMMENT_COUNT INTEGER NOT NULL DEFAULT 0,
                $COL_TASK_UPDATED_AT TEXT,
                $COL_TASK_IS_SYNCED INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )

        // Indexes for ultra-fast filtering and sorting
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_project ON $TABLE_TASKS($COL_TASK_PROJECT_ID)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_completed ON $TABLE_TASKS($COL_TASK_COMPLETED)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_due ON $TABLE_TASKS($COL_TASK_DUE_DATE)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_PROJECTS (
                $COL_PROJECT_ID TEXT PRIMARY KEY,
                $COL_PROJECT_NAME TEXT NOT NULL,
                $COL_PROJECT_COLOR TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SUBTASKS (
                $COL_SUBTASK_ID TEXT PRIMARY KEY,
                $COL_SUBTASK_TASK_ID TEXT NOT NULL,
                $COL_SUBTASK_TITLE TEXT NOT NULL,
                $COL_SUBTASK_COMPLETED INTEGER NOT NULL DEFAULT 0,
                $COL_SUBTASK_POSITION INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_subtasks_task ON $TABLE_SUBTASKS($COL_SUBTASK_TASK_ID)")
        createVersion3Tables(db)
    }

    private fun createVersion3Tables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_LABELS (
                $COL_LABEL_ID TEXT PRIMARY KEY,
                $COL_LABEL_NAME TEXT NOT NULL,
                $COL_LABEL_COLOR TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_TASK_LABELS (
                $COL_TL_TASK_ID TEXT NOT NULL,
                $COL_TL_LABEL_ID TEXT NOT NULL,
                PRIMARY KEY ($COL_TL_TASK_ID, $COL_TL_LABEL_ID)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_PENDING_OPS (
                $COL_OP_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_OP_TYPE TEXT NOT NULL,
                $COL_OP_ENTITY_ID TEXT NOT NULL,
                $COL_OP_PARENT_ID TEXT,
                $COL_OP_PAYLOAD TEXT NOT NULL,
                $COL_OP_ATTEMPTS INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Additive, data-preserving migrations (cached data and unsynced changes are kept)
        if (oldVersion < 2) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_SUBTASKS (
                    $COL_SUBTASK_ID TEXT PRIMARY KEY,
                    $COL_SUBTASK_TASK_ID TEXT NOT NULL,
                    $COL_SUBTASK_TITLE TEXT NOT NULL,
                    $COL_SUBTASK_COMPLETED INTEGER NOT NULL DEFAULT 0,
                    $COL_SUBTASK_POSITION INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_subtasks_task ON $TABLE_SUBTASKS($COL_SUBTASK_TASK_ID)")
        }
        if (oldVersion < 3) {
            createVersion3Tables(db)
        }
    }
}
