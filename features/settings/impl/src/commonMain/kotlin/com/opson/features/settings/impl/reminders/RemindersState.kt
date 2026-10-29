package com.opson.features.settings.impl.reminders

import com.opson.libraries.diary.EventLevel
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
) {
    /** Something is on and the system will not show it. */
    val showPermissionNote: Boolean
        get() = permission == NotificationPermission.Denied && meals.any { it.on }

    val pickingRow: MealReminderRow? get() = meals.firstOrNull { it.slot == pickingTime }
}

/** [on] is the meal's switch and a level other than Off: whether a nudge will come. */
data class MealReminderRow(val slot: MealSlot, val on: Boolean, val time: LocalTime, val timeLabel: String)

object RemindersStateBuilder {

    /** Off, Subtle, Full, as the segmented controls read. */
    val levels: List<EventLevel> = EventLevel.entries

    fun build(
        settings: Settings,
        permission: NotificationPermission = NotificationPermission.Granted,
        pickingTime: MealSlot? = null,
    ): RemindersState {
        val level = settings.mealReminderLevel
        return RemindersState(
            quiet = settings.quiet,
            bigDay = settings.bigDayEvent,
            mealLevel = level,
            meals = MealSlot.meals.map { slot ->
                val time = settings.mealTimes.at(slot)
                MealReminderRow(
                    slot = slot,
                    on = level != EventLevel.Off && settings.mealReminders.isOn(slot),
                    time = time,
                    timeLabel = DateCopy.time(time.hour, time.minute),
                )
            },
            permission = permission,
            pickingTime = pickingTime,
        )
    }
}
