package com.opson.server.routes

import com.opson.server.domain.CustomFood
import com.opson.server.domain.DayRecord
import com.opson.server.domain.DiaryChanges
import com.opson.server.domain.DiaryRow
import com.opson.server.domain.Fast
import com.opson.server.domain.LogEntry
import com.opson.server.domain.LogMethod
import com.opson.server.domain.MealFeeling
import com.opson.server.domain.MealSlot
import com.opson.server.domain.Nutrients
import com.opson.server.domain.Precision
import com.opson.server.domain.SavedMeal
import com.opson.server.domain.SyncResult
import com.opson.server.domain.UserSettings
import com.opson.server.domain.WeighIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Instant

/**
 * Wire format for `POST /v1/sync` (docs/system-design.md §6).
 *
 * camelCase JSON like the rest of the API. Times are epoch milliseconds
 * (`…EpochMs`), days are ISO dates (`"2026-10-25"`), ids are UUID strings,
 * enums are the lowercase wire strings in the domain enums ([MealSlot.wire],
 * [Precision.wire], [LogMethod.wire]).
 *
 * A mutation's `row` is decoded by its `entity` into one of the `*RowDto`
 * types below; the same types come back in `changes`.
 */
@Serializable
data class SyncRequest(
    val cursor: Long = 0,
    val mutations: List<SyncMutationDto> = emptyList(),
    /**
     * Entities added after 0.1 that the client reads from `changes` (today only `fast`). 0.1
     * clients never send it, and get exactly the 0.1 response.
     */
    val accepts: List<String> = emptyList(),
) {
    val acceptsFasts: Boolean get() = SyncEntity.Fast.wire in accepts
}

@Serializable
data class SyncMutationDto(
    /** The client's outbox row id, echoed in `applied` / `rejected`. */
    val id: String,
    /** One of [SyncEntity.wire]. */
    val entity: String,
    val row: JsonObject,
)

@Serializable
data class SyncResponse(
    val applied: List<String>,
    val rejected: List<SyncRejectionDto>,
    val changes: SyncChangesDto,
    val cursor: Long,
    val hasMore: Boolean,
)

/** [code] is stable: `unknown_entity` or `invalid_row`. [message] is for humans and logs. */
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
    /** Only for a request that accepts `fast`; absent otherwise (`explicitNulls = false`). */
    val fasts: List<FastRowDto>? = null,
)

/** `entity` values a mutation may carry. */
enum class SyncEntity(val wire: String) {
    LogEntry("log_entry"),
    DayRecord("day_record"),
    MealFeeling("meal_feeling"),
    WeighIn("weigh_in"),
    CustomFood("custom_food"),
    SavedMeal("saved_meal"),
    Settings("settings"),

    /** 0.2. */
    Fast("fast"),
    ;

    companion object {
        fun fromWire(value: String): SyncEntity? = entries.firstOrNull { it.wire == value }
    }
}

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
    /** slot wire value -> true */
    val ateNoDetails: Map<String, Boolean> = emptyMap(),
    /** null = follow the user's setting */
    val mealGroups: Boolean? = null,
    /** Marked untracked: Trends shows a gap. Absent from older clients, so it defaults to tracked. */
    val untracked: Boolean = false,
    /** "How did today feel?" as a wire value (`steady`, `busy`, …); null when not answered. */
    val feeling: String? = null,
    val clientUpdatedAtEpochMs: Long,
    val deleted: Boolean = false,
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
    /** Item snapshots; the server stores the array as-is. */
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
data class SettingsRowDto(
    /** The client's whole Settings object. The server only ever reads `preset`, in aggregate. */
    val settings: JsonObject,
    val changedAtEpochMs: Long? = null,
    val clientUpdatedAtEpochMs: Long,
    val deleted: Boolean = false,
)

internal val SyncJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/**
 * Decode a mutation row into a domain row. Throws [IllegalArgumentException]
 * (or a serialization exception) with a field-naming message when the row is
 * malformed; the route turns that into an `invalid_row` rejection.
 */
fun SyncEntity.decodeRow(row: JsonObject): DiaryRow = when (this) {
    SyncEntity.LogEntry -> SyncJson.decodeFromJsonElement(LogEntryRowDto.serializer(), row).toDomain()
    SyncEntity.DayRecord -> SyncJson.decodeFromJsonElement(DayRecordRowDto.serializer(), row).toDomain()
    SyncEntity.MealFeeling -> SyncJson.decodeFromJsonElement(MealFeelingRowDto.serializer(), row).toDomain()
    SyncEntity.WeighIn -> SyncJson.decodeFromJsonElement(WeighInRowDto.serializer(), row).toDomain()
    SyncEntity.CustomFood -> SyncJson.decodeFromJsonElement(CustomFoodRowDto.serializer(), row).toDomain()
    SyncEntity.SavedMeal -> SyncJson.decodeFromJsonElement(SavedMealRowDto.serializer(), row).toDomain()
    SyncEntity.Settings -> SyncJson.decodeFromJsonElement(SettingsRowDto.serializer(), row).toDomain()
    SyncEntity.Fast -> SyncJson.decodeFromJsonElement(FastRowDto.serializer(), row).toDomain()
}

