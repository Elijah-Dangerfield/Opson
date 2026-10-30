package com.opson.libraries.health.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.health.ConnectOutcome
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthProfile
import com.opson.libraries.health.HealthRecords
import com.opson.libraries.health.HealthRefresh
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.health.HealthWeighIn
import com.opson.libraries.health.HealthWorkout
import com.opson.libraries.health.StepsRepository
import com.opson.libraries.opson.AppCache
import com.opson.libraries.opson.HealthEnergySnapshot
import com.opson.libraries.opson.HealthStepsSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.internal.SynchronizedObject
import kotlinx.coroutines.internal.synchronized
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * [HealthRepository], [HealthRecords] and [StepsRepository] over the platform's [HealthReader].
 *
 * **Connected** is what the store says, not what Opson remembers: the store has let Opson read
 * (all of it or part), and the person has not turned it off here with Disconnect. HealthKit hides
 * whether reading was allowed, so there it means the sheet has been shown for what Opson reads;
 * a refusal then looks like a store with no data, which the Integrations page says plainly. A
 * grant made in the store itself, or one that outlived a sign-out, counts without tapping Connect
 * again. Only when the store will not say does the remembered choice decide.
 *
 * **One reading, shared.** Every screen asking for a day's energy (Expended so far, Burned on
 * Today and the Activity card, the widget) collects the same flow, so they read in step and never
 * disagree. Reads happen only while the app is in front ([HealthAppForeground]): when a flow is
 * first collected, on every return to the app, when HealthKit's observer query says new data
 * landed, every [refreshEvery] in between, and on [refreshNow]. A read that fails keeps the last
 * good value rather than dropping to nothing, so a store that refuses one read never turns Burned
 * into the everyday estimate. The last reading is kept on the phone
 * ([com.opson.libraries.opson.AppData.healthEnergy]) so a cold start shows it straight away.
 *
 * **Steps** ride the same reading: today's count is re-read on the same ticks as energy, and a
 * span's past days are read once per collection. The last counts are kept on the phone too
 * ([com.opson.libraries.opson.AppData.healthSteps]).
 *
 * Also kept in AppData: that the person connected, and whether they turned it off. Nothing here
 * goes to the server, logs or telemetry; telemetry carries the source and nothing more.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = HealthRepository::class)
