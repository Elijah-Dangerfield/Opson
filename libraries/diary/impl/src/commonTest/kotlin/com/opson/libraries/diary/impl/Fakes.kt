package com.opson.libraries.diary.impl

import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.core.logging.LogEntry
import com.opson.libraries.core.logging.LogId
import com.opson.libraries.core.logging.LogLevel
import com.opson.libraries.core.logging.LogTree
import com.opson.libraries.diary.db.CustomFoodDao
import com.opson.libraries.diary.db.CustomFoodEntity
import com.opson.libraries.diary.db.DayRecordDao
import com.opson.libraries.diary.db.DayRecordEntity
import com.opson.libraries.diary.db.ExerciseDao
import com.opson.libraries.diary.db.ExerciseEntity
import com.opson.libraries.diary.db.FastDao
import com.opson.libraries.diary.db.FastEntity
import com.opson.libraries.diary.db.LogEntryDao
import com.opson.libraries.diary.db.LogEntryEntity
import com.opson.libraries.diary.db.MealFeelingDao
import com.opson.libraries.diary.db.MealFeelingEntity
import com.opson.libraries.diary.db.OutboxDao
import com.opson.libraries.diary.db.OutboxEntity
import com.opson.libraries.diary.db.SavedMealDao
import com.opson.libraries.diary.db.SavedMealEntity
import com.opson.libraries.diary.db.SyncStateDao
import com.opson.libraries.diary.db.SyncStateEntity
import com.opson.libraries.diary.db.WeighInDao
import com.opson.libraries.diary.db.WeighInEntity
import com.opson.libraries.diary.impl.wire.SyncRequestDto
import com.opson.libraries.diary.impl.wire.SyncResponseDto
import com.opson.libraries.opson.storage.db.DatabaseTransactor
import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.CacheJsonSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/*
 * Hand-rolled fakes for the diary impl tests (docs/practices/testing.md: no
 * mocking libraries). FakeDiaryDb stands in for the Room database: one table
 * per DAO, a transactor that rolls every table back when its block throws, and
 * a record of any write made outside a transaction.
 */

class FakeTable<K, V> {
    val state = MutableStateFlow<Map<K, V>>(emptyMap())
    val rows: Collection<V> get() = state.value.values

    fun put(key: K, value: V) {
        state.value = state.value + (key to value)
    }

    fun remove(key: K) {
        state.value = state.value - key
    }

    fun clear() {
        state.value = emptyMap()
    }

    fun observe(): Flow<Map<K, V>> = state
}

class FakeDiaryDb {
    val entries = FakeTable<String, LogEntryEntity>()
    val days = FakeTable<String, DayRecordEntity>()
    val feelings = FakeTable<Pair<String, String>, MealFeelingEntity>()
    val weighIns = FakeTable<String, WeighInEntity>()
    val customFoods = FakeTable<String, CustomFoodEntity>()
    val savedMeals = FakeTable<String, SavedMealEntity>()
    val fasts = FakeTable<String, FastEntity>()
    val exercises = FakeTable<String, ExerciseEntity>()
    val outbox = FakeTable<String, OutboxEntity>()
    val syncState = FakeTable<Int, SyncStateEntity>()

    private var depth = 0
    val writesOutsideTransaction = mutableListOf<String>()
    var transactionsCommitted = 0
        private set

    /** Set to make the next outbox insert throw, to prove the row write rolls back with it. */
    var failNextOutboxInsert = false

    fun recordWrite(table: String) {
        if (depth == 0) writesOutsideTransaction += table
    }

    val transactor = object : DatabaseTransactor {
        override suspend fun <T> inTransaction(block: suspend () -> T): T {
            val snapshot = snapshot()
            depth++
            try {
                val result = block()
                if (depth == 1) transactionsCommitted++
                return result
            } catch (t: Throwable) {
                restore(snapshot)
                throw t
            } finally {
                depth--
            }
        }
    }

    private fun snapshot() = listOf(
        entries.state.value, days.state.value, feelings.state.value, weighIns.state.value,
        customFoods.state.value, savedMeals.state.value, outbox.state.value, syncState.state.value,
        fasts.state.value,
    )

