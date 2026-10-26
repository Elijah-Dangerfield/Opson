package com.opson.libraries.reminders

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.EventLevel
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealReminders
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.MealTimes
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The plan: what should be waiting, given the flag, the settings, today and the clock. */
class ReminderPlannerTest {

    private val today = LocalDate(2026, 10, 25)
    private val noon = Instant.parse("2026-10-25T12:00:00Z")

    /** Someone who chose Subtle with the prototype's meals: breakfast, lunch and dinner. */
    private val chosen = Settings(remindersChosen = true, remindersEvent = EventLevel.Subtle)

    private fun inputs(
        settings: Settings = chosen,
        day: Day = Day.empty(today),
        now: Instant = noon,
        enabled: Boolean = true,
        pending: List<Reminder> = emptyList(),
    ) = PlanInputs(enabled = enabled, settings = settings, today = day, now = now, timeZone = TimeZone.UTC, pending = pending)

    private fun plan(inputs: PlanInputs = inputs()) = ReminderPlanner.plan(inputs)

    private fun meal(date: LocalDate, slot: MealSlot) = ReminderKey.Meal(date, slot)

    @Test
    fun nothingWhileTheFlagIsOff() {
        assertEquals(emptyList(), plan(inputs(enabled = false, settings = chosen.copy(waterReminders = true))))
    }

    @Test
    fun theSyncedDefaultsScheduleNothingUntilSomeoneChooses() {
        // 0.1 synced Subtle and three meals on for everyone; nobody picked them.
        assertEquals(emptyList(), plan(inputs(settings = Settings())))
    }

    @Test
    fun offMeansNoMealNudges() {
        assertEquals(emptyList(), plan(inputs(settings = chosen.copy(remindersEvent = EventLevel.Off))))
    }

    @Test
    fun eachSwitchedOnMealFiresAtItsTimeForSevenDays_fromNowOn() {
        val reminders = plan()

        // Today at noon: breakfast has passed and lunch (12:30) and dinner (19:00) are ahead. Snack is off.
        val todays = reminders.filter { it.key.date == today }.map { it.key }
        assertEquals(listOf(meal(today, MealSlot.Lunch), meal(today, MealSlot.Dinner)), todays)
        assertEquals(Instant.parse("2026-10-25T19:00:00Z"), reminders.first { it.key == meal(today, MealSlot.Dinner) }.at)
        // Six more days of three meals each.
        assertEquals(2 + 6 * 3, reminders.size)
        assertEquals(LocalDate(2026, 10, 31), reminders.last().key.date)
        assertTrue(reminders.zipWithNext().all { (a, b) -> a.at <= b.at }, "in time order")
        assertTrue(reminders.none { (it.key as ReminderKey.Meal).slot == MealSlot.Snack })
    }

    @Test
    fun aMovedMealTimeMovesItsNudge() {
        val settings = chosen.copy(mealTimes = MealTimes(dinner = LocalTime(18, 15)))
        val dinner = plan(inputs(settings = settings)).first { it.key == meal(today, MealSlot.Dinner) }
        assertEquals(Instant.parse("2026-10-25T18:15:00Z"), dinner.at)
    }

    @Test
    fun aMealAlreadyLoggedTodayGetsNoNudge_butTomorrowsStays() {
        val day = Day.empty(today).let { it.copy(itemsBySlot = it.itemsBySlot + (MealSlot.Dinner to listOf(item(MealSlot.Dinner, "18:40")))) }
        val keys = plan(inputs(day = day)).map { it.key }

        assertTrue(meal(today, MealSlot.Dinner) !in keys)
        assertTrue(meal(today.plusDays(1), MealSlot.Dinner) in keys)
        assertEquals(listOf(meal(today, MealSlot.Dinner)), ReminderPlanner.doneToday(inputs(day = day)).filter { it.slot == MealSlot.Dinner })
    }

