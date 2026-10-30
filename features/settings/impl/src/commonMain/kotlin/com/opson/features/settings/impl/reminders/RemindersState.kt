package com.opson.features.settings.impl.reminders

import com.opson.libraries.diary.EventLevel
import com.opson.libraries.diary.LoggingHabits
import com.opson.libraries.diary.MealTimes
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import com.opson.libraries.reminders.NotificationPermission
import com.opson.libraries.ui.time.DateCopy
import kotlinx.datetime.LocalTime

/**
 * Reminders and celebrations (v3 `reminders.png`). No row carries a nutrition number, so the
 * page resolves nothing through the policy; the notification it controls carries none either
 * (`ReminderCopy`).
 *
 * How we talk to you and the two check-in switches (hunger and fullness when adding food, the
 * day-close check-in) are gone: the voice is the brand's, and a question anyone can skip needs no
 * off switch. Their `Settings` keys stay so older JSON round-trips, and nothing reads them.
 */
data class RemindersState(
    val quiet: Boolean = true,
    val bigDay: EventLevel = EventLevel.Full,
    /** What will actually happen: Off until the person chose ([Settings.mealReminderLevel]). */
    val mealLevel: EventLevel = EventLevel.Off,
    val meals: List<MealReminderRow> = emptyList(),
    val permission: NotificationPermission = NotificationPermission.Granted,
    /** The time dialog, open on this meal. */
    val pickingTime: MealSlot? = null,
    /** Celebrations (`release.celebrations`); null while the flag is off. */
    val celebrations: CelebrationsChoice? = null,
    /** The times come from when this person usually logs: "Based on when you usually log." */
    val timesLearned: Boolean = false,
) {
    /** Something is on and the system will not show it. */
    val showPermissionNote: Boolean
        get() = permission == NotificationPermission.Denied && meals.any { it.on }

    val pickingRow: MealReminderRow? get() = meals.firstOrNull { it.slot == pickingTime }
}

/**
 * Celebrations on or off, and whether they say how many days. [counts] is what will happen, so after
 * numbers have been hard it reads off until the person turns it on.
 */
data class CelebrationsChoice(val on: Boolean, val counts: Boolean)

/** [on] is the meal's switch and a level other than Off: whether a nudge will come. */
data class MealReminderRow(val slot: MealSlot, val on: Boolean, val time: LocalTime, val timeLabel: String)

object RemindersStateBuilder {

    /** Off, Subtle, Full, as the segmented controls read. */
    val levels: List<EventLevel> = EventLevel.entries

    fun build(
        settings: Settings,
        permission: NotificationPermission = NotificationPermission.Granted,
        pickingTime: MealSlot? = null,
        celebrationsOn: Boolean = false,
        habits: LoggingHabits = LoggingHabits(),
    ): RemindersState {
        val level = settings.mealReminderLevel
        val times = suggestedTimes(settings, habits) ?: settings.mealTimes
        return RemindersState(
            quiet = settings.quiet,
            bigDay = settings.bigDayEvent,
            mealLevel = level,
            meals = MealSlot.meals.map { slot ->
                val time = times.at(slot)
                MealReminderRow(
                    slot = slot,
                    on = level != EventLevel.Off && settings.mealReminders.isOn(slot),
                    time = time,
                    timeLabel = DateCopy.time(time.hour, time.minute),
                )
            },
            permission = permission,
            pickingTime = pickingTime,
            celebrations = if (celebrationsOn) {
                CelebrationsChoice(on = settings.celebrationsEnabled, counts = settings.celebrationCounts ?: !settings.onboarding.hard)
            } else {
                null
            },
            timesLearned = habits.learned.isNotEmpty() && habits.learned.all { times.at(it) == habits.usual.at(it) },
        )
    }

    /**
     * The meal times reminders start from, learned from when this person usually logs: only before
     * they have ever chosen reminders, and only while their meal times are still the defaults, so a
     * time they set themselves (in onboarding or here) is never replaced. Null when there is
     * nothing to suggest.
     */
    fun suggestedTimes(settings: Settings, habits: LoggingHabits): MealTimes? =
        habits.usual.takeIf { !settings.remindersChosen && settings.mealTimes == MealTimes() && habits.learned.isNotEmpty() }
}