    @Suppress("UNCHECKED_CAST")
    private fun restore(s: List<Map<*, *>>) {
        entries.state.value = s[0] as Map<String, LogEntryEntity>
        days.state.value = s[1] as Map<String, DayRecordEntity>
        feelings.state.value = s[2] as Map<Pair<String, String>, MealFeelingEntity>
        weighIns.state.value = s[3] as Map<String, WeighInEntity>
        customFoods.state.value = s[4] as Map<String, CustomFoodEntity>
        savedMeals.state.value = s[5] as Map<String, SavedMealEntity>
        outbox.state.value = s[6] as Map<String, OutboxEntity>
        syncState.state.value = s[7] as Map<Int, SyncStateEntity>
        fasts.state.value = s[8] as Map<String, FastEntity>
    }

    val entryDao = object : LogEntryDao {
        override suspend fun upsert(rows: List<LogEntryEntity>) {
            recordWrite("log_entries")
            rows.forEach { entries.put(it.id, it) }
        }

        override suspend fun get(id: String) = entries.state.value[id]

        override suspend fun getDay(day: String) = live().filter { it.day == day }.sortedBy { it.loggedAtEpochMs }

        override fun observeDay(day: String) =
            entries.observe().map { m -> m.values.filter { !it.deleted && it.day == day }.sortedBy { it.loggedAtEpochMs } }

        override fun observeRange(from: String, to: String) =
            entries.observe().map { m -> m.values.filter { !it.deleted && it.day in from..to }.sortedBy { it.loggedAtEpochMs } }

        override fun observeDaysWithEntries(from: String, to: String) =
            entries.observe().map { m -> m.values.filter { !it.deleted && it.day in from..to }.map { it.day }.distinct() }

        override fun observeLatest(limit: Int) =
            entries.observe().map { m -> m.values.filter { !it.deleted }.sortedByDescending { it.loggedAtEpochMs }.take(limit) }

        override fun observeLatestFoods(limit: Int) =
            entries.observe().map { m ->
                m.values.filter { !it.deleted && (it.foodId != null || it.customFoodId != null) }
                    .sortedByDescending { it.loggedAtEpochMs }
                    .take(limit)
            }

        override fun observeSlotSince(slot: String, fromDay: String) =
            entries.observe().map { m -> m.values.filter { !it.deleted && it.slot == slot && it.day >= fromDay } }

        override fun observeSince(fromDay: String) =
            entries.observe().map { m -> m.values.filter { !it.deleted && it.day >= fromDay } }

        override suspend fun delete(id: String) {
            recordWrite("log_entries")
            entries.remove(id)
        }

        override suspend fun deleteAll() = entries.clear()

        private fun live() = entries.rows.filter { !it.deleted }
    }

    val dayDao = object : DayRecordDao {
        override suspend fun upsert(rows: List<DayRecordEntity>) {
            recordWrite("day_records")
            rows.forEach { days.put(it.day, it) }
        }

        override suspend fun get(day: String) = days.state.value[day]

        override fun observe(day: String) = days.observe().map { m -> m[day]?.takeUnless { it.deleted } }

        override fun observeRange(from: String, to: String) =
            days.observe().map { m -> m.values.filter { !it.deleted && it.day in from..to }.sortedBy { it.day } }

        override suspend fun delete(day: String) {
            recordWrite("day_records")
            days.remove(day)
        }

        override suspend fun deleteAll() = days.clear()
    }

    val feelingDao = object : MealFeelingDao {
        override suspend fun upsert(rows: List<MealFeelingEntity>) {
            recordWrite("meal_feelings")
            rows.forEach { feelings.put(it.day to it.slot, it) }
        }

        override suspend fun get(day: String, slot: String) = feelings.state.value[day to slot]

        override fun observeDay(day: String) = feelings.observe().map { m -> m.values.filter { !it.deleted && it.day == day } }

        override fun observeRange(from: String, to: String) =
            feelings.observe().map { m -> m.values.filter { !it.deleted && it.day in from..to } }

        override suspend fun delete(day: String, slot: String) {
            recordWrite("meal_feelings")
            feelings.remove(day to slot)
        }

        override suspend fun deleteAll() = feelings.clear()
    }

