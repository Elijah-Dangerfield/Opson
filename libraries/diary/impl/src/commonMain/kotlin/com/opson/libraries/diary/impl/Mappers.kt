package com.opson.libraries.diary.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.diary.CheckIn
import com.opson.libraries.diary.CheckInMood
import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.Energy
import com.opson.libraries.diary.FoodUnit
import com.opson.libraries.diary.Fullness
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.HungerLevel
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Mood
import com.opson.libraries.diary.MoodLevel
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.SavedMeal
import com.opson.libraries.diary.Sleep
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.diary.db.CustomFoodEntity
import com.opson.libraries.diary.db.DayRecordEntity
import com.opson.libraries.diary.db.LogEntryEntity
import com.opson.libraries.diary.db.MealFeelingEntity
import com.opson.libraries.diary.db.SavedMealEntity
import com.opson.libraries.diary.db.WeighInEntity
import com.opson.libraries.diary.impl.wire.CheckInWire
import com.opson.libraries.diary.impl.wire.CustomFoodRowDto
import com.opson.libraries.diary.impl.wire.DayRecordRowDto
import com.opson.libraries.diary.impl.wire.FoodUnitDto
import com.opson.libraries.diary.impl.wire.LogEntryRowDto
import com.opson.libraries.diary.impl.wire.MealFeelingRowDto
import com.opson.libraries.diary.impl.wire.SavedMealRowDto
import com.opson.libraries.diary.impl.wire.WeighInRowDto
import com.opson.libraries.diary.impl.wire.WireJson
import kotlinx.datetime.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlin.time.Instant

private val microsSerializer = MapSerializer(String.serializer(), Double.serializer())
private val ateSerializer = MapSerializer(String.serializer(), Boolean.serializer())
private val moodsSerializer = ListSerializer(String.serializer())
private val draftsSerializer = ListSerializer(LogDraft.serializer())
private val sizesSerializer = ListSerializer(FoodUnit.serializer())

// ---------- log entries ----------

internal fun LogEntryEntity.toDomain(): LogItem? {
    val slot = MealSlot.fromWire(slot) ?: return null
    return LogItem(
        id = id,
        day = LocalDate.parse(day),
        slot = slot,
        loggedAt = Instant.fromEpochMilliseconds(loggedAtEpochMs),
        name = name,
        // A value this build does not know still shows the item; it just reads as an estimate.
        precision = Precision.fromWire(precision) ?: Precision.Estimate,
        method = LogMethod.fromWire(method) ?: LogMethod.Search,
        foodId = foodId,
        customFoodId = customFoodId,
        amountText = amountText,
        quantity = quantity,
        unit = unit,
        grams = grams,
        kcal = kcal,
        protein = protein,
        carbs = carbs,
        fat = fat,
        fiber = fiber,
        micros = microsJson?.let { WireJson.decodeFromString(microsSerializer, it) },
    )
}

internal fun LogItem.toEntity(updatedAtEpochMs: Long, deleted: Boolean = false): LogEntryEntity = LogEntryEntity(
    id = id,
    day = day.toString(),
    slot = slot.wire,
    loggedAtEpochMs = loggedAt.toEpochMilliseconds(),
    foodId = foodId,
    customFoodId = customFoodId,
    name = name,
    amountText = amountText,
    quantity = quantity,
    unit = unit,
    grams = grams,
    precision = precision.wire,
    kcal = kcal,
    protein = protein,
    carbs = carbs,
    fat = fat,
    fiber = fiber,
    microsJson = micros?.let { WireJson.encodeToString(microsSerializer, it) },
    method = method.wire,
    clientUpdatedAtEpochMs = updatedAtEpochMs,
    deleted = deleted,
)

internal fun LogEntryEntity.toRow(): LogEntryRowDto = LogEntryRowDto(
    id = id,
    day = day,
    slot = slot,
    loggedAtEpochMs = loggedAtEpochMs,
    foodId = foodId,
    customFoodId = customFoodId,
    name = name,
    amountText = amountText,
    quantity = quantity,
    unit = unit,
    grams = grams,
    precision = precision,
    kcal = kcal,
    protein = protein,
    carbs = carbs,
    fat = fat,
    fiber = fiber,
    micros = microsJson?.let { WireJson.decodeFromString(microsSerializer, it) },
    method = method,
    clientUpdatedAtEpochMs = clientUpdatedAtEpochMs,
    deleted = deleted,
)