@ContributesBinding(AppScope::class, boundType = HealthRecords::class)
@ContributesBinding(AppScope::class, boundType = StepsRepository::class)
@Inject
class RealHealthRepository(
    private val reader: HealthReader,
    private val appCache: AppCache,
    private val clock: Clock,
    private val appScope: AppCoroutineScope,
    private val foreground: HealthAppForeground,
) : HealthRepository, HealthRecords, StepsRepository {

    /** How often a collected [energy] or [workouts] re-reads while the app is in front. Exposed for tests. */
    internal var refreshEvery: Duration = REFRESH_EVERY

    private val logger = KLog.withTag("Health")
    private val mutableStatus = MutableStateFlow<HealthStatus>(HealthStatus.NotConnected)
    private val mutableCanAskForMore = MutableStateFlow(false)

    /** Failures already reported at error this process: a broken store stays broken, so the rest go at warn. */
    private val reported = mutableSetOf<String>()

    /** A read asked for out of turn; true skips the floor between reads (pull to refresh, Connect). */
    private val pokes = MutableSharedFlow<Boolean>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * When to read, shared by every loop so they read together: on coming to the front, on a
     * poke, when the store says something changed, and every [refreshEvery]. Nothing at all while
     * the app is in the background, the observer query included.
     */
    private val ticks: SharedFlow<Boolean> = foreground.isForeground.flatMapLatest { inFront ->
        if (!inFront) {
            emptyFlow()
        } else {
            channelFlow {
                send(false)
                launch { pokes.collect { send(it) } }
                launch { reader.changes().collect { send(false) } }
                while (true) {
                    delay(refreshEvery)
                    send(false)
                }
            }
        }
    }.shareIn(appScope, SharingStarted.WhileSubscribed(), replay = 0)

    private val energyByDay = SharedByKey<Pair<LocalDate, TimeZone>, EnergyExpended?>()
    private val workoutsByDay = SharedByKey<Pair<LocalDate, TimeZone>, List<HealthWorkout>?>()

    /** Today's steps by day, null for a read that failed with nothing before it; empty when none are recorded. */
    private val stepsByDay = SharedByKey<Pair<LocalDate, TimeZone>, Map<LocalDate, Int>?>()

    /** The latest good reading of each day, what [refreshNow] compares against. */
    private val lastEnergy = mutableMapOf<LocalDate, EnergyExpended>()

    override val source: HealthSource get() = reader.source

    override val status: StateFlow<HealthStatus> = mutableStatus.asStateFlow()

    override val canAskForMore: StateFlow<Boolean> = mutableCanAskForMore.asStateFlow()

    override suspend fun refresh() {
        mutableStatus.value = check()
    }

    override suspend fun connect(): ConnectOutcome {
        when (guarded("availability", Severity.Warn) { reader.availability() }) {
            HealthAvailability.Unsupported, null -> {
                mutableStatus.value = HealthStatus.Unsupported
                return ConnectOutcome.Unsupported
            }
            HealthAvailability.NeedsInstall -> {
                mutableStatus.value = HealthStatus.NeedsInstall
                return ConnectOutcome.NeedsInstall
            }
            HealthAvailability.Available -> Unit
        }
        // An error here is the store refusing to ask at all (no entitlement, unbound service),
        // which the person can't fix and support needs to hear about: never a quiet "denied".
        val granted = guarded("request access", Severity.Error) { reader.requestAccess() }
        if (granted == null) {
            mutableStatus.value = check()
            return ConnectOutcome.Failed
        }
        if (granted) appCache.update { it.copy(healthConnected = true, healthPaused = false) }
        val status = check()
        mutableStatus.value = status
        return if (granted && status == HealthStatus.Connected) {
            // Back from the permission sheet: read at once with what was just allowed.
            pokes.tryEmit(true)
            logger.logEvent(EVENT_CONNECTED, ATTR_SOURCE to source.key)
            ConnectOutcome.Connected
        } else {
            logger.logEvent(EVENT_DENIED, ATTR_SOURCE to source.key)
            ConnectOutcome.Denied
        }
    }

    override suspend fun disconnect() {
        appCache.update { it.copy(healthConnected = false, healthPaused = true, healthEnergy = null, healthSteps = null) }
        lastEnergy.clear()
        guarded("revoke access", Severity.Warn) { reader.revokeAccess() }
        mutableStatus.value = check()
        logger.logEvent(EVENT_DISCONNECTED, ATTR_SOURCE to source.key)
    }

    override fun energy(day: LocalDate, timeZone: TimeZone): Flow<EnergyExpended?> = energyByDay.get(day to timeZone) {
        flow {
            refresh()
            emitAll(whileConnected { energyLoop(day, timeZone) })
        }
    }

    override suspend fun energyBetween(from: LocalDate, to: LocalDate, timeZone: TimeZone): Map<LocalDate, EnergyExpended>? {
        refresh()
        if (mutableStatus.value != HealthStatus.Connected) return null
        val start = from.atStartOfDayIn(timeZone)
        val end = minOf(to.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone), clock.now())
        if (end <= start) return emptyMap()
        return guarded("read energy by day", Severity.Error) { reader.readDaily(start, end, timeZone) }?.filterKeys { it in from..to }
    }

    override fun workouts(day: LocalDate, timeZone: TimeZone): Flow<List<HealthWorkout>?> = workoutsByDay.get(day to timeZone) {
        whileConnected {
            channelFlow {
                var last: List<HealthWorkout>? = null
                // Unknown until the first read, which waits for the app to be in front: never hold a screen on it.
                send(null)
                readOnTicks {
                    val range = dayRange(day, timeZone)
                    val value = if (range == null) {
                        emptyList()
                    } else {
                        guarded("read workouts", Severity.Error) { reader.workouts(range.first, range.second) }?.sortedBy { it.start }
                    }
                    if (value != null) last = value
                    send(value ?: last)
                }
            }.distinctUntilChanged()
        }
    }

    override fun stepsBetween(from: LocalDate, to: LocalDate, timeZone: TimeZone): Flow<Map<LocalDate, Int>?> {
        if (to < from) return flowOf(emptyMap())
        return flow {
            refresh()
            emitAll(whileConnected { stepsSpan(from, to, timeZone) })
        }.distinctUntilChanged()
    }

    /**
     * The span's past days (what was kept first, then one read), and today's count when the span
     * reaches it. A past read that fails with nothing kept makes the whole span unknown rather
     * than a run of days with no steps.
     */
    private fun stepsSpan(from: LocalDate, to: LocalDate, timeZone: TimeZone): Flow<Map<LocalDate, Int>?> {
        val today = clock.now().toLocalDateTime(timeZone).date
        val pastEnd = minOf(to, today.minus(DatePeriod(days = 1)))
        val past: Flow<Map<LocalDate, Int>?> = if (from > pastEnd) {
            flowOf(emptyMap())
        } else {
            flow {
                val saved = savedSteps(from, pastEnd)
                if (saved.isNotEmpty()) emit(saved)
                val read = guarded("read steps by day", Severity.Error) {
                    reader.dailySteps(from.atStartOfDayIn(timeZone), pastEnd.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone), timeZone)
                }?.filterKeys { it in from..pastEnd }
                if (read != null) {
                    keepSteps(read)
                    emit(read)
                } else if (saved.isEmpty()) {
                    emit(null)
                }
            }
        }
        val live: Flow<Map<LocalDate, Int>?> = if (today in from..to) todaySteps(today, timeZone) else flowOf(emptyMap())
        return combine(past, live) { days, now -> if (days == null || now == null) null else days + now }
    }

    /** Today's count on every tick, shared by every screen asking. What was kept comes first; a failed read keeps the last count. */
    private fun todaySteps(day: LocalDate, timeZone: TimeZone): Flow<Map<LocalDate, Int>?> = stepsByDay.get(day to timeZone) {
        channelFlow {
            var last: Map<LocalDate, Int>? = savedSteps(day, day).takeIf { it.isNotEmpty() }
            last?.let { send(it) }
            readOnTicks {
                val range = dayRange(day, timeZone)
                val value = if (range == null) {
                    emptyMap()
                } else {
                    guarded("read steps", Severity.Error) { reader.dailySteps(range.first, range.second, timeZone) }?.filterKeys { it == day }
                }
                if (value != null) {
                    last = value
                    keepSteps(value)
                }
                send(value ?: last)
            }
        }.distinctUntilChanged()
    }

    private suspend fun keepSteps(days: Map<LocalDate, Int>) {
        if (days.isEmpty()) return
        val readAt = clock.now().toEpochMilliseconds()
        appCache.update { data ->
            val merged = data.healthSteps?.days.orEmpty() + days.mapKeys { it.key.toString() }
            // ISO dates sort as days do, so the newest are kept.
            val kept = merged.entries.sortedByDescending { it.key }.take(HealthStepsSnapshot.MAX_DAYS).associate { it.key to it.value }
            data.copy(healthSteps = HealthStepsSnapshot(readAt, kept))
        }
    }

    private suspend fun savedSteps(from: LocalDate, to: LocalDate): Map<LocalDate, Int> =
        appCache.get().healthSteps?.days.orEmpty().mapNotNull { (day, steps) ->
            Catching { LocalDate.parse(day) }.getOrNull()?.takeIf { it in from..to }?.let { it to steps }
        }.toMap()

    override suspend fun refreshNow(day: LocalDate, timeZone: TimeZone): HealthRefresh {
        refresh()
        if (mutableStatus.value != HealthStatus.Connected) return HealthRefresh.NotConnected
        val before = lastEnergy[day] ?: saved(day)
        val now = read(day, timeZone) ?: return HealthRefresh.Failed
        keep(day, now)
        // Every loop reads again too, so workouts and every screen catch up together.
        pokes.tryEmit(true)
        return if (now != before) HealthRefresh.NewData else HealthRefresh.NothingNew
    }

    override suspend fun profile(): HealthProfile? {
        refresh()
        if (mutableStatus.value != HealthStatus.Connected) return null
        return guarded("read profile", Severity.Error) { reader.profile() }
    }

    override suspend fun weighIns(since: Instant): List<HealthWeighIn>? {
        if (mutableStatus.value != HealthStatus.Connected) return null
        val now = clock.now()
        if (now <= since) return emptyList()
        return guarded("read weigh-ins", Severity.Error) { reader.weighIns(since, now) }?.sortedBy { it.measuredAt }
    }

    override suspend fun sources(): List<String>? {
        if (mutableStatus.value != HealthStatus.Connected) return null
        val now = clock.now()
        return guarded("read sources", Severity.Warn) { reader.sources(now - SOURCES_WINDOW, now) }?.sortedBy { it.lowercase() }
    }

    override fun openSourceSettings(): Boolean = reader.openSettings()

    private fun <T> whileConnected(connected: () -> Flow<T?>): Flow<T?> =
        mutableStatus.flatMapLatest { status -> if (status != HealthStatus.Connected) flowOf(null) else connected() }

    /** The saved reading first when it is this day's, then a read on every tick. A failed read keeps what was there. */
    private fun energyLoop(day: LocalDate, timeZone: TimeZone): Flow<EnergyExpended?> = channelFlow {
        var last: EnergyExpended? = lastEnergy[day] ?: saved(day)
        last?.let { send(it) }
        // With nothing saved, say "nothing yet" after a moment rather than hold a screen on the store.
        val grace = if (last == null) launch { delay(FIRST_READ_GRACE); send(null) } else null
        readOnTicks {
            val value = read(day, timeZone)
            grace?.cancel()
            if (value != null) {
                last = value
                keep(day, value)
            }
            send(value ?: last)
        }
    }.distinctUntilChanged()

    /**
     * Runs [read] on each tick: one for this collector as soon as the app is in front (at once,
     * usually), then the shared ones. iOS calls a return to the app "foreground" after Control
     * Center or Face ID too, so an unforced tick inside [READ_FLOOR] of the last read is skipped.
     */
    private suspend fun readOnTicks(read: suspend () -> Unit) {
        var lastAt: Instant? = null
        val first = foreground.isForeground.filter { it }.take(1).map { true }
        merge(first, ticks).collect { force ->
            val now = clock.now()
            val previous = lastAt
            if (force || previous == null || now - previous >= READ_FLOOR) {
                lastAt = now
                read()
            }
        }
    }

    private suspend fun keep(day: LocalDate, value: EnergyExpended) {
        lastEnergy[day] = value
        val snapshot = HealthEnergySnapshot(day.toString(), clock.now().toEpochMilliseconds(), value.restingKcal, value.activeKcal, value.workoutKcal)
        appCache.update { it.copy(healthEnergy = snapshot) }
    }

    private suspend fun saved(day: LocalDate): EnergyExpended? =
        appCache.get().healthEnergy?.takeIf { it.day == day.toString() }?.let { EnergyExpended(it.restingKcal, it.activeKcal, it.workoutKcal) }

    /** Midnight to now for today, midnight to midnight for a past day, null for a future one. */
    private fun dayRange(day: LocalDate, timeZone: TimeZone): Pair<Instant, Instant>? {
        val from = day.atStartOfDayIn(timeZone)
        val to = minOf(day.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone), clock.now())
        return if (to <= from) null else from to to
    }

    private suspend fun read(day: LocalDate, timeZone: TimeZone): EnergyExpended? {
        val (from, to) = dayRange(day, timeZone) ?: return null
        return guarded("read energy", Severity.Error) { reader.read(from, to) }
    }

    private suspend fun check(): HealthStatus {
        when (guarded("availability", Severity.Warn) { reader.availability() }) {
            HealthAvailability.Unsupported, null -> return disconnected(HealthStatus.Unsupported)
            HealthAvailability.NeedsInstall -> return disconnected(HealthStatus.NeedsInstall)
            HealthAvailability.Available -> Unit
        }
        val saved = appCache.get()
        if (saved.healthPaused) return disconnected(HealthStatus.NotConnected)
        val access = guarded("check access", Severity.Warn) { reader.access() } ?: HealthAccess.Unknown
        val connected = when (access) {
            HealthAccess.All, HealthAccess.Partly -> true
            // Revoked in Health Connect's own screen, or never asked.
            HealthAccess.NotAsked -> false
            HealthAccess.Unknown -> saved.healthConnected
        }
        if (!connected) return disconnected(HealthStatus.NotConnected)
        // Allowed in the store, or before a sign-out reset the choice: the store is the truth.
        if (!saved.healthConnected) appCache.update { it.copy(healthConnected = true) }
        mutableCanAskForMore.value = access == HealthAccess.Partly
        return HealthStatus.Connected
    }

    private fun disconnected(status: HealthStatus): HealthStatus {
        mutableCanAskForMore.value = false
        return status
    }

    /**
     * A store that throws never crashes a screen and never reads as zero. What failed decides the
     * level: a read or a request that fails means the page cannot show what it is for, so the
     * first one this process goes at error (which reaches Sentry) and repeats at warn; a check
     * that fails falls back and goes at warn. An empty answer is not a failure and is not logged.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> guarded(what: String, severity: Severity, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val message = "Health store: $what failed (${source.key})"
        if (severity == Severity.Error && reported.add(what)) logger.e(e, message) else logger.w(e, message)
        null
    }

    private enum class Severity { Warn, Error }

    /** One upstream per key while anyone collects it, the latest value replayed to each new collector. */
    // Screens ask from the main thread and the widget from the app scope's workers.
    @OptIn(InternalCoroutinesApi::class)
    private inner class SharedByKey<K, V> {
        private val lock = SynchronizedObject()
        private val flows = LinkedHashMap<K, SharedFlow<V>>()

        fun get(key: K, upstream: () -> Flow<V>): Flow<V> = synchronized(lock) {
            flows.getOrPut(key) {
                // A handful of days at most is ever open; the oldest goes, and a collector still on it keeps its own.
                if (flows.size >= MAX_SHARED_DAYS) flows.remove(flows.keys.first())
                upstream().shareIn(appScope, SharingStarted.WhileSubscribed(STOP_SHARING_AFTER.inWholeMilliseconds), replay = 1)
            }
        }
    }

    companion object {
        val REFRESH_EVERY: Duration = 5.minutes

        /** How far back "Data from" looks. */
        val SOURCES_WINDOW: Duration = 28.days

        /** The least time between two reads nobody asked for. */
        val READ_FLOOR: Duration = 30.seconds

        /** How long a screen waits on a first read before it is told "nothing yet". */
        val FIRST_READ_GRACE: Duration = 2.seconds

        /** A day nobody collects stops reading after this, so a quick screen change doesn't restart it. */
        val STOP_SHARING_AFTER: Duration = 5.seconds

        private const val MAX_SHARED_DAYS = 8

        const val EVENT_CONNECTED = "health.connected"
        const val EVENT_DISCONNECTED = "health.disconnected"
        const val EVENT_DENIED = "health.permission_denied"
        const val ATTR_SOURCE = "source"
    }
}
