package com.opson.server.domain

import java.time.LocalDate
import java.util.UUID
import kotlin.time.Instant

/**
 * The user's diary as the server sees it: the rows `/v1/sync` moves between
 * devices (docs/system-design.md §5, §6). Every row carries
 * [DiaryRow.clientUpdatedAt] (the device clock when the user changed it, the
 * last-write-wins key) and [DiaryRow.deleted] (a tombstone).
 *
 * Health data. None of these values go into logs or span attributes; log ids
 * and counts only.
 */
sealed interface DiaryRow {
    val clientUpdatedAt: Instant
    val deleted: Boolean
}

/** Wire values are the strings stored in Postgres and sent over JSON. */
enum class MealSlot(val wire: String) {
    Breakfast("breakfast"),
    Lunch("lunch"),
    Snack("snack"),
    Dinner("dinner"),
    Uncategorized("uncategorized"),
    ;

    companion object {
        fun fromWire(value: String): MealSlot? = entries.firstOrNull { it.wire == value }
    }
}

enum class Precision(val wire: String) {
    Weighed("weighed"),
    Estimate("estimate"),
    Named("named"),

    /** A serving as the package's label gives it. Clients from before 0.2 read it as an estimate. */
    Label("label"),
    ;

    companion object {
        fun fromWire(value: String): Precision? = entries.firstOrNull { it.wire == value }
    }
}

/** How an entry was logged. Only ever counted in aggregate (analytics views). */
enum class LogMethod(val wire: String) {
    Search("search"),
    Barcode("barcode"),
    Describe("describe"),
    Saved("saved"),
    Copy("copy"),
    AteNoDetails("ate_no_details"),
    QuickNumber("quick_number"),
    Recent("recent"),
    Favorite("favorite"),
    Custom("custom"),
    ;

    companion object {
        fun fromWire(value: String): LogMethod? = entries.firstOrNull { it.wire == value }
    }
}

/** The five headline nutrients. Null is unknown, which is not zero. */
data class Nutrients(
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
) {
    fun all(): List<Double?> = listOf(kcal, protein, carbs, fat, fiber)
}

data class LogEntry(
    val id: UUID,
    val day: LocalDate,
    val slot: MealSlot,
    val loggedAt: Instant,
    val foodId: UUID?,
    val customFoodId: UUID?,
    val name: String,
    val amountText: String?,
    val quantity: Double?,
    val unit: String?,
    val grams: Double?,
    val precision: Precision,
    val nutrients: Nutrients,
    val micros: Map<String, Double>?,
    val method: LogMethod,
    override val clientUpdatedAt: Instant,
    override val deleted: Boolean,
) : DiaryRow

data class DayRecord(
    val day: LocalDate,
    val waterMl: Int?,
    val closedAt: Instant?,
    val note: String?,
    val ateNoDetails: Map<MealSlot, Boolean>,
    val mealGroups: Boolean?,
    override val clientUpdatedAt: Instant,
    override val deleted: Boolean,
    /** Marked untracked (a holiday): Trends shows a gap, never a low day. */
    val untracked: Boolean = false,
    /** "How did today feel?", a client wire value such as `steady`; null when not answered. */
    val feeling: String? = null,
    /** Day close's check-in as the client's JSON; null when not answered. Opaque here, length-checked only. */
    val checkIn: String? = null,
) : DiaryRow

data class MealFeeling(
    val day: LocalDate,
    val slot: MealSlot,
    val hunger: String?,
    val fullness: String?,
    val moods: List<String>,
    val note: String?,
    override val clientUpdatedAt: Instant,
    override val deleted: Boolean,
) : DiaryRow

data class WeighIn(
    val id: UUID,
    val measuredAt: Instant,
    val kg: Double,
    override val clientUpdatedAt: Instant,
    override val deleted: Boolean,
) : DiaryRow

data class CustomFood(
    val id: UUID,
    val name: String,
    val brand: String?,
    val servingAmount: Double,
    val servingUnit: String,
    val nutrients: Nutrients,
    override val clientUpdatedAt: Instant,
    override val deleted: Boolean,
) : DiaryRow

/** [itemsJson] is a JSON array of item snapshots, opaque to the server. */
data class SavedMeal(
    val id: UUID,
    val name: String,
    val usualSlot: MealSlot?,
    val itemsJson: String,
    override val clientUpdatedAt: Instant,
    override val deleted: Boolean,
) : DiaryRow

/** [settingsJson] is the client's whole `Settings` object as a JSON object. */
data class UserSettings(
    val settingsJson: String,
    val changedAt: Instant?,
    override val clientUpdatedAt: Instant,
    override val deleted: Boolean,
) : DiaryRow

/**
 * One fast, keyed by the local [day] it began (one per evening). [endedAt] is null until someone
 * ends it; reaching [goalHours] is derived on the device and never written. 0.2.
 */
data class Fast(
    val day: LocalDate,
    val startedAt: Instant,
    val endedAt: Instant?,
    val goalHours: Int?,
    override val clientUpdatedAt: Instant,
    override val deleted: Boolean,
) : DiaryRow

/** One outbox row from the client: its outbox id (echoed back) and the full row. */
data class SyncMutation(val outboxId: String, val row: DiaryRow)

/** Everything for one user with `server_seq` past the caller's cursor. */
data class DiaryChanges(
    val logEntries: List<LogEntry> = emptyList(),
    val dayRecords: List<DayRecord> = emptyList(),
    val mealFeelings: List<MealFeeling> = emptyList(),
    val weighIns: List<WeighIn> = emptyList(),
    val customFoods: List<CustomFood> = emptyList(),
    val savedMeals: List<SavedMeal> = emptyList(),
    val settings: UserSettings? = null,
    /** Null when the request did not accept fasts, so a 0.1 client's response stays the 0.1 shape. */
    val fasts: List<Fast>? = null,
)

/**
 * [applied] is every outbox id the server is done with: written, or ignored
 * because the server already had a newer copy. Either way the client deletes
 * it from the outbox. [hasMore] means at least one entity hit the page cap
 * and the client should call again with [cursor].
 */
data class SyncResult(
    val applied: List<String>,
    val changes: DiaryChanges,
    val cursor: Long,
    val hasMore: Boolean,
)

/**
 * Push and pull in one transaction. Implementations upsert each mutation by
 * its client key when [DiaryRow.clientUpdatedAt] is strictly newer than the
 * stored copy (last write wins, so a replay is a no-op), scope every read and
 * write to [userId], then return rows past [cursor] capped at
 * [DiaryRules.PAGE_SIZE] per entity. Fasts come back only when [pullFasts]: entities added
 * after 0.1 are left out for a client that did not ask for them.
 *
 * Mutations arrive already validated by [DiaryRules].
 */
interface SyncRepository {
    suspend fun sync(userId: UserId, cursor: Long, mutations: List<SyncMutation>, pullFasts: Boolean = false): SyncResult
}
