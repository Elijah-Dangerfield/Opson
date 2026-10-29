package com.opson.libraries.reminders.impl

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.opson.libraries.reminders.Reminder
import com.opson.libraries.reminders.ReminderAction
import com.opson.libraries.reminders.ReminderActions
import com.opson.libraries.reminders.ReminderNotification
import com.opson.libraries.reminders.ReminderStyle
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.reminders_channel_full
import opson.libraries.resources.generated.resources.reminders_channel_quiet
import opson.libraries.resources.generated.resources.reminders_channel_subtle
import org.jetbrains.compose.resources.getString

/**
 * Implemented by the app's `Application`, so the receivers (constructed by the system, outside
 * DI) can reach the coordinator. `Application.onCreate` builds the graph before any receiver
 * runs.
 */
interface ReminderActionsHost {
    val reminderActions: ReminderActions
}

internal val Context.reminderActions: ReminderActions?
    get() = (applicationContext as? ReminderActionsHost)?.reminderActions

/**
 * The three channels, one per [ReminderStyle], because a channel's sound and importance are
 * fixed once it exists. Subtle is `IMPORTANCE_LOW` (in the shade, silent, no heads-up); Full is
 * `IMPORTANCE_DEFAULT` with the system sound; Quiet is Full with sound and vibration off, for
 * Quiet mode.
 */
internal object ReminderChannels {
    const val FULL = "reminders_full"
    const val SUBTLE = "reminders_subtle"
    const val QUIET = "reminders_quiet"
    val ids = setOf(FULL, SUBTLE, QUIET)

    fun idFor(style: ReminderStyle): String = when (style) {
        ReminderStyle.Full -> FULL
        ReminderStyle.Subtle -> SUBTLE
        ReminderStyle.FullQuiet -> QUIET
    }

    /**
     * Creating a channel that exists only renames it, so this runs before every post. Channels
     * only exist from Android 8; below that the channel id on a notification is ignored.
     */
    suspend fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(FULL, getString(Res.string.reminders_channel_full), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(SUBTLE, getString(Res.string.reminders_channel_subtle), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(QUIET, getString(Res.string.reminders_channel_quiet), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setSound(null, null)
                    enableVibration(false)
                },
            ),
        )
    }
}

/** Builds and posts a reminder, and the intents its tap and buttons send. */
internal object ReminderPosting {
    const val ACTION_FIRE = "com.opson.reminders.FIRE"
    const val ACTION_ANSWER = "com.opson.reminders.ANSWER"
    const val EXTRA_WIRE = "com.opson.reminders.WIRE"
    const val EXTRA_VIA = "com.opson.reminders.VIA"

    /** Posted with the reminder key's id as the tag, so a snooze replaces the nudge it came from. */
    const val NOTIFICATION_ID = 1

    @SuppressLint("MissingPermission") // canPost checks it; lint cannot see through the helper.
    suspend fun post(context: Context, notification: ReminderNotification, wire: ReminderWire): Boolean {
        if (!AndroidLocalNotifications.canPost(context)) return false
        ReminderChannels.ensure(context)
        val builder = NotificationCompat.Builder(context, ReminderChannels.idFor(notification.style))
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent(context, wire, ReminderAction.Open))
        notification.actions.forEach { (action, label) ->
            val intent = if (action == ReminderAction.LogIt) openIntent(context, wire, action) else answerIntent(context, wire, action)
            builder.addAction(0, label, intent)
        }
        NotificationManagerCompat.from(context).notify(notification.reminder.key.id, NOTIFICATION_ID, builder.build())
        return true
    }

    /** The launcher activity, with the reminder in extras; `MainActivity` hands it to [ReminderActions.opened]. */
    private fun openIntent(context: Context, wire: ReminderWire, via: ReminderAction): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
        val intent = launch
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_WIRE, wire.encode())
            .putExtra(EXTRA_VIA, via.wire)
        return PendingIntent.getActivity(context, requestCode(wire, via), intent, FLAGS)
    }

    private fun answerIntent(context: Context, wire: ReminderWire, via: ReminderAction): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(ACTION_ANSWER)
            .putExtra(EXTRA_WIRE, wire.encode())
            .putExtra(EXTRA_VIA, via.wire)
        return PendingIntent.getBroadcast(context, requestCode(wire, via), intent, FLAGS)
    }

    private fun requestCode(wire: ReminderWire, via: ReminderAction): Int = "${wire.id}/${via.wire}".hashCode()

    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
}

/** Reads a tapped reminder back out of an activity intent: what `MainActivity` calls. */
object ReminderIntents {
    fun opened(intent: Intent?): Pair<Reminder, ReminderAction>? {
        val wire = ReminderWire.decode(intent?.getStringExtra(ReminderPosting.EXTRA_WIRE)) ?: return null
        val via = ReminderAction.entries.firstOrNull { it.wire == intent?.getStringExtra(ReminderPosting.EXTRA_VIA) }
            ?: ReminderAction.Open
        val reminder = wire.reminder() ?: return null
        return reminder to via
    }

    /** So a recreated activity does not open the Logger a second time. */
    fun consume(intent: Intent?) {
        intent?.removeExtra(ReminderPosting.EXTRA_WIRE)
        intent?.removeExtra(ReminderPosting.EXTRA_VIA)
    }
}
