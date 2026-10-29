package com.opson.libraries.reminders.impl

import com.opson.libraries.diary.EventLevel
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.reminders.LocalNotifications
import com.opson.libraries.reminders.NotificationPermission
import com.opson.libraries.reminders.Reminder
import com.opson.libraries.reminders.ReminderCopy
import com.opson.libraries.reminders.ReminderKey
import com.opson.libraries.reminders.ReminderNotification
import com.opson.libraries.reminders.ReminderTestFire
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/** Not [com.opson.libraries.core.AutoInit]: the QA menu is the only caller. */
@ContributesBinding(AppScope::class)
@Inject
class DefaultReminderTestFire(
    private val notifications: LocalNotifications,
    private val settingsRepository: SettingsRepository,
    private val strings: ReminderStrings,
    private val clock: Clock,
) : ReminderTestFire {

    override suspend fun fire(key: ReminderKey): NotificationPermission {
        val permission = when (val status = notifications.status()) {
            NotificationPermission.NotAsked -> notifications.request()
            else -> status
        }
        if (permission != NotificationPermission.Granted) return permission

        // With meal reminders Off the real style would be the silent one, which a tester can miss.
        val settings = settingsRepository.settings.value.let {
            if (it.mealReminderLevel == EventLevel.Off) it.copy(remindersChosen = true, remindersEvent = EventLevel.Full) else it
        }
        val text = ReminderCopy.text(key)
        notifications.add(
            ReminderNotification(
                reminder = Reminder(key, at = clock.now() + DELAY, snoozed = true),
                title = strings.get(text.title),
                body = strings.get(text.body),
                style = ReminderCopy.style(key, settings),
                actions = text.actions.map { (action, label) -> action to strings.get(label) },
            ),
        )
        return permission
    }

    private companion object {
        /** Long enough to lock the phone or leave the app and see it arrive as it would. */
        val DELAY = 5.seconds
    }
}
