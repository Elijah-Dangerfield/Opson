package com.opson.libraries.diary.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.diary.DiarySync
import com.opson.libraries.diary.SyncEntity
import com.opson.libraries.diary.SyncRejection
import com.opson.libraries.diary.db.OutboxEntity
import com.opson.libraries.diary.db.SyncStateEntity
import com.opson.libraries.diary.impl.wire.CustomFoodRowDto
import com.opson.libraries.diary.impl.wire.DayRecordRowDto
import com.opson.libraries.diary.impl.wire.FastRowDto
import com.opson.libraries.diary.impl.wire.LogEntryRowDto
import com.opson.libraries.diary.impl.wire.MealFeelingRowDto
import com.opson.libraries.diary.impl.wire.SYNC_ACCEPTS
import com.opson.libraries.diary.impl.wire.SavedMealRowDto
import com.opson.libraries.diary.impl.wire.SyncChangesDto
import com.opson.libraries.diary.impl.wire.SyncMutationDto
import com.opson.libraries.diary.impl.wire.SyncRequestDto
import com.opson.libraries.diary.impl.wire.SyncResponseDto
import com.opson.libraries.diary.impl.wire.WeighInRowDto
import com.opson.libraries.diary.impl.wire.WireJson
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.opson.UserScopedSyncer
import com.opson.libraries.opson.UserScopedWorkStopper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.jsonObject
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Outbox rows per `/v1/sync` request. A steady-state sync carries a handful
 * and never pages. 200 rows is a few dozen KB, comfortably inside the request
 * timeout on a bad connection and well under the server's 500-mutation cap.
 */
internal const val OUTBOX_PAGE_SIZE = 200

/** Requests one sync makes before yielding. Whatever is left goes on the next trigger. */
internal const val MAX_REQUESTS_PER_SYNC = 10

/** How long after a local write the sync waits, so a burst of edits goes up as one request. */
internal val SYNC_DEBOUNCE: Duration = 2.seconds

/** The server refuses a row dated more than this many days past today, in UTC. */
internal const val SERVER_MAX_DAYS_AHEAD = 8

/** Why a sync stopped. Anything but [Drained] left rows queued. */
internal enum class DrainOutcome {
    /** Nothing sendable is queued and the pull caught up. */
    Drained,

    /** This sync used its request budget; the next trigger carries on. */
    BudgetSpent,

    /**
     * The same page came back twice: sending it changed nothing. Without this
     * the loop would post an identical page until the budget ran out.
     */
    Stalled,
}

/**
 * Pushes the diary outbox and pulls what changed, through `POST /v1/sync`
 * (docs/system-design.md section 6).
 *
 * **When.** `UserScopedSyncCoordinator` runs [sync] on sign-in, warm foreground
 * and reconnect; local writes call [requestSync], which syncs [SYNC_DEBOUNCE]
 * after the last one. Every sync posts at least once, even with nothing
 * queued, because the response is how other devices' changes arrive.
 *
 * **Single flight.** Concurrent callers share the sync already running rather
 * than stacking requests. The sync runs in the app scope, so a screen that
 * asked for it and then closed does not cancel it halfway.
 *
 * **Drain.** Up to [MAX_REQUESTS_PER_SYNC] requests of [OUTBOX_PAGE_SIZE]
 * rows, following `hasMore` on the pull side. Progress is judged by the outbox
 * changing, not by the server answering, and a sync that ends with rows still
 * queued says so in a warning: a drain that gives up quietly looks exactly
 * like a clean sync.
 *
 * **Merge.** Acknowledged rows leave the outbox. A refused row leaves it too,
 * the device goes back to the row as it was before, and [rejections] says so.
 * A pulled row replaces the local one unless the local one has a change still
 * waiting, in which case the local one wins and goes up next time. All of it,
 * and the cursor, commit in one transaction.
 *
 * Logs carry counts and ids only. Rows are health data.
 */
