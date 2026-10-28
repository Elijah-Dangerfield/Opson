package com.opson.features.logger.impl

import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.FoodUnit
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.MealSlotPicker
import com.opson.libraries.diary.SavedMeal
import com.opson.libraries.diary.SearchDatabase
import com.opson.libraries.diary.SearchSort
import com.opson.libraries.diary.Settings
import com.opson.libraries.foods.AmountUnit
import com.opson.libraries.foods.Confidence
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodAmount
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.FoodSource
import com.opson.libraries.foods.ReviewStatus
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.foods.FoodTier
import com.opson.libraries.foods.NutrientProfile
import com.opson.libraries.foods.ParsedItem
import com.opson.libraries.foods.Portion
import com.opson.libraries.foods.SearchSource
import com.opson.libraries.foods.toLogDraft
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.scanner.CameraPermission
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.minus
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Realistic logger inputs for previews and `CaloriesHiddenLeakTest`, from design
 * v3's logger screens (3.1 to 3.10). Real calorie values on purpose: Full must
 * have something to show, or the leak test's control proves nothing. Never
 * builds a `Display`; states come from the real builders.
 *
 * The catalog has no cuisine field yet, so the regional dishes carry theirs as
 * the brand, which is what the row's subtitle shows today.
 */
object LoggerFixtures {
    val today: LocalDate = LocalDate(2026, 10, 25)

    /** A catalog food whose first portion has exactly these numbers, [micros] included (wire keys). */
    @Suppress("LongParameterList")
    fun food(
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
        micros: Map<String, Double> = emptyMap(),
    ): Food {
        val per100 = 100.0 / grams
        return Food(
            id = id,
            source = FoodSource.UsdaFndds,
            name = name,
            per100g = NutrientProfile(kcal = kcal * per100, protein = protein * per100, carbs = carbs * per100, fat = fat * per100),
            brand = brand,
            portions = listOf(Portion(portion, grams)) + more,
            micros = micros.mapValues { (_, amount) -> amount * per100 },
        )
    }

    /** One plate's vitamins and minerals, roughly FNDDS's: most of the table, as a catalog dish has. */
    private val arrozMicros = mapOf(
        "vitamin_a_rae_ug" to 60.0, "thiamin_mg" to 0.35, "riboflavin_mg" to 0.25, "niacin_mg" to 9.0, "vitamin_b6_mg" to 0.6,
        "folate_dfe_ug" to 120.0, "vitamin_b12_ug" to 0.4, "vitamin_c_mg" to 12.0, "vitamin_e_mg" to 1.2, "vitamin_k_ug" to 15.0,
        "choline_mg" to 110.0, "calcium_mg" to 60.0, "copper_mg" to 0.2, "iron_mg" to 3.0, "magnesium_mg" to 70.0,
        "manganese_mg" to 0.6, "phosphorus_mg" to 330.0, "potassium_mg" to 600.0, "selenium_ug" to 35.0, "sodium_mg" to 980.0,
        "zinc_mg" to 2.8, "sugars_g" to 3.0, "saturated_fat_g" to 4.5,
    )

