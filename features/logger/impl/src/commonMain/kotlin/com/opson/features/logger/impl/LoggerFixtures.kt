package com.opson.features.logger.impl

import com.opson.features.logger.LogTab
import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.SavedMeal
import com.opson.libraries.diary.Settings
import com.opson.libraries.foods.Confidence
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodAmount
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.FoodSource
import com.opson.libraries.foods.NutrientProfile
import com.opson.libraries.foods.ParsedItem
import com.opson.libraries.foods.Portion
import com.opson.libraries.foods.toLogDraft
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Realistic logger inputs for previews and `BlindModeLeakTest`, from the
 * prototype's `FOODS`, `MEALS` and `SAVED` mocks. Real calorie values on
 * purpose: Full must have something to show, or the leak test's control proves
 * nothing. Never builds a `Display`; states come from the real builders.
 */
object LoggerFixtures {
    val today: LocalDate = LocalDate(2026, 10, 25)

    val banana = Food(
        id = "banana",
        source = FoodSource.UsdaSr,
        name = "Banana, raw",
        per100g = NutrientProfile(kcal = 89.0, protein = 1.1, carbs = 22.8, fat = 0.3, fiber = 2.6),
        micros = mapOf("calcium_mg" to 5.0),
        portions = listOf(Portion("1 medium", 118.0)),
    )
    val yogurt = Food(
        id = "yogurt",
        source = FoodSource.UsdaFndds,
        name = "Greek yogurt, plain",
        per100g = NutrientProfile(kcal = 59.0, protein = 10.0, carbs = 3.6, fat = 0.4),
        micros = mapOf("calcium_mg" to 110.0),
        portions = listOf(Portion("1 package", 170.0), Portion("1 cup", 245.0)),
    )
    val rice = Food(
        id = "rice",
        source = FoodSource.UsdaSr,
        name = "Jasmine rice, cooked",
        per100g = NutrientProfile(kcal = 130.0, protein = 2.7, carbs = 28.0, fat = 0.3, fiber = 0.4),
        portions = listOf(Portion("1 cup", 158.0)),
    )
    val friedRice = Food(
        id = "sinangag",
        source = FoodSource.UsdaFndds,
        name = "Garlic fried rice (sinangag)",
        per100g = NutrientProfile(kcal = 180.0, protein = 3.2, carbs = 30.0, fat = 5.0, fiber = 0.6),
        portions = listOf(Portion("1 cup", 160.0)),
    )
    val adobo = Food(
        id = "adobo",
        source = FoodSource.UsdaFndds,
        name = "Chicken adobo",
        per100g = NutrientProfile(kcal = 190.0, protein = 18.0, carbs = 3.0, fat = 12.0),
        portions = listOf(Portion("1 cup", 215.0)),
    )
    val oats = Food(
        id = "oats",
        source = FoodSource.UsdaSr,
        name = "Oats, cooked with water",
        per100g = NutrientProfile(kcal = 71.0, protein = 2.5, carbs = 12.0, fat = 1.5, fiber = 1.7),
        portions = listOf(Portion("1 cup", 234.0)),
    )
    val granola = CustomFood(id = "granola", name = "Lola's granola", servingAmount = 45.0, servingUnit = "g", kcal = 210.0, protein = 5.0, carbs = 28.0, fat = 9.0)

    fun item(food: Food, slot: MealSlot, day: LocalDate = today, minutesAgo: Int = 0): LogItem =
        food.toLogDraft(FoodAmount.Servings(1.0)).toItem(day, slot, minutesAgo)

    private fun LogDraft.toItem(day: LocalDate, slot: MealSlot, minutesAgo: Int): LogItem = LogItem(
        id = "${foodId ?: name}-$day-${slot.wire}-$minutesAgo",
        day = day,
        slot = slot,
        loggedAt = Instant.parse("2026-10-25T18:00:00Z") - minutesAgo.minutes,
        name = name,
        precision = precision,
        method = LogMethod.Search,
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
        micros = micros,
    )

    /** Today with breakfast and lunch logged, dinner to go. */
    fun day(date: LocalDate = today): Day = Day(
        date = date,
        itemsBySlot = MealSlot.entries.associateWith { slot ->
            when (slot) {
                MealSlot.Breakfast -> listOf(item(yogurt, slot, date, 600), item(banana, slot, date, 590))
                MealSlot.Lunch -> listOf(item(adobo, slot, date, 360), item(rice, slot, date, 359))
                else -> emptyList()
            }
        },
    )