    val weighInDao = object : WeighInDao {
        override suspend fun upsert(rows: List<WeighInEntity>) {
            recordWrite("weigh_ins")
            rows.forEach { weighIns.put(it.id, it) }
        }

        override suspend fun get(id: String) = weighIns.state.value[id]

        override fun observeSince(sinceEpochMs: Long) = weighIns.observe().map { m ->
            m.values.filter { !it.deleted && it.measuredAtEpochMs >= sinceEpochMs }.sortedBy { it.measuredAtEpochMs }
        }

        override suspend fun delete(id: String) {
            recordWrite("weigh_ins")
            weighIns.remove(id)
        }

        override suspend fun deleteAll() = weighIns.clear()
    }

    val customFoodDao = object : CustomFoodDao {
        override suspend fun upsert(rows: List<CustomFoodEntity>) {
            recordWrite("custom_foods")
            rows.forEach { customFoods.put(it.id, it) }
        }

        override suspend fun get(id: String) = customFoods.state.value[id]

        override fun observeAll() = customFoods.observe().map { m -> m.values.filter { !it.deleted }.sortedBy { it.name.lowercase() } }

        override suspend fun delete(id: String) {
            recordWrite("custom_foods")
            customFoods.remove(id)
        }

        override suspend fun deleteAll() = customFoods.clear()
    }

    val savedMealDao = object : SavedMealDao {
        override suspend fun upsert(rows: List<SavedMealEntity>) {
            recordWrite("saved_meals")
            rows.forEach { savedMeals.put(it.id, it) }
        }

        override suspend fun get(id: String) = savedMeals.state.value[id]

        override fun observeAll() = savedMeals.observe().map { m -> m.values.filter { !it.deleted }.sortedBy { it.name.lowercase() } }

        override suspend fun delete(id: String) {
            recordWrite("saved_meals")
            savedMeals.remove(id)
        }

        override suspend fun deleteAll() = savedMeals.clear()
    }

    val fastDao = object : FastDao {
        override suspend fun upsert(rows: List<FastEntity>) {
            recordWrite("fasts")
            rows.forEach { fasts.put(it.day, it) }
        }

        override suspend fun get(day: String) = fasts.state.value[day]

        override suspend fun latest() = latestOf(fasts.state.value)

        override fun observeLatest() = fasts.observe().map { latestOf(it) }

        override suspend fun delete(day: String) {
            recordWrite("fasts")
            fasts.remove(day)
        }

        override suspend fun deleteAll() = fasts.clear()

        private fun latestOf(rows: Map<String, FastEntity>) =
            rows.values.filter { !it.deleted }.maxWithOrNull(compareBy<FastEntity> { it.startedAtEpochMs }.thenBy { it.day })
    }

    val exerciseDao = object : ExerciseDao {
        override suspend fun upsert(rows: List<ExerciseEntity>) {
            recordWrite("exercises")
            rows.forEach { exercises.put(it.id, it) }
        }

        override suspend fun get(id: String) = exercises.state.value[id]

        override fun observeDay(day: String) = exercises.observe().map { rows ->
            rows.values.filter { it.day == day && !it.deleted }.sortedWith(compareBy<ExerciseEntity> { it.startedAtEpochMs }.thenBy { it.id })
        }

        override suspend fun delete(id: String) {
            recordWrite("exercises")
            exercises.remove(id)
        }

        override suspend fun deleteAll() = exercises.clear()
    }

    val outboxDao = object : OutboxDao {
        override suspend fun insert(row: OutboxEntity) {
            recordWrite("outbox")
            if (failNextOutboxInsert) {
                failNextOutboxInsert = false
                error("disk full")
            }
            check(outbox.rows.none { it.entity == row.entity && it.entityId == row.entityId }) {
                "unique (entity, entityId) violated"
            }
            outbox.put(row.id, row)
        }

        override suspend fun pendingFor(entity: String, entityId: String) =
            outbox.rows.firstOrNull { it.entity == entity && it.entityId == entityId }

        override suspend fun deleteFor(entity: String, entityId: String) {
            recordWrite("outbox")
            outbox.rows.filter { it.entity == entity && it.entityId == entityId }.forEach { outbox.remove(it.id) }
        }

        override suspend fun page(limit: Int, maxDay: String) = outbox.rows
            .filter { row -> row.day.let { it == null || it <= maxDay } }
            .sortedWith(compareBy<OutboxEntity> { it.createdAtEpochMs }.thenBy { it.id })
            .take(limit)

        override suspend fun pageExcept(limit: Int, maxDay: String, entities: List<String>) =
            page(Int.MAX_VALUE, maxDay).filter { it.entity !in entities }.take(limit)

        override suspend fun getByIds(ids: List<String>) = ids.mapNotNull { outbox.state.value[it] }

        override suspend fun deleteByIds(ids: List<String>) {
            recordWrite("outbox")
            ids.forEach { outbox.remove(it) }
        }

        override suspend fun pendingEntityIds(entity: String) = outbox.rows.filter { it.entity == entity }.map { it.entityId }

        override suspend fun count() = outbox.rows.size

        override fun observeCount() = outbox.observe().map { it.size }

        override suspend fun deleteAll() = outbox.clear()
    }

