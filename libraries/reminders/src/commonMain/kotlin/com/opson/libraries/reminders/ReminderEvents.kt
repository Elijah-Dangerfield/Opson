package com.opson.libraries.reminders

import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import com.opson.libraries.diary.Settings

/**
 * The reminder app events (`docs/practices/app-events.md`, Reminders). Kinds, levels, counts and
 * permission states only: never a meal name, a time or any text.
 */
object ReminderEvents {
    private val logger = KLog.withTag("Reminders")

    /** A change to what gets scheduled, from Reminders or Water settings. */
    fun set(kind: ReminderKind, settings: Settings, permission: NotificationPermission) {
        logger.logEvent(
            "reminder.set",
            "kind" to kind.wire,
            "level" to settings.mealReminderLevel.name.lowercase(),
            "meals_on" to settings.mealReminders.countOn,
            "water_on" to settings.waterReminders,
            "permission" to permission.wire,
        )
    }

    fun permission(granted: Boolean) {
        logger.logEvent("reminder.permission", "granted" to granted)
    }

    fun delivered(reminder: Reminder) {
        logger.logEvent("reminder.delivered", "kind" to reminder.key.kind.wire, "snoozed" to reminder.snoozed)
    }

    fun tapped(reminder: Reminder, action: ReminderAction) {
        logger.logEvent("reminder.tapped", "kind" to reminder.key.kind.wire, "action" to action.wire)
    }
}
