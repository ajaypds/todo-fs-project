package com.example.todoflow.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderPlannerTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(date: String, time: String) = ZonedDateTime.parse("${date}T$time+05:30[Asia/Kolkata]")

    @Test
    fun remindsAtNineOnTheDueDay() {
        val now = at("2026-11-08", "20:00:00")
        val delay = ReminderPlanner.delayMillis("2026-11-09T23:59:59Z", completed = false, now = now)
        // 20:00 -> 09:00 next day = 13 hours
        assertEquals(13L * 60 * 60 * 1000, delay)
    }

    @Test
    fun acceptsDateOnlyStrings() {
        val now = at("2026-11-08", "08:00:00")
        assertEquals(24L * 60 * 60 * 1000 + 60 * 60 * 1000, ReminderPlanner.delayMillis("2026-11-09", false, now))
    }

    @Test
    fun skipsWhenTodaysReminderTimeAlreadyPassed() {
        val now = at("2026-11-08", "10:30:00")
        assertNull(ReminderPlanner.delayMillis("2026-11-08T23:59:59Z", false, now))
    }

    @Test
    fun remindsLaterTodayIfBeforeNine() {
        val now = at("2026-11-08", "07:30:00")
        assertEquals(90L * 60 * 1000, ReminderPlanner.delayMillis("2026-11-08T23:59:59Z", false, now))
    }

    @Test
    fun skipsPastDueDates() {
        assertNull(ReminderPlanner.delayMillis("2026-11-01T23:59:59Z", false, at("2026-11-08", "08:00:00")))
    }

    @Test
    fun skipsCompletedTasks() {
        assertNull(ReminderPlanner.delayMillis("2026-11-20T23:59:59Z", true, at("2026-11-08", "08:00:00")))
    }

    @Test
    fun skipsMissingOrBrokenDueDates() {
        val now = at("2026-11-08", "08:00:00")
        assertNull(ReminderPlanner.delayMillis(null, false, now))
        assertNull(ReminderPlanner.delayMillis("", false, now))
        assertNull(ReminderPlanner.delayMillis("not-a-date", false, now))
    }

    @Test
    fun dueDayStripsTheTimePart() {
        assertEquals("2026-11-09", ReminderPlanner.dueDay("2026-11-09T23:59:59Z"))
        assertNull(ReminderPlanner.dueDay(null))
        assertNull(ReminderPlanner.dueDay(""))
    }
}