    val syncStateDao = object : SyncStateDao {
        override suspend fun get() = syncState.state.value[SyncStateEntity.SINGLETON_ID]

        override suspend fun upsert(row: SyncStateEntity) {
            recordWrite("sync_state")
            syncState.put(row.id, row)
        }

        override suspend fun deleteAll() = syncState.clear()
    }

    val tables = DiaryTables(
        entries = entryDao,
        days = dayDao,
        feelings = feelingDao,
        weighIns = weighInDao,
        customFoods = customFoodDao,
        savedMeals = savedMealDao,
        fasts = fastDao,
        exercises = exerciseDao,
        outbox = outboxDao,
        syncState = syncStateDao,
        transactor = transactor,
    )
}

/** Counts "food was logged" calls instead of ending fasts. */
class FakeFastEnder : FastEnder {
    var foodLogged = 0
        private set

    override suspend fun foodLogged() {
        foodLogged++
    }
}

class FakeClock(var now: Instant = Instant.parse("2026-10-25T12:00:00Z")) : Clock {
    override fun now(): Instant = now
}

class FakeCalendar(var today: LocalDate = LocalDate(2026, 10, 25)) : LocalCalendar {
    override fun timeZone(): TimeZone = TimeZone.UTC

    override fun today(): LocalDate = today
}

class FakeScheduler : DiarySyncScheduler {
    var requests = 0
        private set

    override fun requestSync() {
        requests++
    }
}

class FakeActiveUser(initial: ActiveUser = ActiveUser.SignedIn("user-a")) : ActiveUserSource {
    override val user = MutableStateFlow(initial)
}

/** Answers `/v1/sync` with whatever [respond] returns, and remembers every request. */
class FakeSyncApi(var respond: suspend (SyncRequestDto) -> SyncResponseDto) : DiarySyncApi {
    val requests = mutableListOf<SyncRequestDto>()

    override suspend fun sync(request: SyncRequestDto): Result<SyncResponseDto> {
        requests += request
        return runCatching { respond(request) }
    }
}

/**
 * An in-memory [Cache] with an atomic update, like the real ones. It starts
 * from [initial], read lazily because a serializer's default is only
 * reachable through a suspend call.
 */
class FakeCache<T : Any>(private val initial: suspend () -> T) : Cache<T> {
    private val state = MutableStateFlow<T?>(null)
    private val lock = Mutex()

    override val updates: Flow<T> = flow {
        get()
        emitAll(state.filterNotNull())
    }

    override suspend fun get(): T {
        state.value?.let { return it }
        state.compareAndSet(null, initial())
        return checkNotNull(state.value)
    }

    override suspend fun set(value: T) {
        state.value = value
    }

    override suspend fun clear() {
        state.value = initial()
    }

    override suspend fun update(transform: (T) -> T): T = lock.withLock {
        transform(get()).also { state.value = it }
    }
}

class FakeCacheFactory : CacheFactory {
    override fun <T : Any> inMemory(defaultValue: () -> T): Cache<T> = FakeCache { defaultValue() }

    override fun <T : Any> persistent(name: String, serializer: CacheJsonSerializer<T>, loadEagerly: Boolean): Cache<T> =
        FakeCache { serializer.read(null) }
}

/** Remembers what was logged at warn and above, to assert on the drain warning. */
class RecordingTree : LogTree() {
    val entries = mutableListOf<LogEntry>()

    override fun log(entry: LogEntry): LogId? {
        if (entry.level.priority >= LogLevel.Warn.priority) entries += entry
        return null
    }
}