internal fun LogEntryRowDto.toEntity(): LogEntryEntity = LogEntryEntity(
    id = id,
    day = day,
    slot = slot,
    loggedAtEpochMs = loggedAtEpochMs,
    foodId = foodId,
    customFoodId = customFoodId,
    name = name,
    amountText = amountText,
    quantity = quantity,
    unit = unit,
    grams = grams,
    precision = precision,
    kcal = kcal,
    protein = protein,
    carbs = carbs,
    fat = fat,
    fiber = fiber,
    microsJson = micros?.let { WireJson.encodeToString(microsSerializer, it) },
    method = method,
    clientUpdatedAtEpochMs = clientUpdatedAtEpochMs,
    deleted = deleted,
)

// ---------- day records ----------

internal fun DayRecordEntity.ateNoDetailsSlots(): Set<MealSlot> =
    WireJson.decodeFromString(ateSerializer, ateNoDetailsJson)
        .filterValues { it }
        .keys
        .mapNotNull(MealSlot::fromWire)
        .toSet()

internal fun Set<MealSlot>.toAteJson(): String =
    WireJson.encodeToString(ateSerializer, associate { it.wire to true })

internal fun DayRecordEntity.toRow(): DayRecordRowDto = DayRecordRowDto(
    day = day,
    waterMl = waterMl,
    closedAtEpochMs = closedAtEpochMs,
    note = note,
    ateNoDetails = WireJson.decodeFromString(ateSerializer, ateNoDetailsJson),
    mealGroups = mealGroups,
    clientUpdatedAtEpochMs = clientUpdatedAtEpochMs,
    deleted = deleted,
    untracked = untracked,
    feeling = feeling,
    checkIn = checkIn,
)

internal fun DayRecordRowDto.toEntity(): DayRecordEntity = DayRecordEntity(
    day = day,
    waterMl = waterMl,
    closedAtEpochMs = closedAtEpochMs,
    note = note,
    ateNoDetailsJson = WireJson.encodeToString(ateSerializer, ateNoDetails),
    mealGroups = mealGroups,
    clientUpdatedAtEpochMs = clientUpdatedAtEpochMs,
    deleted = deleted,
    untracked = untracked,
    feeling = feeling,
    checkIn = checkIn,
)

internal fun emptyDayRecord(day: String): DayRecordEntity = DayRecordEntity(
    day = day,
    waterMl = null,
    closedAtEpochMs = null,
    note = null,
    ateNoDetailsJson = "{}",
    mealGroups = null,
    clientUpdatedAtEpochMs = 0,
    deleted = false,
)

// ---------- the day's check-in ----------

/** Null for an empty check-in, so clearing every row clears the column. */
internal fun CheckIn?.toCheckInJson(): String? = this?.takeUnless { it.isEmpty }?.let { checkIn ->
    WireJson.encodeToString(
        CheckInWire.serializer(),
        CheckInWire(
            hunger = checkIn.hunger?.let(LegacyHunger::nearest)?.wire,
            hungerLevel = checkIn.hunger?.value,
            energy = checkIn.energy?.wire,
            sleep = checkIn.sleep?.wire,
            mood = checkIn.mood?.wire,
            moods = checkIn.moods.sortedBy { it.ordinal }.map { it.wire },
        ),
    )
}

/**
 * Unreadable JSON or unknown values read as unanswered, never as a crash. A check-in from before
 * the hunger scale has only the old word, which reads as its step on the scale ([LegacyHunger]).
 */
internal fun String?.toCheckIn(): CheckIn? {
    val json = this ?: return null
    val wire = Catching { WireJson.decodeFromString(CheckInWire.serializer(), json) }.getOrNull() ?: return null
    return CheckIn(
        hunger = HungerLevel.of(wire.hungerLevel) ?: LegacyHunger.fromWire(wire.hunger)?.level,
        energy = Energy.fromWire(wire.energy),
        sleep = Sleep.fromWire(wire.sleep),
        mood = MoodLevel.fromWire(wire.mood),
        moods = wire.moods.mapNotNullTo(LinkedHashSet()) { CheckInMood.fromWire(it) },
    ).takeUnless { it.isEmpty }
}

