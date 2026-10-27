package com.opson.libraries.reminders.impl

import com.opson.libraries.reminders.LocalNotifications
import com.opson.libraries.reminders.NotificationPermission
import com.opson.libraries.reminders.NotificationPermissions
import com.opson.libraries.reminders.Reminder
import com.opson.libraries.reminders.ReminderKey
import com.opson.libraries.reminders.ReminderKind
import com.opson.libraries.reminders.ReminderNotification
import com.opson.libraries.reminders.ReminderStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Inject
import platform.Foundation.NSDateComponents
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationInterruptionLevel
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.coroutines.resume
import kotlin.time.Clock

/**
 * Reminders on iOS: one non-repeating `UNCalendarNotificationTrigger` per reminder. The trigger
 * matches a local date and time, so a nudge set for 7 pm still fires at 7 pm after a time zone
 * change. Pending requests survive a reboot on their own, and the request list is what
 * [pending] reads, so iOS needs no record of its own.
 *
 * The delegate that answers taps and buttons is [IosReminderDelegate].
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = LocalNotifications::class)
@ContributesBinding(AppScope::class, boundType = NotificationPermissions::class)
@Inject
class IosLocalNotifications(
    private val clock: Clock,
) : LocalNotifications {

    private val center: UNUserNotificationCenter get() = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun status(): NotificationPermission = suspendCancellableCoroutine { continuation ->
        center.getNotificationSettingsWithCompletionHandler { settings ->
            val status = when (settings?.authorizationStatus) {
                UNAuthorizationStatusAuthorized, UNAuthorizationStatusProvisional, UNAuthorizationStatusEphemeral ->
                    NotificationPermission.Granted
                UNAuthorizationStatusDenied -> NotificationPermission.Denied
                UNAuthorizationStatusNotDetermined -> NotificationPermission.NotAsked
                else -> NotificationPermission.NotAsked
            }
            continuation.resume(status)
        }
    }

    /** Alert and sound only: a badge counting unlogged meals would be exactly the nagging this avoids. */
    override suspend fun request(): NotificationPermission {
        if (status() == NotificationPermission.Granted) return NotificationPermission.Granted
        suspendCancellableCoroutine { continuation ->
            center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { _, _ ->
                continuation.resume(Unit)
            }
        }
        return status()
    }

    override fun openSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }

    override suspend fun pending(): List<Reminder> {
        val now = clock.now()
        return pendingRequests().mapNotNull { it.wire()?.reminder() }.filter { it.at > now }
    }

    override suspend fun replaceAll(notifications: List<ReminderNotification>) {
        val keep = notifications.map { it.reminder.id }.toSet()
        val stale = pendingRequests().map { it.identifier }.filter { it.isOurs() && it !in keep }
        if (stale.isNotEmpty()) center.removePendingNotificationRequestsWithIdentifiers(stale)
        schedule(notifications)
    }

    override suspend fun add(notification: ReminderNotification) {
        schedule(listOf(notification))
    }

    override suspend fun dismiss(keys: List<ReminderKey>) {
        if (keys.isEmpty()) return
        center.removeDeliveredNotificationsWithIdentifiers(keys.flatMap { listOf(it.id, "${it.id}$SNOOZED_SUFFIX") })
    }

    override suspend fun dismissAll() {
        val delivered = suspendCancellableCoroutine { continuation ->
            center.getDeliveredNotificationsWithCompletionHandler { list ->
                continuation.resume(list.orEmpty().filterIsInstance<UNNotification>().map { it.request.identifier })
            }
        }
        val ours = delivered.filter { it.isOurs() }
        if (ours.isNotEmpty()) center.removeDeliveredNotificationsWithIdentifiers(ours)
    }

    /**
     * On the main thread, as [IosReminderDelegate] builds its category: the coordinator calls in
     * from a worker, and a UserNotifications object built there (UNNotificationAction) segfaulted.
     */
    private suspend fun schedule(notifications: List<ReminderNotification>) {
        if (notifications.isEmpty()) return
        withContext(Dispatchers.Main) { notifications.forEach(::addRequest) }
    }

    private fun addRequest(notification: ReminderNotification) {
        val reminder = notification.reminder
        val content = UNMutableNotificationContent().apply {
            setTitle(notification.title)
            setBody(notification.body)
            setUserInfo(mapOf<Any?, Any?>(USER_INFO_WIRE to ReminderWire.of(notification).encode()))
            setThreadIdentifier(reminder.key.kind.wire)
            if (reminder.key.kind == ReminderKind.Meal) setCategoryIdentifier(MEAL_CATEGORY)
            setSound(if (notification.style == ReminderStyle.Full) UNNotificationSound.defaultSound else null)
            setInterruptionLevel(
                if (notification.style == ReminderStyle.Subtle) {
                    UNNotificationInterruptionLevel.UNNotificationInterruptionLevelPassive
                } else {
                    UNNotificationInterruptionLevel.UNNotificationInterruptionLevelActive
                },
            )
        }
        val local = reminder.at.toLocalDateTime(TimeZone.currentSystemDefault())
        val components = NSDateComponents().apply {
            setYear(local.year.toLong())
            setMonth(local.month.ordinal.toLong() + 1)
            setDay(local.day.toLong())
            setHour(local.hour.toLong())
            setMinute(local.minute.toLong())
            // Planned nudges sit on the minute; a snooze or a QA test fire does not, and without
            // seconds a test fire seconds away matches a time already past and never arrives.
            setSecond(local.second.toLong())
        }
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier(reminder.id, content, trigger)
        center.addNotificationRequest(request, withCompletionHandler = null)
    }

    private suspend fun pendingRequests(): List<UNNotificationRequest> = suspendCancellableCoroutine { continuation ->
        center.getPendingNotificationRequestsWithCompletionHandler { list ->
            continuation.resume(list.orEmpty().filterIsInstance<UNNotificationRequest>())
        }
    }

    internal companion object {
        const val USER_INFO_WIRE = "opson_reminder"
        private const val SNOOZED_SUFFIX = "-snoozed"

        private fun String.isOurs(): Boolean = startsWith("meal-") || startsWith("water-")

        fun UNNotificationRequest.wire(): ReminderWire? = ReminderWire.decode(content.userInfo[USER_INFO_WIRE] as? String)
    }
}
