package com.opson.libraries.reminders.impl

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.reminders.ReminderAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Work a receiver hands off with `goAsync`, finished within the system's ten seconds. The
 * coordinator writes through Room and the settings store, both off the main thread.
 */
private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private val RECEIVER_BUDGET = 8.seconds

private fun BroadcastReceiver.runAsync(work: suspend () -> Unit) {
    val result = goAsync()
    receiverScope.launch {
        Catching { withTimeoutOrNull(RECEIVER_BUDGET) { work() } }.logOnFailure { "Reminder receiver failed" }
        result.finish()
    }
}

/**
 * An alarm went off: post the reminder its intent carries. Needs nothing from DI to post, so a
 * reminder still shows if the rest of the app is slow to start; telling the coordinator is best
 * effort, for the `reminder.delivered` event.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderPosting.ACTION_FIRE) return
        val wire = ReminderWire.decode(intent.getStringExtra(ReminderPosting.EXTRA_WIRE)) ?: return
        val notification = wire.notification() ?: return
        runAsync {
            if (ReminderPosting.post(context, notification, wire)) {
                context.reminderActions?.delivered(notification.reminder)
            }
        }
    }
}

/** "Already ate" and "In 30 min", answered from the notification without opening the app. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderPosting.ACTION_ANSWER) return
        val reminder = ReminderWire.decode(intent.getStringExtra(ReminderPosting.EXTRA_WIRE))?.reminder() ?: return
        val via = ReminderAction.entries.firstOrNull { it.wire == intent.getStringExtra(ReminderPosting.EXTRA_VIA) } ?: return
        NotificationManagerCompat.from(context).cancel(reminder.key.id, ReminderPosting.NOTIFICATION_ID)
        val actions = context.reminderActions ?: return
        runAsync {
            when (via) {
                ReminderAction.AlreadyAte -> actions.alreadyAte(reminder)
                ReminderAction.Snooze -> actions.snooze(reminder)
                ReminderAction.Open, ReminderAction.LogIt -> actions.opened(reminder, via)
            }
        }
    }
}

/**
 * Alarms are gone after a reboot or an app update, and sit at the wrong local time after a time
 * zone or clock change. Each of these starts the process, and the coordinator replans from
 * settings on disk.
 */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        val actions = context.reminderActions ?: return
        runAsync { actions.refresh() }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
        )
    }
}
