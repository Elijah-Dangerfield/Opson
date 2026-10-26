package com.opson.libraries.reminders

import com.opson.libraries.diary.MealSlot
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** What a reminder is about. One per meal per day, and two water nudges a day. */
sealed interface ReminderKey {
    val date: LocalDate

    /** Stable across replans, so the platform can tell "the same reminder, moved" from "a new one". */
    val id: String

    /** For telemetry: never a meal name, only the kind. */
    val kind: ReminderKind

    data class Meal(override val date: LocalDate, val slot: MealSlot) : ReminderKey {
        override val id: String get() = "meal-$date-${slot.wire}"
        override val kind: ReminderKind get() = ReminderKind.Meal
    }

    /** [index] 0 is mid-morning, 1 mid-afternoon ([ReminderPlanner.WATER_TIMES]). */
    data class Water(override val date: LocalDate, val index: Int) : ReminderKey {
        override val id: String get() = "water-$date-$index"
        override val kind: ReminderKind get() = ReminderKind.Water
    }
}

enum class ReminderKind(val wire: String) {
    Meal("meal"),
    Water("water"),
}

/**
 * One reminder at [at]. A [snoozed] one is the same key moved 30 minutes on by "In 30 min", with
 * its own id so it can sit beside the nudge it came from.
 */
data class Reminder(val key: ReminderKey, val at: Instant, val snoozed: Boolean = false) {
    val id: String get() = if (snoozed) "${key.id}-snoozed" else key.id
}

/** The buttons on a meal nudge, and the tap on the nudge itself ([Open]). */
enum class ReminderAction(val wire: String) {
    Open("open"),
    LogIt("log_it"),
    AlreadyAte("already_ate"),
    Snooze("snooze"),
}

/**
 * How a notification interrupts. [Subtle] is silent with no heads-up; [Full] uses the system's
 * sound; [FullQuiet] is Full with Quiet mode on, so no sound or vibration either.
 */
enum class ReminderStyle { Subtle, Full, FullQuiet }

/**
 * A reminder with its text resolved, ready for the platform. Built only by the coordinator, from
 * [ReminderCopy], so no text here can carry a number.
 */
data class ReminderNotification(
    val reminder: Reminder,
    val title: String,
    val body: String,
    val style: ReminderStyle,
    /** The buttons, in order, with their labels. Empty for water. */
    val actions: List<Pair<ReminderAction, String>>,
)
