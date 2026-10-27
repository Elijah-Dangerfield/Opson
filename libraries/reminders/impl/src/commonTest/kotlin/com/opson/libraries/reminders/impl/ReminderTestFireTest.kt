package com.opson.libraries.reminders.impl

import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.reminders.NotificationPermission
import com.opson.libraries.reminders.ReminderAction
import com.opson.libraries.reminders.ReminderKey
import com.opson.libraries.reminders.ReminderStyle
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The QA menu's fire-now: real copy, a few seconds out, never on top of the scheduled nudge. */
class ReminderTestFireTest : CoroutineTest() {

    private val now = Instant.parse("2026-10-25T12:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private val lunch = ReminderKey.Meal(LocalDate(2026, 10, 25), MealSlot.Lunch)

    private fun fire(platform: FakeLocalNotifications, settings: Settings = Settings()) =
        DefaultReminderTestFire(platform, FakeSettingsRepository(settings), { it.key }, clock)

    @Test
    fun aMealNudge_goesOutSecondsFromNow_withItsButtons_evenWithMealRemindersOff() = runUnitTest {
        val platform = FakeLocalNotifications()

        val result = fire(platform, Settings(quiet = false)).fire(lunch)

        assertEquals(NotificationPermission.Granted, result)
        val sent = platform.scheduled.single()
        assertEquals(now + 5.seconds, sent.reminder.at)
        assertEquals("reminders_note_lunch", sent.title)
        assertEquals(listOf(ReminderAction.AlreadyAte, ReminderAction.LogIt, ReminderAction.Snooze), sent.actions.map { it.first })
        assertEquals(ReminderStyle.Full, sent.style, "Off would be the silent style, easy to miss while testing")
    }

    @Test
    fun itNeverReplacesTheScheduledNudgeForTheSameMeal() = runUnitTest {
        val platform = FakeLocalNotifications()

        fire(platform).fire(lunch)

        assertTrue(platform.ids.single() != lunch.id)
    }

    @Test
    fun withoutPermission_nothingFires() = runUnitTest {
        val platform = FakeLocalNotifications(permission = NotificationPermission.Denied)

        assertEquals(NotificationPermission.Denied, fire(platform).fire(lunch))
        assertTrue(platform.scheduled.isEmpty())
    }
}
