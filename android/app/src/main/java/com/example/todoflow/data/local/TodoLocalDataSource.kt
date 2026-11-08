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
    PROJECT,
    LABEL
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

    fun observeTasks(
        filter: TaskFilterType = TaskFilterType.ALL,
        projectId: String? = null,
        labelId: String? = null
    ): Flow<List<TaskEntity>> = flow {
        taskUpdatesTrigger.collect {
            val tasks = getTasks(filter, projectId, labelId)
            emit(tasks)
        }
    }.flowOn(Dispatchers.IO).distinctUntilChanged()

    fun observeProjects(): Flow<List<ProjectEntity>> = flow {
        projectUpdatesTrigger.collect {
            val projects = getProjects()
            emit(projects)
        }
    }.flowOn(Dispatchers.IO).distinctUntilChanged()

    suspend fun getTasks(
        filter: TaskFilterType = TaskFilterType.ALL,
        projectId: String? = null,
        labelId: String? = null
    ): List<TaskEntity> = withContext(Dispatchers.IO) {
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
                // Due today or overdue, and not finished yet
                selectionBuilder.append(
                    "${TodoDbHelper.COL_TASK_DUE_DATE} IS NOT NULL AND substr(${TodoDbHelper.COL_TASK_DUE_DATE}, 1, 10) <= ? " +
                        "AND ${TodoDbHelper.COL_TASK_COMPLETED} = 0"
                )
                selectionArgs.add(java.time.LocalDate.now().toString())
            }
            TaskFilterType.UPCOMING -> {
                // Due after today, not finished yet
                selectionBuilder.append(
                    "${TodoDbHelper.COL_TASK_DUE_DATE} IS NOT NULL AND substr(${TodoDbHelper.COL_TASK_DUE_DATE}, 1, 10) > ? " +
                        "AND ${TodoDbHelper.COL_TASK_COMPLETED} = 0"
                )
                selectionArgs.add(java.time.LocalDate.now().toString())
            }
            TaskFilterType.COMPLETED -> {
                selectionBuilder.append("${TodoDbHelper.COL_TASK_COMPLETED} = 1")
            }
            TaskFilterType.LABEL -> {
                if (!labelId.isNullOrBlank()) {
                    selectionBuilder.append(
                        "${TodoDbHelper.COL_TASK_ID} IN (SELECT ${TodoDbHelper.COL_TL_TASK_ID} FROM " +
                            "${TodoDbHelper.TABLE_TASK_LABELS} WHERE ${TodoDbHelper.COL_TL_LABEL_ID} = ?)"
                    )
                    selectionArgs.add(labelId)
                }
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
            val labelsByTaskId = mutableMapOf<String, MutableList<LabelEntity>>()
            db.rawQuery(
                "SELECT tl.${TodoDbHelper.COL_TL_TASK_ID}, l.${TodoDbHelper.COL_LABEL_ID}, " +
                    "l.${TodoDbHelper.COL_LABEL_NAME}, l.${TodoDbHelper.COL_LABEL_COLOR} " +
                    "FROM ${TodoDbHelper.TABLE_TASK_LABELS} tl " +
                    "JOIN ${TodoDbHelper.TABLE_LABELS} l ON l.${TodoDbHelper.COL_LABEL_ID} = tl.${TodoDbHelper.COL_TL_LABEL_ID} " +
                    "ORDER BY l.${TodoDbHelper.COL_LABEL_NAME} ASC",
                null
            ).use { lc ->
                while (lc.moveToNext()) {
                    labelsByTaskId.getOrPut(lc.getString(0)) { mutableListOf() }
                        .add(LabelEntity(lc.getString(1), lc.getString(2), lc.getString(3)))
                }
            }
            return@withContext tasks.map { t ->
                t.copy(
                    subtasks = subtasksByTaskId[t.id] ?: emptyList(),
                    labels = labelsByTaskId[t.id] ?: emptyList()
                )
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
        val finalCompleted = task.completed
        val finalIsSynced = task.isSynced

        val cursor = db.query(
            TodoDbHelper.TABLE_TASKS,
            arrayOf(TodoDbHelper.COL_TASK_COMPLETED, TodoDbHelper.COL_TASK_IS_SYNCED),
            "${TodoDbHelper.COL_TASK_ID} = ?",
            arrayOf(task.id),
            null, null, null
        )
        cursor.use {
            if (it.moveToFirst()) {
                val isSynced = it.getInt(it.getColumnIndexOrThrow(TodoDbHelper.COL_TASK_IS_SYNCED)) == 1
                // A local change is still waiting to be uploaded: don't overwrite it with server data
                if (!isSynced && task.isSynced) return

            }
        }

        val values = ContentValues().apply {
            put(TodoDbHelper.COL_TASK_ID, task.id)
            put(TodoDbHelper.COL_TASK_TITLE, task.title)
            put(TodoDbHelper.COL_TASK_DESCRIPTION, task.description)
            put(TodoDbHelper.COL_TASK_COMPLETED, if (finalCompleted) 1 else 0)
            put(TodoDbHelper.COL_TASK_PRIORITY, task.priority)
            put(TodoDbHelper.COL_TASK_DUE_DATE, task.dueDate)
            put(TodoDbHelper.COL_TASK_PROJECT_ID, task.projectId)
            put(TodoDbHelper.COL_TASK_PROJECT_NAME, task.projectName)
            put(TodoDbHelper.COL_TASK_POSITION, task.position)
            put(TodoDbHelper.COL_TASK_COMMENT_COUNT, task.commentCount)
            put(TodoDbHelper.COL_TASK_UPDATED_AT, task.updatedAt)
            put(TodoDbHelper.COL_TASK_IS_SYNCED, if (finalIsSynced) 1 else 0)
        }
        db.insertWithOnConflict(
            TodoDbHelper.TABLE_TASKS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )

        // Labels travel with the task
        db.delete(TodoDbHelper.TABLE_TASK_LABELS, "${TodoDbHelper.COL_TL_TASK_ID} = ?", arrayOf(task.id))
        for (label in task.labels) {
            putLabel(db, label)
            db.insertWithOnConflict(
                TodoDbHelper.TABLE_TASK_LABELS,
                null,
                ContentValues().apply {
                    put(TodoDbHelper.COL_TL_TASK_ID, task.id)
                    put(TodoDbHelper.COL_TL_LABEL_ID, label.id)
                },
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }

        if (finalIsSynced) {
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

    suspend fun markTaskSynced(taskId: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(TodoDbHelper.COL_TASK_IS_SYNCED, 1)
        }
        db.update(
            TodoDbHelper.TABLE_TASKS,
            values,
            "${TodoDbHelper.COL_TASK_ID} = ?",
            arrayOf(taskId)
        )
    }

    /**
     * Removes already-synced tasks (and their subtasks) that no longer exist on the server,
     * e.g. deleted from the web app or another device. Unsynced local changes are kept.
     */
    suspend fun pruneSyncedTasksNotIn(serverIds: Set<String>) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val staleIds = mutableListOf<String>()
        db.query(
            TodoDbHelper.TABLE_TASKS,
            arrayOf(TodoDbHelper.COL_TASK_ID),
            "${TodoDbHelper.COL_TASK_IS_SYNCED} = 1",
            null, null, null, null
        ).use {
            while (it.moveToNext()) {
                val id = it.getString(0)
                if (id !in serverIds) staleIds.add(id)
            }
        }
        if (staleIds.isEmpty()) return@withContext
        db.beginTransaction()
        try {
            for (id in staleIds) {
                db.delete(TodoDbHelper.TABLE_TASKS, "${TodoDbHelper.COL_TASK_ID} = ?", arrayOf(id))
                db.delete(TodoDbHelper.TABLE_SUBTASKS, "${TodoDbHelper.COL_SUBTASK_TASK_ID} = ?", arrayOf(id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyTasksChanged()
    }

    /** Removes cached projects that no longer exist on the server (keeps not-yet-synced temp ones). */
    suspend fun pruneProjectsNotIn(serverIds: Set<String>) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val staleIds = mutableListOf<String>()
        db.query(
            TodoDbHelper.TABLE_PROJECTS,
            arrayOf(TodoDbHelper.COL_PROJECT_ID),
            null, null, null, null, null
        ).use {
            while (it.moveToNext()) {
                val id = it.getString(0)
                if (id !in serverIds && !id.startsWith("temp_p_")) staleIds.add(id)
            }
        }
        if (staleIds.isEmpty()) return@withContext
        db.beginTransaction()
        try {
            for (id in staleIds) {
                db.delete(TodoDbHelper.TABLE_PROJECTS, "${TodoDbHelper.COL_PROJECT_ID} = ?", arrayOf(id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyProjectsChanged()
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

    // ---------------------------------------------------------------- labels

    private fun putLabel(db: SQLiteDatabase, label: LabelEntity) {
        db.insertWithOnConflict(
            TodoDbHelper.TABLE_LABELS,
            null,
            ContentValues().apply {
                put(TodoDbHelper.COL_LABEL_ID, label.id)
                put(TodoDbHelper.COL_LABEL_NAME, label.name)
                put(TodoDbHelper.COL_LABEL_COLOR, label.color)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private val labelUpdatesTrigger = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    fun observeLabels(): Flow<List<LabelEntity>> = flow {
        labelUpdatesTrigger.collect { emit(queryLabels()) }
    }.flowOn(Dispatchers.IO).distinctUntilChanged()

    private fun queryLabels(): List<LabelEntity> {
        val labels = mutableListOf<LabelEntity>()
        dbHelper.readableDatabase.query(
            TodoDbHelper.TABLE_LABELS, null, null, null, null, null,
            "${TodoDbHelper.COL_LABEL_NAME} COLLATE NOCASE ASC"
        ).use {
            while (it.moveToNext()) {
                labels.add(
                    LabelEntity(
                        id = it.getString(it.getColumnIndexOrThrow(TodoDbHelper.COL_LABEL_ID)),
                        name = it.getString(it.getColumnIndexOrThrow(TodoDbHelper.COL_LABEL_NAME)),
                        color = it.getString(it.getColumnIndexOrThrow(TodoDbHelper.COL_LABEL_COLOR))
                    )
                )
            }
        }
        return labels
    }

    /** Replaces the cached label list with the server's list. */
    suspend fun replaceLabels(labels: List<LabelEntity>) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete(TodoDbHelper.TABLE_LABELS, null, null)
            labels.forEach { putLabel(db, it) }
            // Drop links to labels that no longer exist
            db.execSQL(
                "DELETE FROM ${TodoDbHelper.TABLE_TASK_LABELS} WHERE ${TodoDbHelper.COL_TL_LABEL_ID} NOT IN " +
                    "(SELECT ${TodoDbHelper.COL_LABEL_ID} FROM ${TodoDbHelper.TABLE_LABELS})"
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        labelUpdatesTrigger.tryEmit(Unit)
        notifyTasksChanged()
    }

    suspend fun upsertLabel(label: LabelEntity) = withContext(Dispatchers.IO) {
        putLabel(dbHelper.writableDatabase, label)
        labelUpdatesTrigger.tryEmit(Unit)
    }

    /** Applies a local (optimistic) edit to a task; the row stays "unsynced" until the server confirms. */
    suspend fun updateTaskFields(
        taskId: String,
        title: String,
        description: String?,
        priority: Int,
        dueDate: String?,
        projectId: String?,
        projectName: String?,
        labels: List<LabelEntity>
    ) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put(TodoDbHelper.COL_TASK_TITLE, title)
                put(TodoDbHelper.COL_TASK_DESCRIPTION, description)
                put(TodoDbHelper.COL_TASK_PRIORITY, priority)
                put(TodoDbHelper.COL_TASK_DUE_DATE, dueDate)
                put(TodoDbHelper.COL_TASK_PROJECT_ID, projectId)
                put(TodoDbHelper.COL_TASK_PROJECT_NAME, projectName)
                put(TodoDbHelper.COL_TASK_IS_SYNCED, 0)
            }
            db.update(TodoDbHelper.TABLE_TASKS, values, "${TodoDbHelper.COL_TASK_ID} = ?", arrayOf(taskId))
            db.delete(TodoDbHelper.TABLE_TASK_LABELS, "${TodoDbHelper.COL_TL_TASK_ID} = ?", arrayOf(taskId))
            for (label in labels) {
                putLabel(db, label)
                db.insertWithOnConflict(
                    TodoDbHelper.TABLE_TASK_LABELS,
                    null,
                    ContentValues().apply {
                        put(TodoDbHelper.COL_TL_TASK_ID, taskId)
                        put(TodoDbHelper.COL_TL_LABEL_ID, label.id)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        labelUpdatesTrigger.tryEmit(Unit)
        notifyTasksChanged()
    }

    suspend fun markTaskUnsynced(taskId: String) = withContext(Dispatchers.IO) {
        dbHelper.writableDatabase.update(
            TodoDbHelper.TABLE_TASKS,
            ContentValues().apply { put(TodoDbHelper.COL_TASK_IS_SYNCED, 0) },
            "${TodoDbHelper.COL_TASK_ID} = ?",
            arrayOf(taskId)
        )
        notifyTasksChanged()
    }

    // ------------------------------------------------- pending (offline) operations

    private val opsTrigger = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    fun observePendingOpCount(): Flow<Int> = flow {
        opsTrigger.collect { emit(countPendingOps()) }
    }.flowOn(Dispatchers.IO).distinctUntilChanged()

    private fun countPendingOps(): Int =
        dbHelper.readableDatabase.rawQuery("SELECT COUNT(*) FROM ${TodoDbHelper.TABLE_PENDING_OPS}", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    suspend fun enqueueOp(type: String, entityId: String, parentId: String?, payload: String): Long =
        withContext(Dispatchers.IO) {
            val id = dbHelper.writableDatabase.insert(
                TodoDbHelper.TABLE_PENDING_OPS,
                null,
                ContentValues().apply {
                    put(TodoDbHelper.COL_OP_TYPE, type)
                    put(TodoDbHelper.COL_OP_ENTITY_ID, entityId)
                    put(TodoDbHelper.COL_OP_PARENT_ID, parentId)
                    put(TodoDbHelper.COL_OP_PAYLOAD, payload)
                }
            )
            opsTrigger.tryEmit(Unit)
            id
        }

    suspend fun getPendingOps(): List<PendingOp> = withContext(Dispatchers.IO) {
        val ops = mutableListOf<PendingOp>()
        dbHelper.readableDatabase.query(
            TodoDbHelper.TABLE_PENDING_OPS, null, null, null, null, null,
            "${TodoDbHelper.COL_OP_ID} ASC"
        ).use {
            while (it.moveToNext()) {
                ops.add(
                    PendingOp(
                        id = it.getLong(it.getColumnIndexOrThrow(TodoDbHelper.COL_OP_ID)),
                        type = it.getString(it.getColumnIndexOrThrow(TodoDbHelper.COL_OP_TYPE)),
                        entityId = it.getString(it.getColumnIndexOrThrow(TodoDbHelper.COL_OP_ENTITY_ID)),
                        parentId = it.getString(it.getColumnIndexOrThrow(TodoDbHelper.COL_OP_PARENT_ID)),
                        payload = it.getString(it.getColumnIndexOrThrow(TodoDbHelper.COL_OP_PAYLOAD)),
                        attempts = it.getInt(it.getColumnIndexOrThrow(TodoDbHelper.COL_OP_ATTEMPTS))
                    )
                )
            }
        }
        ops
    }

    suspend fun deleteOp(opId: Long) = withContext(Dispatchers.IO) {
        dbHelper.writableDatabase.delete(
            TodoDbHelper.TABLE_PENDING_OPS, "${TodoDbHelper.COL_OP_ID} = ?", arrayOf(opId.toString())
        )
        opsTrigger.tryEmit(Unit)
    }

    suspend fun incrementOpAttempts(opId: Long) = withContext(Dispatchers.IO) {
        dbHelper.writableDatabase.execSQL(
            "UPDATE ${TodoDbHelper.TABLE_PENDING_OPS} SET ${TodoDbHelper.COL_OP_ATTEMPTS} = ${TodoDbHelper.COL_OP_ATTEMPTS} + 1 " +
                "WHERE ${TodoDbHelper.COL_OP_ID} = ?",
            arrayOf(opId)
        )
    }

    /** True if any queued operation still refers to [id] (as the entity or as its parent). */
    suspend fun hasPendingOps(id: String): Boolean = withContext(Dispatchers.IO) {
        dbHelper.readableDatabase.rawQuery(
            "SELECT 1 FROM ${TodoDbHelper.TABLE_PENDING_OPS} WHERE ${TodoDbHelper.COL_OP_ENTITY_ID} = ? " +
                "OR ${TodoDbHelper.COL_OP_PARENT_ID} = ? LIMIT 1",
            arrayOf(id, id)
        ).use { it.moveToFirst() }
    }

    /** Drops every queued operation about [id] (used when an item that was never uploaded is deleted). */
    suspend fun removeOpsFor(id: String) = withContext(Dispatchers.IO) {
        dbHelper.writableDatabase.delete(
            TodoDbHelper.TABLE_PENDING_OPS,
            "${TodoDbHelper.COL_OP_ENTITY_ID} = ? OR ${TodoDbHelper.COL_OP_PARENT_ID} = ?",
            arrayOf(id, id)
        )
        opsTrigger.tryEmit(Unit)
    }

    suspend fun pendingDeletedTaskIds(): Set<String> = withContext(Dispatchers.IO) {
        val ids = mutableSetOf<String>()
        dbHelper.readableDatabase.query(
            TodoDbHelper.TABLE_PENDING_OPS,
            arrayOf(TodoDbHelper.COL_OP_ENTITY_ID),
            "${TodoDbHelper.COL_OP_TYPE} = ?",
            arrayOf("DELETE_TASK"),
            null, null, null
        ).use { while (it.moveToNext()) ids.add(it.getString(0)) }
        ids
    }

    /**
     * After the server created something that was first stored under a temporary id, point every
     * remaining queued operation (entity, parent and payload) at the real id.
     */
    suspend fun remapPendingIds(tempId: String, realId: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.execSQL(
            "UPDATE ${TodoDbHelper.TABLE_PENDING_OPS} SET " +
                "${TodoDbHelper.COL_OP_ENTITY_ID} = REPLACE(${TodoDbHelper.COL_OP_ENTITY_ID}, ?, ?), " +
                "${TodoDbHelper.COL_OP_PARENT_ID} = REPLACE(${TodoDbHelper.COL_OP_PARENT_ID}, ?, ?), " +
                "${TodoDbHelper.COL_OP_PAYLOAD} = REPLACE(${TodoDbHelper.COL_OP_PAYLOAD}, ?, ?)",
            arrayOf(tempId, realId, tempId, realId, tempId, realId)
        )
        // Cached rows that point at the temporary project
        db.execSQL(
            "UPDATE ${TodoDbHelper.TABLE_TASKS} SET ${TodoDbHelper.COL_TASK_PROJECT_ID} = ? " +
                "WHERE ${TodoDbHelper.COL_TASK_PROJECT_ID} = ?",
            arrayOf(realId, tempId)
        )
        db.execSQL(
            "UPDATE ${TodoDbHelper.TABLE_SUBTASKS} SET ${TodoDbHelper.COL_SUBTASK_TASK_ID} = ? " +
                "WHERE ${TodoDbHelper.COL_SUBTASK_TASK_ID} = ?",
            arrayOf(realId, tempId)
        )
        opsTrigger.tryEmit(Unit)
        notifyTasksChanged()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(TodoDbHelper.TABLE_SUBTASKS, null, null)
        db.delete(TodoDbHelper.TABLE_TASKS, null, null)
        db.delete(TodoDbHelper.TABLE_PROJECTS, null, null)
        db.delete(TodoDbHelper.TABLE_LABELS, null, null)
        db.delete(TodoDbHelper.TABLE_TASK_LABELS, null, null)
        db.delete(TodoDbHelper.TABLE_PENDING_OPS, null, null)
        notifyTasksChanged()
        notifyProjectsChanged()
        labelUpdatesTrigger.tryEmit(Unit)
        opsTrigger.tryEmit(Unit)
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
