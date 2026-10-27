package com.opson.features.today.impl

import com.opson.features.today.impl.calendar.CalendarState
import com.opson.features.today.impl.cards.TodayCardsState
import com.opson.features.today.impl.cards.TodayCardsStateBuilder
import com.opson.features.today.impl.calendar.CalendarStateBuilder
import com.opson.features.today.impl.dayclose.DayCloseInputs
import com.opson.features.today.impl.dayclose.DayCloseState
import com.opson.features.today.impl.dayclose.DayCloseStateBuilder
import com.opson.features.today.impl.daynote.DayNoteState
import com.opson.features.today.impl.item.ItemSheetState
import com.opson.features.today.impl.item.ItemSheetStateBuilder
import com.opson.features.today.impl.item.ItemStep
import com.opson.features.today.impl.meal.MealFeelState
import com.opson.features.today.impl.meal.MealNoteState
import com.opson.features.today.impl.meal.MealSheetState
import com.opson.features.today.impl.meal.MealSheetStateBuilder
import com.opson.features.today.impl.water.WaterSheetState
import com.opson.features.today.impl.water.WaterSheetStateBuilder
import com.opson.libraries.diary.CheckIn
import com.opson.libraries.diary.CheckInMood
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayFeeling
import com.opson.libraries.diary.DayHunger
import com.opson.libraries.diary.Energy
import com.opson.libraries.diary.EventLevel
import com.opson.libraries.diary.Fullness
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.LoggedToMeal
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.MealTimes
import com.opson.libraries.diary.Mood
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.Sleep
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.nutrition.ExerciseAdjustment
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/**
 * Realistic Today inputs for previews and `BlindModeLeakTest`, from the
 * prototype's `MEALS`, `BIG_DINNER` and loose items. Real calorie values on
 * purpose: Full must have something to show, or the leak test's control
 * proves nothing. Never builds a `Display`; every state comes from the real
 * builders with the policy handed in.
 */
object TodayFixtures {
    /** Sunday, as the prototype's `TODAY`. Policies for these fixtures should read noon on this day. */
    val today: LocalDate = LocalDate(2026, 10, 25)

    val timeZone: TimeZone = TimeZone.UTC

    /** Noon on [today], before the 9 pm reveal. */
    val noon: Instant = LocalDateTime(today, LocalTime(12, 0)).toInstant(timeZone)

    /** 9:30 pm on [today], after the reveal. */
    val evening: Instant = LocalDateTime(today, LocalTime(21, 30)).toInstant(timeZone)

    @Suppress("LongParameterList")
    private fun item(
        id: String,
        date: LocalDate,
        slot: MealSlot,
        at: LocalTime,
        name: String,
        amount: String,
        precision: Precision,
        kcal: Double?,
        protein: Double? = null,
        carbs: Double? = null,
        fat: Double? = null,
        fiber: Double? = null,
        micros: Map<String, Double>? = null,
        portion: Portion? = null,
    ) = LogItem(
        id = "$id-$date",
        day = date,
        slot = slot,
        loggedAt = LocalDateTime(date, at).toInstant(timeZone),
        name = name,
        precision = precision,
        method = LogMethod.Search,
        amountText = amount,
        kcal = kcal,
        protein = protein,
        carbs = carbs,
        fat = fat,
        fiber = fiber,
        micros = micros,
        foodId = portion?.foodId,
        quantity = portion?.quantity,
        unit = portion?.unit,
        grams = portion?.grams,
    )

    /** What a food logged from the catalog carries besides its name: which food, how many of what, and grams. */
    private data class Portion(val foodId: String, val quantity: Double, val unit: String, val grams: Double?)

    private fun breakfast(date: LocalDate) = listOf(
        item("oats", date, MealSlot.Breakfast, LocalTime(8, 10), "Oat porridge with milk", "1 bowl", Precision.Weighed, 300.0, 10.0, 50.0, 6.0, 8.0,
            micros = mapOf("calcium_mg" to 230.0, "iron_mg" to 3.4, "magnesium_mg" to 110.0, "zinc_mg" to 2.6, "vitamin_b12_ug" to 1.1)),
        item("banana", date, MealSlot.Breakfast, LocalTime(8, 11), "Banana", "1 medium", Precision.Estimate, 105.0, 1.0, 27.0, 0.0, 3.0,
            micros = mapOf("potassium_mg" to 420.0, "vitamin_c_mg" to 10.0, "magnesium_mg" to 32.0)),
        item("coffee", date, MealSlot.Breakfast, LocalTime(8, 12), "Coffee with milk", "1 mug", Precision.Estimate, 40.0, 2.0, 3.0, 2.0, 0.0),
    )