@OptIn(FlowPreview::class)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = UserScopedSyncer::class, multibinding = true)
@ContributesBinding(AppScope::class, boundType = UserScopedWorkStopper::class, multibinding = true)
@ContributesBinding(AppScope::class, boundType = DiarySync::class)
@ContributesBinding(AppScope::class, boundType = DiarySyncScheduler::class)
@Inject
class DiarySyncer(
    private val api: DiarySyncApi,
    private val tables: DiaryTables,
    private val settingsStore: SettingsStore,
    private val activeUser: ActiveUserSource,
    private val appScope: AppCoroutineScope,
    private val clock: Clock,
) : UserScopedSyncer, UserScopedWorkStopper, DiarySync, DiarySyncScheduler {

    private val logger = KLog.withTag("DiarySync")

    private val syncMutex = Mutex()
    private var inFlight: Deferred<Result<Unit>>? = null

    /**
     * The user whose work [stopWorkFor] stopped. Their data is about to be
     * wiped, and the session may already be the next user's, so a sync must
     * not start for them again: it would push the departing user's outbox
     * into the new account. Cleared once auth reports someone else.
     */
    @Volatile
    private var stoppedFor: String? = null

    // A channel rather than a shared flow so a request made before the
    // collector below has started is kept, not dropped.
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val rejectionEvents = MutableSharedFlow<SyncRejection>(extraBufferCapacity = 64)

    override val rejections: Flow<SyncRejection> = rejectionEvents.asSharedFlow()

    init {
        appScope.launch { requests.receiveAsFlow().debounce(SYNC_DEBOUNCE).collect { sync() } }
        appScope.launch {
            activeUser.user.collect { user ->
                val stopped = stoppedFor ?: return@collect
                if (user !is ActiveUser.SignedIn || user.userId != stopped) stoppedFor = null
            }
        }
    }

    override fun requestSync() {
        requests.trySend(Unit)
    }

    override suspend fun syncNow(): Result<Unit> = sync()

    override suspend fun sync(): Result<Unit> {
        val deferred = syncMutex.withLock {
            inFlight?.takeIf { it.isActive } ?: appScope.async { runSync() }.also { inFlight = it }
        }
        return try {
            deferred.await()
        } catch (e: CancellationException) {
            // The shared sync was stopped for a user change. Only rethrow when
            // the caller itself is being cancelled.
            currentCoroutineContext().ensureActive()
            Result.failure(e)
        }
    }

    override suspend fun stopWorkFor(previousUserId: String) {
        stoppedFor = previousUserId
        val running = syncMutex.withLock { inFlight.also { inFlight = null } }
        running?.cancelAndJoin()
    }

    private suspend fun runSync(): Result<Unit> {
        val user = activeUser.user.value
        if (user is ActiveUser.SignedIn && user.userId == stoppedFor) return Result.success(Unit)
        return Catching { drain(user) }
    }

    private suspend fun drain(user: ActiveUser) {
        var cursor = tables.syncState.get()?.cursor ?: 0L
        var previousKeys: List<String>? = null
        var outcome = DrainOutcome.BudgetSpent
        var pullComplete = false
        var pulledSettings = false

        for (request in 0 until MAX_REQUESTS_PER_SYNC) {
            val page = tables.outbox.page(OUTBOX_PAGE_SIZE, maxSendableDay())
            val keys = page.map { it.id }
            if (page.isNotEmpty() && keys == previousKeys) {
                outcome = DrainOutcome.Stalled
                break
            }
            previousKeys = keys

            val request = SyncRequestDto(cursor = cursor, mutations = page.mapNotNull { it.toMutation() }, accepts = SYNC_ACCEPTS)
            val response = api.sync(request).getOrThrow()
            pulledSettings = pulledSettings || response.changes.settings != null
            apply(response)
            cursor = response.cursor
            pullComplete = !response.hasMore
            // A short page means nothing sendable was left behind it. Done,
            // unless the server left part of it unresolved, in which case the
            // next pass sees the same page and calls it stalled.
            if (pullComplete && page.size < OUTBOX_PAGE_SIZE && (keys.isEmpty() || tables.outbox.getByIds(keys).isEmpty())) {
                outcome = DrainOutcome.Drained
                break
            }
        }

        if (pullComplete && user is ActiveUser.SignedIn && settingsStore.markPulled(user.userId, pulledSettings)) {
            requestSync()
        }
        warnIfIncomplete(outcome)
    }

    private suspend fun warnIfIncomplete(outcome: DrainOutcome) {
        val remaining = tables.outbox.count()
        if (remaining == 0) return
        val sendable = tables.outbox.page(OUTBOX_PAGE_SIZE, maxSendableDay()).size
        when {
            outcome == DrainOutcome.Stalled -> logger.w {
                "Diary outbox stalled: the same page came back unresolved; $remaining change(s) queued"
            }
            sendable > 0 -> logger.w {
                "Diary sync ended ($outcome) with $remaining change(s) still queued; the next sync continues"
            }
            // Only changes to days the server will not take yet. Expected, so no warning.
            else -> logger.i { "Diary outbox holds $remaining change(s) for days ahead" }
        }
    }

    private fun maxSendableDay(): String =
        clock.now().toLocalDateTime(TimeZone.UTC).date.plus(SERVER_MAX_DAYS_AHEAD, DateTimeUnit.DAY).toString()

    private suspend fun OutboxEntity.toMutation(): SyncMutationDto? {
        val row = Catching { WireJson.parseToJsonElement(payload).jsonObject }.getOrNull()
        if (row == null) {
            // Unreadable, so it can never be sent. Dropping it is the only way
            // past it; keeping it would stall every sync behind it.
            logger.e { "Dropping an unreadable outbox row ($entity $id)" }
            tables.outbox.deleteByIds(listOf(id))
            return null
        }
        return SyncMutationDto(id = id, entity = entity, row = row)
    }

    private suspend fun apply(response: SyncResponseDto) {
        val settingsReverts = mutableListOf<String?>()
        val rejected = mutableListOf<SyncRejection>()
        tables.transactor.inTransaction {
            if (response.applied.isNotEmpty()) tables.outbox.deleteByIds(response.applied)
            if (response.rejected.isNotEmpty()) {
                val codes = response.rejected.associate { it.id to it.code }
                // A refused row that is no longer here was superseded by a
                // newer change, which gets its own verdict. Nothing to undo.
                val rows = tables.outbox.getByIds(codes.keys.toList())
                if (rows.isNotEmpty()) tables.outbox.deleteByIds(rows.map { it.id })
                rows.forEach { row ->
                    val entity = SyncEntity.fromWire(row.entity) ?: return@forEach
                    if (entity == SyncEntity.Settings) settingsReverts += row.basePayload else revert(entity, row)
                    rejected += SyncRejection(entity, row.entityId, codes.getValue(row.id))
                }
            }
            merge(response.changes)
            tables.syncState.upsert(
                SyncStateEntity(cursor = response.cursor, lastSuccessAtEpochMs = clock.now().toEpochMilliseconds()),
            )
        }
        settingsReverts.forEach { settingsStore.revert(it) }
        response.changes.settings?.let { settingsStore.applyPulled(it) }
        if (rejected.isNotEmpty()) {
            logger.w { "Server refused ${rejected.size} diary change(s), rolled back: ${rejected.groupingBy { it.entity.wire }.eachCount()}" }
            rejected.forEach { rejectionEvents.tryEmit(it) }
        }
    }

    /** The merge rule: a pulled row replaces the local one unless the local one has a change waiting. */
    private suspend fun merge(changes: SyncChangesDto) {
        suspend fun pending(entity: SyncEntity): Set<String> = tables.outbox.pendingEntityIds(entity.wire).toSet()

        if (changes.logEntries.isNotEmpty()) {
            val waiting = pending(SyncEntity.LogEntry)
            changes.logEntries.filter { it.id !in waiting }.map { it.toEntity() }
                .takeIf { it.isNotEmpty() }?.let { tables.entries.upsert(it) }
        }
        if (changes.dayRecords.isNotEmpty()) {
            val waiting = pending(SyncEntity.DayRecord)
            changes.dayRecords.filter { it.day !in waiting }.map { it.toEntity() }
                .takeIf { it.isNotEmpty() }?.let { tables.days.upsert(it) }
        }
        if (changes.mealFeelings.isNotEmpty()) {
            val waiting = pending(SyncEntity.MealFeeling)
            changes.mealFeelings.filter { feelingKey(it.day, it.slot) !in waiting }.map { it.toEntity() }
                .takeIf { it.isNotEmpty() }?.let { tables.feelings.upsert(it) }
        }
        if (changes.weighIns.isNotEmpty()) {
            val waiting = pending(SyncEntity.WeighIn)
            changes.weighIns.filter { it.id !in waiting }.map { it.toEntity() }
                .takeIf { it.isNotEmpty() }?.let { tables.weighIns.upsert(it) }
        }
        if (changes.customFoods.isNotEmpty()) {
            val waiting = pending(SyncEntity.CustomFood)
            changes.customFoods.filter { it.id !in waiting }.map { it.toEntity() }
                .takeIf { it.isNotEmpty() }?.let { tables.customFoods.upsert(it) }
        }
        if (changes.savedMeals.isNotEmpty()) {
            val waiting = pending(SyncEntity.SavedMeal)
            changes.savedMeals.filter { it.id !in waiting }.map { it.toEntity() }
                .takeIf { it.isNotEmpty() }?.let { tables.savedMeals.upsert(it) }
        }
        val fasts = changes.fasts.orEmpty()
        if (fasts.isNotEmpty()) {
            val waiting = pending(SyncEntity.Fast)
            fasts.filter { it.day !in waiting }.map { it.toEntity() }
                .takeIf { it.isNotEmpty() }?.let { tables.fasts.upsert(it) }
        }
    }

    /** Puts a refused row back as it was before the change, or removes it if it was new. */
    private suspend fun revert(entity: SyncEntity, row: OutboxEntity) {
        val base = row.basePayload
        Catching {
            when (entity) {
                SyncEntity.LogEntry ->
                    if (base == null) tables.entries.delete(row.entityId)
                    else tables.entries.upsert(listOf(decode(LogEntryRowDto.serializer(), base).toEntity()))

                SyncEntity.DayRecord ->
                    if (base == null) tables.days.delete(row.entityId)
                    else tables.days.upsert(listOf(decode(DayRecordRowDto.serializer(), base).toEntity()))

                SyncEntity.MealFeeling -> {
                    val (day, slot) = row.entityId.split('|', limit = 2)
                    if (base == null) tables.feelings.delete(day, slot)
                    else tables.feelings.upsert(listOf(decode(MealFeelingRowDto.serializer(), base).toEntity()))
                }

                SyncEntity.WeighIn ->
                    if (base == null) tables.weighIns.delete(row.entityId)
                    else tables.weighIns.upsert(listOf(decode(WeighInRowDto.serializer(), base).toEntity()))

                SyncEntity.CustomFood ->
                    if (base == null) tables.customFoods.delete(row.entityId)
                    else tables.customFoods.upsert(listOf(decode(CustomFoodRowDto.serializer(), base).toEntity()))

                SyncEntity.SavedMeal ->
                    if (base == null) tables.savedMeals.delete(row.entityId)
                    else tables.savedMeals.upsert(listOf(decode(SavedMealRowDto.serializer(), base).toEntity()))

                SyncEntity.Fast ->
                    if (base == null) tables.fasts.delete(row.entityId)
                    else tables.fasts.upsert(listOf(decode(FastRowDto.serializer(), base).toEntity()))

                SyncEntity.Settings -> Unit
            }
        }.onFailure { logger.w(it) { "Could not roll back a refused ${entity.wire} (${row.entityId})" } }
    }

    private fun <T> decode(serializer: KSerializer<T>, json: String): T =
        WireJson.decodeFromString(serializer, json)
}
