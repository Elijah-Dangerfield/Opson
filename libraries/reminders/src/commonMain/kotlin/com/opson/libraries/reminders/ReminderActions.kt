package com.opson.libraries.reminders

import com.opson.libraries.diary.MealSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * What the platform reports back about a reminder. Implemented by the coordinator; the Android
 * receivers and `MainActivity` reach it through the `Application`, the iOS delegate through DI.
 */
interface ReminderActions {

    /** The nudge itself, or its Log it button: open the Logger for that meal. */
    fun opened(reminder: Reminder, via: ReminderAction)

    /** "Already ate": the meal is done, with no details, without opening the app. */
    suspend fun alreadyAte(reminder: Reminder)

    /** "In 30 min": the same nudge again in [ReminderPlanner.SNOOZE]. */
    suspend fun snooze(reminder: Reminder)

    /** Android posted it. iOS has no such callback for a backgrounded app. */
    fun delivered(reminder: Reminder)

    /**
     * Plans and schedules now, and returns when that is done. For a process woken by the system
     * (boot, time zone change) that must not die before the alarms are back.
     */
    suspend fun refresh()
}

/** "Open the Logger on this meal and day", from a tapped reminder. */
data class MealOpen(val slot: MealSlot, val date: LocalDate)

/**
 * Logger requests from tapped reminders, for the app shell to navigate. Each request is
 * delivered once, to one collector, so a recomposed shell does not open the Logger twice.
 */
interface ReminderOpens {
    val requests: Flow<MealOpen>
}
