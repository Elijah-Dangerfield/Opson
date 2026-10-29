package com.opson.features.foods.impl

import com.opson.features.logger.SubmitFoodAvailability
import com.opson.libraries.core.Catching
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodCatalog
import com.opson.libraries.foods.FoodSearchFilter
import com.opson.libraries.foods.FoodSearchResult
import com.opson.libraries.foods.ParsedItem
import com.opson.libraries.foods.ReviewStatus
import com.opson.libraries.foods.SearchOptions
import com.opson.libraries.foods.SubmittedFoods
import kotlinx.coroutines.flow.StateFlow
import com.opson.libraries.core.fixed
import com.opson.libraries.diary.DayWrite
import com.opson.libraries.diary.DayReflection
import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.CustomFoodDraft
import com.opson.libraries.diary.CustomFoodRepository
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealPicks
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.PickedForMeal
import com.opson.libraries.diary.SavedMeal
import com.opson.libraries.diary.SavedMealRepository
import com.opson.libraries.diary.RepeatPlan
import com.opson.libraries.diary.RepeatRepository
import com.opson.libraries.diary.RepeatRule
import com.opson.libraries.diary.RepeatSaved
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.Metric
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

fun policy(preset: PresetId, at: Instant = NOON): VisibilityPolicy = policy(NumbersSettings(preset = preset), at)

fun policy(numbers: NumbersSettings, at: Instant = NOON): VisibilityPolicy = VisibilityPolicy(numbers, Clock.fixed(at), TimeZone.UTC)

/** Macros only with carbs and fat hidden: where someone who had Nutrient focus lands. */
fun nutrientFocus(): VisibilityPolicy =
    policy(NumbersSettings(PresetId.MacrosOnly, overrides = mapOf(Metric.Carbs to Visibility.Hide, Metric.Fat to Visibility.Hide)))

class FakePolicySource(preset: PresetId = PresetId.Full) : VisibilityPolicySource {
    override val policy = MutableStateFlow(policy(preset))
}

/** A diary that only answers the reads Foods makes: a day, and recent foods. Writes are inert, but an edited entry is kept. */
class FakeDiaryRepository(days: List<Day> = emptyList()) : DiaryRepository {
    private val days = days.associateBy { it.date }
    val recent = MutableStateFlow<List<LogItem>>(emptyList())
    val updated = mutableListOf<LogItem>()

    override fun observeDay(date: LocalDate): Flow<Day> = flowOf(days[date] ?: Day.empty(date))

    override fun observeDays(from: LocalDate, to: LocalDate): Flow<List<Day>> = flowOf(emptyList())

    override fun observeLoggedDates(year: Int, month: Int): Flow<Set<LocalDate>> = flowOf(emptySet())

    override suspend fun addItems(day: LocalDate, slot: MealSlot, items: List<LogDraft>, method: LogMethod): DayWrite<List<LogItem>> =
        error("Foods never logs")

    override suspend fun updateItem(item: LogItem): DayWrite<Unit> {
        updated += item
        return DayWrite.Written(Unit)
    }

    override suspend fun removeItem(id: String): DayWrite<Unit> = DayWrite.Written(Unit)

    override suspend fun moveItem(id: String, slot: MealSlot): DayWrite<Unit> = DayWrite.Written(Unit)

    override suspend fun copyDay(from: LocalDate, to: LocalDate): DayWrite<List<LogItem>> = DayWrite.Written(emptyList())

    override suspend fun copySlot(fromDay: LocalDate, toDay: LocalDate, slot: MealSlot): DayWrite<List<LogItem>> = DayWrite.Written(emptyList())

    override suspend fun clearDay(day: LocalDate): DayWrite<Unit> = DayWrite.Written(Unit)

    override suspend fun addWater(day: LocalDate, ml: Int): DayWrite<Unit> = DayWrite.Written(Unit)

    override suspend fun removeWater(day: LocalDate, ml: Int): DayWrite<Unit> = DayWrite.Written(Unit)

    override suspend fun setWater(day: LocalDate, ml: Int): DayWrite<Unit> = DayWrite.Written(Unit)

    override suspend fun closeDay(day: LocalDate, reflection: DayReflection) = Unit

    override suspend fun reopenDay(day: LocalDate) = Unit

    override suspend fun setDayNote(day: LocalDate, note: String?): DayWrite<Unit> = DayWrite.Written(Unit)

    override suspend fun setUntracked(day: LocalDate, untracked: Boolean) = Unit


    override suspend fun setAteNoDetails(day: LocalDate, slot: MealSlot, ate: Boolean): DayWrite<Unit> = DayWrite.Written(Unit)

    override suspend fun setFeeling(day: LocalDate, slot: MealSlot, feeling: MealFeeling?): DayWrite<Unit> = DayWrite.Written(Unit)

    override suspend fun setMealGroups(day: LocalDate, on: Boolean?) = Unit

