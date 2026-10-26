package com.opson.libraries.diary.impl

import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.SyncEntity
import com.opson.libraries.diary.db.CustomFoodDao
import com.opson.libraries.diary.db.DayRecordDao
import com.opson.libraries.diary.db.LogEntryDao
import com.opson.libraries.diary.db.MealFeelingDao
import com.opson.libraries.diary.db.OutboxDao
import com.opson.libraries.diary.db.OutboxEntity
import com.opson.libraries.diary.db.SavedMealDao
import com.opson.libraries.diary.db.SyncStateDao
import com.opson.libraries.diary.db.WeighInDao
import com.opson.libraries.opson.storage.db.DatabaseTransactor
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.math.max
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Every diary table and the transactor that writes them together. One parameter instead of nine. */
@Inject
class DiaryTables(
    val entries: LogEntryDao,
    val days: DayRecordDao,
    val feelings: MealFeelingDao,
    val weighIns: WeighInDao,
    val customFoods: CustomFoodDao,
    val savedMeals: SavedMealDao,
    val outbox: OutboxDao,
    val syncState: SyncStateDao,
    val transactor: DatabaseTransactor,
)

/** Asks for a sync soon after a local write. [DiarySyncer] debounces it. */
interface DiarySyncScheduler {
    fun requestSync()
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class SystemLocalCalendar(private val clock: Clock) : LocalCalendar {
    // Read each time rather than cached: the user can cross a time zone with the app open.
    override fun timeZone(): TimeZone = TimeZone.currentSystemDefault()

    override fun today(): LocalDate = clock.now().toLocalDateTime(timeZone()).date
}

/** Client ids are lowercase UUIDs, which is the only spelling the server accepts. */
internal fun newId(): String = Uuid.random().toString()

/**
 * The `clientUpdatedAt` for a change to a row last stamped [previous]. Always
 * strictly newer than [previous]: the server keeps a write only when it is
 * strictly newer, so two edits in the same millisecond, or a clock that stepped
 * back, would otherwise be acknowledged and then silently dropped.
 */
internal fun stamp(nowEpochMs: Long, previous: Long?): Long = max(nowEpochMs, (previous ?: Long.MIN_VALUE / 2) + 1)

/**
 * Writes rows and their outbox mutations in one transaction, then asks for a
 * sync. Every local diary write goes through here, which is what makes "the
 * row and its mutation land together" a property of the code rather than of
 * each call site.
 */
@Inject
class DiaryWriter(
    private val tables: DiaryTables,
    private val clock: Clock,
    private val scheduler: DiarySyncScheduler,
) {
    suspend fun <T> write(block: suspend WriteScope.() -> T): T {
        val scope = WriteScope(clock.now().toEpochMilliseconds())
        val result = tables.transactor.inTransaction { scope.block() }
        if (scope.enqueued > 0) scheduler.requestSync()
        return result
    }

    inner class WriteScope(val nowEpochMs: Long) {
        internal var enqueued = 0
            private set

        fun stamp(previous: Long?): Long = stamp(nowEpochMs, previous)

        /**
         * Queues [payload] as the row's next state. A change already waiting
         * for the same row is replaced under a new id, keeping its base, so the
         * server's ack for the old id cannot remove this one.
         */
        suspend fun enqueue(entity: SyncEntity, entityId: String, payload: String, base: String?, day: String? = null) {
            tables.outbox.enqueue(entity, entityId, payload, base, nowEpochMs, day)
            enqueued++
        }
    }
}

internal suspend fun OutboxDao.enqueue(
    entity: SyncEntity,
    entityId: String,
    payload: String,
    base: String?,
    nowEpochMs: Long,
    day: String? = null,
) {
    val waiting = pendingFor(entity.wire, entityId)
    if (waiting != null) deleteFor(entity.wire, entityId)
    insert(
        OutboxEntity(
            id = newId(),
            entity = entity.wire,
            entityId = entityId,
            payload = payload,
            basePayload = if (waiting != null) waiting.basePayload else base,
            createdAtEpochMs = nowEpochMs,
            day = day,
        ),
    )
}

/** The outbox key of a meal feeling, which the server keys by day and slot. */
internal fun feelingKey(day: String, slot: String): String = "$day|$slot"
