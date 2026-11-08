package com.example.todoflow.reminders

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.WorkInfo
import com.example.todoflow.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReminderSchedulerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Another test in the same JVM may already have initialised WorkManager
        runCatching { WorkManager.initialize(context, Configuration.Builder().build()) }
    }

    @Test
    fun schedulesOneReminderPerFutureDueTaskAndCancelsWhenDisabled() {
        val now = ZonedDateTime.now()
        val tomorrow = now.plusDays(2).toLocalDate().toString()
        val tasks = listOf(
            TaskEntity(id = "a", title = "A", dueDate = "${tomorrow}T10:00:00Z"),
            TaskEntity(id = "b", title = "B", dueDate = tomorrow),
            TaskEntity(id = "c", title = "C"),
        )
        ReminderScheduler.sync(context, tasks, enabled = true, now = now)
        // WorkManager commits enqueue/cancel asynchronously: wait for the expected count
        fun active(expected: Int): Int {
            var count = -1
            repeat(100) {
                count = WorkManager.getInstance(context).getWorkInfosByTag("due_reminder").get()
                    .count { it.state == WorkInfo.State.ENQUEUED }
                if (count == expected) return count
                Thread.sleep(50)
            }
            return count
        }
        assertEquals(2, active(2))
        ReminderScheduler.sync(context, tasks, enabled = true, now = now) // idempotent
        assertEquals(2, active(2))
        ReminderScheduler.sync(context, tasks, enabled = false, now = now)
        assertEquals(0, active(0))
    }
}
