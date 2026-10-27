package com.opson.features.trends.impl

import com.opson.libraries.nutrition.RevealOn
import com.opson.features.trends.NutrientsRange
import com.opson.libraries.diary.CheckIn
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayHunger
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private typealias Menu = Map<MealSlot, List<TrendsFixtures.Food>>

/**
 * Realistic inputs for previews, the builder tests and `BlindModeLeakTest`,
 * shaped after the prototype's mocks: the week of Oct 19 to 25, 2026 seen on
 * Sunday the 25th at noon, Thursday left untracked, three foods new this week
 * (chicken kabsa, poutine, rice pudding), and the prototype's `WEIGHTS` curve.
 * Meal times drift a little day to day, dinner runs later on Fridays and
 * Saturdays, and hunger is tagged on most days and checked in on most evenings, for Patterns.
 *
 * Nothing here builds a `Display`: every number goes through the real
 * builders and the policy the caller passes, which is what makes the leak test
 * test the mapping and not the fixture.
 */
object TrendsFixtures {

    val today: LocalDate = LocalDate(2026, 10, 25)
    val timeZone: TimeZone = TimeZone.UTC

    /** Noon on the 25th: before the 9 pm reveal, as the leak test's clock. */
    val clock: Clock = fixedClock(LocalDateTime(today, LocalTime(12, 0)))

    /** 10 pm on the 25th: after the reveal, so the weekly check for the week has opened. */
    val evening: Clock = fixedClock(LocalDateTime(today, LocalTime(22, 0)))

    fun fixedClock(at: LocalDateTime): Clock = object : Clock {
        override fun now(): Instant = at.toInstant(timeZone)
    }

    fun policy(
        preset: PresetId = PresetId.Full,
        overrides: Map<Metric, Visibility> = emptyMap(),
        clock: Clock = this.clock,
        revealOn: RevealOn = RevealOn.DayClose,
    ): VisibilityPolicy = VisibilityPolicy(NumbersSettings(preset = preset, overrides = overrides, revealOn = revealOn), clock, timeZone)

    fun settings(policy: VisibilityPolicy? = null): Settings =
        Settings(numbers = policy?.settings ?: NumbersSettings())

    /** No watch energy unless asked for, as with `release.health` off; pass `expended()` to draw the Expended card. */
    fun trendsInputs(
        settings: Settings = Settings(),
        days: List<Day> = days(),
        weighIns: List<WeighIn> = weighIns(),
        expended: Map<LocalDate, EnergyExpended>? = null,
    ): TrendsInputs = TrendsInputs(today, settings, days, weighIns, timeZone, expended)

    /** A watch's ninety days before today: resting about 1,600, active wandering around 550. */
    fun expended(): Map<LocalDate, EnergyExpended> = (1..WEIGH_IN_DAYS).associate { back ->
        val active = round(550 + sin(back * 1.3) * 140 + cos(back * 0.7) * 60)
        today.minus(back, DateTimeUnit.DAY) to EnergyExpended(restingKcal = 1610.0, activeKcal = active, workoutKcal = null)
    }

    fun nutrientsInputs(
        range: NutrientsRange = NutrientsRange.Day,
        fromTrends: Boolean = range == NutrientsRange.WeekAverage,
        settings: Settings = Settings(),
        days: List<Day> = days(),
    ): NutrientsInputs {
        val window = TrendsCalendar.weekWindow(today, settings.weekStart)
        val shown = when (range) {
            NutrientsRange.Day -> days.filter { it.date == today }
            NutrientsRange.WeekAverage -> days.filter { it.date >= window.start && it.date <= window.end }
        }
        return NutrientsInputs(today, today, range, fromTrends, settings, shown)
    }

    /** With the progress photos flag on and the last set taken today, as the design draws Weight. */
    fun weightInputs(
        settings: Settings = Settings(),
        weighIns: List<WeighIn> = weighIns(),
        photosReleased: Boolean = true,
    ): WeightInputs = WeightInputs(today, settings, weighIns, timeZone, photosReleased, photosLastTaken = today)

