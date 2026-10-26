package com.opson.features.foods.impl

import com.opson.libraries.core.fixed
import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.CustomFoodDraft
import com.opson.libraries.diary.CustomFoodRepository
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayFeeling
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealPicks
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.PickedForMeal
import com.opson.libraries.diary.SavedMeal
import com.opson.libraries.diary.SavedMealRepository
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/** Noon on the fixtures' today, before the 9 pm reveal. */
val NOON: Instant = Instant.parse("2026-10-25T12:00:00Z")

fun policy(preset: PresetId, at: Instant = NOON): VisibilityPolicy =
    VisibilityPolicy(NumbersSettings(preset = preset), Clock.fixed(at), TimeZone.UTC)

class FakePolicySource(preset: PresetId = PresetId.Full) : VisibilityPolicySource {
    override val policy = MutableStateFlow(policy(preset))
}

/** A diary that only answers the reads Foods makes: a day, and recent foods. Writes are inert. */
class FakeDiaryRepository(days: List<Day> = emptyList()) : DiaryRepository {
    private val days = days.associateBy { it.date }
    val recent = MutableStateFlow<List<LogItem>>(emptyList())

    override fun observeDay(date: LocalDate): Flow<Day> = flowOf(days[date] ?: Day.empty(date))

    override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<Day>> = flowOf(emptyList())

    override fun observeLoggedDates(year: Int, month: Int): Flow<Set<LocalDate>> = flowOf(emptySet())

    override suspend fun addItems(day: LocalDate, slot: MealSlot, items: List<LogDraft>, method: LogMethod): List<LogItem> =
        error("Foods never logs")

    override suspend fun updateItem(item: LogItem) = Unit

    override suspend fun removeItem(id: String) = Unit

    override suspend fun moveItem(id: String, slot: MealSlot) = Unit

    override suspend fun copyDay(from: LocalDate, to: LocalDate): List<LogItem> = emptyList()

    override suspend fun copySlot(fromDay: LocalDate, toDay: LocalDate, slot: MealSlot): List<LogItem> = emptyList()

    override suspend fun clearDay(day: LocalDate) = Unit

    override suspend fun addWater(day: LocalDate, ml: Int) = Unit

    override suspend fun removeWater(day: LocalDate, ml: Int) = Unit

    override suspend fun setWater(day: LocalDate, ml: Int) = Unit

    override suspend fun closeDay(day: LocalDate) = Unit

    override suspend fun reopenDay(day: LocalDate) = Unit

    override suspend fun setDayNote(day: LocalDate, note: String?) = Unit

    override suspend fun setUntracked(day: LocalDate, untracked: Boolean) = Unit

    override suspend fun setDayFeeling(day: LocalDate, feeling: DayFeeling?) = Unit

    override suspend fun setAteNoDetails(day: LocalDate, slot: MealSlot, ate: Boolean) = Unit

    override suspend fun setFeeling(day: LocalDate, slot: MealSlot, feeling: MealFeeling?) = Unit

    override suspend fun setMealGroups(day: LocalDate, on: Boolean?) = Unit

    override fun recentFoods(limit: Int): Flow<List<LogItem>> = recent.map { it.take(limit) }

    override fun usualAt(slot: MealSlot, limit: Int, days: Int): Flow<List<LogItem>> = flowOf(emptyList())

    override fun frequentFoods(limit: Int, days: Int): Flow<List<LogItem>> = flowOf(emptyList())
}

class FakeCustomFoodRepository(foods: List<CustomFood> = emptyList()) : CustomFoodRepository {
    val foods = MutableStateFlow(foods)
    val created = mutableListOf<CustomFoodDraft>()

    override fun observeAll(): Flow<List<CustomFood>> = foods

    override suspend fun get(id: String): CustomFood? = foods.value.firstOrNull { it.id == id }

    override suspend fun create(draft: CustomFoodDraft): CustomFood {
        created += draft
        val food = CustomFood(
            id = "made-${created.size}",
            name = draft.name,
            servingAmount = draft.servingAmount,
            servingUnit = draft.servingUnit,
            brand = draft.brand,
            kcal = draft.kcal,
            protein = draft.protein,
            carbs = draft.carbs,
            fat = draft.fat,
            fiber = draft.fiber,
        )
        foods.value = foods.value + food
        return food
    }

    override suspend fun update(food: CustomFood) = Unit

    override suspend fun delete(id: String) = Unit
}

class FakeSavedMealRepository(meals: List<SavedMeal> = emptyList()) : SavedMealRepository {
    val meals = MutableStateFlow(meals)

    data class Created(val name: String, val usualSlot: MealSlot?, val items: List<LogDraft>)

    val created = mutableListOf<Created>()

    override fun observeAll(): Flow<List<SavedMeal>> = meals

    override suspend fun get(id: String): SavedMeal? = meals.value.firstOrNull { it.id == id }

    override suspend fun create(name: String, usualSlot: MealSlot?, items: List<LogDraft>): SavedMeal {
        created += Created(name, usualSlot, items)
        val meal = SavedMeal(id = "saved-${created.size}", name = name, usualSlot = usualSlot, items = items)
        meals.value = meals.value + meal
        return meal
    }

    override suspend fun update(meal: SavedMeal) = Unit

    override suspend fun delete(id: String) = Unit
}

class FakeMealPicks : MealPicks {
    override val latest = MutableStateFlow<PickedForMeal?>(null)

    override fun picked(picks: PickedForMeal) {
        latest.value = picks
    }

    override fun consumed(picks: PickedForMeal) {
        if (latest.value == picks) latest.value = null
    }
}
