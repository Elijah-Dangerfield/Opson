package com.opson.libraries.diary.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.opson.libraries.opson.storage.db.ClearableDao
import kotlinx.coroutines.flow.Flow

/*
 * One DAO per table, each a ClearableDao so a user change wipes it. Writes that
 * must land together with their outbox row go through a DatabaseTransactor in
 * the repository, not through a combined DAO method, so each DAO stays small
 * enough to fake by hand.
 */

@Dao
interface LogEntryDao : ClearableDao {
    @Upsert
    suspend fun upsert(rows: List<LogEntryEntity>)

    @Query("SELECT * FROM log_entries WHERE id = :id")
    suspend fun get(id: String): LogEntryEntity?

    @Query("SELECT * FROM log_entries WHERE day = :day AND deleted = 0 ORDER BY loggedAtEpochMs, id")
    suspend fun getDay(day: String): List<LogEntryEntity>

    @Query("SELECT * FROM log_entries WHERE day = :day AND deleted = 0 ORDER BY loggedAtEpochMs, id")
    fun observeDay(day: String): Flow<List<LogEntryEntity>>

    @Query(
        "SELECT * FROM log_entries WHERE day >= :from AND day <= :to AND deleted = 0 " +
            "ORDER BY day, loggedAtEpochMs, id",
    )
    fun observeRange(from: String, to: String): Flow<List<LogEntryEntity>>

    @Query("SELECT DISTINCT day FROM log_entries WHERE day >= :from AND day <= :to AND deleted = 0")
    fun observeDaysWithEntries(from: String, to: String): Flow<List<String>>

    /** Newest first, of every kind: the last time anything was eaten, for fasting. */
    @Query("SELECT * FROM log_entries WHERE deleted = 0 ORDER BY loggedAtEpochMs DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<LogEntryEntity>>

    /**
     * Entries logged from a food, newest first, for Recent; the repository collapses repeats of
     * the same food. A quick number or a name-only line has no food to pick again.
     */
    @Query(
        "SELECT * FROM log_entries WHERE deleted = 0 AND (foodId IS NOT NULL OR customFoodId IS NOT NULL) " +
            "ORDER BY loggedAtEpochMs DESC LIMIT :limit",
    )
    fun observeLatestFoods(limit: Int): Flow<List<LogEntryEntity>>

    @Query("SELECT * FROM log_entries WHERE slot = :slot AND day >= :fromDay AND deleted = 0")
    fun observeSlotSince(slot: String, fromDay: String): Flow<List<LogEntryEntity>>

    @Query("SELECT * FROM log_entries WHERE day >= :fromDay AND deleted = 0")
    fun observeSince(fromDay: String): Flow<List<LogEntryEntity>>

    /** Hard delete, only for undoing a row the server never had. */
    @Query("DELETE FROM log_entries WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM log_entries")
    override suspend fun deleteAll()
}

@Dao
interface DayRecordDao : ClearableDao {
    @Upsert
    suspend fun upsert(rows: List<DayRecordEntity>)

    @Query("SELECT * FROM day_records WHERE day = :day")
    suspend fun get(day: String): DayRecordEntity?

    @Query("SELECT * FROM day_records WHERE day = :day AND deleted = 0")
    fun observe(day: String): Flow<DayRecordEntity?>

    @Query("SELECT * FROM day_records WHERE day >= :from AND day <= :to AND deleted = 0 ORDER BY day")
    fun observeRange(from: String, to: String): Flow<List<DayRecordEntity>>

    @Query("DELETE FROM day_records WHERE day = :day")
    suspend fun delete(day: String)

    @Query("DELETE FROM day_records")
    override suspend fun deleteAll()
}

@Dao
interface MealFeelingDao : ClearableDao {
    @Upsert
    suspend fun upsert(rows: List<MealFeelingEntity>)

    @Query("SELECT * FROM meal_feelings WHERE day = :day AND slot = :slot")
    suspend fun get(day: String, slot: String): MealFeelingEntity?

    @Query("SELECT * FROM meal_feelings WHERE day = :day AND deleted = 0")
    fun observeDay(day: String): Flow<List<MealFeelingEntity>>

    @Query("SELECT * FROM meal_feelings WHERE day >= :from AND day <= :to AND deleted = 0")
    fun observeRange(from: String, to: String): Flow<List<MealFeelingEntity>>

    @Query("DELETE FROM meal_feelings WHERE day = :day AND slot = :slot")
    suspend fun delete(day: String, slot: String)