private fun uuid(field: String, value: String): UUID =
    runCatching { UUID.fromString(value) }.getOrNull()
        ?.takeIf { it.toString() == value.lowercase() }
        ?: throw IllegalArgumentException("$field is not a UUID")

private fun day(value: String): LocalDate =
    runCatching { LocalDate.parse(value) }.getOrElse { throw IllegalArgumentException("day is not an ISO date") }

private fun slot(field: String, value: String): MealSlot =
    MealSlot.fromWire(value) ?: throw IllegalArgumentException("$field is not a meal slot")

private fun instant(ms: Long): Instant = Instant.fromEpochMilliseconds(ms)

fun LogEntryRowDto.toDomain(): LogEntry = LogEntry(
    id = uuid("id", id),
    day = day(day),
    slot = slot("slot", slot),
    loggedAt = instant(loggedAtEpochMs),
    foodId = foodId?.let { uuid("foodId", it) },
    customFoodId = customFoodId?.let { uuid("customFoodId", it) },
    name = name,
    amountText = amountText,
    quantity = quantity,
    unit = unit,
    grams = grams,
    precision = Precision.fromWire(precision) ?: throw IllegalArgumentException("precision is not a precision"),
    nutrients = Nutrients(kcal, protein, carbs, fat, fiber),
    micros = micros,
    method = LogMethod.fromWire(method) ?: throw IllegalArgumentException("method is not a logging method"),
    clientUpdatedAt = instant(clientUpdatedAtEpochMs),
    deleted = deleted,
)

fun DayRecordRowDto.toDomain(): DayRecord = DayRecord(
    day = day(day),
    waterMl = waterMl,
    closedAt = closedAtEpochMs?.let(::instant),
    note = note,
    ateNoDetails = ateNoDetails.mapKeys { (key, _) -> slot("ateNoDetails", key) },
    mealGroups = mealGroups,
    untracked = untracked,
    feeling = feeling,
    clientUpdatedAt = instant(clientUpdatedAtEpochMs),
    deleted = deleted,
)

fun MealFeelingRowDto.toDomain(): MealFeeling = MealFeeling(
    day = day(day),
    slot = slot("slot", slot),
    hunger = hunger,
    fullness = fullness,
    moods = moods,
    note = note,
    clientUpdatedAt = instant(clientUpdatedAtEpochMs),
    deleted = deleted,
)

fun WeighInRowDto.toDomain(): WeighIn = WeighIn(
    id = uuid("id", id),
    measuredAt = instant(measuredAtEpochMs),
    kg = kg,
    clientUpdatedAt = instant(clientUpdatedAtEpochMs),
    deleted = deleted,
)

fun CustomFoodRowDto.toDomain(): CustomFood = CustomFood(
    id = uuid("id", id),
    name = name,
    brand = brand,
    servingAmount = servingAmount,
    servingUnit = servingUnit,
    nutrients = Nutrients(kcal, protein, carbs, fat, fiber),
    clientUpdatedAt = instant(clientUpdatedAtEpochMs),
    deleted = deleted,
)

fun SavedMealRowDto.toDomain(): SavedMeal = SavedMeal(
    id = uuid("id", id),
    name = name,
    usualSlot = usualSlot?.let { slot("usualSlot", it) },
    itemsJson = SyncJson.encodeToString(JsonArray.serializer(), items),
    clientUpdatedAt = instant(clientUpdatedAtEpochMs),
    deleted = deleted,
)

fun SettingsRowDto.toDomain(): UserSettings = UserSettings(
    settingsJson = SyncJson.encodeToString(JsonObject.serializer(), settings),
    changedAt = changedAtEpochMs?.let(::instant),
    clientUpdatedAt = instant(clientUpdatedAtEpochMs),
    deleted = deleted,
)

fun FastRowDto.toDomain(): Fast = Fast(
    day = day(day),
    startedAt = instant(startedAtEpochMs),
    endedAt = endedAtEpochMs?.let(::instant),
    goalHours = goalHours,
    clientUpdatedAt = instant(clientUpdatedAtEpochMs),
    deleted = deleted,
)

