package com.opson.libraries.reminders.impl

import com.opson.libraries.core.AutoInit
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.reminders.ReminderAction
import com.opson.libraries.reminders.ReminderActions
import com.opson.libraries.reminders.ReminderCopy
import com.opson.libraries.reminders.impl.IosLocalNotifications.Companion.wire
import com.opson.libraries.reminders.ReminderStyle
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import org.jetbrains.compose.resources.getString
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationActionOptionForeground
import platform.UserNotifications.UNNotificationActionOptionNone
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationCategoryOptionNone
import platform.UserNotifications.UNNotificationDefaultActionIdentifier
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The notification center's delegate: taps and buttons on a reminder, and how one shows while
 * the app is open.
 *
 * It has to be the delegate before launch finishes, or a tap that cold-starts the app is never
 * delivered. [AutoInit] gets it built in `iOSApp.init`, and being a singleton keeps it alive (the
 * center holds its delegate weakly).
 *
 * Log it opens the app (`UNNotificationActionOptionForeground`); Already ate and In 30 min run
 * in the background, so the meal can be checked off from the lock screen.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = AutoInit::class, multibinding = true)
@Inject
class IosReminderDelegate(
    actions: ReminderActions,
    appScope: AppCoroutineScope,
) : AutoInit {

    /** Held here: the center keeps only a weak reference. A Kotlin class cannot also be an NSObject. */
    private val delegate = ReminderCenterDelegate(actions, appScope)

    init {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.delegate = delegate
        appScope.launch { center.setNotificationCategories(setOf(delegate.mealCategory())) }
    }
}

private class ReminderCenterDelegate(
    private val actions: ReminderActions,
    private val appScope: AppCoroutineScope,
) : NSObject(), UNUserNotificationCenterDelegateProtocol {

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        val style = willPresentNotification.request.wire()?.notification()?.style
        withCompletionHandler(
            when (style) {
                ReminderStyle.Full -> UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList or
                    UNNotificationPresentationOptionSound
                ReminderStyle.FullQuiet -> UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList
                else -> UNNotificationPresentationOptionList
            },
        )
    }

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        val reminder = didReceiveNotificationResponse.notification.request.wire()?.reminder()
        if (reminder == null) {
            withCompletionHandler()
            return
        }
        when (didReceiveNotificationResponse.actionIdentifier) {
            UNNotificationDefaultActionIdentifier -> {
                actions.opened(reminder, ReminderAction.Open)
                withCompletionHandler()
            }
            ReminderAction.LogIt.wire -> {
                actions.opened(reminder, ReminderAction.LogIt)
                withCompletionHandler()
            }
            // The system keeps a backgrounded app running until the handler is called.
            ReminderAction.AlreadyAte.wire -> appScope.launch {
                actions.alreadyAte(reminder)
                withCompletionHandler()
            }
            ReminderAction.Snooze.wire -> appScope.launch {
                actions.snooze(reminder)
                withCompletionHandler()
            }
            else -> withCompletionHandler()
        }
    }

    suspend fun mealCategory(): UNNotificationCategory {
        val buttons = ReminderCopy.mealActions.map { (action, label) ->
            UNNotificationAction.actionWithIdentifier(
                identifier = action.wire,
                title = getString(label),
                options = if (action == ReminderAction.LogIt) UNNotificationActionOptionForeground else UNNotificationActionOptionNone,
            )
        }
        return UNNotificationCategory.categoryWithIdentifier(
            identifier = MEAL_CATEGORY,
            actions = buttons,
            intentIdentifiers = emptyList<Any>(),
            options = UNNotificationCategoryOptionNone,
        )
    }
}

/** The category meal nudges carry, which gives them their three buttons. */
internal const val MEAL_CATEGORY = "opson.meal"
