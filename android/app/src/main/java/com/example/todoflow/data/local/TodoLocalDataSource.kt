package com.example.todoflow.data.local

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext

enum class TaskFilterType {
    ALL,
    INBOX,
    TODAY,
    UPCOMING,
    COMPLETED,
    PROJECT
}

class TodoLocalDataSource(private val dbHelper: TodoDbHelper) {

    // Trigger used to notify observers when tasks or projects in SQLite change
    private val taskUpdatesTrigger = MutableSharedFlow<Unit>(replay = 1).apply {
        tryEmit(Unit)
    }

    private val projectUpdatesTrigger = MutableSharedFlow<Unit>(replay = 1).apply {
        tryEmit(Unit)
    }

    private fun notifyTasksChanged() {
        taskUpdatesTrigger.tryEmit(Unit)
    }

    private fun notifyProjectsChanged() {
        projectUpdatesTrigger.tryEmit(Unit)
    }

    fun observeTasks(filter: TaskFilterType = TaskFilterType.ALL, projectId: String? = null): Flow<List<TaskEntity>> = flow {
        taskUpdatesTrigger.collect {
            val tasks = getTasks(filter, projectId)
            emit(tasks)
        }
    }.flowOn(Dispatchers.IO).distinctUntilChanged()

    fun observeProjects(): Flow<List<ProjectEntity>> = flow {
        projectUpdatesTrigger.collect {
            val projects = getProjects()
            emit(projects)
        }
    }.flowOn(Dispatchers.IO).distinctUntilChanged()

    suspend fun getTasks(filter: TaskFilterType = TaskFilterType.ALL, projectId: String? = null): List<TaskEntity> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val tasks = mutableListOf<TaskEntity>()

        val selectionBuilder = StringBuilder()
        val selectionArgs = mutableListOf<String>()

        when (filter) {
            TaskFilterType.ALL -> {
                // Keep all tasks visible (both pending and completed)
            }
            TaskFilterType.INBOX -> {
                selectionBuilder.append("(${TodoDbHelper.COL_TASK_PROJECT_ID} IS NULL OR ${TodoDbHelper.COL_TASK_PROJECT_ID} = '')")
            }
            TaskFilterType.TODAY -> {
                selectionBuilder.append("${TodoDbHelper.COL_TASK_DUE_DATE} IS NOT NULL")
            }
            TaskFilterType.UPCOMING -> {
                selectionBuilder.append("${TodoDbHelper.COL_TASK_DUE_DATE} IS NOT NULL")
            }
            TaskFilterType.COMPLETED -> {
                selectionBuilder.append("${TodoDbHelper.COL_TASK_COMPLETED} = 1")
            }
            TaskFilterType.PROJECT -> {
                if (!projectId.isNullOrBlank()) {
                    selectionBuilder.append("${TodoDbHelper.COL_TASK_PROJECT_ID} = ?")
                    selectionArgs.add(projectId)
                }
            }
        }

