package com.opson.libraries.diary.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * The diary tables on the device: the server's tables (docs/system-design.md
 * section 5) minus `server_seq`, plus the outbox and the sync cursor.
 *
 * Columns hold wire values (ISO day strings, slot and precision wire names,
 * epoch milliseconds, JSON text), so a row maps to and from its sync DTO with
 * no type converters and no lossy step in between. ISO dates sort as text, so
 * range queries on `day` are plain string comparisons.
 *
 * `deleted` is a tombstone: a delete is a write like any other, so it syncs.
 * Reads filter tombstones out.
 */

@Entity(tableName = "log_entries", indices = [Index("day"), Index("loggedAtEpochMs")])
data class LogEntryEntity(
    @PrimaryKey val id: String,
    val day: String,
    val slot: String,
    val loggedAtEpochMs: Long,
    val foodId: String?,
    val customFoodId: String?,
    val name: String,
    val amountText: String?,
    val quantity: Double?,
    val unit: String?,
    val grams: Double?,
    val precision: String,
    val kcal: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val fiber: Double?,
    /** JSON object, micronutrient key to amount; null when the item has none. */
    val microsJson: String?,
    val method: String,
    val clientUpdatedAtEpochMs: Long,
    val deleted: Boolean,
)

@Entity(tableName = "day_records")
data class DayRecordEntity(
    @PrimaryKey val day: String,
    val waterMl: Int?,
    val closedAtEpochMs: Long?,
    val note: String?,
    /** JSON object, slot wire value to true. */
    val ateNoDetailsJson: String,
    /** Null follows the user's setting. */
    val mealGroups: Boolean?,
    val clientUpdatedAtEpochMs: Long,
    val deleted: Boolean,
)

@Entity(tableName = "meal_feelings", primaryKeys = ["day", "slot"])
data class MealFeelingEntity(
    val day: String,
    val slot: String,
    val hunger: String?,
    val fullness: String?,
    /** JSON array of mood wire values. */
    val moodsJson: String,
    val note: String?,
    val clientUpdatedAtEpochMs: Long,
    val deleted: Boolean,
)

@Entity(tableName = "weigh_ins", indices = [Index("measuredAtEpochMs")])
data class WeighInEntity(
    @PrimaryKey val id: String,
    val measuredAtEpochMs: Long,
    val kg: Double,
    val clientUpdatedAtEpochMs: Long,
    val deleted: Boolean,
)

@Entity(tableName = "custom_foods")
data class CustomFoodEntity(
    @PrimaryKey val id: String,
    val name: String,
    val brand: String?,
    val servingAmount: Double,
    val servingUnit: String,
    val kcal: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val fiber: Double?,
    val clientUpdatedAtEpochMs: Long,
    val deleted: Boolean,
)

@Entity(tableName = "saved_meals")
data class SavedMealEntity(
    @PrimaryKey val id: String,
    val name: String,
    val usualSlot: String?,
    /** JSON array of item snapshots, sent to the server as-is. */
    val itemsJson: String,
    val clientUpdatedAtEpochMs: Long,
    val deleted: Boolean,
)

/**
 * A change waiting to reach the server. There is at most one per
 * (`entity`, `entityId`): a newer change to the same row replaces the older
 * mutation under a fresh [id], so the server's `applied` list for the old id
 * can never delete the newer one.
 *
 * [payload] is the full row as sent (`*RowDto` JSON). [basePayload] is the row
 * as it was before the first change still waiting here, or null when the row
 * did not exist; a rejection restores it.
 *
 * [day] is the diary day the row belongs to, when it has one. The server
 * refuses a day more than two days ahead while Today lets the user plan six
 * ahead, so a change to a later day waits here until its day comes into range.
 */
@Entity(
    tableName = "outbox",
    indices = [Index(value = ["entity", "entityId"], unique = true), Index("createdAtEpochMs")],
)
data class OutboxEntity(
    @PrimaryKey val id: String,
    val entity: String,
    val entityId: String,
    val payload: String,
    val basePayload: String?,
    val createdAtEpochMs: Long,
    val day: String? = null,
)

/** One row: the pull cursor and when a sync last finished. */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val cursor: Long,
    val lastSuccessAtEpochMs: Long?,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
