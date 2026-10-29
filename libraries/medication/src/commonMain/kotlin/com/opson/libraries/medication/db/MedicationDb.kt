package com.opson.libraries.medication.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.opson.libraries.opson.storage.db.ClearableDao
import kotlinx.coroutines.flow.Flow

/**
 * A logged dose (Room 13). Columns follow the spec's `medication_doses` server table, so a later
 * `medication_dose` sync entity maps one to one: a client uuid [id], the [clientUpdatedAtEpochMs]
 * stamp and a [deleted] tombstone. Nothing syncs yet; the prod server has no such table.
 *
 * [drug], [product] and [brand] are copied from the plan so history reads the same after it
 * changes. [day] is the local date of [takenAtEpochMs]. [site] is null for pills.
 */
@Entity(tableName = "medication_doses", indices = [Index("day")])
data class MedicationDoseEntity(
    @PrimaryKey val id: String,
    val medicationId: String,
    val product: String,
    val drug: String,
    val brand: String?,
    val dose: Double,
    val doseUnit: String,
    val takenAtEpochMs: Long,
    val day: String,
    val site: String?,
    val note: String?,
    val clientUpdatedAtEpochMs: Long,
    val deleted: Boolean,
)

/**
 * A day's side effects (Room 13), one row per day: [symptoms] is a JSON object of symptom wire
 * name to severity wire name. The day is the key, as `day_records` is, so a later sync (its own
 * entity, or the check-in's `symptoms` field) has a natural id.
 */
@Entity(tableName = "symptom_days")
data class SymptomDayEntity(
    @PrimaryKey val day: String,
    val symptoms: String,
    val clientUpdatedAtEpochMs: Long,
    val deleted: Boolean,
)

@Dao
interface MedicationDoseDao : ClearableDao {
    @Upsert
    suspend fun upsert(rows: List<MedicationDoseEntity>)

    @Query("SELECT * FROM medication_doses WHERE id = :id")
    suspend fun get(id: String): MedicationDoseEntity?

    @Query("SELECT * FROM medication_doses WHERE deleted = 0 ORDER BY takenAtEpochMs DESC, id")
    fun observeAll(): Flow<List<MedicationDoseEntity>>

    @Query("SELECT * FROM medication_doses WHERE deleted = 0 AND day >= :from AND day <= :to ORDER BY takenAtEpochMs, id")
    fun observeRange(from: String, to: String): Flow<List<MedicationDoseEntity>>

    @Query("DELETE FROM medication_doses")
    override suspend fun deleteAll()
}

@Dao
interface SymptomDayDao : ClearableDao {
    @Upsert
    suspend fun upsert(rows: List<SymptomDayEntity>)

    @Query("SELECT * FROM symptom_days WHERE day = :day")
    suspend fun get(day: String): SymptomDayEntity?

    @Query("SELECT * FROM symptom_days WHERE day = :day AND deleted = 0")
    fun observeDay(day: String): Flow<SymptomDayEntity?>

    @Query("SELECT * FROM symptom_days WHERE deleted = 0 AND day >= :from AND day <= :to ORDER BY day")
    fun observeRange(from: String, to: String): Flow<List<SymptomDayEntity>>

    @Query("DELETE FROM symptom_days")
    override suspend fun deleteAll()
}
