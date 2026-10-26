package com.opson.libraries.diary.impl.wire

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/*
 * The `POST /v1/sync` wire format, mirrored field for field from the server's
 * `apps/server/.../routes/SyncDto.kt`. camelCase, epoch-millisecond times, ISO
 * day strings, lowercase UUID strings, lowercase enum wire values. The same
 * row shapes go up as mutations and come back as changes.
 *
 * Debug builds decode responses strictly (NetworkJson), so a field the server
 * adds shows up here as a test failure rather than as silent loss.
 */

@Serializable
data class SyncRequestDto(
    val cursor: Long = 0,
    val mutations: List<SyncMutationDto> = emptyList(),
)

@Serializable
data class SyncMutationDto(
    /** The outbox row id, echoed back in `applied` or `rejected`. */
    val id: String,
    val entity: String,
    val row: JsonObject,
)

@Serializable
data class SyncResponseDto(
    val applied: List<String>,
    val rejected: List<SyncRejectionDto>,
    val changes: SyncChangesDto,
    val cursor: Long,
    val hasMore: Boolean,
)

@Serializable
data class SyncRejectionDto(
    val id: String,
    val code: String,
    val message: String,
)

@Serializable
data class SyncChangesDto(
    val logEntries: List<LogEntryRowDto> = emptyList(),
    val dayRecords: List<DayRecordRowDto> = emptyList(),
    val mealFeelings: List<MealFeelingRowDto> = emptyList(),
    val weighIns: List<WeighInRowDto> = emptyList(),
    val customFoods: List<CustomFoodRowDto> = emptyList(),
    val savedMeals: List<SavedMealRowDto> = emptyList(),
    val settings: SettingsRowDto? = null,
)

@Serializable
data class LogEntryRowDto(
    val id: String,
    val day: String,
    val slot: String,
    val loggedAtEpochMs: Long,
    val foodId: String? = null,
    val customFoodId: String? = null,
    val name: String,
    val amountText: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val grams: Double? = null,
    val precision: String,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
    val micros: Map<String, Double>? = null,
    val method: String,
    val clientUpdatedAtEpochMs: Long,
    val deleted: Boolean = false,
)

@Serializable
data class DayRecordRowDto(
    val day: String,
    val waterMl: Int? = null,
    val closedAtEpochMs: Long? = null,
    val note: String? = null,
    /** slot wire value to true */
    val ateNoDetails: Map<String, Boolean> = emptyMap(),
    /** null follows the user's setting */
    val mealGroups: Boolean? = null,
    val clientUpdatedAtEpochMs: Long,
    val deleted: Boolean = false,
    /** Marked untracked: Trends shows a gap. */
    val untracked: Boolean = false,
    /** A `DayFeeling` wire value, or null. */
    val feeling: String? = null,
)

@Serializable
data class MealFeelingRowDto(
    val day: String,
    val slot: String,
    val hunger: String? = null,
    val fullness: String? = null,
    val moods: List<String> = emptyList(),
    val note: String? = null,
    val clientUpdatedAtEpochMs: Long,
    val deleted: Boolean = false,
)

@Serializable
data class WeighInRowDto(
    val id: String,
    val measuredAtEpochMs: Long,
    val kg: Double,
    val clientUpdatedAtEpochMs: Long,
    val deleted: Boolean = false,
)

@Serializable
data class CustomFoodRowDto(
    val id: String,
    val name: String,
    val brand: String? = null,
    val servingAmount: Double,
    val servingUnit: String,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
    val clientUpdatedAtEpochMs: Long,
    val deleted: Boolean = false,
)

@Serializable
data class SavedMealRowDto(
    val id: String,
    val name: String,
    val usualSlot: String? = null,
    val items: JsonArray = JsonArray(emptyList()),
    val clientUpdatedAtEpochMs: Long,
    val deleted: Boolean = false,
)

@Serializable
data class SettingsRowDto(
    /** The whole Settings object. The server only ever reads `preset`, in aggregate. */
    val settings: JsonObject,
    val changedAtEpochMs: Long? = null,
    val clientUpdatedAtEpochMs: Long,
    val deleted: Boolean = false,
)

/**
 * How outbox payloads are written and read back. Defaults are encoded so a
 * stored payload is the complete row whatever the DTO's defaults become.
 */
internal val WireJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/** The server takes at most 500 mutations per request; the drain stays well under it. */
internal const val SERVER_MAX_MUTATIONS = 500
