package com.opson.server.data

import com.opson.server.db.CustomFoodsTable
import com.opson.server.db.DayRecordsTable
import com.opson.server.db.ExercisesTable
import com.opson.server.db.FastsTable
import com.opson.server.db.LogEntriesTable
import com.opson.server.db.MealFeelingsTable
import com.opson.server.db.RecipesTable
import com.opson.server.db.SavedMealsTable
import com.opson.server.db.UserSettingsTable
import com.opson.server.db.WeighInsTable
import com.opson.server.db.toKotlinInstant
import com.opson.server.domain.CustomFood
import com.opson.server.domain.CustomFoodDetails
import com.opson.server.domain.DayRecord
import com.opson.server.domain.Exercise
import com.opson.server.domain.Fast
import com.opson.server.domain.FoodSize
import com.opson.server.domain.LogEntry
import com.opson.server.domain.LogMethod
import com.opson.server.domain.MealFeeling
import com.opson.server.domain.MealSlot
import com.opson.server.domain.Nutrients
import com.opson.server.domain.Precision
import com.opson.server.domain.PublicRecipes
import com.opson.server.domain.PublicState
import com.opson.server.domain.Recipe
import com.opson.server.domain.RecipeSharing
import com.opson.server.domain.SavedMeal
import com.opson.server.domain.UserSettings
import com.opson.server.domain.WeighIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow

// Row to domain mappers for the synced diary tables, shared by sync's pull and the export, so the
// two can never read a column differently.

private val JSON = Json { ignoreUnknownKeys = true }
private val MICROS_SERIALIZER = MapSerializer(String.serializer(), Double.serializer())
private val SLOT_FLAGS_SERIALIZER = MapSerializer(String.serializer(), Boolean.serializer())
private val SIZES_SERIALIZER = ListSerializer(SizeJson.serializer())

/** How `custom_foods.sizes` stores one size. */
@Serializable
internal data class SizeJson(val label: String, val grams: Double)

internal fun ResultRow.toLogEntry(): LogEntry = LogEntry(
    id = this[LogEntriesTable.id],
    day = this[LogEntriesTable.day],
    slot = MealSlot.fromWire(this[LogEntriesTable.slot]) ?: MealSlot.Uncategorized,
    loggedAt = this[LogEntriesTable.loggedAt].toKotlinInstant(),
    foodId = this[LogEntriesTable.foodId],
    customFoodId = this[LogEntriesTable.customFoodId],
    name = this[LogEntriesTable.name],
    amountText = this[LogEntriesTable.amountText],
    quantity = this[LogEntriesTable.quantity].clean(),
    unit = this[LogEntriesTable.unit],
    grams = this[LogEntriesTable.grams].clean(),
    precision = Precision.fromWire(this[LogEntriesTable.precision]) ?: Precision.Estimate,
    nutrients = Nutrients(
        kcal = this[LogEntriesTable.kcal].clean(),
        protein = this[LogEntriesTable.protein].clean(),
        carbs = this[LogEntriesTable.carbs].clean(),
        fat = this[LogEntriesTable.fat].clean(),
        fiber = this[LogEntriesTable.fiber].clean(),
    ),
    micros = this[LogEntriesTable.micros]?.let { JSON.decodeFromString(MICROS_SERIALIZER, it) },
    method = LogMethod.fromWire(this[LogEntriesTable.method]) ?: LogMethod.Search,
    clientUpdatedAt = this[LogEntriesTable.clientUpdatedAt].toKotlinInstant(),
    deleted = this[LogEntriesTable.deleted],
)

internal fun ResultRow.toDayRecord(): DayRecord = DayRecord(
    day = this[DayRecordsTable.day],
    waterMl = this[DayRecordsTable.waterMl],
    closedAt = this[DayRecordsTable.closedAt]?.toKotlinInstant(),
    note = this[DayRecordsTable.note],
    ateNoDetails = JSON.decodeFromString(SLOT_FLAGS_SERIALIZER, this[DayRecordsTable.ateNoDetails])
        .mapNotNull { (key, value) -> MealSlot.fromWire(key)?.let { it to value } }
        .toMap(),
    mealGroups = this[DayRecordsTable.mealGroups],
    untracked = this[DayRecordsTable.untracked],
    feeling = this[DayRecordsTable.feeling],
    checkIn = this[DayRecordsTable.checkIn],
    clientUpdatedAt = this[DayRecordsTable.clientUpdatedAt].toKotlinInstant(),
    deleted = this[DayRecordsTable.deleted],
)

internal fun ResultRow.toMealFeeling(): MealFeeling = MealFeeling(
    day = this[MealFeelingsTable.day],
    slot = MealSlot.fromWire(this[MealFeelingsTable.slot]) ?: MealSlot.Uncategorized,
    hunger = this[MealFeelingsTable.hunger],
    fullness = this[MealFeelingsTable.fullness],
    moods = this[MealFeelingsTable.moods],
    note = this[MealFeelingsTable.note],
    clientUpdatedAt = this[MealFeelingsTable.clientUpdatedAt].toKotlinInstant(),
    deleted = this[MealFeelingsTable.deleted],
)

