package com.opson.libraries.reminders

/**
 * The QA menu's "fire it now": shows one reminder a few seconds from now with the real copy, the
 * real buttons and the person's tone, whatever the schedule, the switches and `release.reminders`
 * say. Nothing else calls it.
 *
 * It goes out as a snooze of the reminder, so its id cannot replace the scheduled nudge for the
 * same meal. A replan in the seconds before it fires (a foreground, a settings change) cancels it
 * when the planner would not want that reminder, so test with the app open.
 */
interface ReminderTestFire {

    /** Asks for permission first when it was never asked; anything but Granted fires nothing. */
    suspend fun fire(key: ReminderKey): NotificationPermission
}
