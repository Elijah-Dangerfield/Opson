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
    /**
     * Entities added after 0.1 that this client can take in `changes` ([SYNC_ACCEPTS]). The
     * server leaves them out for a request that does not name them, so a 0.1 client gets the
     * 0.1 payload.
     */
    val accepts: List<String> = emptyList(),
)

/** Every entity past the 0.1 set this client reads from `changes`. */
internal val SYNC_ACCEPTS: List<String> = listOf("fast", "exercise", "recipe")

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
    /** Present only when the request accepts `fast`. */
    val fasts: List<FastRowDto>? = null,
    /** Present only when the request accepts `exercise`. */
    val exercises: List<ExerciseRowDto>? = null,
    /** Present only when the request accepts `recipe`. */
    val recipes: List<RecipeRowDto>? = null,
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
    /** Day close's check-in as `CheckInWire` JSON, or null. A server that predates it drops it. */
    val checkIn: String? = null,
)

/**
 * The check-in as it is stored and synced: wire values, each optional. [hungerLevel] is the 1 to
 * 10 hunger scale. [hunger] is the first check-in's "hunger through the day" (rarely to very):
 * still written, the nearest word to [hungerLevel], so a 0.2 phone and the export keep a hunger,
 * and read only when [hungerLevel] is missing. [mood] is the 5-point mood, [moods] the tags.
 */
@Serializable
data class CheckInWire(
    val hunger: String? = null,
    val hungerLevel: Int? = null,
    val energy: String? = null,
    val sleep: String? = null,
    val mood: String? = null,
    val moods: List<String> = emptyList(),
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
    /**
     * 0.2: the serving's weight, the other serving sizes and the rest of the label. This build
     * always sends [sizes], empty for none. A row without it is an older build's, which knows
     * nothing of these three, so the server keeps what it has for all of them.
     */
    val servingGrams: Double? = null,
    val sizes: List<FoodUnitDto>? = null,
    val micros: Map<String, Double>? = null,
)

/** One of a custom food's serving sizes on the wire: "tbsp" is 15 g. */
@Serializable
data class FoodUnitDto(val label: String, val grams: Double)

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
data class FastRowDto(
    /** The local day the fast began; its key. */
    val day: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
    val goalHours: Int? = null,
    val clientUpdatedAtEpochMs: Long,
    val deleted: Boolean = false,
)

@Serializable
data class ExerciseRowDto(
    val id: String,
    /** The local day it counts on. */
    val day: String,
    val startedAtEpochMs: Long,
    /** An `ExerciseActivities` key, or `other`. */
    val activity: String,
    /** Other's name; null for a listed activity. */
    val name: String? = null,
    val minutes: Int,
    /** What counts in Burned, worked out or typed when it was saved. */
    val kcal: Int,
    val kcalTyped: Boolean = false,
    /** Counts even when a health app reads activity. */
    val notOnWatch: Boolean = false,
    val clientUpdatedAtEpochMs: Long,
    val deleted: Boolean = false,
)

/**
 * A recipe. The `public*` fields are the server's sharing state: they come down so this phone
 * knows a recipe is shared, and the server ignores them on the way up.
 */
@Serializable
data class RecipeRowDto(
    val id: String,
    val name: String,
    val servings: Double,
    /** `LogDraft` JSON for the whole batch, the same shape as a saved meal's items. */
    val items: JsonArray = JsonArray(emptyList()),
    val notes: String? = null,
    val clientUpdatedAtEpochMs: Long,
    val deleted: Boolean = false,
    /** The share code of someone else's recipe this one was saved from. */
    val savedFrom: String? = null,
    val publicCode: String? = null,
    val publicState: String? = null,
    val publicShowName: Boolean? = null,
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
