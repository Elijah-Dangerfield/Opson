package com.opson.features.logger.impl

import com.opson.libraries.core.AppState
import com.opson.libraries.core.Catching
import com.opson.libraries.core.fixed
import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.CustomFoodDraft
import com.opson.libraries.diary.CustomFoodRepository
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.LoggedToMeal
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.RecentLogEvents
import com.opson.libraries.diary.SavedMeal
import com.opson.libraries.diary.SavedMealRepository
import com.opson.libraries.diary.SelectedDay
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodCatalog
import com.opson.libraries.foods.FoodSearchResult
import com.opson.libraries.foods.ParsedItem
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

/**
 * An in-memory diary that remembers every write. Only what the logger reads
 * and writes does anything; the rest is inert.
 */
class FakeDiaryRepository(days: List<Day> = emptyList()) : DiaryRepository {
    private val daysState = MutableStateFlow(days.associateBy { it.date })
    val favorites = MutableStateFlow<List<LogItem>>(emptyList())
    private var nextId = 0
    val usual = MutableStateFlow<Map<MealSlot, List<LogItem>>>(emptyMap())
    val recent = MutableStateFlow<List<LogItem>>(emptyList())

    data class Added(val day: LocalDate, val slot: MealSlot, val items: List<LogDraft>, val method: LogMethod)

    val added = mutableListOf<Added>()
    val water = mutableListOf<Pair<LocalDate, Int>>()
    val copied = mutableListOf<Triple<LocalDate, LocalDate, MealSlot>>()
    val ateNoDetails = mutableListOf<Pair<LocalDate, MealSlot>>()
    val feelings = mutableListOf<Triple<LocalDate, MealSlot, MealFeeling?>>()

    override fun observeDay(date: LocalDate): Flow<Day> = daysState.map { it[date] ?: Day.empty(date) }

    override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<Day>> = flowOf(emptyList())

    override fun observeLoggedDates(year: Int, month: Int): Flow<Set<LocalDate>> = flowOf(emptySet())

    override suspend fun addItems(day: LocalDate, slot: MealSlot, items: List<LogDraft>, method: LogMethod): List<LogItem> {
        added += Added(day, slot, items, method)
        return items.map { draft ->
            LogItem(
                id = "new-${nextId++}",
                day = day,
                slot = slot,
                loggedAt = NOON,
                name = draft.name,
                precision = draft.precision,
                method = draft.method ?: method,
                kcal = draft.kcal,
            )
        }
    }

    override suspend fun updateItem(item: LogItem) = Unit

    override suspend fun removeItem(id: String) = Unit

    override suspend fun moveItem(id: String, slot: MealSlot) = Unit

    override suspend fun copyDay(from: LocalDate, to: LocalDate): List<LogItem> = emptyList()

    override suspend fun copySlot(fromDay: LocalDate, toDay: LocalDate, slot: MealSlot): List<LogItem> {
        copied += Triple(fromDay, toDay, slot)
        return daysState.value[fromDay]?.items(slot).orEmpty()
    }

    override suspend fun clearDay(day: LocalDate) = Unit

    override suspend fun addWater(day: LocalDate, ml: Int) {
        water += day to ml
    }

    override suspend fun removeWater(day: LocalDate, ml: Int) = Unit

    override suspend fun setWater(day: LocalDate, ml: Int) = Unit

    override suspend fun closeDay(day: LocalDate) = Unit

    override suspend fun reopenDay(day: LocalDate) = Unit

    override suspend fun setDayNote(day: LocalDate, note: String?) = Unit

    override suspend fun setAteNoDetails(day: LocalDate, slot: MealSlot, ate: Boolean) {
        if (ate) ateNoDetails += day to slot
    }

    override suspend fun setFeeling(day: LocalDate, slot: MealSlot, feeling: MealFeeling?) {
        feelings += Triple(day, slot, feeling)
    }

    override suspend fun setMealGroups(day: LocalDate, on: Boolean?) = Unit

    override fun recentFoods(limit: Int): Flow<List<LogItem>> = recent

    override fun usualAt(slot: MealSlot, limit: Int, days: Int): Flow<List<LogItem>> = usual.map { it[slot].orEmpty() }

    override fun frequentFoods(limit: Int, days: Int): Flow<List<LogItem>> = favorites
}