    val arroz = food(
        "arroz", "Arroz con pollo", "1 plate", 350.0, 525.0, 32.0, 60.0, 18.0, "Latin American", listOf(Portion("1 cup", 200.0)),
        micros = arrozMicros,
    )
    val sinangag = food("sinangag", "Garlic fried rice (sinangag)", "1 cup", 160.0, 288.0, 5.0, 48.0, 8.0, "Filipino")
    val salad = food("salad", "Side salad", "1 bowl", 120.0, 60.0, 2.0, 8.0, 3.0)
    val banana = food(
        "banana", "Banana", "1 medium", 118.0, 105.0, 1.3, 27.0, 0.4,
        micros = mapOf("potassium_mg" to 422.0, "vitamin_c_mg" to 10.3, "vitamin_b6_mg" to 0.43, "magnesium_mg" to 32.0, "manganese_mg" to 0.32),
    )
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
        settings: Settings = Settings(),
        date: LocalDate = today,
        firstRun: Boolean = false,
    ): LoggerInputs = LoggerInputs(
        today = today,
        day = if (firstRun) Day.empty(date) else day(date),
        dayBefore = if (firstRun) Day.empty(date.minus(1, DateTimeUnit.DAY)) else dayBefore(),
        settings = settings,
        recent = if (firstRun) emptyList() else listOf(rice, arroz, yogurt, banana, poutine, salad).map { item(it, MealSlot.Lunch) },
        savedMeals = if (firstRun) emptyList() else savedMeals,
        customFoods = listOf(granola),
    )

    /**
     * The + sheet (13.4a, 13.4b) with every flag the design draws on: the barcode, the photo,
     * voice and fasting (with the timer on). [height] Full is the sheet dragged up, Recent showing.
     */
    @Suppress("LongParameterList")
    fun sheetState(
        policy: VisibilityPolicy,
        height: SheetHeight = SheetHeight.Partial,
        settings: Settings = Settings(fasting = true),
        date: LocalDate = today,
        firstRun: Boolean = false,
        flags: Boolean = true,
        slot: MealSlot = MealSlot.Dinner,
        added: Boolean = false,
        exercise: Boolean = false,
        closed: Boolean = false,
    ): LoggerState {
        val inputs = inputs(settings = settings, date = date, firstRun = firstRun).let { if (closed) it.copy(day = it.day.closed()) else it }
        // Jasmine rice went in from Recent's + since the sheet opened: its row draws ✓.
        val logged = if (added) inputs.copy(day = inputs.day.with(item(rice, slot, date, minutesAgo = 1))) else inputs
        return LoggerStateBuilder.build(
            inputs = logged,
            policy = policy,
            choices = LoggerChoices(
                slot = slot,
                height = height,
                scanAvailable = flags,
                photoAvailable = flags,
                packAvailable = flags,
                voiceEnabled = flags,
                fastingReleased = flags,
                exerciseReleased = exercise,
                seen = inputs.day.allItems.mapTo(HashSet()) { it.id },
            ),
        )
    }

    /** [this] with [item] logged to its slot. */
    private fun Day.with(item: LogItem): Day = copy(itemsBySlot = itemsBySlot + (item.slot to items(item.slot) + item))

    /** [this] closed with Close the day at 9 pm. */
    private fun Day.closed(): Day = copy(closedAt = LocalDateTime(date, LocalTime(21, 0)).toInstant(TimeZone.UTC))

    /** 10:40 on [today]: a time this person has no regular meal at (3.1b). */
    val midMorning: LocalDateTime = LocalDateTime(today, LocalTime(10, 40))

    /**
     * Five days with breakfast at 8 and dinner at 7, and the foods from "Usual at dinner" also
     * eaten mid-morning, unfiled. Nothing near 10:40 is a meal, so the meal picks Uncategorized.
     */
    fun midMorningHistory(): List<Day> = (1..5).map { n ->
        val date = today.minus(n, DateTimeUnit.DAY)
        fun at(food: Food, slot: MealSlot, hour: Int, minute: Int) =
            food.toLogDraft(FoodAmount.Servings(1.0)).toItem(date, slot, 0).copy(
                id = "${food.id}-$date-$hour",
                loggedAt = LocalDateTime(date, LocalTime(hour, minute)).toInstant(TimeZone.UTC),
            )
        Day(
            date = date,
            itemsBySlot = MealSlot.entries.associateWith { slot ->
                when (slot) {
                    MealSlot.Breakfast -> listOf(at(yogurt, slot, 8, 0))
                    MealSlot.Uncategorized -> listOf(at(arroz, slot, 10, 32), at(sinangag, slot, 10, 31), at(salad, slot, 10, 30))
                    MealSlot.Dinner -> listOf(at(banana, slot, 19, 0))
                    else -> emptyList()
                }
            },
        )
    }

    /** The + sheet at 10:40, the meal picked from this history: Uncategorized. */
    fun uncategorizedState(policy: VisibilityPolicy): LoggerState {
        val inputs = inputs().copy(recentDays = midMorningHistory(), now = midMorning, timeZone = TimeZone.UTC)
        val slot = MealSlotPicker.pick(inputs.recentDays, inputs.now, inputs.timeZone)
        return LoggerStateBuilder.build(inputs = inputs, policy = policy, choices = LoggerChoices(slot = slot))
    }

    // ── Search (13.4c) ──────────────────────────────────────────────────────

    /** What search reads: rice is this person's usual cup, so its row says so. */
    fun searchInputs(settings: Settings = Settings(), date: LocalDate = today, firstRun: Boolean = false): SearchInputs = SearchInputs(
        today = today,
        day = if (firstRun) Day.empty(date) else day(date),
        settings = settings,
        usualBySlot = if (firstRun) emptyMap() else mapOf(MealSlot.Dinner to listOf(arroz, sinangag, salad).map { item(it, MealSlot.Dinner) }),
        recent = if (firstRun) emptyList() else listOf(banana, yogurt, poutine).map { item(it, MealSlot.Lunch) },
        customFoods = listOf(granola),
        savedMeals = if (firstRun) emptyList() else savedMeals,
        history = if (firstRun) emptyList() else listOf(rice, banana, yogurt, arroz).mapIndexed { i, food -> item(food, MealSlot.Dinner, date.minus(1, DateTimeUnit.DAY), minutesAgo = i) },
        frequent = if (firstRun) emptyList() else listOf(rice, banana).map { item(it, MealSlot.Dinner, date.minus(1, DateTimeUnit.DAY)) },
    )

    fun search(query: String = "rice", offline: Boolean = false): SearchOutcome = SearchOutcome(
        query = query,
        foods = if (offline) listOf(rice) else listOf(rice, brownRice, riceNoodles, friedRice, riceCakes, pudding),
        offline = offline,
    )

    /** A term the catalog has nothing for, as in 3.6. */
    const val MISSING_TERM: String = "kare-kare"

    fun emptySearch(): SearchOutcome = SearchOutcome(query = MISSING_TERM, foods = emptyList(), offline = false)

    /**
     * Search for [query] with the catalog's [search] answer, as 13.4c draws it: [added] logs
     * jasmine rice from its + since search opened, so its row draws ✓.
     */
    @Suppress("LongParameterList")
    fun searchState(
        policy: VisibilityPolicy,
        query: String = "rice",
        search: SearchOutcome? = if (query.isBlank()) null else search(query),
        scope: SearchScope = SearchScope.All,
        added: Boolean = true,
        pickingForMeal: Boolean = false,
        flags: Boolean = true,
        firstRun: Boolean = false,
        settings: Settings = Settings(),
        closed: Boolean = false,
        reviews: Map<String, ReviewStatus> = emptyMap(),
    ): FoodSearchState {
        val inputs = searchInputs(settings = settings, firstRun = firstRun)
            .let { if (closed) it.copy(day = it.day.closed()) else it }
            .copy(reviews = reviews)
        val logged = if (added && !pickingForMeal) inputs.copy(day = inputs.day.with(item(rice, MealSlot.Dinner, minutesAgo = 1))) else inputs
        val choices = SearchChoices(
            slot = MealSlot.Dinner,
            query = query,
            search = search,
            scope = scope,
            scanAvailable = flags,
            addFoodAvailable = flags,
            pickingForMeal = pickingForMeal,
            seen = inputs.day.allItems.mapTo(HashSet()) { it.id },
        )
        val picked = if (pickingForMeal) FoodSearchStateBuilder.picks(logged, choices).take(2).map { it.toLogPick() } else emptyList()
        return FoodSearchStateBuilder.build(logged, policy, choices.copy(picked = picked))
    }

    fun searchEmptyState(policy: VisibilityPolicy): FoodSearchState =
        searchState(policy, query = MISSING_TERM, search = emptySearch(), added = false)

    /**
     * "granola" with the person's own granola sent to Opson's food database and not checked yet:
     * its row reads "Unverified", and "Can't find it?" offers the pack's label under it.
     */
    fun searchUnverifiedState(policy: VisibilityPolicy): FoodSearchState = searchState(
        policy,
        query = "granola",
        search = SearchOutcome(query = "granola", foods = emptyList(), offline = false),
        added = false,
        reviews = mapOf(granola.id to ReviewStatus.Pending),
    )

    // More rice for 13.4c.
    val brownRice = food("brown-rice", "Brown rice, cooked", "1 cup", 195.0, 215.0, 5.0, 45.0, 1.8)
    val riceNoodles = food("rice-noodles", "Rice noodles, cooked", "1 cup", 176.0, 190.0, 3.2, 42.0, 0.4)
    val friedRice = food("fried-rice", "Fried rice, takeout", "1 cup", 198.0, 280.0, 6.0, 42.0, 9.0)
    val riceCakes = food("rice-cakes", "Rice cakes, plain", "1 cake", 9.0, 35.0, 0.7, 7.3, 0.3)

    // Describe's foods, with the measures and the alternatives the server sends with them.
    val eggLarge = food("egg", "Egg, large", "1 large", 50.0, 78.0, 6.3, 0.6, 5.3, more = listOf(Portion("1 cup", 135.0)))
    val eggScrambled = food("egg-scrambled", "Egg, scrambled", "1 large", 61.0, 91.0, 6.1, 1.0, 6.7)
    val eggFried = food("egg-fried", "Egg, fried", "1 large", 46.0, 90.0, 6.3, 0.4, 6.8)
    val toastWhite = food("toast", "Toast, white", "1 slice", 28.0, 80.0, 2.6, 15.0, 1.1, more = listOf(Portion("1 thick slice", 39.0)))
    val toastWheat = food("toast-wheat", "Toast, whole wheat", "1 slice", 33.0, 90.0, 4.4, 15.0, 1.4)
    val toastSourdough = food("toast-sourdough", "Toast, sourdough", "1 slice", 28.0, 76.0, 3.0, 14.0, 0.7)

    val parsed: List<ParsedItem> = listOf(
        ParsedItem(
            text = "2 eggs",
            name = "Egg, large",
            quantity = 2.0,
            amountText = "2 large",
            confidence = Confidence.High,
            foodId = "egg",
            portionLabel = "1 large",
            grams = 100.0,
            kcal = 156.0,
            protein = 12.6,
            carbs = 1.1,
            fat = 10.6,
            food = eggLarge,
            alternatives = listOf(eggScrambled, eggFried),
        ),
        ParsedItem(
            text = "toast",
            name = "Toast, white",
            quantity = 1.0,
            amountText = "1 slice",
            confidence = Confidence.Medium,
            foodId = "toast",
            portionLabel = "1 slice",
            grams = 28.0,
            kcal = 80.0,
            protein = 2.6,
            carbs = 15.0,
            fat = 1.1,
            food = toastWhite,
            alternatives = listOf(toastWheat, toastSourdough),
        ),
    )

    /** A phrase the catalog had nothing for: no food, no amount controls, a tap finds it. */
    val unmatched = ParsedItem(text = "grandma's kugel", name = "grandma's kugel", quantity = 1.0, amountText = "1", confidence = Confidence.Low)

    /** "2 eggs and toast" read into lines, as [LoggerViewModel] reads it, under [policy]. */
    fun describeReview(policy: VisibilityPolicy, items: List<ParsedItem> = parsed): DescribeReview =
        DescribeRows.read(DescribeReview(), items, householdOnly = policy.level(Metric.Calories) == Visibility.Hide)

    /** Describe it for breakfast, "2 eggs and toast" read into two lines, as in 3.8. */
    fun describeState(
        policy: VisibilityPolicy,
        parsedLines: Boolean = true,
        review: DescribeReview = if (parsedLines) describeReview(policy) else DescribeReview(),
        text: String = if (parsedLines) "2 eggs and toast" else "",
        quickAdd: QuickAddForm = QuickAddForm(),
    ): LoggerState = LoggerStateBuilder.build(
        inputs = inputs(),
        policy = policy,
        choices = LoggerChoices(
            slot = MealSlot.Breakfast,
            pane = LoggerPane.Describe,
            height = SheetHeight.Full,
            describeText = text,
            describeStatus = if (review.rows.isNotEmpty()) DescribeStatus.Parsed else DescribeStatus.Idle,
            review = review,
            quickAdd = quickAdd,
        ),
    )

    /**
     * A protein bar's label copied into quick add. Its macros come to 500 kcal at 4/4/9 and the
     * energy typed is 400, so All numbers spells out the gap.
     */
    val proteinBar = QuickAddForm(
        name = "Protein bar",
        numbers = mapOf(Metric.Calories to "400", Metric.Protein to "30", Metric.Carbs to "50", Metric.Fat to "20", Metric.Fiber to "4"),
    )

    /** Describe with nothing typed in the box and [proteinBar] in "Or just the numbers". */
    fun quickAddState(policy: VisibilityPolicy, form: QuickAddForm = proteinBar): LoggerState =
        describeState(policy, parsedLines = false, quickAdd = form)

    /**
     * After fixing the read: the eggs swapped to scrambled and stepped to three,
     * toast taken out, a coffee added from search, and a line the catalog
     * didn't know.
     */
    fun describeEditedState(policy: VisibilityPolicy): LoggerState {
        val householdOnly = policy.level(Metric.Calories) == Visibility.Hide
        val read = describeReview(policy, parsed + unmatched)
        val eggs = read.rows.first()
        val edited = read.copy(
            rows = listOf(DescribeRows.more(DescribeRows.swapped(eggs, eggScrambled, householdOnly))) +
                read.rows.drop(2) +
                DescribeRows.added("describe:9", "coffee", coffee, householdOnly),
            dismissed = setOf("toast"),
        )
        return describeState(policy, review = edited, text = "2 eggs and toast and grandma's kugel")
    }

    /** Looking for something the read missed: "coffee" typed, the catalog's answer under it. */
    fun describeFindState(policy: VisibilityPolicy): LoggerState {
        val review = describeReview(policy).copy(find = DescribeFind(replacing = null, query = "coffee", answer = SwapAnswer("coffee", listOf(coffee, latte))))
        return describeState(policy, review = review)
    }

    val coffee = food("coffee", "Coffee, brewed", "1 cup (8 fl oz)", 240.0, 2.0, 0.3, 0.0, 0.0)
    val latte = food("latte", "Coffee, latte", "1 medium", 480.0, 206.0, 13.0, 20.0, 8.0)


    fun savedState(policy: VisibilityPolicy): LoggerState = LoggerStateBuilder.build(
        inputs = inputs(),
        policy = policy,
        choices = LoggerChoices(slot = MealSlot.Dinner, pane = LoggerPane.Saved, height = SheetHeight.Full),
    )

    // ── Food detail (13.4d) ─────────────────────────────────────────────────

    /** 6:12 pm on [today], when the detail fixtures are opened. */
    val evening: LocalTime = LocalTime(18, 12)

    /** Peanut butter as 13.4d draws it: a tablespoon is 16 g, and the label's numbers per 32 g. */
    val peanutButter = food(
        "peanut-butter", "Peanut butter", "1 tbsp", 16.0, 95.0, 4.0, 3.5, 8.0,
        micros = mapOf("vitamin_e_mg" to 1.4, "magnesium_mg" to 27.0, "potassium_mg" to 104.0, "niacin_mg" to 2.1, "sodium_mg" to 73.0),
    )

    /** Sizes this person saved for arroz con pollo, so the unit menu has more than the catalog's. */
    val arrozSizes: Settings = Settings()
        .withUnit("f:arroz", FoodUnit("serving", 300.0))
        .withUnit("f:arroz", FoodUnit("oz", 28.0))
        .toggleFavorite("f:arroz")

    fun detailInputs(policy: VisibilityPolicy, settings: Settings = Settings(), inRecents: Boolean = true): DetailInputs =
        DetailInputs(settings = settings, policy = policy, day = day(), today = today, now = evening, inRecents = inRecents)

    /**
     * Peanut butter, 32 typed into the field in grams, which made it weighed.
     * [nutrientsOpen]: All nutrients tapped open.
     */
    fun detailState(policy: VisibilityPolicy, nutrientsOpen: Boolean = false): FoodDetailState {
        val food = AmountFood.Catalog(peanutButter)
        val ref = FoodRef.Catalog(peanutButter.id)
        val choice = DetailChoice.initial(food, MealSlot.Dinner)
            .copy(unit = AmountUnit.Grams, nutrientsOpen = nutrientsOpen)
            .typed("32")
        return FoodDetailStateBuilder.build(food, choice, detailInputs(policy), ref, PickSource.Search)
    }

    /** Peanut butter as it opens for someone who weighs themselves in pounds: two tablespoons, ounces before grams in the menu. */
    fun detailPoundsState(policy: VisibilityPolicy): FoodDetailState {
        val food = AmountFood.Catalog(peanutButter)
        val settings = Settings(weightUnit = WeightUnit.Lb)
        val choice = DetailChoice.initial(food, MealSlot.Snack, prefersOunces = true).copy(quantity = 2.0)
        return FoodDetailStateBuilder.build(food, choice, detailInputs(policy, settings, inRecents = false), FoodRef.Catalog(peanutButter.id), PickSource.Search)
    }

    /** Arroz con pollo as it opens, one plate, with the person's own sizes in the unit menu. */
    fun detailUnitsState(policy: VisibilityPolicy): FoodDetailState {
        val food = AmountFood.Catalog(arroz)
        val ref = FoodRef.Catalog(arroz.id)
        val choice = DetailChoice.initial(food, MealSlot.Dinner, arrozSizes.unitsFor(ref.key))
        return FoodDetailStateBuilder.build(food, choice, detailInputs(policy, arrozSizes), ref, PickSource.Usual)
    }

    /** Oat milk from a carton that states its volume, so the menu offers ml and fl oz. */
    val oatMilk = food(
        "oat-milk", "Oat milk", "1 cup (240 ml)", 240.0, 50.0, 0.4, 6.6, 2.1,
        micros = mapOf("calcium_mg" to 120.0, "vitamin_d_ug" to 1.0, "potassium_mg" to 160.0, "sugars_g" to 3.3),
    ).copy(source = FoodSource.OpenFoodFacts, brand = "Oatly")

    /** Oat milk at 250 ml, typed, so it is weighed. */
    fun liquidDetailState(policy: VisibilityPolicy): FoodDetailState {
        val food = AmountFood.Catalog(oatMilk)
        val ref = FoodRef.Catalog(oatMilk.id)
        val ml = food.units(emptyList()).first { it.label == "ml" }
        val choice = DetailChoice.initial(food, MealSlot.Breakfast).copy(unit = ml).typed("250")
        return FoodDetailStateBuilder.build(food, choice, detailInputs(policy), ref, PickSource.Search)
    }

    /** A creamer the person made, counted in tablespoons and never weighed: no grams to offer yet. */
    val creamer = CustomFood(id = "creamer", name = "Hazelnut creamer", servingAmount = 1.0, servingUnit = "tbsp", kcal = 35.0, carbs = 5.0, fat = 1.5)

    /** The person's own granola, sent to Opson's food database and waiting for review. */
    fun inReviewDetailState(policy: VisibilityPolicy): FoodDetailState {
        val food = AmountFood.Custom(granola)
        val choice = DetailChoice.initial(food, MealSlot.Breakfast)
        val inputs = detailInputs(policy).copy(review = ReviewStatus.Pending)
        return FoodDetailStateBuilder.build(food, choice, inputs, FoodRef.Custom(granola.id), PickSource.Search)
    }

    fun creamerDetailState(policy: VisibilityPolicy): FoodDetailState {
        val food = AmountFood.Custom(creamer)
        val choice = DetailChoice.initial(food, MealSlot.Breakfast).copy(quantity = 2.0)
        return FoodDetailStateBuilder.build(food, choice, detailInputs(policy), FoodRef.Custom(creamer.id), PickSource.Recent)
    }

    /**
     * No numbers, peanut butter, two tablespoons typed for a snack and "A little" picked,
     * the hunger questions on.
     */
    fun noNumbersDetailState(policy: VisibilityPolicy): FoodDetailState {
        val food = AmountFood.Catalog(peanutButter)
        val choice = DetailChoice.initial(food, MealSlot.Snack).typed("2").copy(hunger = Hunger.Neutral)
        return FoodDetailStateBuilder.build(food, choice, detailInputs(policy, Settings(askTags = true)), FoodRef.Catalog(peanutButter.id), PickSource.Search)
    }

    /** The Custom size dialog open, "my bowl" half typed. */
    fun customSizeState(policy: VisibilityPolicy): FoodDetailState =
        detailUnitsState(policy).copy(customSize = CustomSizeUi(name = "my bowl", grams = "420"))


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
        // A label's: what a US Nutrition Facts panel lists.
        micros = mapOf("calcium_mg" to 150.0, "potassium_mg" to 200.0, "vitamin_d_ug" to 0.0, "iron_mg" to 0.0, "sodium_mg" to 55.0),
    ).copy(source = FoodSource.UsdaBranded, barcode = "00036632027627", tier = FoodTier.Label, verified = true)


    /** The code the scan fixtures read and missed. */
    const val MISSED_CODE = "4006381333931"

    /** The detail a hit opens, as it opens: the label's serving, nothing chosen yet. */
    fun scannedDetailState(policy: VisibilityPolicy): FoodDetailState {
        val food = AmountFood.Catalog(scannedYogurt)
        val ref = FoodRef.Catalog(scannedYogurt.id)
        return FoodDetailStateBuilder.build(food, DetailChoice.initial(food, MealSlot.Dinner), detailInputs(policy), ref, PickSource.Barcode)
    }

    fun scanState(model: ScanModel): BarcodeScanState = BarcodeScanStateBuilder.build(MealSlot.Dinner, model)

    /** Almost every phone's back camera has a torch, so the button shows. */
    val scanning: ScanModel = ScanModel(permission = CameraPermission.Granted, hasTorch = true)
    val scanningLit: ScanModel = scanning.copy(torchOn = true)
    val lookingUp: ScanModel = scanning.copy(lookup = Lookup.Running(MISSED_CODE))
    val lookingUpSlow: ScanModel = scanning.copy(lookup = Lookup.Running(MISSED_CODE, slow = true))
    val missed: ScanModel = scanning.copy(lookup = Lookup.Missed(MISSED_CODE))
    val missedNamed: ScanModel = missed.copy(name = "Rye crispbread")

    /** With `release.submitFood`: Add this food comes first. */
    val missedAddFood: ScanModel = missed.copy(canAddFood = true)
    val unavailable: ScanModel = scanning.copy(lookup = Lookup.Failed(MISSED_CODE))
    val cameraOff: ScanModel = ScanModel(permission = CameraPermission.Denied(canAskAgain = false))
    val cameraAskAgain: ScanModel = ScanModel(permission = CameraPermission.Denied(canAskAgain = true))
    val cameraUnavailable: ScanModel = scanning.copy(cameraUnavailable = true)
    val typing: ScanModel = cameraUnavailable.copy(typed = "40063813")
    val typingMisread: ScanModel = cameraUnavailable.copy(typed = "4006381333932", typedMisread = true)

    // ── Search options (release.searchOptions) ──────────────────────────────

    /** "rice" across every source a badge names: USDA generic, a label, Open Food Facts, and your own. */
    val usdaRice: Food = rice.copy(tier = FoodTier.Reference, verified = true)
    val brandedRice: Food = food("rice-branded", "Jasmine rice, ready to heat", "1 cup", 140.0, 210.0, 4.0, 46.0, 1.0, "Seeds of Change")
        .copy(source = FoodSource.UsdaBranded, tier = FoodTier.Label, verified = true)
    val offRice: Food = food("rice-off", "Riz basmati", "1 portion (125 g)", 125.0, 180.0, 4.0, 38.0, 1.0, "Taureau Ailé")
        .copy(source = FoodSource.OpenFoodFacts, tier = FoodTier.Community, verified = false)
    val riceBowl = CustomFood(
        id = "rice-bowl",
        name = "Lola's rice bowl",
        servingAmount = 1.0,
        servingUnit = "bowl",
        kcal = 480.0,
        protein = 22.0,
        carbs = 60.0,
        fat = 14.0,
    )

    /** Everything the options read: favorites, a longer history, and the catalog foods the device knows. */
    fun optionsInputs(settings: Settings = Settings()): SearchInputs = searchInputs(settings = settings).copy(
        customFoods = listOf(granola, riceBowl),
        frequent = listOf(arroz, banana, yogurt).map { item(it, MealSlot.Dinner) },
        history = listOf(sinangag, banana, arroz, yogurt, kabsa).mapIndexed { i, food -> item(food, MealSlot.Lunch, minutesAgo = i * 60) },
        known = listOf(arroz, banana, yogurt, sinangag, kabsa).associateBy { it.id },
    )

    /**
     * Search with `release.searchOptions` on: tabs, the options button and a badge on every row.
     * The catalog's answer is for exactly the filter these choices ask for.
     */
    fun optionsState(
        policy: VisibilityPolicy,
        tab: SearchTab = SearchTab.All,
        query: String = "rice",
        settings: Settings = Settings(),
        openFoodFacts: Boolean = true,
    ): FoodSearchState {
        val inputs = optionsInputs(settings)
        val choices = SearchChoices(
            slot = MealSlot.Dinner,
            query = query,
            searchOptions = true,
            tab = tab,
            openFoodFacts = openFoodFacts,
        )
        val filter = FoodSearchStateBuilder.catalogFilter(inputs, choices)
        val foods = when {
            filter?.source == SearchSource.Usda || tab == SearchTab.Common -> listOf(usdaRice, brandedRice, pudding)
            filter?.source == SearchSource.OpenFoodFacts -> listOf(offRice)
            tab == SearchTab.Restaurant || tab == SearchTab.Beverages -> emptyList()
            else -> listOf(usdaRice, brandedRice, offRice, pudding)
        }
        val search = filter?.let { SearchOutcome(query = query.trim(), foods = foods, offline = false, filter = it) }
        return FoodSearchStateBuilder.build(inputs, policy, choices.copy(search = search))
    }

    /** The options sheet with a sort and a database picked, Open Food Facts offered. */
    fun searchOptionsSheetState(): SearchOptionsState = SearchOptionsState.of(
        Settings(searchSort = SearchSort.MostRecent, searchDatabase = SearchDatabase.Usda),
        openFoodFacts = true,
    )
}
