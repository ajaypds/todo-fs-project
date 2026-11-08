package com.example.todoflow.reminders

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.todoflow.data.local.TaskEntity
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Keeps one scheduled WorkManager job per task that is due in the future (fires at
 * [ReminderPlanner.REMINDER_HOUR] on the due day). Call [sync] whenever tasks or the setting change.
 */
object ReminderScheduler {

    private const val TAG_ALL = "due_reminder"
    private const val TAG_TASK_PREFIX = "task:"
    private const val TAG_DUE_PREFIX = "due:"

    private fun uniqueName(taskId: String) = "reminder_$taskId"

    /** Blocking (reads WorkManager state): call from a background thread. */
    fun sync(context: Context, tasks: List<TaskEntity>, enabled: Boolean, now: ZonedDateTime = ZonedDateTime.now()) {
        val workManager = WorkManager.getInstance(context)

        // taskId -> (delay, due day) for every reminder that should exist
        val desired = if (!enabled) {
            emptyMap()
        } else {
            tasks.filter { !it.id.startsWith("temp_") }.mapNotNull { task ->
                ReminderPlanner.delayMillis(task.dueDate, task.completed, now)?.let { delay ->
                    task.id to (delay to ReminderPlanner.dueDay(task.dueDate).orEmpty())
                }
            }.toMap()
        }

        // taskId -> due day of reminders that are already scheduled
        val existing = workManager.getWorkInfosByTag(TAG_ALL).get()
            .filter { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
            .mapNotNull { info ->
                val taskId = info.tags.firstOrNull { it.startsWith(TAG_TASK_PREFIX) }?.removePrefix(TAG_TASK_PREFIX)
                val dueDay = info.tags.firstOrNull { it.startsWith(TAG_DUE_PREFIX) }?.removePrefix(TAG_DUE_PREFIX)
                if (taskId != null) taskId to dueDay else null
            }.toMap()

        // Remove reminders for tasks that are done, deleted, no longer due, or when disabled
        (existing.keys - desired.keys).forEach { workManager.cancelUniqueWork(uniqueName(it)) }

        // Add new ones and replace those whose due date changed
        desired.forEach { (taskId, plan) ->
            val (delay, dueDay) = plan
            if (existing[taskId] == dueDay) return@forEach
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(Data.Builder().putString(ReminderWorker.KEY_TASK_ID, taskId).build())
                .addTag(TAG_ALL)
                .addTag(TAG_TASK_PREFIX + taskId)
                .addTag(TAG_DUE_PREFIX + dueDay)
                .build()
            workManager.enqueueUniqueWork(uniqueName(taskId), ExistingWorkPolicy.REPLACE, request)
        }
    }
}