internal fun ResultRow.toWeighIn(): WeighIn = WeighIn(
    id = this[WeighInsTable.id],
    measuredAt = this[WeighInsTable.measuredAt].toKotlinInstant(),
    kg = this[WeighInsTable.kg].clean() ?: 0.0,
    clientUpdatedAt = this[WeighInsTable.clientUpdatedAt].toKotlinInstant(),
    deleted = this[WeighInsTable.deleted],
)

internal fun ResultRow.toCustomFood(): CustomFood = CustomFood(
    id = this[CustomFoodsTable.id],
    name = this[CustomFoodsTable.name],
    brand = this[CustomFoodsTable.brand],
    servingAmount = this[CustomFoodsTable.servingAmount].clean() ?: 0.0,
    servingUnit = this[CustomFoodsTable.servingUnit],
    nutrients = Nutrients(
        kcal = this[CustomFoodsTable.kcal].clean(),
        protein = this[CustomFoodsTable.protein].clean(),
        carbs = this[CustomFoodsTable.carbs].clean(),
        fat = this[CustomFoodsTable.fat].clean(),
        fiber = this[CustomFoodsTable.fiber].clean(),
    ),
    clientUpdatedAt = this[CustomFoodsTable.clientUpdatedAt].toKotlinInstant(),
    deleted = this[CustomFoodsTable.deleted],
    details = CustomFoodDetails(
        servingGrams = this[CustomFoodsTable.servingGrams].clean(),
        sizes = this[CustomFoodsTable.sizes]?.let { JSON.decodeFromString(SIZES_SERIALIZER, it) }.orEmpty().map { FoodSize(it.label, it.grams) },
        micros = this[CustomFoodsTable.micros]?.let { JSON.decodeFromString(MICROS_SERIALIZER, it) }.orEmpty(),
    ),
)

internal fun ResultRow.toSavedMeal(): SavedMeal = SavedMeal(
    id = this[SavedMealsTable.id],
    name = this[SavedMealsTable.name],
    usualSlot = this[SavedMealsTable.usualSlot]?.let { MealSlot.fromWire(it) },
    itemsJson = this[SavedMealsTable.items],
    clientUpdatedAt = this[SavedMealsTable.clientUpdatedAt].toKotlinInstant(),
    deleted = this[SavedMealsTable.deleted],
)

internal fun ResultRow.toFast(): Fast = Fast(
    day = this[FastsTable.day],
    startedAt = this[FastsTable.startedAt].toKotlinInstant(),
    endedAt = this[FastsTable.endedAt]?.toKotlinInstant(),
    goalHours = this[FastsTable.goalHours]?.toInt(),
    clientUpdatedAt = this[FastsTable.clientUpdatedAt].toKotlinInstant(),
    deleted = this[FastsTable.deleted],
)

internal fun ResultRow.toExercise(): Exercise = Exercise(
    id = this[ExercisesTable.id],
    day = this[ExercisesTable.day],
    startedAt = this[ExercisesTable.startedAt].toKotlinInstant(),
    activity = this[ExercisesTable.activity],
    name = this[ExercisesTable.name],
    minutes = this[ExercisesTable.minutes].toInt(),
    kcal = this[ExercisesTable.kcal],
    kcalTyped = this[ExercisesTable.kcalTyped],
    notOnWatch = this[ExercisesTable.notOnWatch],
    clientUpdatedAt = this[ExercisesTable.clientUpdatedAt].toKotlinInstant(),
    deleted = this[ExercisesTable.deleted],
)

internal fun ResultRow.toRecipe(): Recipe {
    val items = this[RecipesTable.items]
    return Recipe(
        id = this[RecipesTable.id],
        name = this[RecipesTable.name],
        servings = this[RecipesTable.servings].clean() ?: 1.0,
        itemsJson = items,
        itemCount = PublicRecipes.items(items).size,
        notes = this[RecipesTable.notes],
        clientUpdatedAt = this[RecipesTable.clientUpdatedAt].toKotlinInstant(),
        deleted = this[RecipesTable.deleted],
        savedFrom = this[RecipesTable.savedFrom],
        sharing = RecipeSharing(
            code = this[RecipesTable.publicCode],
            state = PublicState.fromWire(this[RecipesTable.publicState]),
            showName = this[RecipesTable.publicShowName],
        ),
    )
}

internal fun ResultRow.toUserSettings(): UserSettings = UserSettings(
    settingsJson = this[UserSettingsTable.settings],
    changedAt = this[UserSettingsTable.changedAt]?.toKotlinInstant(),
    clientUpdatedAt = this[UserSettingsTable.clientUpdatedAt].toKotlinInstant(),
    deleted = this[UserSettingsTable.deleted],
)