    private fun lunch(date: LocalDate) = listOf(
        item("adobo", date, MealSlot.Lunch, LocalTime(12, 40), "Chicken adobo", "1 thigh with sauce", Precision.Estimate, 310.0, 26.0, 5.0, 20.0, 0.0,
            micros = mapOf("iron_mg" to 1.8, "zinc_mg" to 2.9, "vitamin_b12_ug" to 0.6, "sodium_mg" to 890.0)),
        item("rice", date, MealSlot.Lunch, LocalTime(12, 41), "Jasmine rice, cooked", "1 cup", Precision.Estimate, 205.0, 4.0, 45.0, 0.0, 1.0),
    )

    private fun snack(date: LocalDate) = listOf(
        item("apple", date, MealSlot.Snack, LocalTime(15, 30), "Apple", "1 medium", Precision.Estimate, 95.0, 0.0, 25.0, 0.0, 4.0,
            micros = mapOf("vitamin_c_mg" to 8.4, "vitamin_k_ug" to 4.0)),
        item(
            "pb", date, MealSlot.Snack, LocalTime(15, 31), "Peanut butter", "2 tbsp", Precision.Weighed, 190.0, 8.0, 7.0, 16.0, 2.0,
            portion = Portion(foodId = "peanut-butter", quantity = 2.0, unit = "tbsp", grams = 32.0),
        ),
    )

    private fun bigDinner(date: LocalDate) = listOf(
        item("pancit", date, MealSlot.Dinner, LocalTime(19, 30), "Pancit canton", "2 plates", Precision.Estimate, 760.0, 28.0, 96.0, 26.0, 5.0),
        item("lechon", date, MealSlot.Dinner, LocalTime(19, 31), "Lechon kawali", "3 pieces", Precision.Estimate, 640.0, 30.0, 2.0, 56.0, 0.0),
        item("cake", date, MealSlot.Dinner, LocalTime(19, 32), "Birthday cake", "1 slice", Precision.Named, 450.0, 5.0, 58.0, 22.0, 1.0),
    )

    private fun addedDinner(date: LocalDate) = listOf(
        item("arroz", date, MealSlot.Dinner, LocalTime(18, 17), "Arroz con pollo", "1 plate", Precision.Estimate, 525.0, 32.0, 60.0, 18.0, 4.0),
        item("sinangag", date, MealSlot.Dinner, LocalTime(18, 17), "Garlic fried rice (sinangag)", "1 cup", Precision.Estimate, 288.0, 5.0, 48.0, 8.0, 1.0),
    )

    private fun loose(date: LocalDate) = listOf(
        item("sparkling", date, MealSlot.Uncategorized, LocalTime(10, 5), "Sparkling water", "1 can", Precision.Named, null),
        item("granola", date, MealSlot.Uncategorized, LocalTime(16, 20), "Granola bar", "1 bar", Precision.Estimate, 190.0, 4.0, 29.0, 7.0, 2.0),
    )

    /**
     * Breakfast, lunch and a snack logged, dinner to go, 750 ml of water. [dinner] fills dinner
     * in: the big one for Day close's big day, or the one just added (3.9).
     */
    fun day(date: LocalDate = today, bigDay: Boolean = false, dinner: Boolean = false, loose: Boolean = false): Day = Day(
        date = date,
        itemsBySlot = mapOf(
            MealSlot.Breakfast to breakfast(date),
            MealSlot.Lunch to lunch(date),
            MealSlot.Snack to snack(date),
            MealSlot.Dinner to when {
                bigDay -> bigDinner(date)
                dinner -> addedDinner(date)
                else -> emptyList()
            },
            MealSlot.Uncategorized to if (loose) loose(date) else emptyList(),
        ),
        waterMl = 750,
        feelings = mapOf(
            MealSlot.Breakfast to MealFeeling(hunger = Hunger.Hungry, fullness = Fullness.Satisfied, moods = setOf(Mood.Calm)),
            MealSlot.Lunch to MealFeeling(note = "Ate outside with Sam. Felt relaxed."),
        ),
    )

    /** What the logger reports after adding [addedDinner] (3.9): two foods, at 6:17 pm. */
    fun added(): LoggedToMeal = LoggedToMeal(
        day = today,
        slot = MealSlot.Dinner,
        itemIds = addedDinner(today).map { it.id },
        at = LocalDateTime(today, LocalTime(18, 17)).toInstant(timeZone),
    )

    /** The usual day, marked untracked (a holiday): logged, but a gap in Trends. */
    fun untrackedDay(): Day = day().copy(untracked = true)

    /** Two days back, closed, with yesterday's kind of meals. */
    fun pastDay(): Day = day(today.minus(DatePeriod(days = 2))).copy(closedAt = noon)