    /** [checkInOn]: hunger from the check-ins, as the design draws Patterns. */
    fun patternsInputs(settings: Settings = Settings(), days: List<Day> = days(), checkInOn: Boolean = false): PatternsInputs =
        PatternsInputs(today, settings, days, timeZone, checkInOn)

    /** The prototype week with ninety days of ordinary history behind it, oldest first. */
    fun days(): List<Day> {
        val history = (HISTORY_DAYS downTo 1).map { back ->
            val date = WEEK_START.minus(back, DateTimeUnit.DAY)
            // Every ninth day untracked, so older weeks have gaps too.
            if (back % UNTRACKED_EVERY == 0) Day.empty(date) else day(date, HISTORY_MENUS[back % HISTORY_MENUS.size])
        }
        val week = WEEK_MENUS.mapIndexed { index, menu -> day(WEEK_START.plus(index, DateTimeUnit.DAY), menu) }
        return history + week
    }

    /** The prototype's `WEIGHTS`: ninety mornings drifting down with daily water noise, oldest first. */
    fun weighIns(): List<WeighIn> = (0 until WEIGH_IN_DAYS).map { i ->
        val date = today.minus(WEIGH_IN_DAYS - 1 - i, DateTimeUnit.DAY)
        val kg = round((START_KG - i * DRIFT + sin(i * 1.7) * 0.35 + cos(i * 0.9) * 0.2) * 10) / 10
        WeighIn(id = "w$i", measuredAt = LocalDateTime(date, LocalTime(7, 30)).toInstant(timeZone), kg = kg)
    }

    private val WEEK_START = LocalDate(2026, 10, 19)
    private const val HISTORY_DAYS = 100
    private const val UNTRACKED_EVERY = 9
    private const val WEIGH_IN_DAYS = 90
    private const val START_KG = 74.6
    private const val DRIFT = 0.014

    internal class Food(
        val id: String,
        val name: String,
        val kcal: Double,
        val protein: Double,
        val carbs: Double,
        val fat: Double,
        val fiber: Double,
        val micros: Map<String, Double> = emptyMap(),
    )