    override fun recentFoods(limit: Int): Flow<List<LogItem>> = recent.map { it.take(limit) }

    override fun usualAt(slot: MealSlot, limit: Int, days: Int): Flow<List<LogItem>> = flowOf(emptyList())

    override fun frequentFoods(limit: Int, days: Int): Flow<List<LogItem>> = flowOf(emptyList())
}

class FakeCustomFoodRepository(foods: List<CustomFood> = emptyList()) : CustomFoodRepository {
    val foods = MutableStateFlow(foods)
    val created = mutableListOf<CustomFoodDraft>()

    /** Ids of foods updated or deleted, so a test can show a food was left alone. */
    val changed = mutableListOf<String>()

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
            servingGrams = draft.servingGrams,
            sizes = draft.sizes,
            micros = draft.micros,
        )
        foods.value = foods.value + food
        return food
    }

    override suspend fun update(food: CustomFood) {
        changed += food.id
    }

    override suspend fun delete(id: String) {
        changed += id
    }
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

class FakeCalendar(private val today: LocalDate = FoodsFixtures.today) : LocalCalendar {
    override fun timeZone(): TimeZone = TimeZone.UTC

    override fun today(): LocalDate = today
}

/** A catalog that knows [foods] and nothing else; offline for anything it does not hold. */
class FakeFoodCatalog(private val foods: List<Food> = emptyList()) : FoodCatalog {
    override suspend fun search(query: String, limit: Int, filter: FoodSearchFilter): Catching<FoodSearchResult> =
        Catching.success(FoodSearchResult(query, foods.filter { it.name.contains(query, ignoreCase = true) }, fromCache = false))

    override suspend fun searchOptions(): Catching<SearchOptions> = Catching.success(SearchOptions(openFoodFacts = false))

    override suspend fun detail(id: String): Catching<Food> =
        foods.firstOrNull { it.id == id }?.let { Catching.success(it) } ?: Catching.failure(IllegalStateException("offline"))

    override suspend fun barcode(code: String): Catching<Food?> = Catching.success(null)

    override suspend fun describe(text: String): Catching<List<ParsedItem>> = Catching.success(emptyList())

    override fun observeFavorites(): Flow<List<Food>> = flowOf(emptyList())

    override fun observeRecent(limit: Int): Flow<List<Food>> = flowOf(emptyList())

    override suspend fun markUsed(food: Food) = Unit

    override suspend fun setFavorite(food: Food, favorite: Boolean) = Unit
}

class FakeSettingsRepository(initial: Settings = Settings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: StateFlow<Settings> = state
    override val isKnown: StateFlow<Boolean> = MutableStateFlow(true)

    override suspend fun update(stampNumbersChange: Boolean, transform: (Settings) -> Settings): Settings =
        transform(state.value).also { state.value = it }
}

/** [SubmittedFoods] in memory: the marks a screen reads. */
class FakeSubmittedFoods(initial: Map<String, ReviewStatus> = emptyMap()) : SubmittedFoods {
    override val reviews = MutableStateFlow(initial)

    override suspend fun sent(customFoodId: String, submissionId: String, foodId: String) {
        reviews.value = reviews.value + (customFoodId to ReviewStatus.Pending)
    }
}

/** The `release.submitFood` flag, fixed. */
class FakeSubmitFoodAvailability(var on: Boolean = false) : SubmitFoodAvailability {
    override fun available(): Boolean = on
}

/** Repeats in memory. [loggedOnSave] is what the next save says it logged for today. */
class FakeRepeatRepository(rules: List<RepeatRule> = emptyList()) : RepeatRepository {
    val rules = MutableStateFlow(rules)
    var loggedOnSave = true

    data class Created(val plan: RepeatPlan, val loggedToday: List<String>)

    val created = mutableListOf<Created>()
    val deleted = mutableListOf<String>()

    override fun observeAll(): Flow<List<RepeatRule>> = rules

    override suspend fun get(id: String): RepeatRule? = rules.value.firstOrNull { it.id == id }

    override suspend fun create(plan: RepeatPlan, loggedToday: List<String>): RepeatSaved {
        created += Created(plan, loggedToday)
        val rule = RepeatRule(id = "repeat-${created.size}", plan = plan)
        rules.value = rules.value + rule
        return RepeatSaved(rule, loggedOnSave)
    }

    override suspend fun update(id: String, plan: RepeatPlan, paused: Boolean): RepeatSaved? {
        val rule = get(id)?.copy(plan = plan, paused = paused) ?: return null
        rules.value = rules.value.map { if (it.id == id) rule else it }
        return RepeatSaved(rule, loggedOnSave)
    }

    override suspend fun setPaused(id: String, paused: Boolean): RepeatSaved? = get(id)?.let { update(id, it.plan, paused) }

    override suspend fun delete(id: String) {
        deleted += id
        rules.value = rules.value.filterNot { it.id == id }
    }
}