    /** Yesterday: a breakfast and a dinner to offer again. */
    fun yesterday(): Day {
        val date = today.minus(DatePeriod(days = 1))
        return Day(
            date = date,
            itemsBySlot = mapOf(
                MealSlot.Breakfast to listOf(
                    item("yogurt", date, MealSlot.Breakfast, LocalTime(8, 30), "Greek yogurt, plain", "1 package", Precision.Weighed, 100.0, 17.0, 6.0, 1.0),
                    item("banana", date, MealSlot.Breakfast, LocalTime(8, 31), "Banana", "1 medium", Precision.Estimate, 105.0, 1.0, 27.0, 0.0, 3.0),
                ),
                MealSlot.Dinner to addedDinner(date),
            ),
        )
    }

    /** Tomorrow, with breakfast and lunch planned. */
    fun plannedDay(): Day {
        val date = today.plus(DatePeriod(days = 1))
        return Day(date = date, itemsBySlot = mapOf(MealSlot.Breakfast to breakfast(date), MealSlot.Lunch to lunch(date)))
    }

    fun explorer(): ExplorerStats = ExplorerStats(
        safeFoods = listOf("Rice", "Banana", "Chicken adobo", "Toast", "Greek yogurt", "Apples"),
        newFoods = listOf(
            NewFoodSeen("Poutine", today.minus(DatePeriod(days = 1))),
            NewFoodSeen("Rice pudding", today.minus(DatePeriod(days = 3))),
            NewFoodSeen("Chicken kabsa", today.minus(DatePeriod(days = 5))),
        ),
        bridge = FoodBridge(from = "Rice", to = "Rice pudding", shared = "rice", foodId = "rice-pudding"),
    )

    fun inputs(
        day: Day = day(),
        settings: Settings = Settings(),
        explorer: ExplorerStats? = explorer(),
        dayBefore: Day = if (day.date == today) yesterday() else Day.empty(day.date.minus(DatePeriod(days = 1))),
        remindersOn: Boolean = false,
        expended: EnergyExpended? = null,
        summaryOn: Boolean = false,
        selectOn: Boolean = false,
        weighIns: List<WeighIn> = emptyList(),
        foodActionsOn: Boolean = false,
    ): TodayInputs = TodayInputs(
        today = today,
        timeZone = timeZone,
        day = day,
        dayBefore = dayBefore,
        settings = settings,
        explorer = explorer,
        remindersOn = remindersOn,
        expended = expended,
        summaryOn = summaryOn,
        selectOn = selectOn,
        weighIns = weighIns,
        foodActionsOn = foodActionsOn,
    )

    /** Two weeks of morning weigh-ins ending on [today] at 7:02 am, 73.9 kg, the line about level. */
    val weighIns: List<WeighIn> = (13 downTo 0).map { back ->
        val date = today.minus(DatePeriod(days = back))
        WeighIn(id = "w-$date", measuredAt = LocalDateTime(date, LocalTime(7, 2)).toInstant(timeZone), kg = 73.9 + ((back + 1) % 3 - 1) * 0.1)
    }

    /** v3 `today-empty`'s "Expended so far 410 kcal" at 8 in the morning. */
    val morningExpended: EnergyExpended = EnergyExpended(restingKcal = 330.0, activeKcal = 80.0, workoutKcal = 0.0)

    /** The foods picked in v3 `today-select`: porridge, banana and the adobo. */
    val picked: Set<String> = setOf("oats-$today", "banana-$today", "adobo-$today")

    /** The apple and the peanut butter, the snack picked in `today-select-v4` and dragged in `today-drag-move`. */
    val snackPicked: Set<String> = setOf("apple-$today", "pb-$today")

    /** The peanut butter, the food `food-actions` and `edit-a-copy` start from: 2 tbsp, weighed, a catalog food. */
    val peanutButter: String = "pb-$today"

    /** The banana, the row v3 swipes in `today-swipe-delete` and confirms in `today-delete-confirm`. */
    val banana: String = "banana-$today"

    /** The check-in v3 `day-close` shows: steady hunger, good energy, OK sleep, calm and social. */
    val checkIn: CheckIn = CheckIn(hunger = DayHunger.Steady, energy = Energy.Good, sleep = Sleep.Ok, moods = setOf(CheckInMood.Calm, CheckInMood.Social))

    /** v3 `today-full`'s "Expended so far 1,990 kcal": 1,580 resting and 410 active, 150 of it in a workout. */
    val expended: EnergyExpended = EnergyExpended(restingKcal = 1580.0, activeKcal = 410.0, workoutKcal = 150.0)