fun Fast.toRowDto(): FastRowDto = FastRowDto(
    day = day.toString(),
    startedAtEpochMs = startedAt.toEpochMilliseconds(),
    endedAtEpochMs = endedAt?.toEpochMilliseconds(),
    goalHours = goalHours,
    clientUpdatedAtEpochMs = clientUpdatedAt.toEpochMilliseconds(),
    deleted = deleted,
)

fun LogEntry.toRowDto(): LogEntryRowDto = LogEntryRowDto(
    id = id.toString(),
    day = day.toString(),
    slot = slot.wire,
    loggedAtEpochMs = loggedAt.toEpochMilliseconds(),
    foodId = foodId?.toString(),
    customFoodId = customFoodId?.toString(),
    name = name,
    amountText = amountText,
    quantity = quantity,
    unit = unit,
    grams = grams,
    precision = precision.wire,
    kcal = nutrients.kcal,
    protein = nutrients.protein,
    carbs = nutrients.carbs,
    fat = nutrients.fat,
    fiber = nutrients.fiber,
    micros = micros,
    method = method.wire,
    clientUpdatedAtEpochMs = clientUpdatedAt.toEpochMilliseconds(),
    deleted = deleted,
)

fun DayRecord.toRowDto(): DayRecordRowDto = DayRecordRowDto(
    day = day.toString(),
    waterMl = waterMl,
    closedAtEpochMs = closedAt?.toEpochMilliseconds(),
    note = note,
    ateNoDetails = ateNoDetails.mapKeys { (slot, _) -> slot.wire },
    mealGroups = mealGroups,
    untracked = untracked,
    feeling = feeling,
    clientUpdatedAtEpochMs = clientUpdatedAt.toEpochMilliseconds(),
    deleted = deleted,
)

fun MealFeeling.toRowDto(): MealFeelingRowDto = MealFeelingRowDto(
    day = day.toString(),
    slot = slot.wire,
    hunger = hunger,
    fullness = fullness,
    moods = moods,
    note = note,
    clientUpdatedAtEpochMs = clientUpdatedAt.toEpochMilliseconds(),
    deleted = deleted,
)

fun WeighIn.toRowDto(): WeighInRowDto = WeighInRowDto(
    id = id.toString(),
    measuredAtEpochMs = measuredAt.toEpochMilliseconds(),
    kg = kg,
    clientUpdatedAtEpochMs = clientUpdatedAt.toEpochMilliseconds(),
    deleted = deleted,
)

fun CustomFood.toRowDto(): CustomFoodRowDto = CustomFoodRowDto(
    id = id.toString(),
    name = name,
    brand = brand,
    servingAmount = servingAmount,
    servingUnit = servingUnit,
    kcal = nutrients.kcal,
    protein = nutrients.protein,
    carbs = nutrients.carbs,
    fat = nutrients.fat,
    fiber = nutrients.fiber,
    clientUpdatedAtEpochMs = clientUpdatedAt.toEpochMilliseconds(),
    deleted = deleted,
)

fun SavedMeal.toRowDto(): SavedMealRowDto = SavedMealRowDto(
    id = id.toString(),
    name = name,
    usualSlot = usualSlot?.wire,
    items = SyncJson.decodeFromString(JsonArray.serializer(), itemsJson),
    clientUpdatedAtEpochMs = clientUpdatedAt.toEpochMilliseconds(),
    deleted = deleted,
)

fun UserSettings.toRowDto(): SettingsRowDto = SettingsRowDto(
    settings = SyncJson.decodeFromString(JsonObject.serializer(), settingsJson),
    changedAtEpochMs = changedAt?.toEpochMilliseconds(),
    clientUpdatedAtEpochMs = clientUpdatedAt.toEpochMilliseconds(),
    deleted = deleted,
)

fun DiaryChanges.toDto(): SyncChangesDto = SyncChangesDto(
    logEntries = logEntries.map { it.toRowDto() },
    dayRecords = dayRecords.map { it.toRowDto() },
    mealFeelings = mealFeelings.map { it.toRowDto() },
    weighIns = weighIns.map { it.toRowDto() },
    customFoods = customFoods.map { it.toRowDto() },
    savedMeals = savedMeals.map { it.toRowDto() },
    settings = settings?.toRowDto(),
    fasts = fasts?.map { it.toRowDto() },
)

fun SyncResult.toResponse(rejected: List<SyncRejectionDto>): SyncResponse = SyncResponse(
    applied = applied,
    rejected = rejected,
    changes = changes.toDto(),
    cursor = cursor,
    hasMore = hasMore,
)
