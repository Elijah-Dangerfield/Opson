package com.opson.libraries.reminders.impl

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.app.NotificationManagerCompat
import com.opson.libraries.opson.ActivityProvider
import com.opson.libraries.reminders.LocalNotifications
import com.opson.libraries.reminders.NotificationPermission
import com.opson.libraries.reminders.NotificationPermissions
import com.opson.libraries.reminders.Reminder
import com.opson.libraries.reminders.ReminderKey
import com.opson.libraries.reminders.ReminderNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.coroutines.resume
import kotlin.time.Clock

/**
 * Reminders on Android: one inexact alarm per reminder, posted by [ReminderAlarmReceiver].
 *
 * **Inexact on purpose.** `setAndAllowWhileIdle` fires within a few minutes, Doze included, and
 * needs no permission. Exact alarms need `SCHEDULE_EXACT_ALARM` (denied by default for new
 * installs on Android 14) or `USE_EXACT_ALARM`, which Play reserves for alarm and calendar apps,
 * and a meal nudge a few minutes late loses nothing.
 *
 * `AlarmManager` cannot list what it holds, so what was scheduled is kept in
 * [SharedPreferences][android.content.SharedPreferences]: it is how [replaceAll] knows what to
 * cancel and what [pending] returns. Alarms do not survive a reboot or an update;
 * [ReminderBootReceiver] asks the coordinator to replan, and [replaceAll] re-sets every alarm in
 * the plan rather than only the new ones, so that is enough to put them back.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = LocalNotifications::class)
@ContributesBinding(AppScope::class, boundType = NotificationPermissions::class)
@Inject
class AndroidLocalNotifications(
    private val context: Context,
    private val activityProvider: ActivityProvider,
    private val clock: Clock,
) : LocalNotifications {

    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    override suspend fun status(): NotificationPermission {
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) return NotificationPermission.Granted
        val prompts = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        return if (prompts && !prefs.getBoolean(KEY_ASKED, false)) NotificationPermission.NotAsked else NotificationPermission.Denied
    }

    override suspend fun request(): NotificationPermission {
        val now = status()
        if (now == NotificationPermission.Granted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return now
        val activity = activityProvider.currentActivity() as? ComponentActivity ?: return now
        prefs.edit { putBoolean(KEY_ASKED, true) }
        val granted = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                lateinit var launcher: ActivityResultLauncher<String>
                launcher = activity.activityResultRegistry.register(REQUEST_KEY, ActivityResultContracts.RequestPermission()) { granted ->
                    launcher.unregister()
                    if (continuation.isActive) continuation.resume(granted)
                }
                continuation.invokeOnCancellation { launcher.unregister() }
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        return if (granted) NotificationPermission.Granted else NotificationPermission.Denied
    }

    override fun openSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    override suspend fun pending(): List<Reminder> = mutex.withLock {
        val now = clock.now().toEpochMilliseconds()
        load().filter { it.atMs > now }.mapNotNull { it.reminder() }
    }

    override suspend fun replaceAll(notifications: List<ReminderNotification>) = mutex.withLock {
        val next = notifications.map(ReminderWire::of)
        val keep = next.map { it.id }.toSet()
        load().filter { it.id !in keep }.forEach { alarms.cancel(alarmIntent(it)) }
        next.forEach(::arm)
        save(next)
    }

    override suspend fun add(notification: ReminderNotification) = mutex.withLock {
        val wire = ReminderWire.of(notification)
        arm(wire)
        save(load().filter { it.id != wire.id } + wire)
    }

    override suspend fun dismiss(keys: List<ReminderKey>) {
        val manager = NotificationManagerCompat.from(context)
        keys.forEach { manager.cancel(it.id, ReminderPosting.NOTIFICATION_ID) }
    }

    override suspend fun dismissAll() {
        val manager = context.getSystemService(NotificationManager::class.java)
        // Below Android 8 there are no channels to tell ours apart, but every reminder is posted
        // with the same notification id, so that id is what finds them.
        manager.activeNotifications
            .filter {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.notification.channelId in ReminderChannels.ids
                } else {
                    it.id == ReminderPosting.NOTIFICATION_ID
                }
            }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    private fun arm(wire: ReminderWire) {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wire.atMs, alarmIntent(wire))
    }

    /** One per reminder id: the data uri keeps them apart, the extras carry the notification. */
    private fun alarmIntent(wire: ReminderWire): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
            .setAction(ReminderPosting.ACTION_FIRE)
            .setData(Uri.parse("opson-reminder://alarm/${wire.id}"))
            .putExtra(ReminderPosting.EXTRA_WIRE, wire.encode())
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun load(): List<ReminderWire> = prefs.getString(KEY_SCHEDULED, null)
        ?.let { runCatching { WireJson.decodeFromString(ListSerializer(ReminderWire.serializer()), it) }.getOrNull() }
        .orEmpty()

    private fun save(scheduled: List<ReminderWire>) {
        prefs.edit { putString(KEY_SCHEDULED, WireJson.encodeToString(ListSerializer(ReminderWire.serializer()), scheduled)) }
    }

    internal companion object {
        const val PREFS = "opson_reminders"
        private const val KEY_SCHEDULED = "scheduled"
        private const val KEY_ASKED = "asked_post_notifications"
        private const val REQUEST_KEY = "opson-reminders-post-notifications"

        /** Whether posting would be shown. Below 13 there is no runtime permission to hold. */
        fun canPost(context: Context): Boolean =
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
}
