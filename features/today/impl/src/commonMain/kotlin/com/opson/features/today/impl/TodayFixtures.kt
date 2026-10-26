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
import com.opson.features.today.impl.meal.MealFeelState
import com.opson.features.today.impl.meal.MealNoteState
import com.opson.features.today.impl.meal.MealSheetState
import com.opson.features.today.impl.meal.MealSheetStateBuilder
import com.opson.features.today.impl.water.WaterSheetState
import com.opson.features.today.impl.water.WaterSheetStateBuilder
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayFeeling
import com.opson.libraries.diary.Fullness
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.LoggedToMeal
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Mood
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
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
    )

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
        item("pb", date, MealSlot.Snack, LocalTime(15, 31), "Peanut butter", "2 tbsp", Precision.Weighed, 190.0, 8.0, 7.0, 16.0, 2.0),
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
        expended: EnergyExpended? = null,
    ): TodayInputs = TodayInputs(
        today = today,
        timeZone = timeZone,
        day = day,
        dayBefore = dayBefore,
        settings = settings,
        explorer = explorer,
        expended = expended,
    )

    /** v3 `today-full`'s "Expended so far 1,990 kcal": 1,580 resting and 410 active, 150 of it in a workout. */
    val expended: EnergyExpended = EnergyExpended(restingKcal = 1580.0, activeKcal = 410.0, workoutKcal = 150.0)

    fun todayState(
        policy: VisibilityPolicy,
        day: Day = day(),
        settings: Settings = Settings(),
        menuOpen: Boolean = false,
        clearArmed: Boolean = false,
        highlight: MealSlot? = null,
        added: LoggedToMeal? = null,
        expended: EnergyExpended? = null,
    ): TodayState = TodayStateBuilder.build(inputs(day, settings, expended = expended), policy, menuOpen, clearArmed, highlight, added)

    fun dayCloseState(
        policy: VisibilityPolicy,
        bigDay: Boolean = false,
        settings: Settings = Settings(),
        committed: Boolean = false,
        date: LocalDate = today,
        note: String = "",
        feeling: DayFeeling? = null,
    ): DayCloseState = DayCloseStateBuilder.build(
        DayCloseInputs(today = today, day = day(date, bigDay = bigDay, dinner = !bigDay), settings = settings),
        policy,
        note = note,
        committed = committed,
        feeling = feeling,
    )

    fun todayCardsState(policy: VisibilityPolicy, settings: Settings = Settings(), fastingReleased: Boolean = false): TodayCardsState =
        TodayCardsStateBuilder.build(settings, policy, fastingReleased)

    fun calendarState(): CalendarState {
        val logged = (1..24).filter { it % 7 != 4 }.map { LocalDate(today.year, today.month, it) }.toSet() + today
        return CalendarStateBuilder.build(CalendarStateBuilder.monthOf(today), selected = today, today = today, logged = logged)
    }

    fun mealSheetState(slot: MealSlot = MealSlot.Dinner): MealSheetState =
        MealSheetStateBuilder.build(slot, today, day(), yesterday(), Settings())

    fun mealNoteState(): MealNoteState = MealNoteState(MealSlot.Lunch, draft = "Ate outside with Sam. Felt relaxed.", loaded = true)

    fun mealFeelState(): MealFeelState =
        MealFeelState(MealSlot.Breakfast, hunger = Hunger.Hungry, fullness = Fullness.Satisfied, moods = setOf(Mood.Calm), loaded = true)

    fun waterSheetState(settings: Settings = Settings()): WaterSheetState = WaterSheetStateBuilder.build(day(), settings, custom = "")

    fun itemSheetState(policy: VisibilityPolicy): ItemSheetState =
        requireNotNull(ItemSheetStateBuilder.build("adobo-$today", day(), today, policy)) { "The fixture day has the adobo" }

    fun dayNoteState(): DayNoteState = DayNoteState(draft = "Dinner at Lola's, a lazy Sunday", isToday = true, loaded = true)
}
