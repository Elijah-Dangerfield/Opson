package com.opson.features.logger.impl

import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.Fullness
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Mood
import com.opson.libraries.diary.SavedMeal
import com.opson.libraries.diary.Settings
import com.opson.libraries.foods.Confidence
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodAmount
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.FoodSource
import com.opson.libraries.foods.FoodTier
import com.opson.libraries.foods.NutrientProfile
import com.opson.libraries.foods.ParsedItem
import com.opson.libraries.foods.Portion
import com.opson.libraries.foods.toLogDraft
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.scanner.CameraPermission
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Realistic logger inputs for previews and `BlindModeLeakTest`, from design
 * v3's logger screens (3.1 to 3.10). Real calorie values on purpose: Full must
 * have something to show, or the leak test's control proves nothing. Never
 * builds a `Display`; states come from the real builders.
 *
 * The catalog has no cuisine field yet, so the regional dishes carry theirs as
 * the brand, which is what the row's subtitle shows today.
 */
object LoggerFixtures {
    val today: LocalDate = LocalDate(2026, 10, 25)

    /** A catalog food whose first portion has exactly these numbers. */
    @Suppress("LongParameterList")
    private fun food(
        id: String,
        name: String,
        portion: String,
        grams: Double,
        kcal: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
        brand: String? = null,
        more: List<Portion> = emptyList(),
    ): Food {
        val per100 = 100.0 / grams
        return Food(
            id = id,
            source = FoodSource.UsdaFndds,
            name = name,
            per100g = NutrientProfile(kcal = kcal * per100, protein = protein * per100, carbs = carbs * per100, fat = fat * per100),
            brand = brand,
            portions = listOf(Portion(portion, grams)) + more,
        )
    }