    @Test
    fun alreadyAteCountsAsLogged() {
        val day = Day.empty(today).copy(ateNoDetails = setOf(MealSlot.Lunch))
        assertTrue(meal(today, MealSlot.Lunch) !in plan(inputs(day = day)).map { it.key })
    }

    @Test
    fun withMealGroupsOffAnythingLoggedSinceTheMealBeforeCounts() {
        val settings = chosen.copy(mealGroups = false)
        // Logged at 11:00, after breakfast's 8:00: that is lunch. Dinner, after lunch's 12:30, is still open.
        val day = Day.empty(today).let {
            it.copy(itemsBySlot = it.itemsBySlot + (MealSlot.Uncategorized to listOf(item(MealSlot.Uncategorized, "11:00"))))
        }
        val keys = plan(inputs(settings = settings, day = day, now = Instant.parse("2026-10-25T10:00:00Z"))).map { it.key }

        assertTrue(meal(today, MealSlot.Lunch) !in keys)
        assertTrue(meal(today, MealSlot.Dinner) in keys)
    }

    @Test
    fun aSnoozeStaysWhileItIsAheadAndWanted() {
        val snoozed = Reminder(meal(today, MealSlot.Lunch), noon + 30.minutes, snoozed = true)
        val now = noon + 1.minutes

        assertTrue(snoozed in plan(inputs(now = now, pending = listOf(snoozed))))
        // Lunch got logged: the snooze goes too.
        val lunch = Day.empty(today).copy(ateNoDetails = setOf(MealSlot.Lunch))
        assertTrue(snoozed !in plan(inputs(now = now, day = lunch, pending = listOf(snoozed))))
        // Past it, or with reminders off, it is gone.
        assertTrue(snoozed !in plan(inputs(now = noon + 31.minutes, pending = listOf(snoozed))))
        assertTrue(snoozed !in plan(inputs(now = now, settings = chosen.copy(remindersEvent = EventLevel.Off), pending = listOf(snoozed))))
        // Unsnoozed pending entries are not inputs: the plan decides those.
        val stale = Reminder(meal(today, MealSlot.Snack), noon + 10.minutes)
        assertTrue(stale !in plan(inputs(now = now, pending = listOf(stale))))
    }

    @Test
    fun aSwitchedOffMealHasNoNudge() {
        val settings = chosen.copy(mealReminders = MealReminders(breakfast = false, lunch = false, snack = true, dinner = false))
        val slots = plan(inputs(settings = settings)).map { (it.key as ReminderKey.Meal).slot }.toSet()
        assertEquals(setOf(MealSlot.Snack), slots)
    }

    @Test
    fun waterNudgesMidMorningAndMidAfternoon_untilTheGoalIsReached() {
        val settings = Settings(waterReminders = true)
        val morning = Instant.parse("2026-10-25T09:00:00Z")

        val water = plan(inputs(settings = settings, now = morning)).filter { it.key.date == today }
        assertEquals(listOf(ReminderKey.Water(today, 0), ReminderKey.Water(today, 1)), water.map { it.key })
        assertEquals(Instant.parse("2026-10-25T10:30:00Z"), water.first().at)

        val reached = Day.empty(today).copy(waterMl = settings.waterGoalMl)
        assertEquals(emptyList(), plan(inputs(settings = settings, now = morning, day = reached)).filter { it.key.date == today })
        assertEquals(2 * 7, plan(inputs(settings = settings, now = morning)).size, "water needs no opt-in: it defaults off")
    }

    private fun LocalDate.plusDays(days: Int) = LocalDate.fromEpochDays(toEpochDays() + days)

    private fun item(slot: MealSlot, at: String) = LogItem(
        id = "item-$at",
        day = today,
        slot = slot,
        loggedAt = Instant.parse("2026-10-25T$at:00Z"),
        name = "Soup",
        precision = Precision.Estimate,
        method = LogMethod.Search,
        kcal = 300.0,
    )
}