class FakeCustomFoodRepository(foods: List<CustomFood> = emptyList()) : CustomFoodRepository {
    val foods = MutableStateFlow(foods)

    override fun observeAll(): Flow<List<CustomFood>> = foods

    override suspend fun get(id: String): CustomFood? = foods.value.firstOrNull { it.id == id }

    override suspend fun create(draft: CustomFoodDraft): CustomFood {
        val food = CustomFood(id = "made-${foods.value.size}", name = draft.name, servingAmount = draft.servingAmount, servingUnit = draft.servingUnit)
        foods.value = foods.value + food
        return food
    }

    override suspend fun update(food: CustomFood) = Unit

    override suspend fun delete(id: String) = Unit
}

class FakeSavedMealRepository(meals: List<SavedMeal> = emptyList()) : SavedMealRepository {
    val meals = MutableStateFlow(meals)

    override fun observeAll(): Flow<List<SavedMeal>> = meals

    override suspend fun get(id: String): SavedMeal? = meals.value.firstOrNull { it.id == id }

    override suspend fun create(name: String, usualSlot: MealSlot?, items: List<LogDraft>): SavedMeal =
        error("The logger never saves a meal")

    override suspend fun update(meal: SavedMeal) = Unit

    override suspend fun delete(id: String) = Unit
}

/** A catalog that answers from [foods], or fails when [online] is false, and records every search. */
class FakeFoodCatalog(
    var foods: List<Food> = emptyList(),
    var online: Boolean = true,
    var describeResult: Catching<List<ParsedItem>> = Catching.success(emptyList()),
) : FoodCatalog {
    val searches = mutableListOf<String>()
    val marked = mutableListOf<Food>()

    override suspend fun search(query: String, limit: Int): Catching<FoodSearchResult> {
        searches += query
        val hits = foods.filter { it.name.contains(query, ignoreCase = true) }
        return Catching.success(FoodSearchResult(query = query, foods = hits, fromCache = !online))
    }

    override suspend fun detail(id: String): Catching<Food> =
        foods.firstOrNull { it.id == id }?.let { Catching.success(it) } ?: Catching.failure(IllegalStateException("offline"))

    override suspend fun barcode(code: String): Catching<Food?> = Catching.success(null)

    override suspend fun describe(text: String): Catching<List<ParsedItem>> = describeResult

    override fun observeFavorites(): Flow<List<Food>> = flowOf(emptyList())

    override fun observeRecent(limit: Int): Flow<List<Food>> = flowOf(emptyList())

    override suspend fun markUsed(food: Food) {
        marked += food
    }

    override suspend fun setFavorite(food: Food, favorite: Boolean) = Unit
}

class FakeSettingsRepository(initial: Settings = Settings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: StateFlow<Settings> = state
    override val isKnown: StateFlow<Boolean> = MutableStateFlow(true)

    override suspend fun update(stampNumbersChange: Boolean, transform: (Settings) -> Settings): Settings =
        transform(state.value).also { state.value = it }
}

class FakePolicySource(preset: PresetId = PresetId.Full) : VisibilityPolicySource {
    override val policy = MutableStateFlow(policy(preset))
}

class FakeCalendar(private val today: LocalDate = LoggerFixtures.today) : LocalCalendar {
    override fun timeZone(): TimeZone = TimeZone.UTC

    override fun today(): LocalDate = today
}

class FakeSelectedDay(day: LocalDate = LoggerFixtures.today) : SelectedDay {
    override val date = MutableStateFlow(day)

    override fun select(day: LocalDate) {
        date.value = day
    }
}

class FakeRecentLogEvents : RecentLogEvents {
    override val latest = MutableStateFlow<LoggedToMeal?>(null)
    val logged = mutableListOf<LoggedToMeal>()

    override fun logged(event: LoggedToMeal) {
        logged += event
        latest.value = event
    }

    override fun consumed(event: LoggedToMeal) {
        if (latest.value == event) latest.value = null
    }

    override suspend fun undo(event: LoggedToMeal) = consumed(event)
}

class RecordingTelemetry : LoggerTelemetry {
    val events = mutableListOf<Pair<LogVia, Int>>()

    override fun methodPicked(via: LogVia, items: Int) {
        events += via to items
    }
}

class FakeAppState(offline: Boolean = false) : AppState {
    override val isOffline = MutableStateFlow(offline)
    override val isBlockActive: StateFlow<Boolean> = MutableStateFlow(false)
}
