package com.opson.libraries.reminders

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.EventLevel
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Everything a plan depends on. [today] is today's [Day]; its date is "today". */
data class PlanInputs(
    val enabled: Boolean,
    val settings: Settings,
    val today: Day,
    val now: Instant,
    val timeZone: TimeZone,
    /** What the platform is holding. Only snoozed reminders are read from it: they exist nowhere else. */
    val pending: List<Reminder> = emptyList(),
)

/**
 * Which reminders should be waiting, from now until [DAYS_AHEAD] days on. Pure: the coordinator
 * hands the result to the platform as the whole pending set, so anything not in it is cancelled.
 *
 * The rules (docs/plans/screens/reminders.md):
 * - Nothing while the flag is off.
 * - A meal nudge fires at that meal's time ([Settings.mealTimes]) on each day its switch is on,
 *   while [Settings.mealReminderLevel] is not Off, which it is until the person chose a level.
 * - Today's nudge for a meal that is already done is dropped. With meal groups off there are no
 *   meals to look in, so a meal counts as done once anything was logged since the meal before it.
 * - A snoozed nudge stays while it is in the future and still wanted.
 * - Water nudges come mid-morning and mid-afternoon while water reminders are on, and stop for
 *   the day once the water goal is reached.
 */
object ReminderPlanner {
    /** Four meals and two waters a day stay well under iOS's 64 pending requests. */
    const val DAYS_AHEAD = 7

    val SNOOZE = 30.minutes

    /** "One mid-morning, one mid-afternoon." */
    val WATER_TIMES: List<LocalTime> = listOf(LocalTime(10, 30), LocalTime(15, 0))

    fun plan(inputs: PlanInputs): List<Reminder> {
        if (!inputs.enabled) return emptyList()
        val dates = (0 until DAYS_AHEAD).map { inputs.today.date.plus(DatePeriod(days = it)) }

        val meals = dates.flatMap { date ->
            MealSlot.meals.map { slot -> Reminder(ReminderKey.Meal(date, slot), at(date, inputs.settings.mealTimes.at(slot), inputs)) }
        }
        val water = dates.flatMap { date ->
            WATER_TIMES.mapIndexed { index, time -> Reminder(ReminderKey.Water(date, index), at(date, time, inputs)) }
        }
        val snoozed = inputs.pending.filter { it.snoozed }

        return (meals + water + snoozed)
            .filter { it.at > inputs.now && wanted(it.key, inputs) }
            .distinctBy { it.id }
            .sortedBy { it.at }
    }

    /** Whether [key] should still fire, whatever its time. Also how a tap decides whether to act. */
    fun wanted(key: ReminderKey, inputs: PlanInputs): Boolean {
        if (!inputs.enabled) return false
        val settings = inputs.settings
        val isToday = key.date == inputs.today.date
        return when (key) {
            is ReminderKey.Meal -> settings.mealReminderLevel != EventLevel.Off &&
                settings.mealReminders.isOn(key.slot) &&
                !(isToday && isMealDone(inputs.today, key.slot, inputs))
            is ReminderKey.Water -> settings.waterReminders &&
                key.index in WATER_TIMES.indices &&
                !(isToday && inputs.today.waterMl >= settings.waterGoalMl)
        }
    }

    /** Today's meals that are done, whose delivered nudges can go. */
    fun doneToday(inputs: PlanInputs): List<ReminderKey.Meal> =
        MealSlot.meals.filter { isMealDone(inputs.today, it, inputs) }.map { ReminderKey.Meal(inputs.today.date, it) }

    private fun isMealDone(day: Day, slot: MealSlot, inputs: PlanInputs): Boolean {
        if (day.mealGroupsOn(inputs.settings)) return day.isMealDone(slot)
        if (slot in day.ateNoDetails) return true
        val since = previousMealTime(slot, inputs.settings)
        return day.allItems.any { it.loggedAt.toLocalDateTime(inputs.timeZone).time >= since }
    }

    /** The latest meal time before [slot]'s, or midnight for the first meal of the day. */
    private fun previousMealTime(slot: MealSlot, settings: Settings): LocalTime {
        val mine = settings.mealTimes.at(slot)
        return MealSlot.meals.map(settings.mealTimes::at).filter { it < mine }.maxOrNull() ?: LocalTime(0, 0)
    }

    private fun at(date: LocalDate, time: LocalTime, inputs: PlanInputs): Instant =
        date.atTime(time).toInstant(inputs.timeZone)
}