/**
 * The first check-in's "hunger through the day", how often hunger came up, and where each word
 * sits on the 1 to 10 hunger scale: hungry more often reads as hungrier. Approximate on purpose,
 * the two questions were not the same, but it keeps every old answer on the chart in the right
 * order. Kept for reading old rows and for writing the nearest word, which a 0.2 phone shows.
 */
internal enum class LegacyHunger(val wire: String, private val step: Int) {
    Rarely("rarely", 6),
    Some("some", 5),
    Steady("steady", 4),
    Often("often", 3),
    Very("very", 2),
    ;

    val level: HungerLevel? get() = HungerLevel.of(step)

    companion object {
        fun fromWire(value: String?): LegacyHunger? = entries.firstOrNull { it.wire == value }

        /** The word whose step is closest: 1 and 2 are "very", 6 and fuller "rarely". */
        fun nearest(level: HungerLevel): LegacyHunger = entries.minBy { kotlin.math.abs(it.step - level.value) }
    }
}

// ---------- meal feelings ----------

internal fun MealFeelingEntity.toDomain(): MealFeeling = MealFeeling(
    hunger = Hunger.fromWire(hunger),
    fullness = Fullness.fromWire(fullness),
    moods = WireJson.decodeFromString(moodsSerializer, moodsJson).mapNotNull(Mood::fromWire).toSet(),
    note = note,
)

internal fun MealFeeling.toEntity(day: String, slot: MealSlot, updatedAtEpochMs: Long, deleted: Boolean = false) =
    MealFeelingEntity(
        day = day,
        slot = slot.wire,
        hunger = hunger?.wire,
        fullness = fullness?.wire,
        moodsJson = WireJson.encodeToString(moodsSerializer, Mood.entries.filter { it in moods }.map { it.wire }),
        note = note?.takeIf { it.isNotBlank() },
        clientUpdatedAtEpochMs = updatedAtEpochMs,
        deleted = deleted,
    )

internal fun MealFeelingEntity.toRow(): MealFeelingRowDto = MealFeelingRowDto(
    day = day,
    slot = slot,
    hunger = hunger,
    fullness = fullness,
    moods = WireJson.decodeFromString(moodsSerializer, moodsJson),
    note = note,
    clientUpdatedAtEpochMs = clientUpdatedAtEpochMs,
    deleted = deleted,
)

internal fun MealFeelingRowDto.toEntity(): MealFeelingEntity = MealFeelingEntity(
    day = day,
    slot = slot,
    hunger = hunger,
    fullness = fullness,
    moodsJson = WireJson.encodeToString(moodsSerializer, moods),
    note = note,
    clientUpdatedAtEpochMs = clientUpdatedAtEpochMs,
    deleted = deleted,
)

// ---------- weigh-ins ----------

internal fun WeighInEntity.toDomain(): WeighIn =
    WeighIn(id = id, measuredAt = Instant.fromEpochMilliseconds(measuredAtEpochMs), kg = kg)

internal fun WeighInEntity.toRow(): WeighInRowDto = WeighInRowDto(
    id = id,
    measuredAtEpochMs = measuredAtEpochMs,
    kg = kg,
    clientUpdatedAtEpochMs = clientUpdatedAtEpochMs,
    deleted = deleted,
)

internal fun WeighInRowDto.toEntity(): WeighInEntity = WeighInEntity(
    id = id,
    measuredAtEpochMs = measuredAtEpochMs,
    kg = kg,
    clientUpdatedAtEpochMs = clientUpdatedAtEpochMs,
    deleted = deleted,
)

// ---------- custom foods ----------

internal fun CustomFoodEntity.toDomain(): CustomFood = CustomFood(
    id = id,
    name = name,
    servingAmount = servingAmount,
    servingUnit = servingUnit,
    brand = brand,
    kcal = kcal,
    protein = protein,
    carbs = carbs,
    fat = fat,
    fiber = fiber,
    servingGrams = servingGrams,
    // Sizes or micros another build wrote in a shape this one cannot read are dropped, not fatal.
    sizes = sizesJson?.let { Catching { WireJson.decodeFromString(sizesSerializer, it) }.getOrNull() }.orEmpty(),
    micros = microsJson?.let { Catching { WireJson.decodeFromString(microsSerializer, it) }.getOrNull() }.orEmpty(),
)