    private val OATS = Food("oats", "Oat porridge with milk", 300.0, 10.0, 50.0, 6.0, 8.0, mapOf("calcium_mg" to 250.0, "iron_mg" to 3.0, "magnesium_mg" to 80.0, "vitamin_b12_ug" to 0.9, "thiamin_mg" to 0.4, "phosphorus_mg" to 300.0, "manganese_mg" to 1.8))
    private val BANANA = Food("banana", "Banana", 105.0, 1.0, 27.0, 0.0, 3.0, mapOf("potassium_mg" to 420.0, "vitamin_c_mg" to 10.0, "magnesium_mg" to 32.0, "vitamin_b6_mg" to 0.43, "manganese_mg" to 0.32))
    private val ADOBO = Food("adobo", "Chicken adobo", 520.0, 38.0, 12.0, 34.0, 1.0, mapOf("iron_mg" to 2.0, "zinc_mg" to 3.5, "sodium_mg" to 1100.0, "vitamin_b12_ug" to 0.6, "niacin_mg" to 10.0, "vitamin_b6_mg" to 0.5, "selenium_ug" to 25.0, "phosphorus_mg" to 250.0, "choline_mg" to 90.0))
    private val RICE = Food("rice", "Rice", 205.0, 4.0, 45.0, 0.0, 1.0, mapOf("magnesium_mg" to 19.0, "iron_mg" to 0.3, "manganese_mg" to 0.7, "selenium_ug" to 12.0, "copper_mg" to 0.1))
    private val YOGURT = Food("yogurt", "Greek yogurt", 150.0, 15.0, 8.0, 5.0, 0.0, mapOf("calcium_mg" to 180.0, "vitamin_b12_ug" to 1.0, "potassium_mg" to 220.0, "riboflavin_mg" to 0.35, "phosphorus_mg" to 200.0))
    private val MANGO = Food("mango", "Mango", 100.0, 1.0, 25.0, 0.0, 3.0, mapOf("vitamin_c_mg" to 60.0, "vitamin_a_rae_ug" to 90.0, "folate_dfe_ug" to 70.0, "copper_mg" to 0.1))
    private val SALAD = Food("salad", "Spinach salad", 180.0, 6.0, 10.0, 12.0, 4.0, mapOf("vitamin_k_ug" to 300.0, "vitamin_a_rae_ug" to 400.0, "folate_dfe_ug" to 150.0, "vitamin_e_mg" to 2.0, "iron_mg" to 2.0, "riboflavin_mg" to 0.2, "copper_mg" to 0.15, "manganese_mg" to 0.9))
    private val SALMON = Food("salmon", "Salmon with potatoes", 640.0, 42.0, 45.0, 30.0, 5.0, mapOf("vitamin_d_ug" to 12.0, "potassium_mg" to 1100.0, "sodium_mg" to 600.0, "selenium_ug" to 40.0, "niacin_mg" to 9.0, "vitamin_b6_mg" to 0.9, "choline_mg" to 120.0, "thiamin_mg" to 0.3))
    private val KABSA = Food("kabsa", "Chicken kabsa", 610.0, 40.0, 70.0, 18.0, 3.0, mapOf("iron_mg" to 3.0, "sodium_mg" to 900.0))
    private val POUTINE = Food("poutine", "Poutine", 740.0, 20.0, 80.0, 38.0, 6.0, mapOf("sodium_mg" to 1500.0, "calcium_mg" to 300.0))
    private val RICE_PUDDING = Food("ricepud", "Rice pudding", 250.0, 6.0, 45.0, 5.0, 0.0, mapOf("calcium_mg" to 150.0))

    /** Monday to Sunday. Thursday is untracked; Sunday is today, dinner not yet logged. */
    private val WEEK_MENUS: List<Menu> = listOf(
        mapOf(MealSlot.Breakfast to listOf(OATS, BANANA), MealSlot.Lunch to listOf(ADOBO, RICE), MealSlot.Snack to listOf(YOGURT), MealSlot.Dinner to listOf(SALMON)),
        mapOf(MealSlot.Breakfast to listOf(YOGURT, BANANA), MealSlot.Lunch to listOf(KABSA), MealSlot.Snack to listOf(MANGO), MealSlot.Dinner to listOf(ADOBO, RICE)),
        mapOf(MealSlot.Breakfast to listOf(OATS, BANANA), MealSlot.Lunch to listOf(SALAD, RICE), MealSlot.Snack to listOf(YOGURT), MealSlot.Dinner to listOf(SALMON, MANGO)),
        emptyMap(),
        mapOf(MealSlot.Breakfast to listOf(OATS), MealSlot.Lunch to listOf(ADOBO, RICE), MealSlot.Snack to listOf(RICE_PUDDING), MealSlot.Dinner to listOf(POUTINE)),
        mapOf(MealSlot.Breakfast to listOf(YOGURT, MANGO), MealSlot.Lunch to listOf(SALAD, SALMON), MealSlot.Snack to listOf(BANANA), MealSlot.Dinner to listOf(KABSA)),
        mapOf(MealSlot.Breakfast to listOf(OATS, BANANA), MealSlot.Lunch to listOf(ADOBO, RICE)),
    )

    /** Ordinary days before the week, none with this week's new foods. */
    private val HISTORY_MENUS: List<Menu> = listOf(
        mapOf(MealSlot.Breakfast to listOf(OATS, BANANA), MealSlot.Lunch to listOf(ADOBO, RICE), MealSlot.Dinner to listOf(SALMON)),
        mapOf(MealSlot.Breakfast to listOf(YOGURT, MANGO), MealSlot.Lunch to listOf(SALAD, RICE), MealSlot.Snack to listOf(BANANA), MealSlot.Dinner to listOf(ADOBO, RICE)),
        mapOf(MealSlot.Lunch to listOf(SALMON), MealSlot.Snack to listOf(YOGURT), MealSlot.Dinner to listOf(ADOBO, RICE, SALAD)),
    )