    @Query("DELETE FROM meal_feelings")
    override suspend fun deleteAll()
}

@Dao
interface WeighInDao : ClearableDao {
    @Upsert
    suspend fun upsert(rows: List<WeighInEntity>)

    @Query("SELECT * FROM weigh_ins WHERE id = :id")
    suspend fun get(id: String): WeighInEntity?

    @Query("SELECT * FROM weigh_ins WHERE measuredAtEpochMs >= :sinceEpochMs AND deleted = 0 ORDER BY measuredAtEpochMs, id")
    fun observeSince(sinceEpochMs: Long): Flow<List<WeighInEntity>>

    @Query("DELETE FROM weigh_ins WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM weigh_ins")
    override suspend fun deleteAll()
}

@Dao
interface CustomFoodDao : ClearableDao {
    @Upsert
    suspend fun upsert(rows: List<CustomFoodEntity>)

    @Query("SELECT * FROM custom_foods WHERE id = :id")
    suspend fun get(id: String): CustomFoodEntity?

    @Query("SELECT * FROM custom_foods WHERE deleted = 0 ORDER BY name COLLATE NOCASE, id")
    fun observeAll(): Flow<List<CustomFoodEntity>>

    @Query("DELETE FROM custom_foods WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM custom_foods")
    override suspend fun deleteAll()
}

@Dao
interface SavedMealDao : ClearableDao {
    @Upsert
    suspend fun upsert(rows: List<SavedMealEntity>)

    @Query("SELECT * FROM saved_meals WHERE id = :id")
    suspend fun get(id: String): SavedMealEntity?

    @Query("SELECT * FROM saved_meals WHERE deleted = 0 ORDER BY name COLLATE NOCASE, id")
    fun observeAll(): Flow<List<SavedMealEntity>>

    @Query("DELETE FROM saved_meals WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM saved_meals")
    override suspend fun deleteAll()
}

/** Fasts. Only the latest is ever read: nothing compares fasts, so there are no streaks or records. */
@Dao
interface FastDao : ClearableDao {
    @Upsert
    suspend fun upsert(rows: List<FastEntity>)

    @Query("SELECT * FROM fasts WHERE day = :day")
    suspend fun get(day: String): FastEntity?

    @Query("SELECT * FROM fasts WHERE deleted = 0 ORDER BY startedAtEpochMs DESC, day DESC LIMIT 1")
    suspend fun latest(): FastEntity?

    @Query("SELECT * FROM fasts WHERE deleted = 0 ORDER BY startedAtEpochMs DESC, day DESC LIMIT 1")
    fun observeLatest(): Flow<FastEntity?>

    @Query("DELETE FROM fasts WHERE day = :day")
    suspend fun delete(day: String)

    @Query("DELETE FROM fasts")
    override suspend fun deleteAll()
}

@Dao
interface ExerciseDao : ClearableDao {
    @Upsert
    suspend fun upsert(rows: List<ExerciseEntity>)

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun get(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE day = :day AND deleted = 0 ORDER BY startedAtEpochMs, id")
    fun observeDay(day: String): Flow<List<ExerciseEntity>>

    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM exercises")
    override suspend fun deleteAll()
}

@Dao
interface OutboxDao : ClearableDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: OutboxEntity)

    @Query("SELECT * FROM outbox WHERE entity = :entity AND entityId = :entityId")
    suspend fun pendingFor(entity: String, entityId: String): OutboxEntity?

    @Query("DELETE FROM outbox WHERE entity = :entity AND entityId = :entityId")
    suspend fun deleteFor(entity: String, entityId: String)

    /** The oldest changes the server will take now: none dated after [maxDay]. */
    @Query("SELECT * FROM outbox WHERE day IS NULL OR day <= :maxDay ORDER BY createdAtEpochMs, id LIMIT :limit")
    suspend fun page(limit: Int, maxDay: String): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<OutboxEntity>

    @Query("DELETE FROM outbox WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    /** Entity ids with a change still waiting, for the merge rule. */
    @Query("SELECT entityId FROM outbox WHERE entity = :entity")
    suspend fun pendingEntityIds(entity: String): List<String>

    @Query("SELECT COUNT(*) FROM outbox")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM outbox")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM outbox")
    override suspend fun deleteAll()
}

@Dao
interface SyncStateDao : ClearableDao {
    @Query("SELECT * FROM sync_state WHERE id = 0")
    suspend fun get(): SyncStateEntity?

    @Upsert
    suspend fun upsert(row: SyncStateEntity)

    @Query("DELETE FROM sync_state")
    override suspend fun deleteAll()
}
