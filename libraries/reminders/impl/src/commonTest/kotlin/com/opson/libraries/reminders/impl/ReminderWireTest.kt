package com.opson.libraries.reminders.impl

import com.opson.libraries.reminders.Reminder
import com.opson.libraries.reminders.ReminderAction
import com.opson.libraries.reminders.ReminderKey
import com.opson.libraries.reminders.ReminderNotification
import com.opson.libraries.reminders.ReminderStyle
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** What the platform keeps for a dose nudge reads back as the same nudge, and holds no dose. */
class ReminderWireTest {

    @Test
    fun aDoseNudgeRoundTripsWithItsMedicationIdOnly() {
        val notification = ReminderNotification(
            reminder = Reminder(ReminderKey.Dose(LocalDate(2026, 10, 29), "9b2c"), Instant.parse("2026-10-29T19:00:00Z")),
            title = "Dose day",
            body = "Tap to log it when you're done.",
            style = ReminderStyle.Subtle,
            actions = listOf(ReminderAction.AlreadyAte to "Already did", ReminderAction.LogIt to "Log it"),
        )
        val encoded = ReminderWire.of(notification).encode()

        assertEquals(notification, ReminderWire.decode(encoded)?.notification())
        assertEquals("dose", ReminderWire.decode(encoded)?.kind)
    }

    @Test
    fun aDoseRecordWithNoMedicationIsDroppedRatherThanGuessedAt() {
        assertNull(ReminderWire(id = "dose-x", atMs = 0, kind = "dose", date = "2026-10-29").reminder())
    }
}