internal fun CustomFood.toEntity(updatedAtEpochMs: Long, deleted: Boolean = false): CustomFoodEntity = CustomFoodEntity(
    id = id,
    name = name.trim(),
    brand = brand?.trim()?.takeIf { it.isNotEmpty() },
    servingAmount = servingAmount,
    servingUnit = servingUnit.trim(),
    kcal = kcal,
    protein = protein,
    carbs = carbs,
    fat = fat,
    fiber = fiber,
    clientUpdatedAtEpochMs = updatedAtEpochMs,
    deleted = deleted,
    servingGrams = servingGrams,
    sizesJson = sizes.takeIf { it.isNotEmpty() }?.let { WireJson.encodeToString(sizesSerializer, it) },
    microsJson = micros.takeIf { it.isNotEmpty() }?.let { WireJson.encodeToString(microsSerializer, it) },
)

internal fun CustomFoodEntity.toRow(): CustomFoodRowDto {
    val food = toDomain()
    return CustomFoodRowDto(
        id = id,
        name = name,
        brand = brand,
        servingAmount = servingAmount,
        servingUnit = servingUnit,
        kcal = kcal,
        protein = protein,
        carbs = carbs,
        fat = fat,
        fiber = fiber,
        clientUpdatedAtEpochMs = clientUpdatedAtEpochMs,
        deleted = deleted,
        servingGrams = servingGrams,
        // Always present, empty for none: an absent list is how the server tells an older build's row.
        sizes = food.sizes.map { FoodUnitDto(it.label, it.grams) },
        micros = food.micros,
    )
}

/**
 * A pulled row as stored. One without [CustomFoodRowDto.sizes] came from a server or a build
 * that predates them, so the serving's weight, sizes and micros already [kept] here stay.
 */
internal fun CustomFoodRowDto.toEntity(kept: CustomFoodEntity? = null): CustomFoodEntity {
    val known = sizes != null
    return CustomFoodEntity(
        id = id,
        name = name,
        brand = brand,
        servingAmount = servingAmount,
        servingUnit = servingUnit,
        kcal = kcal,
        protein = protein,
        carbs = carbs,
        fat = fat,
        fiber = fiber,
        clientUpdatedAtEpochMs = clientUpdatedAtEpochMs,
        deleted = deleted,
        servingGrams = if (known) servingGrams else kept?.servingGrams,
        sizesJson = if (known) {
            sizes.orEmpty().takeIf { it.isNotEmpty() }?.let { units -> WireJson.encodeToString(sizesSerializer, units.map { FoodUnit(it.label, it.grams) }) }
        } else {
            kept?.sizesJson
        },
        microsJson = if (known) micros?.takeIf { it.isNotEmpty() }?.let { WireJson.encodeToString(microsSerializer, it) } else kept?.microsJson,
    )
}

// ---------- saved meals ----------

internal fun SavedMealEntity.toDomain(): SavedMeal = SavedMeal(
    id = id,
    name = name,
    usualSlot = usualSlot?.let(MealSlot::fromWire),
    // Items another build wrote in a shape this one cannot read are dropped, not fatal.
    items = Catching { WireJson.decodeFromString(draftsSerializer, itemsJson) }.getOrDefault(emptyList()),
)

internal fun SavedMeal.toEntity(updatedAtEpochMs: Long, deleted: Boolean = false): SavedMealEntity = SavedMealEntity(
    id = id,
    name = name.trim(),
    usualSlot = usualSlot?.wire,
    itemsJson = WireJson.encodeToString(draftsSerializer, items),
    clientUpdatedAtEpochMs = updatedAtEpochMs,
    deleted = deleted,
)

internal fun SavedMealEntity.toRow(): SavedMealRowDto = SavedMealRowDto(
    id = id,
    name = name,
    usualSlot = usualSlot,
    items = WireJson.decodeFromString(JsonArray.serializer(), itemsJson),
    clientUpdatedAtEpochMs = clientUpdatedAtEpochMs,
    deleted = deleted,
)

internal fun SavedMealRowDto.toEntity(): SavedMealEntity = SavedMealEntity(
    id = id,
    name = name,
    usualSlot = usualSlot,
    itemsJson = WireJson.encodeToString(JsonArray.serializer(), items),
    clientUpdatedAtEpochMs = clientUpdatedAtEpochMs,
    deleted = deleted,
)
