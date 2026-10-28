package com.opson.libraries.reminders.impl

import com.opson.libraries.config.AppConfigFlow
import com.opson.libraries.core.AutoInit
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.reminders.ReminderAction
import com.opson.libraries.reminders.ReminderActions
import com.opson.libraries.reminders.ReminderCopy
import com.opson.libraries.reminders.impl.IosLocalNotifications.Companion.wire
import com.opson.libraries.reminders.ReminderStyle
import com.opson.libraries.reminders.RemindersEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    config: AppConfigFlow,
) : AutoInit {

    /** Held here: the center keeps only a weak reference. A Kotlin class cannot also be an NSObject. */
    private val delegate = ReminderCenterDelegate(actions, appScope)

    init {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.delegate = delegate
        // Only once reminders are released: nothing reminder-related runs in a build that hides them.
        appScope.launch {
            config.map(RemindersEnabled::isOn).distinctUntilChanged().filter { it }.first()
            val category = delegate.mealCategory()
            withContext(Dispatchers.Main) { center.setNotificationCategories(setOf(category)) }
        }
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
            ReminderAction.AlreadyAte.wire ->
                appScope.answerInBackground(work = { actions.alreadyAte(reminder) }, done = withCompletionHandler)
            ReminderAction.Snooze.wire ->
                appScope.answerInBackground(work = { actions.snooze(reminder) }, done = withCompletionHandler)
            else -> withCompletionHandler()
        }
    }

    suspend fun mealCategory(): UNNotificationCategory {
        // Strings first (getString suspends and may resume on a worker thread), then build the
        // UserNotifications objects on the main thread, where they were crashing off it at launch.
        val titles = ReminderCopy.mealActions.map { (action, label) -> action to getString(label) }
        return withContext(Dispatchers.Main) { category(titles) }
    }

    private fun category(titles: List<Pair<ReminderAction, String>>): UNNotificationCategory {
        val buttons = titles.map { (action, title) ->
            UNNotificationAction.actionWithIdentifier(
                identifier = action.wire,
                title = title,
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
