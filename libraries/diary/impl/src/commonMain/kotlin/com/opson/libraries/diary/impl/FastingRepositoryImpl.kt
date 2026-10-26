package com.opson.libraries.diary.impl

import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import com.opson.libraries.diary.Fast
import com.opson.libraries.diary.FastingRelease
import com.opson.libraries.diary.FastingRepository
import com.opson.libraries.diary.FastingSchedule
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.MinuteTicker
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.SyncEntity
import com.opson.libraries.diary.db.FastEntity
import com.opson.libraries.diary.fastingTimerOn
import com.opson.libraries.diary.minuteTicks
import com.opson.libraries.diary.impl.wire.FastRowDto
import com.opson.libraries.diary.impl.wire.WireJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Told just before food is logged on a day up to today, so a running fast ends: logging food
 * always ends a fast. An interface so the diary's tests can stand it in.
 */
interface FastEnder {
    suspend fun foodLogged()
}

/**
 * The fasts on this device (docs/plans/screens/fasting.md).
 *
 * **Starting.** A fast starts when the eating window closes. Nothing runs in the background, so
 * the screens that show the timer call [startDue] on every tick, and it writes the fast that
 * [FastingSchedule.due] says is missing: dated at the close, not at the moment it was noticed.
 * Its `clientUpdatedAt` is that start too, so a real end on another device, always later, wins
 * last-write-wins over a second device starting the same evening's fast.
 *
 * **Ending.** End fast, turning the timer off, and logging food each stamp the end now. Reaching
 * the goal is never written; [Fast.endsBy] derives it.
 *
 * Telemetry: `fast.started` with the plan key and `fast.ended` with what ended it. Never a
 * duration or a time: how long someone goes without eating is health data.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = FastingRepository::class)
@ContributesBinding(AppScope::class, boundType = FastEnder::class)
@Inject
class FastingRepositoryImpl(
    private val tables: DiaryTables,
    private val writer: DiaryWriter,
    private val settingsRepository: SettingsRepository,
    private val release: FastingRelease,
    private val calendar: LocalCalendar,
    private val clock: Clock,
) : FastingRepository, FastEnder {

    private val logger = KLog.withTag("Fasting")

    // One start or end at a time, so two screens ticking together cannot both start a fast.
    private val mutex = Mutex()

    override fun observeLatest(): Flow<Fast?> = tables.fasts.observeLatest().map { it?.toDomain() }

    override suspend fun startDue() {
        mutex.withLock { startDueLocked() }
    }

    override suspend fun end() = endRunning(BY_USER)

    override suspend fun endForTimerOff() = endRunning(BY_TIMER_OFF)

    /**
     * Starts the evening's fast first when it is due and was never written (the app was closed
     * when the window shut), so the record reads from the close to this meal rather than not at
     * all. Then ends whatever runs.
     */
    override suspend fun foodLogged() {
        mutex.withLock { startDueLocked() }
        endRunning(BY_FOOD)
    }

    private suspend fun startDueLocked() {
        // Until the user's own settings have landed, these are the defaults, not theirs.
        if (!settingsRepository.isKnown.value) return
        val settings = settingsRepository.settings.value
        if (!settings.fastingTimerOn(release.isOn())) return
        val latest = tables.fasts.latest()?.toDomain()
        val lastLoggedAt = tables.entries.observeLatest(1).first().firstOrNull()
            ?.let { Instant.fromEpochMilliseconds(it.loggedAtEpochMs) }
        val due = FastingSchedule.due(settings.fastPlan, clock.now(), calendar.timeZone(), latest, lastLoggedAt) ?: return
        val started = writer.write {
            // A row for that evening already exists (pulled, or tombstoned): leave it.
            if (tables.fasts.get(due.day.toString()) != null) return@write false
            put(due.toEntity(clientUpdatedAtEpochMs = due.startedAt.toEpochMilliseconds()), base = null)
            true
        }
        if (started) logger.logEvent("fast.started", ATTR_PLAN to settings.fastPlan.key)
    }

    private suspend fun endRunning(by: String) {
        val ended = mutex.withLock {
            val now = clock.now()
            val latest = tables.fasts.latest() ?: return@withLock false
            if (!latest.toDomain().isRunningAt(now)) return@withLock false
            writer.write {
                put(
                    latest.copy(endedAtEpochMs = nowEpochMs, clientUpdatedAtEpochMs = stamp(latest.clientUpdatedAtEpochMs)),
                    base = latest,
                )
            }
            true
        }
        if (ended) logger.logEvent("fast.ended", ATTR_BY to by)
    }

    private suspend fun DiaryWriter.WriteScope.put(row: FastEntity, base: FastEntity?) {
        tables.fasts.upsert(listOf(row))
        enqueue(SyncEntity.Fast, row.day, row.json(), base?.json(), day = row.day)
    }

    private fun FastEntity.json() = WireJson.encodeToString(FastRowDto.serializer(), toRow())

    private companion object {
        const val ATTR_PLAN = "plan"
        const val ATTR_BY = "by"
        const val BY_USER = "user"
        const val BY_FOOD = "food"
        const val BY_TIMER_OFF = "timer_off"
    }
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class SystemMinuteTicker(private val clock: Clock) : MinuteTicker {
    override fun ticks(): Flow<Instant> = minuteTicks(clock)
}

internal fun FastEntity.toDomain(): Fast = Fast(
    day = LocalDate.parse(day),
    startedAt = Instant.fromEpochMilliseconds(startedAtEpochMs),
    endedAt = endedAtEpochMs?.let(Instant::fromEpochMilliseconds),
    goalHours = goalHours,
)

internal fun Fast.toEntity(clientUpdatedAtEpochMs: Long): FastEntity = FastEntity(
    day = day.toString(),
    startedAtEpochMs = startedAt.toEpochMilliseconds(),
    endedAtEpochMs = endedAt?.toEpochMilliseconds(),
    goalHours = goalHours,
    clientUpdatedAtEpochMs = clientUpdatedAtEpochMs,
    deleted = false,
)

internal fun FastEntity.toRow(): FastRowDto = FastRowDto(
    day = day,
    startedAtEpochMs = startedAtEpochMs,
    endedAtEpochMs = endedAtEpochMs,
    goalHours = goalHours,
    clientUpdatedAtEpochMs = clientUpdatedAtEpochMs,
    deleted = deleted,
)

internal fun FastRowDto.toEntity(): FastEntity = FastEntity(
    day = day,
    startedAtEpochMs = startedAtEpochMs,
    endedAtEpochMs = endedAtEpochMs,
    goalHours = goalHours,
    clientUpdatedAtEpochMs = clientUpdatedAtEpochMs,
    deleted = deleted,
)
