package com.opson.libraries.reminders.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.EventLevel
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.opson.AppEvents
import com.opson.libraries.reminders.MealOpen
import com.opson.libraries.reminders.Reminder
import com.opson.libraries.reminders.ReminderAction
import com.opson.libraries.reminders.ReminderKey
import com.opson.libraries.reminders.ReminderStyle
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.LocalDate
import com.opson.libraries.reminders.DoseNudge
import com.opson.libraries.reminders.DoseOpen
import com.opson.libraries.reminders.DoseReminders
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The coordinator against a fake platform: what it schedules as the flag, the settings and the
 * day move, and what it does with a notification's buttons. The rules themselves are
 * `ReminderPlannerTest`.
 */
class ReminderCoordinatorTest : CoroutineTest() {

    private val today = LocalDate(2026, 10, 25)
    private val now = Instant.parse("2026-10-25T12:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private val chosen = Settings(remindersChosen = true, remindersEvent = EventLevel.Full, quiet = false)

    private val flag = MutableStateFlow(true)
    private val platform = FakeLocalNotifications()
    private val diary = FakeDiary()
    private lateinit var settings: FakeSettingsRepository
    private lateinit var appScope: AppCoroutineScope

    private val doses = FakeDoseReminders()

    private fun coordinator(initial: Settings = chosen, known: Boolean = true): ReminderCoordinator {
        settings = FakeSettingsRepository(initial, known)
        appScope = AppCoroutineScope(dispatchers)
        return ReminderCoordinator(
            config = configFlow(flag),
            settingsRepository = settings,
            diary = diary,
            calendar = FixedCalendar(today),
            appEvents = AppEvents(FakeEventBus()),
            clock = clock,
            notifications = platform,
            strings = { it.key },
            appScope = appScope,
            doseReminders = doses,
        )
    }

    private fun test(block: suspend TestScope.() -> Unit) = runUnitTest {
        try {
            block()
        } finally {
            appScope.cancel()
        }
    }

    private fun meal(slot: MealSlot) = ReminderKey.Meal(today, slot)

    @Test
    fun withTheFlagOffNothingIsScheduled_andWhatWasShowingGoes() = test {
        flag.value = false
        coordinator()

        assertEquals(emptyList(), platform.ids)
        assertTrue(platform.dismissedAll > 0)
    }

    @Test
    fun turningTheFlagOnSchedules_andOffCancels() = test {
        flag.value = false
        coordinator()
        flag.value = true

        assertTrue(meal(MealSlot.Dinner).id in platform.ids)
        assertTrue(meal(MealSlot.Lunch).id in platform.ids)

        flag.value = false
        assertEquals(emptyList(), platform.ids)
    }

    @Test
    fun settingsThatAreNotTheUsersYetScheduleNothing() = test {
        coordinator(known = false)
        assertEquals(0, platform.replaces)

        settings.known.value = true
        assertTrue(platform.ids.isNotEmpty())
    }

    @Test
    fun theTextIsTheCopysResources_inThePlainVoice_andTheLoudnessFollowsTheLevel() = test {
        coordinator()

        val dinner = platform.scheduled.first { it.reminder.key == meal(MealSlot.Dinner) }
        assertEquals("reminders_note_dinner", dinner.title)
        assertEquals("reminders_note_meal_body", dinner.body)
        assertEquals(listOf("reminders_note_already_ate", "reminders_note_log_it", "reminders_note_snooze"), dinner.actions.map { it.second })
        assertEquals(ReminderStyle.Full, dinner.style)

        // A warm tone stored before the setting went keeps the plain words.
        settings.update { it.copy(tone = com.opson.libraries.diary.Tone.Warm, quiet = true) }
        val quiet = platform.scheduled.first { it.reminder.key == meal(MealSlot.Dinner) }
        assertEquals("reminders_note_dinner", quiet.title)
        assertEquals(ReminderStyle.FullQuiet, quiet.style)
    }

    @Test
    fun loggingAMealDropsItsNudge_andTakesADeliveredOneOffTheScreen() = test {
        coordinator()
        assertTrue(meal(MealSlot.Dinner).id in platform.ids)

        diary.days.value = mapOf(today to Day.empty(today).copy(ateNoDetails = setOf(MealSlot.Dinner)))

        assertTrue(meal(MealSlot.Dinner).id !in platform.ids)
        assertTrue(meal(MealSlot.Dinner) in platform.dismissed)
        assertTrue(ReminderKey.Meal(LocalDate(2026, 10, 26), MealSlot.Dinner).id in platform.ids, "tomorrow's stays")
    }

    @Test
    fun alreadyAteMarksTheMeal_withNoDetails() = test {
        val coordinator = coordinator()
        val dinner = Reminder(meal(MealSlot.Dinner), Instant.parse("2026-10-25T19:00:00Z"))

        coordinator.alreadyAte(dinner)

        assertEquals(setOf(MealSlot.Dinner), diary.days.value.getValue(today).ateNoDetails)
        assertTrue(meal(MealSlot.Dinner) in platform.dismissed)
        assertTrue(meal(MealSlot.Dinner).id !in platform.ids)
    }

    @Test
    fun aSnoozeComesBackInThirtyMinutes_andSurvivesAReplan() = test {
        val coordinator = coordinator()
        val lunch = Reminder(meal(MealSlot.Lunch), Instant.parse("2026-10-25T12:00:00Z"))

        coordinator.snooze(lunch)

        val snoozed = platform.scheduled.single { it.reminder.snoozed }
        assertEquals(now + 30.minutes, snoozed.reminder.at)
        assertEquals(meal(MealSlot.Lunch), snoozed.reminder.key)

        settings.update { it.copy(quiet = true) } // any replan
        assertTrue(snoozed.reminder.id in platform.ids)
    }

    @Test
    fun aTapOpensTheLoggerForThatMealOnce() = test {
        val coordinator = coordinator()
        val dinner = Reminder(meal(MealSlot.Dinner), Instant.parse("2026-10-25T19:00:00Z"))

        coordinator.opened(dinner, ReminderAction.LogIt)

        assertEquals(MealOpen(MealSlot.Dinner, today), coordinator.requests.first())
        assertTrue(meal(MealSlot.Dinner) in platform.dismissed)
    }

    @Test
    fun aDoseDayIsScheduledWithWordsThatNeverSayTheDose_andGoesOnceLogged() = test {
        val dose = ReminderKey.Dose(today, "med")
        doses.nudges.value = listOf(DoseNudge("med", today, LocalTime(19, 0)))
        coordinator()

        val nudge = platform.scheduled.single { it.reminder.key == dose }
        assertEquals("medication_reminder_title", nudge.title)
        assertEquals("medication_reminder_body_text", nudge.body)
        assertEquals(listOf("medication_reminder_already", "medication_reminder_log"), nudge.actions.map { it.second })

        doses.nudges.value = listOf(DoseNudge("med", today, LocalTime(19, 0), covered = true))
        assertTrue(dose.id !in platform.ids)
        assertTrue(dose in platform.dismissed)
    }

    @Test
    fun aDoseNudgeOpensTheDoseSheet_andAlreadyDidLogsAtItsTime() = test {
        doses.nudges.value = listOf(DoseNudge("med", today, LocalTime(19, 0)))
        val coordinator = coordinator()
        val nudge = Reminder(ReminderKey.Dose(today, "med"), Instant.parse("2026-10-25T19:00:00Z"))

        coordinator.opened(nudge, ReminderAction.LogIt)
        assertEquals(DoseOpen("med", today), coordinator.doseRequests.first())

        coordinator.alreadyAte(nudge)
        assertEquals(listOf(Triple("med", today, nudge.at)), doses.didCalls)
        assertTrue(diary.days.value[today]?.ateNoDetails.isNullOrEmpty(), "a dose marks no meal")
    }

    @Test
    fun aRefreshReappliesAnUnchangedPlan_asAfterAReboot() = test {
        val coordinator = coordinator()
        val before = platform.replaces

        coordinator.refresh()

        assertTrue(platform.replaces > before)
    }
}

/** Dose days the test sets, and the "Already did" taps it saw. */
private class FakeDoseReminders : DoseReminders {
    val nudges = MutableStateFlow<List<DoseNudge>>(emptyList())
    val didCalls = mutableListOf<Triple<String, LocalDate, Instant>>()

    override fun upcoming(from: LocalDate, days: Int): Flow<List<DoseNudge>> = nudges

    override suspend fun alreadyDid(medicationId: String, date: LocalDate, at: Instant) {
        didCalls += Triple(medicationId, date, at)
    }
}