    /** Yesterday, with a dinner to copy. */
    fun dayBefore(): Day {
        val date = today.minus(1, DateTimeUnit.DAY)
        return Day(
            date = date,
            itemsBySlot = MealSlot.entries.associateWith { slot ->
                if (slot == MealSlot.Dinner) listOf(item(adobo, slot, date, 1500), item(friedRice, slot, date, 1499)) else emptyList()
            },
        )
    }

    fun inputs(
        basket: List<BasketItem> = emptyList(),
        settings: Settings = Settings(),
        date: LocalDate = today,
        firstRun: Boolean = false,
    ): LoggerInputs = LoggerInputs(
        today = today,
        day = if (firstRun) Day.empty(date) else day(date),
        dayBefore = if (firstRun) Day.empty(date.minus(1, DateTimeUnit.DAY)) else dayBefore(),
        settings = settings,
        basket = basket,
        favorites = if (firstRun) emptyList() else listOf(banana, yogurt, rice, adobo, oats).map { item(it, MealSlot.Snack) },
        usualBySlot = if (firstRun) emptyMap() else mapOf(MealSlot.Dinner to listOf(item(adobo, MealSlot.Dinner), item(friedRice, MealSlot.Dinner))),
        recent = if (firstRun) emptyList() else listOf(banana, yogurt, adobo, rice, friedRice).map { item(it, MealSlot.Lunch) },
        customFoods = listOf(granola),
    )

    /** Banana and yogurt picked, the prototype's demo basket. */
    fun pickedBasket(): List<BasketItem> = listOf(banana, yogurt).map { food ->
        val ref = FoodRef.Catalog(food.id)
        BasketItem(ref.key, ref, food.toLogDraft(FoodAmount.Servings(1.0), method = LogMethod.Favorite), PickSource.Favorite, food)
    }

    fun search(query: String = "rice", offline: Boolean = false): SearchOutcome =
        SearchOutcome(query = query, foods = if (offline) listOf(rice) else listOf(rice, friedRice), offline = offline)

    fun loggerState(
        policy: VisibilityPolicy,
        basket: List<BasketItem> = emptyList(),
        query: String = "",
        search: SearchOutcome? = null,
        settings: Settings = Settings(),
        date: LocalDate = today,
        firstRun: Boolean = false,
    ): LoggerState = LoggerStateBuilder.build(
        inputs = inputs(basket = basket, settings = settings, date = date, firstRun = firstRun),
        policy = policy,
        slot = MealSlot.Dinner,
        query = query,
        search = search,
    )

    fun amountState(policy: VisibilityPolicy, settings: Settings = Settings()): AmountState {
        val food = AmountFood.Catalog(banana)
        val ref = FoodRef.Catalog(banana.id)
        return AmountStateBuilder.build(food, AmountChoice.initial(food).copy(quantity = 1.5), settings, policy, ref, PickSource.Search)
    }

    val parsed: List<ParsedItem> = listOf(
        ParsedItem(text = "2 eggs", name = "Egg, scrambled", quantity = 2.0, amountText = "2 large", confidence = Confidence.High, foodId = "egg", grams = 122.0, kcal = 182.0, protein = 12.0, carbs = 2.0, fat = 13.0),
        ParsedItem(text = "toast", name = "Toast, white", quantity = 1.0, amountText = "1 slice", confidence = Confidence.Medium, foodId = "toast", grams = 28.0, kcal = 82.0, protein = 2.6, carbs = 15.0, fat = 1.1),
    )

    val savedMeals: List<SavedMeal> = listOf(
        SavedMeal(id = "sunday", name = "Sunday breakfast", usualSlot = MealSlot.Breakfast, items = listOf(yogurt, banana).map { it.toLogDraft(FoodAmount.Servings(1.0)) }),
        SavedMeal(id = "rice-bowl", name = "Adobo rice bowl", usualSlot = MealSlot.Dinner, items = listOf(adobo, rice).map { it.toLogDraft(FoodAmount.Servings(1.0)) }),
    )

    fun logInputs(settings: Settings = Settings()): LogInputs =
        LogInputs(today = today, day = day(), dayBefore = dayBefore(), settings = settings, savedMeals = savedMeals)

    fun logState(policy: VisibilityPolicy, tab: LogTab, parsed: Boolean = true): LogState = LogStateBuilder.build(
        inputs = logInputs(),
        policy = policy,
        slot = MealSlot.Dinner,
        tab = tab,
        text = "2 eggs and toast",
        describe = if (parsed) DescribeModel.Parsed(this.parsed) else DescribeModel.Idle,
        number = "",
        focusNumber = false,
    )
}
