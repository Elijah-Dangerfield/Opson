package com.opson.libraries.reminders

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.EventLevel
import com.opson.libraries.diary.Settings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Dose nudges (`release.medication`): when they fire, when they go, and what they say. */
class DoseReminderPlanTest {

    private val today = LocalDate(2026, 10, 29)
    private val noon = Instant.parse("2026-10-29T12:00:00Z")
    private val evening = DoseNudge("med", today, LocalTime(19, 0))

    /** Meal reminders never chosen: a dose nudge does not wait on them. */
    private fun inputs(doses: List<DoseNudge>, enabled: Boolean = true, day: Day = Day.empty(today), now: Instant = noon) =
        PlanInputs(enabled = enabled, settings = Settings(), today = day, now = now, timeZone = TimeZone.UTC, doses = doses)

    @Test
    fun aDoseDayGetsANudgeAtItsTime() {
        val plan = ReminderPlanner.plan(inputs(listOf(evening)))
        assertEquals(listOf(Reminder(ReminderKey.Dose(today, "med"), Instant.parse("2026-10-29T19:00:00Z"))), plan)
        assertEquals("dose-2026-10-29-med", plan.single().id)
    }

    @Test
    fun aCoveredDoseDayIsDroppedAndItsDeliveredNudgeTakenOff() {
        val covered = evening.copy(covered = true)
        assertEquals(emptyList(), ReminderPlanner.plan(inputs(listOf(covered))))
        assertEquals(listOf(ReminderKey.Dose(today, "med")), ReminderPlanner.dosesDoneToday(inputs(listOf(covered))))
    }

    @Test
    fun aPassedTimeAndTheReminderFlagBothMeanNothing() {
        assertEquals(emptyList(), ReminderPlanner.plan(inputs(listOf(evening), now = Instant.parse("2026-10-29T20:00:00Z"))))
        assertEquals(emptyList(), ReminderPlanner.plan(inputs(listOf(evening), enabled = false)))
    }

    @Test
    fun closingTheDayDoesNotDropADose() {
        val closed = Day.empty(today).copy(closedAt = noon)
        assertEquals(1, ReminderPlanner.plan(inputs(listOf(evening), day = closed)).size)
    }

    @Test
    fun aDoseDayPastTheHorizonWaits() {
        val far = evening.copy(date = LocalDate(2026, 11, 12))
        assertEquals(emptyList(), ReminderPlanner.plan(inputs(listOf(far))))
    }

    /** Lock screens are public: the words are fixed resources with no argument. */
    @Test
    fun theNudgeNeverSaysTheDoseOrTheMedication() {
        val text = ReminderCopy.text(ReminderKey.Dose(today, "med"))
        assertEquals("medication_reminder_title", text.title.key)
        assertEquals("medication_reminder_body_text", text.body.key)
        assertEquals(listOf(ReminderAction.AlreadyAte, ReminderAction.LogIt), text.actions.map { it.first })
        val noQuickLog = ReminderCopy.text(ReminderKey.Dose(today, "med"), quickLog = false)
        assertEquals(listOf(ReminderAction.LogIt), noQuickLog.actions.map { it.first })
    }

    @Test
    fun aDoseNudgeFollowsTheLevelAndQuietMode() {
        val key = ReminderKey.Dose(today, "med")
        assertEquals(ReminderStyle.Subtle, ReminderCopy.style(key, Settings()))
        val full = Settings(remindersChosen = true, remindersEvent = EventLevel.Full, quiet = false)
        assertEquals(ReminderStyle.Full, ReminderCopy.style(key, full))
        assertEquals(ReminderStyle.FullQuiet, ReminderCopy.style(key, full.copy(quiet = true)))
    }
}