        val cursor: Cursor = db.query(
            TodoDbHelper.TABLE_TASKS,
            null,
            if (selectionBuilder.isNotEmpty()) selectionBuilder.toString() else null,
            if (selectionArgs.isNotEmpty()) selectionArgs.toTypedArray() else null,
            null,
            null,
            "${TodoDbHelper.COL_TASK_POSITION} ASC, ${TodoDbHelper.COL_TASK_PRIORITY} ASC, ${TodoDbHelper.COL_TASK_UPDATED_AT} DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                tasks.add(cursorToTask(it))
            }
        }

        if (tasks.isNotEmpty()) {
            val subtasksByTaskId = mutableMapOf<String, MutableList<SubtaskEntity>>()
            val subtaskCursor = db.query(
                TodoDbHelper.TABLE_SUBTASKS,
                null,
                null,
                null,
                null,
                null,
                "${TodoDbHelper.COL_SUBTASK_POSITION} ASC, ${TodoDbHelper.COL_SUBTASK_ID} ASC"
            )
            subtaskCursor.use { sc ->
                while (sc.moveToNext()) {
                    val subtask = SubtaskEntity(
                        id = sc.getString(sc.getColumnIndexOrThrow(TodoDbHelper.COL_SUBTASK_ID)),
                        taskId = sc.getString(sc.getColumnIndexOrThrow(TodoDbHelper.COL_SUBTASK_TASK_ID)),
                        title = sc.getString(sc.getColumnIndexOrThrow(TodoDbHelper.COL_SUBTASK_TITLE)),
                        completed = sc.getInt(sc.getColumnIndexOrThrow(TodoDbHelper.COL_SUBTASK_COMPLETED)) == 1,
                        position = sc.getInt(sc.getColumnIndexOrThrow(TodoDbHelper.COL_SUBTASK_POSITION))
                    )
                    subtasksByTaskId.getOrPut(subtask.taskId) { mutableListOf() }.add(subtask)
                }
            }
            return@withContext tasks.map { t ->
                t.copy(subtasks = subtasksByTaskId[t.id] ?: emptyList())
            }
        }
        tasks
    }

    suspend fun getProjects(): List<ProjectEntity> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val projects = mutableListOf<ProjectEntity>()

        val cursor = db.query(
            TodoDbHelper.TABLE_PROJECTS,
            null,
            null,
            null,
            null,
            null,
            "${TodoDbHelper.COL_PROJECT_NAME} ASC"
        )

        cursor.use {
            while (it.moveToNext()) {
                projects.add(
                    ProjectEntity(
                        id = it.getString(it.getColumnIndexOrThrow(TodoDbHelper.COL_PROJECT_ID)),
                        name = it.getString(it.getColumnIndexOrThrow(TodoDbHelper.COL_PROJECT_NAME)),
                        color = it.getString(it.getColumnIndexOrThrow(TodoDbHelper.COL_PROJECT_COLOR))
                    )
                )
            }
        }
        projects
    }

    suspend fun upsertTasks(tasks: List<TaskEntity>) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            for (task in tasks) {
                insertOrUpdateTaskInTransaction(db, task)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyTasksChanged()
    }

    suspend fun upsertTask(task: TaskEntity) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        insertOrUpdateTaskInTransaction(db, task)
        notifyTasksChanged()
    }

    private fun insertOrUpdateTaskInTransaction(db: SQLiteDatabase, task: TaskEntity) {
        val values = ContentValues().apply {
            put(TodoDbHelper.COL_TASK_ID, task.id)
            put(TodoDbHelper.COL_TASK_TITLE, task.title)
            put(TodoDbHelper.COL_TASK_DESCRIPTION, task.description)
            put(TodoDbHelper.COL_TASK_COMPLETED, if (task.completed) 1 else 0)
            put(TodoDbHelper.COL_TASK_PRIORITY, task.priority)
            put(TodoDbHelper.COL_TASK_DUE_DATE, task.dueDate)
            put(TodoDbHelper.COL_TASK_PROJECT_ID, task.projectId)
            put(TodoDbHelper.COL_TASK_PROJECT_NAME, task.projectName)
            put(TodoDbHelper.COL_TASK_POSITION, task.position)
            put(TodoDbHelper.COL_TASK_COMMENT_COUNT, task.commentCount)
            put(TodoDbHelper.COL_TASK_UPDATED_AT, task.updatedAt)
            put(TodoDbHelper.COL_TASK_IS_SYNCED, if (task.isSynced) 1 else 0)
        }
        db.insertWithOnConflict(
            TodoDbHelper.TABLE_TASKS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )

        if (task.subtasks.isNotEmpty()) {
            db.delete(
                TodoDbHelper.TABLE_SUBTASKS,
                "${TodoDbHelper.COL_SUBTASK_TASK_ID} = ?",
                arrayOf(task.id)
            )
            for (subtask in task.subtasks) {
                val sValues = ContentValues().apply {
                    put(TodoDbHelper.COL_SUBTASK_ID, subtask.id)
                    put(TodoDbHelper.COL_SUBTASK_TASK_ID, task.id)
                    put(TodoDbHelper.COL_SUBTASK_TITLE, subtask.title)
                    put(TodoDbHelper.COL_SUBTASK_COMPLETED, if (subtask.completed) 1 else 0)
                    put(TodoDbHelper.COL_SUBTASK_POSITION, subtask.position)
                }
                db.insertWithOnConflict(
                    TodoDbHelper.TABLE_SUBTASKS,
                    null,
                    sValues,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
        }
    }

    suspend fun updateTaskCompleted(taskId: String, completed: Boolean) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(TodoDbHelper.COL_TASK_COMPLETED, if (completed) 1 else 0)
            put(TodoDbHelper.COL_TASK_IS_SYNCED, 0)
        }
        db.update(
            TodoDbHelper.TABLE_TASKS,
            values,
            "${TodoDbHelper.COL_TASK_ID} = ?",
            arrayOf(taskId)
        )
        notifyTasksChanged()
    }

    suspend fun deleteTask(taskId: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(
            TodoDbHelper.TABLE_TASKS,
            "${TodoDbHelper.COL_TASK_ID} = ?",
            arrayOf(taskId)
        )
        db.delete(
            TodoDbHelper.TABLE_SUBTASKS,
            "${TodoDbHelper.COL_SUBTASK_TASK_ID} = ?",
            arrayOf(taskId)
        )
        notifyTasksChanged()
    }

    suspend fun updateTaskPositions(orderedTaskIds: List<String>) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            for ((index, id) in orderedTaskIds.withIndex()) {
                val values = ContentValues().apply {
                    put(TodoDbHelper.COL_TASK_POSITION, index)
                }
                db.update(
                    TodoDbHelper.TABLE_TASKS,
                    values,
                    "${TodoDbHelper.COL_TASK_ID} = ?",
                    arrayOf(id)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyTasksChanged()
    }

    suspend fun upsertProject(project: ProjectEntity) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(TodoDbHelper.COL_PROJECT_ID, project.id)
            put(TodoDbHelper.COL_PROJECT_NAME, project.name)
            put(TodoDbHelper.COL_PROJECT_COLOR, project.color)
        }
        db.insertWithOnConflict(
            TodoDbHelper.TABLE_PROJECTS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
        notifyProjectsChanged()
    }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(
            TodoDbHelper.TABLE_PROJECTS,
            "${TodoDbHelper.COL_PROJECT_ID} = ?",
            arrayOf(projectId)
        )
        // Also clear project_id for tasks that belonged to this project
        val values = ContentValues().apply {
            putNull(TodoDbHelper.COL_TASK_PROJECT_ID)
            putNull(TodoDbHelper.COL_TASK_PROJECT_NAME)
        }
        db.update(
            TodoDbHelper.TABLE_TASKS,
            values,
            "${TodoDbHelper.COL_TASK_PROJECT_ID} = ?",
            arrayOf(projectId)
        )
        notifyTasksChanged()
        notifyProjectsChanged()
    }

    suspend fun upsertProjects(projects: List<ProjectEntity>) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            for (project in projects) {
                val values = ContentValues().apply {
                    put(TodoDbHelper.COL_PROJECT_ID, project.id)
                    put(TodoDbHelper.COL_PROJECT_NAME, project.name)
                    put(TodoDbHelper.COL_PROJECT_COLOR, project.color)
                }
                db.insertWithOnConflict(
                    TodoDbHelper.TABLE_PROJECTS,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyProjectsChanged()
    }

    suspend fun updateSubtaskCompleted(subtaskId: String, completed: Boolean) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(TodoDbHelper.COL_SUBTASK_COMPLETED, if (completed) 1 else 0)
        }
        db.update(
            TodoDbHelper.TABLE_SUBTASKS,
            values,
            "${TodoDbHelper.COL_SUBTASK_ID} = ?",
            arrayOf(subtaskId)
        )
        notifyTasksChanged()
    }

    suspend fun upsertSubtask(subtask: SubtaskEntity) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(TodoDbHelper.COL_SUBTASK_ID, subtask.id)
            put(TodoDbHelper.COL_SUBTASK_TASK_ID, subtask.taskId)
            put(TodoDbHelper.COL_SUBTASK_TITLE, subtask.title)
            put(TodoDbHelper.COL_SUBTASK_COMPLETED, if (subtask.completed) 1 else 0)
            put(TodoDbHelper.COL_SUBTASK_POSITION, subtask.position)
        }
        db.insertWithOnConflict(
            TodoDbHelper.TABLE_SUBTASKS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
        notifyTasksChanged()
    }

    suspend fun deleteSubtask(subtaskId: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(
            TodoDbHelper.TABLE_SUBTASKS,
            "${TodoDbHelper.COL_SUBTASK_ID} = ?",
            arrayOf(subtaskId)
        )
        notifyTasksChanged()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(TodoDbHelper.TABLE_SUBTASKS, null, null)
        db.delete(TodoDbHelper.TABLE_TASKS, null, null)
        db.delete(TodoDbHelper.TABLE_PROJECTS, null, null)
        notifyTasksChanged()
        notifyProjectsChanged()
    }

    private fun cursorToTask(cursor: Cursor): TaskEntity {
        return TaskEntity(
            id = cursor.getString(cursor.getColumnIndexOrThrow(TodoDbHelper.COL_TASK_ID)),
            title = cursor.getString(cursor.getColumnIndexOrThrow(TodoDbHelper.COL_TASK_TITLE)),
            description = cursor.getString(cursor.getColumnIndexOrThrow(TodoDbHelper.COL_TASK_DESCRIPTION)),
            completed = cursor.getInt(cursor.getColumnIndexOrThrow(TodoDbHelper.COL_TASK_COMPLETED)) == 1,
            priority = cursor.getInt(cursor.getColumnIndexOrThrow(TodoDbHelper.COL_TASK_PRIORITY)),
            dueDate = cursor.getString(cursor.getColumnIndexOrThrow(TodoDbHelper.COL_TASK_DUE_DATE)),
            projectId = cursor.getString(cursor.getColumnIndexOrThrow(TodoDbHelper.COL_TASK_PROJECT_ID)),
            projectName = cursor.getString(cursor.getColumnIndexOrThrow(TodoDbHelper.COL_TASK_PROJECT_NAME)),
            position = cursor.getInt(cursor.getColumnIndexOrThrow(TodoDbHelper.COL_TASK_POSITION)),
            commentCount = cursor.getInt(cursor.getColumnIndexOrThrow(TodoDbHelper.COL_TASK_COMMENT_COUNT)),
            updatedAt = cursor.getString(cursor.getColumnIndexOrThrow(TodoDbHelper.COL_TASK_UPDATED_AT)),
            isSynced = cursor.getInt(cursor.getColumnIndexOrThrow(TodoDbHelper.COL_TASK_IS_SYNCED)) == 1
        )
    }
}
