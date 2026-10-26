package com.opson.libraries.reminders

/** Whether the app may post notifications. */
enum class NotificationPermission(val wire: String) {
    Granted("granted"),
    Denied("denied"),

    /** Never asked. The system prompt can still be shown. */
    NotAsked("not_asked"),
}

/**
 * Asking for and checking notification permission. Screens use this; only the coordinator
 * schedules ([LocalNotifications]).
 *
 * Ask only at the moment someone turns a reminder on, never at launch.
 */
interface NotificationPermissions {
    suspend fun status(): NotificationPermission

    /** Shows the system prompt when it can, and answers with the result. Granted answers at once. */
    suspend fun request(): NotificationPermission

    /** The system settings page for this app's notifications, for after a denial. */
    fun openSettings()
}

/**
 * The platform's scheduled local notifications: `AlarmManager` and `NotificationManager` on
 * Android, `UNUserNotificationCenter` on iOS. A fake in tests.
 */
interface LocalNotifications : NotificationPermissions {

    /** What is waiting to fire, as last handed to [replaceAll] plus any snoozes, minus what has fired. */
    suspend fun pending(): List<Reminder>

    /**
     * Makes the pending set exactly [notifications]: anything pending and not here is cancelled,
     * everything here is (re)scheduled. Idempotent, so it is safe to call on every replan, and
     * after a reboot that dropped the platform's alarms.
     */
    suspend fun replaceAll(notifications: List<ReminderNotification>)

    /** Adds one, leaving the rest alone. For "In 30 min". */
    suspend fun add(notification: ReminderNotification)

    /** Takes these off the screen if they were delivered: the meal got logged. */
    suspend fun dismiss(keys: List<ReminderKey>)

    /** Takes every delivered reminder off the screen: reminders were turned off. */
    suspend fun dismissAll()
}
