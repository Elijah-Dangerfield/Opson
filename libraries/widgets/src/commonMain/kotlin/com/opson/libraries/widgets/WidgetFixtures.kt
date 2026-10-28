package com.opson.libraries.widgets

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The design's day for previews, the Widgets page and `CaloriesHiddenLeakTest`: breakfast, lunch and a
 * snack logged, dinner still to come, 1,245 kcal against 1,900 to 2,300 (v3 `widgets.png`). Real
 * calorie values on purpose, so Full has something to show and the leak test's control means
 * something. Never builds a `Display`; the snapshot comes from [WidgetSnapshotBuilder].
 */
object WidgetFixtures {
    /** Sunday, as the prototype's `TODAY`. */
    val today: LocalDate = LocalDate(2026, 10, 25)

    val timeZone: TimeZone = TimeZone.UTC

    /** Noon on [today], before the 9 pm reveal. */
    val noon: Instant = LocalDateTime(today, LocalTime(12, 0)).toInstant(timeZone)

    val clock: Clock = object : Clock {
        override fun now(): Instant = noon
    }

    val targets: Targets = Targets(
        energyKcal = 2100,
        minKcal = 1900,
        maxKcal = 2300,
        proteinG = 120,
        carbsG = 250,
        fatG = 70,
        fiberG = 30,
        startingKcal = 2050,
        startingMinKcal = 1900,
    )

    fun settings(numbers: NumbersSettings = NumbersSettings(), mealGroups: Boolean = true): Settings =
        Settings(numbers = numbers, targets = targets, mealGroups = mealGroups)

    fun policy(preset: PresetId): VisibilityPolicy = VisibilityPolicy(NumbersSettings(preset = preset), clock, timeZone)

    /** Breakfast 445, lunch 515, snack 285: 1,245 kcal, dinner not yet. */
    val day: Day = Day(
        date = today,
        itemsBySlot = MealSlot.entries.associateWith { emptyList<LogItem>() } + mapOf(
            MealSlot.Breakfast to listOf(
                item("oats", MealSlot.Breakfast, LocalTime(8, 10), "Oat porridge with milk", 300.0),
                item("banana", MealSlot.Breakfast, LocalTime(8, 11), "Banana", 105.0),
                item("coffee", MealSlot.Breakfast, LocalTime(8, 12), "Coffee with milk", 40.0),
            ),
            MealSlot.Lunch to listOf(
                item("adobo", MealSlot.Lunch, LocalTime(12, 40), "Chicken adobo", 310.0),
                item("rice", MealSlot.Lunch, LocalTime(12, 41), "Jasmine rice, cooked", 205.0),
            ),
            MealSlot.Snack to listOf(
                item("apple", MealSlot.Snack, LocalTime(15, 30), "Apple", 95.0),
                item("pb", MealSlot.Snack, LocalTime(15, 31), "Peanut butter", 190.0),
            ),
        ),
    )

    fun inputs(numbers: NumbersSettings = NumbersSettings(), day: Day = this.day, mealGroups: Boolean = true): WidgetInputs =
        WidgetInputs(today = day, settings = settings(numbers, mealGroups))

    private fun item(id: String, slot: MealSlot, at: LocalTime, name: String, kcal: Double) = LogItem(
        id = "$id-$today",
        day = today,
        slot = slot,
        loggedAt = LocalDateTime(today, at).toInstant(timeZone),
        name = name,
        precision = Precision.Estimate,
        method = LogMethod.Search,
        kcal = kcal,
    )
}
