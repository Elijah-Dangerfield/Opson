package com.opson.libraries.reminders.impl

import com.opson.libraries.config.AppConfigFlow
import com.opson.libraries.core.AutoInit
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.EventLevel
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.opson.AppEvent
import com.opson.libraries.opson.AppEvents
import com.opson.libraries.reminders.MealOpen
import com.opson.libraries.reminders.PlanInputs
import com.opson.libraries.reminders.Reminder
import com.opson.libraries.reminders.ReminderAction
import com.opson.libraries.reminders.ReminderActions
import com.opson.libraries.reminders.ReminderCopy
import com.opson.libraries.reminders.ReminderEvents
import com.opson.libraries.reminders.ReminderKey
import com.opson.libraries.reminders.ReminderNotification
import com.opson.libraries.reminders.ReminderOpens
import com.opson.libraries.reminders.ReminderPlanner
import com.opson.libraries.reminders.RemindersEnabled
import com.opson.libraries.reminders.LocalNotifications
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import me.tatarka.inject.annotations.Inject
import org.jetbrains.compose.resources.StringResource
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps the platform's pending reminders equal to [ReminderPlanner]'s plan, and answers what the
 * platform reports back ([ReminderActions]).
 *
 * It replans whenever an input moves: the `release.reminders` flag (each config emission, so a
 * remote flip lands without a restart), the settings (only once they are the user's own,
 * `isKnown`), today's day (a meal logged drops its nudge and takes a delivered one off the
 * screen), the date (every foreground, and midnight), and [refresh]. Each replan hands the
 * platform the whole set, which is idempotent, so a replan after a reboot puts the alarms back.
 *
 * [AutoInit] because the plan has to be current before anyone opens a screen: an Android alarm
 * or a notification action starts the process, and this is what extends the seven-day horizon
 * from there.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = ReminderActions::class)
@ContributesBinding(AppScope::class, boundType = ReminderOpens::class)
@ContributesBinding(AppScope::class, boundType = AutoInit::class, multibinding = true)
@Inject
class ReminderCoordinator(
    private val config: AppConfigFlow,
    private val settingsRepository: SettingsRepository,
    private val diary: DiaryRepository,
    private val calendar: LocalCalendar,
    private val appEvents: AppEvents,
    private val clock: Clock,
    private val notifications: LocalNotifications,
    private val strings: ReminderStrings,
    private val appScope: AppCoroutineScope,
) : ReminderActions, ReminderOpens, AutoInit {

    private val logger = KLog.withTag("ReminderCoordinator")
    private val mutex = Mutex()
    private val opens = Channel<MealOpen>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val nudges = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val requests: Flow<MealOpen> = opens.receiveAsFlow()

    private val enabled: Flow<Boolean> = config.map(RemindersEnabled::isOn).distinctUntilChanged()

    /** The user's own settings: the defaults a store holds before its first read would cancel everything. */
    private val knownSettings: Flow<Settings> =
        combine(settingsRepository.settings, settingsRepository.isKnown) { settings, known -> settings.takeIf { known } }
            .filterNotNull()

    /** Re-read today on every foreground, at midnight, and on a nudge. */
    private val today: Flow<LocalDate> =
        merge(appEvents.live().filterIsInstance<AppEvent.OnForeground>().map { }, midnights(), nudges)
            .onStart { emit(Unit) }
            .map { calendar.today() }
            .distinctUntilChanged()

    /** Bumps on every foreground and nudge, so an unchanged plan is still re-applied (reboot, horizon). */
    private val ticks: Flow<Int> =
        merge(appEvents.live().filterIsInstance<AppEvent.OnForeground>().map { }, nudges)
            .scan(0) { count, _ -> count + 1 }

    private val started = appScope.launch {
        combine(enabled, knownSettings, today.flatMapLatest(diary::observeDay), ticks) { on, settings, day, _ ->
            Triple(on, settings, day)
        }.collect { (on, settings, day) -> apply(on, settings, day) }
    }

    override fun opened(reminder: Reminder, via: ReminderAction) {
        ReminderEvents.tapped(reminder, via)
        val key = reminder.key
        if (key is ReminderKey.Meal) opens.trySend(MealOpen(key.slot, key.date))
        appScope.launch { Catching { notifications.dismiss(listOf(key)) }.logOnFailure { "Dismiss after open failed" } }
    }

    override suspend fun alreadyAte(reminder: Reminder) {
        ReminderEvents.tapped(reminder, ReminderAction.AlreadyAte)
        val key = reminder.key as? ReminderKey.Meal ?: return
        diary.setAteNoDetails(key.date, key.slot, ate = true)
        notifications.dismiss(listOf(key))
        refresh()
    }

    override suspend fun snooze(reminder: Reminder) {
        ReminderEvents.tapped(reminder, ReminderAction.Snooze)
        notifications.dismiss(listOf(reminder.key))
        val settings = settingsRepository.settings.value
        val later = reminder.copy(at = clock.now() + ReminderPlanner.SNOOZE, snoozed = true)
        mutex.withLock { notifications.add(resolve(later, settings)) }
    }

    override fun delivered(reminder: Reminder) {
        ReminderEvents.delivered(reminder)
    }

    override suspend fun refresh() {
        val settings = withTimeoutOrNull(KNOWN_TIMEOUT) { knownSettings.first() } ?: run {
            logger.w { "Settings not known in time; leaving the schedule as it is" }
            return
        }
        val on = enabled.first()
        val day = diary.observeDay(calendar.today()).first()
        apply(on, settings, day)
        nudges.tryEmit(Unit)
    }

    private suspend fun apply(on: Boolean, settings: Settings, day: Day) {
        mutex.withLock { schedule(on, settings, day) }
    }

    private suspend fun schedule(on: Boolean, settings: Settings, day: Day) {
        Catching {
            val inputs = PlanInputs(
                enabled = on,
                settings = settings,
                today = day,
                now = clock.now(),
                timeZone = calendar.timeZone(),
                pending = notifications.pending(),
            )
            val plan = ReminderPlanner.plan(inputs)
            notifications.replaceAll(plan.map { resolve(it, settings) })
            val anythingOn = settings.mealReminderLevel != EventLevel.Off || settings.waterReminders
            if (!on || !anythingOn) notifications.dismissAll() else notifications.dismiss(ReminderPlanner.doneToday(inputs))
            logger.d { "Scheduled ${plan.size} reminders" }
        }.logOnFailure { "Scheduling reminders failed" }
    }

    private suspend fun resolve(reminder: Reminder, settings: Settings): ReminderNotification {
        val text = ReminderCopy.text(reminder.key, settings.tone)
        return ReminderNotification(
            reminder = reminder,
            title = strings.get(text.title),
            body = strings.get(text.body),
            style = ReminderCopy.style(reminder.key, settings),
            actions = text.actions.map { (action, label) -> action to strings.get(label) },
        )
    }

    /** Emits at each local midnight, so the plan rolls onto the new day without a foreground. */
    private fun midnights(): Flow<Unit> = flow {
        while (true) {
            val zone = calendar.timeZone()
            val next = calendar.today().plus(DatePeriod(days = 1)).atStartOfDayIn(zone)
            delay((next - clock.now()).coerceAtLeast(1.seconds))
            emit(Unit)
        }
    }

    private companion object {
        val KNOWN_TIMEOUT = 8.seconds
    }
}

/** Resolves notification copy. The real one is `getString`; tests pass the resource's key. */
fun interface ReminderStrings {
    suspend fun get(resource: StringResource): String
}
