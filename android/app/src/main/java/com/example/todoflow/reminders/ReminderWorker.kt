package com.example.todoflow.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.todoflow.TodoFlowApplication
import com.example.todoflow.data.local.TaskFilterType
import java.time.LocalDate

/** Shows the "due today" notification for one task, if it is still relevant when it fires. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.success()
        val app = applicationContext as TodoFlowApplication
        if (!app.sessionManager.remindersEnabled.value || !app.sessionManager.isLoggedIn.value) {
            return Result.success()
        }

        val task = app.localDataSource.getTasks(TaskFilterType.ALL).firstOrNull { it.id == taskId }
            ?: return Result.success()
        // Completed, deleted or rescheduled since the reminder was set up: stay quiet
        if (task.completed || ReminderPlanner.dueDay(task.dueDate) != LocalDate.now().toString()) {
            return Result.success()
        }

        ReminderNotifier.showDueToday(applicationContext, task.id, task.title)
        return Result.success()
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
    }
}