    val arroz = food("arroz", "Arroz con pollo", "1 plate", 350.0, 525.0, 32.0, 60.0, 18.0, "Latin American", listOf(Portion("1 cup", 200.0)))
    val sinangag = food("sinangag", "Garlic fried rice (sinangag)", "1 cup", 160.0, 288.0, 5.0, 48.0, 8.0, "Filipino")
    val salad = food("salad", "Side salad", "1 bowl", 120.0, 60.0, 2.0, 8.0, 3.0)
    val banana = food("banana", "Banana", "1 medium", 118.0, 105.0, 1.3, 27.0, 0.4)
    val yogurt = food("yogurt", "Greek yogurt, plain", "1 package", 170.0, 100.0, 17.0, 6.0, 0.7)
    val poutine = food("poutine", "Poutine", "Small box", 300.0, 690.0, 18.0, 72.0, 36.0)
    val rice = food("rice", "Jasmine rice, cooked", "1 cup", 158.0, 205.0, 4.3, 44.0, 0.4)
    val pudding = food("pudding", "Rice pudding", "1 package", 113.0, 133.0, 4.0, 23.0, 3.0)
    val mango = food("mango", "Mango sticky rice", "1 plate", 250.0, 440.0, 6.0, 84.0, 11.0, "Thai")
    val kabsa = food("kabsa", "Chicken kabsa", "1 plate", 400.0, 627.0, 38.0, 76.0, 19.0, "Saudi")
    val granola = CustomFood(
        id = "granola",
        name = "Lola's granola",
        servingAmount = 45.0,
        servingUnit = "g",
        kcal = 210.0,
        protein = 5.0,
        carbs = 28.0,
        fat = 9.0,
    )

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
                MealSlot.Lunch -> listOf(item(kabsa, slot, date, 360))
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
                if (slot == MealSlot.Dinner) listOf(item(arroz, slot, date, 1500), item(salad, slot, date, 1499)) else emptyList()
            },
        )
    }

    val savedMeals: List<SavedMeal> = listOf(
        SavedMeal(
            id = "sunday",
            name = "Sunday breakfast",
            usualSlot = MealSlot.Breakfast,
            items = listOf(yogurt, banana).map { it.toLogDraft(FoodAmount.Servings(1.0)) },
        ),
        SavedMeal(
            id = "rice-plate",
            name = "Rice plate",
            usualSlot = MealSlot.Dinner,
            items = listOf(arroz, salad).map { it.toLogDraft(FoodAmount.Servings(1.0)) },
        ),
    )

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
        usualBySlot = if (firstRun) emptyMap() else mapOf(MealSlot.Dinner to listOf(arroz, sinangag, salad).map { item(it, MealSlot.Dinner) }),
        recent = if (firstRun) emptyList() else listOf(banana, yogurt, poutine).map { item(it, MealSlot.Lunch) },
        customFoods = listOf(granola),
        savedMeals = if (firstRun) emptyList() else savedMeals,
    )

    /** Arroz con pollo and the garlic rice picked from "Usual at dinner", as in 3.2. */
    fun pickedBasket(): List<BasketItem> = listOf(arroz, sinangag).map { food ->
        val ref = FoodRef.Catalog(food.id)
        BasketItem(ref.key, ref, food.toLogDraft(FoodAmount.Servings(1.0), method = LogMethod.Favorite), PickSource.Usual, food)
    }

    fun search(query: String = "rice", offline: Boolean = false): SearchOutcome = SearchOutcome(
        query = query,
        foods = if (offline) listOf(rice) else listOf(rice, sinangag, pudding, arroz, mango, kabsa),
        offline = offline,
    )

    /** A term the catalog has nothing for, as in 3.6. */
    const val MISSING_TERM: String = "kare-kare"

    fun emptySearch(): SearchOutcome = SearchOutcome(query = MISSING_TERM, foods = emptyList(), offline = false)

    @Suppress("LongParameterList")
    fun loggerState(
        policy: VisibilityPolicy,
        basket: List<BasketItem> = emptyList(),
        query: String = "",
        search: SearchOutcome? = null,
        settings: Settings = Settings(),
        date: LocalDate = today,
        firstRun: Boolean = false,
        pickingForMeal: Boolean = false,
        scanAvailable: Boolean = false,
    ): LoggerState = LoggerStateBuilder.build(
        inputs = inputs(basket = basket, settings = settings, date = date, firstRun = firstRun),
        policy = policy,
        choices = LoggerChoices(
            slot = MealSlot.Dinner,
            query = query,
            search = search,
            pickingForMeal = pickingForMeal,
            scanAvailable = scanAvailable,
        ),
    )

    fun searchState(policy: VisibilityPolicy): LoggerState = loggerState(policy, query = "rice", search = search())

    fun searchEmptyState(policy: VisibilityPolicy): LoggerState =
        loggerState(policy, query = MISSING_TERM, search = emptySearch())

    val parsed: List<ParsedItem> = listOf(
        ParsedItem(
            text = "2 eggs",
            name = "Egg, large",
            quantity = 2.0,
            amountText = "2",
            confidence = Confidence.High,
            foodId = "egg",
            grams = 100.0,
            kcal = 156.0,
            protein = 12.6,
            carbs = 1.1,
            fat = 10.6,
        ),
        ParsedItem(
            text = "toast",
            name = "Toast, white",
            quantity = 1.0,
            amountText = "1 slice",
            confidence = Confidence.Medium,
            foodId = "toast",
            grams = 28.0,
            kcal = 80.0,
            protein = 2.6,
            carbs = 15.0,
            fat = 1.1,
        ),
    )

    /** Describe it for breakfast, "2 eggs and toast" read into two lines, as in 3.8. */
    fun describeState(policy: VisibilityPolicy, parsedLines: Boolean = true): LoggerState = LoggerStateBuilder.build(
        inputs = inputs(basket = if (parsedLines) parsed.mapIndexed { index, item -> item.toDescribedItem(index) } else emptyList()),
        policy = policy,
        choices = LoggerChoices(
            slot = MealSlot.Breakfast,
            pane = LoggerPane.Describe,
            describeText = if (parsedLines) "2 eggs and toast" else "",
            describeStatus = if (parsedLines) DescribeStatus.Parsed else DescribeStatus.Idle,
        ),
    )

    fun savedState(policy: VisibilityPolicy): LoggerState = LoggerStateBuilder.build(
        inputs = inputs(),
        policy = policy,
        choices = LoggerChoices(slot = MealSlot.Dinner, pane = LoggerPane.Saved),
    )

    /** Arroz con pollo, one plate, an estimate, hungry before: 3.3. */
    fun amountState(policy: VisibilityPolicy, settings: Settings = Settings()): AmountState {
        val food = AmountFood.Catalog(arroz)
        val ref = FoodRef.Catalog(arroz.id)
        val choice = AmountChoice.initial(food).copy(hunger = Hunger.Hungry)
        return AmountStateBuilder.build(food, choice, settings, policy, ref, PickSource.Usual, MealSlot.Dinner)
    }

    /** A packaged food a scan found: a manufacturer's label, its serving first. */
    val scannedYogurt: Food = food(
        id = "scanned-yogurt",
        name = "Greek yogurt, vanilla",
        portion = "1 container (150 g)",
        grams = 150.0,
        kcal = 140.0,
        protein = 12.0,
        carbs = 16.0,
        fat = 3.5,
        brand = "Oikos",
    ).copy(source = FoodSource.UsdaBranded, barcode = "00036632027627", tier = FoodTier.Label, verified = true)

    /** The code the scan fixtures read and missed. */
    const val MISSED_CODE = "4006381333931"

    /** The amount sheet a hit opens, as it opens: the label's serving, nothing chosen yet. */
    fun scannedAmountState(policy: VisibilityPolicy): AmountState {
        val food = AmountFood.Catalog(scannedYogurt)
        val ref = FoodRef.Catalog(scannedYogurt.id)
        return AmountStateBuilder.build(food, AmountChoice.initial(food), Settings(), policy, ref, PickSource.Barcode, MealSlot.Dinner)
    }

    fun scanState(model: ScanModel): BarcodeScanState = BarcodeScanStateBuilder.build(MealSlot.Dinner, model)

    val scanning: ScanModel = ScanModel(permission = CameraPermission.Granted)
    val lookingUpSlow: ScanModel = scanning.copy(lookup = Lookup.Running(MISSED_CODE, slow = true))
    val missed: ScanModel = scanning.copy(lookup = Lookup.Missed(MISSED_CODE))
    val missedNamed: ScanModel = missed.copy(name = "Rye crispbread")
    val unavailable: ScanModel = scanning.copy(lookup = Lookup.Failed(MISSED_CODE))
    val cameraOff: ScanModel = ScanModel(permission = CameraPermission.Denied(canAskAgain = false))
    val cameraAskAgain: ScanModel = ScanModel(permission = CameraPermission.Denied(canAskAgain = true))

    /** After lunch in Mindful: very hungry before, full after, rushed, as in 3.10. */
    fun howItFeltState(): HowItFeltState = HowItFeltState(
        loading = false,
        slot = MealSlot.Lunch,
        hunger = Hunger.VeryHungry,
        fullness = Fullness.Full,
        moods = setOf(Mood.Rushed),
    )
}