    /** Someone who let all of their activity into the range: [expended]'s 410 active moves 1,900 to 2,300 up to 2,310 to 2,710. */
    val exerciseAll: Settings = Settings(exerciseRange = ExerciseAdjustment.All)

    /** Some of it: half of 410, so 2,105 to 2,505. */
    val exerciseSome: Settings = Settings(exerciseRange = ExerciseAdjustment.Some)

    fun todayState(
        policy: VisibilityPolicy,
        day: Day = day(),
        settings: Settings = Settings(),
        menuOpen: Boolean = false,
        clearArmed: Boolean = false,
        highlight: MealSlot? = null,
        added: LoggedToMeal? = null,
        remindersOn: Boolean = false,
        expended: EnergyExpended? = null,
        summaryOn: Boolean = false,
        selectOn: Boolean = false,
        weighIns: List<WeighIn> = emptyList(),
        ui: TodayUi = TodayUi(),
        foodActionsOn: Boolean = false,
    ): TodayState = TodayStateBuilder.build(
        inputs(
            day,
            settings,
            remindersOn = remindersOn,
            expended = expended,
            summaryOn = summaryOn,
            selectOn = selectOn,
            weighIns = weighIns,
            foodActionsOn = foodActionsOn,
        ),
        policy,
        menuOpen,
        clearArmed,
        highlight,
        added,
        ui = ui,
    )

    fun dayCloseState(
        policy: VisibilityPolicy,
        bigDay: Boolean = false,
        settings: Settings = Settings(),
        committed: Boolean = false,
        date: LocalDate = today,
        note: String = "",
        feeling: DayFeeling? = null,
        expended: EnergyExpended? = null,
        checkInOn: Boolean = false,
        checkIn: CheckIn? = null,
    ): DayCloseState = DayCloseStateBuilder.build(
        DayCloseInputs(today = today, day = day(date, bigDay = bigDay, dinner = !bigDay), settings = settings, expended = expended, checkInOn = checkInOn),
        policy,
        note = note,
        committed = committed,
        feeling = feeling,
        checkIn = checkIn,
    )

    fun todayCardsState(
        policy: VisibilityPolicy,
        settings: Settings = Settings(),
        fastingReleased: Boolean = false,
        summaryOn: Boolean = false,
    ): TodayCardsState = TodayCardsStateBuilder.build(settings, policy, fastingReleased, summaryOn)

    /** Someone whose first goal was changing their weight, as v3 `today-customize-summary` opens. */
    val changingWeight: Settings = Settings(onboarding = OnboardingAnswers(why = listOf("changing_weight")))

    fun calendarState(): CalendarState {
        val logged = (1..24).filter { it % 7 != 4 }.map { LocalDate(today.year, today.month, it) }.toSet() + today
        return CalendarStateBuilder.build(CalendarStateBuilder.monthOf(today), selected = today, today = today, logged = logged)
    }

    fun mealSheetState(slot: MealSlot = MealSlot.Dinner): MealSheetState =
        MealSheetStateBuilder.build(slot, today, day(), yesterday(), Settings())

    fun mealNoteState(): MealNoteState = MealNoteState(MealSlot.Lunch, draft = "Ate outside with Sam. Felt relaxed.", loaded = true)

    /** v3 `logger-how-it-felt`: lunch, very hungry before, full after, rushed. */
    fun mealFeelState(): MealFeelState =
        MealFeelState(MealSlot.Lunch, hunger = Hunger.VeryHungry, fullness = Fullness.Full, moods = setOf(Mood.Rushed), loaded = true)

    fun waterSheetState(settings: Settings = Settings()): WaterSheetState = WaterSheetStateBuilder.build(day(), settings, custom = "")

    fun itemSheetState(policy: VisibilityPolicy): ItemSheetState =
        requireNotNull(ItemSheetStateBuilder.build("adobo-$today", day(), today, policy)) { "The fixture day has the adobo" }

    /** The peanut butter's ⋮ with `release.foodActions` on (13.3d), on [step]. */
    fun foodActionsState(policy: VisibilityPolicy, step: ItemStep = ItemStep.Actions): ItemSheetState =
        requireNotNull(ItemSheetStateBuilder.build(peanutButter, day(), today, policy, actionsOn = true, step = step)) { "The fixture day has the peanut butter" }

    fun dayNoteState(): DayNoteState = DayNoteState(draft = "Dinner at Lola's, a lazy Sunday", isToday = true, loaded = true)

    /** Meal reminders on at Subtle, dinner at 7:15 pm (v3 `today-rhythm.png`). */
    val remindersSet: Settings = Settings(
        remindersChosen = true,
        remindersEvent = EventLevel.Subtle,
        mealTimes = MealTimes(dinner = LocalTime(19, 15)),
    )
}