    private fun day(date: LocalDate, menu: Menu): Day {
        val items = MealSlot.entries.associateWith { slot ->
            menu[slot].orEmpty().mapIndexed { index, food -> item(date, slot, index, food) }
        }
        // Hunger tagged on two days in three, so Patterns has something to say.
        val seed = date.toEpochDays().toInt()
        val feelings = if (seed % 3 == 0) {
            emptyMap()
        } else {
            MealSlot.meals.filter { menu[it] != null }.associateWith { MealFeeling(hunger = HUNGER.getValue(it)) }
        }
        return Day(date = date, itemsBySlot = items, feelings = feelings, checkIn = checkIn(seed, menu))
    }

    /**
     * Checked in three evenings in four, hungrier on a day without breakfast, so Patterns'
     * check-in card has a shape and its skipped-breakfast line something to say.
     */
    private fun checkIn(seed: Int, menu: Menu): CheckIn? {
        if (menu.isEmpty() || seed % 4 == 0) return null
        val hunger = if (menu[MealSlot.Breakfast] == null) {
            if (seed % 2 == 0) DayHunger.Very else DayHunger.Often
        } else {
            CHECK_IN_HUNGER[seed % CHECK_IN_HUNGER.size]
        }
        return CheckIn(hunger = hunger)
    }

    private val CHECK_IN_HUNGER = listOf(DayHunger.Some, DayHunger.Steady, DayHunger.Rarely, DayHunger.Some, DayHunger.Steady)

    private fun item(date: LocalDate, slot: MealSlot, index: Int, food: Food): LogItem = LogItem(
        id = "$date-${slot.wire}-$index",
        day = date,
        slot = slot,
        loggedAt = LocalDateTime(date, mealTime(date, slot)).toInstant(timeZone).plus(index.minutes),
        name = food.name,
        precision = Precision.Estimate,
        method = LogMethod.Search,
        foodId = food.id,
        kcal = food.kcal,
        protein = food.protein,
        carbs = food.carbs,
        fat = food.fat,
        fiber = food.fiber,
        micros = food.micros,
    )

    /**
     * When a meal was logged: its usual time, a few minutes either side day to
     * day, now and then later, and dinner about an hour later on Fridays and
     * Saturdays, so the Patterns timeline has a shape.
     */
    private fun mealTime(date: LocalDate, slot: MealSlot): LocalTime {
        val usual = MEAL_TIMES.getValue(slot)
        val seed = date.toEpochDays().toInt() + slot.ordinal * 7
        val jitter = (seed * 37 % 21 + 21) % 21 - 10
        val late = when {
            slot == MealSlot.Dinner && (date.dayOfWeek == DayOfWeek.FRIDAY || date.dayOfWeek == DayOfWeek.SATURDAY) -> 60
            seed % 6 == 0 -> 50
            else -> 0
        }
        val minutes = usual.hour * 60 + usual.minute + jitter + late
        return LocalTime(minutes / 60, minutes % 60)
    }

    private val HUNGER = mapOf(
        MealSlot.Breakfast to Hunger.Peckish,
        MealSlot.Lunch to Hunger.Hungry,
        MealSlot.Snack to Hunger.Peckish,
        MealSlot.Dinner to Hunger.VeryHungry,
    )

    private val MEAL_TIMES = mapOf(
        MealSlot.Breakfast to LocalTime(8, 10),
        MealSlot.Lunch to LocalTime(12, 20),
        MealSlot.Snack to LocalTime(15, 30),
        MealSlot.Dinner to LocalTime(18, 40),
        MealSlot.Uncategorized to LocalTime(11, 0),
    )
}
