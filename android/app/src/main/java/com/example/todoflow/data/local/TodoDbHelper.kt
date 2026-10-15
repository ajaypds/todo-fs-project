package com.example.todoflow.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class TodoDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "todoflow.db"
        const val DATABASE_VERSION = 1

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

        const val TABLE_PROJECTS = "projects"
        const val COL_PROJECT_ID = "id"
        const val COL_PROJECT_NAME = "name"
        const val COL_PROJECT_COLOR = "color"
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TASKS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PROJECTS")
        onCreate(db)
    }
}
