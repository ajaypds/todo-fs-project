package com.example.todoflow.reminders

import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/** Decides when a due-date reminder should fire. Pure logic so it can be unit tested. */
object ReminderPlanner {

    /** Local time of day (hour) at which the reminder for a task due that day is shown. */
    const val REMINDER_HOUR = 9

    /**
     * Milliseconds from [now] until the reminder for a task due on [dueDate] ("yyyy-MM-dd..." as
     * stored by the app/server), or null when there is nothing to schedule: no or unparsable due
     * date, task already completed, or that day's reminder time has already passed.
     */
    fun delayMillis(dueDate: String?, completed: Boolean, now: ZonedDateTime): Long? {
        if (completed || dueDate.isNullOrBlank()) return null
        val day = try {
            LocalDate.parse(dueDate.substringBefore("T"))
        } catch (_: Exception) {
            return null
        }
        val fireAt = day.atTime(REMINDER_HOUR, 0).atZone(now.zone)
        if (!fireAt.isAfter(now)) return null
        return Duration.between(now, fireAt).toMillis()
    }

    /** The due day (local date string) a reminder was scheduled for; used to detect changed due dates. */
    fun dueDay(dueDate: String?): String? = dueDate?.substringBefore("T")?.takeIf { it.isNotBlank() }
}
