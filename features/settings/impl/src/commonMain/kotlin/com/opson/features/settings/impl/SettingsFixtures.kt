package com.opson.features.settings.impl

import com.opson.libraries.diary.EventLevel
import com.opson.libraries.diary.MacroMode
import com.opson.libraries.diary.MealReminders
import com.opson.libraries.diary.MealTimes
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.DayGroup
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.GroupShape
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ModeTiming
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RestingEnergy
import com.opson.libraries.nutrition.RestingFrom
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Inputs for previews and `CaloriesHiddenLeakTest`. Every page builds its state from these through
 * its real state builder, so the leak test exercises the same mapping the view model runs.
 * Nothing here is a resolved number: the targets are the design's, and the policy decides what
 * shows.
 */
object SettingsFixtures {

    /** Oct 25 2026 at noon UTC: before the 9 pm reveal, so a delayed number stays shut. */
    val clock: Clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-10-25T12:00:00Z")
    }

    val timeZone: TimeZone = TimeZone.UTC

    /** The day [clock] reads. */
    val today: LocalDate = LocalDate(2026, 10, 25)

    /**
     * Design sync 4's Burned (`energy-today`): 1,610 resting, calculated, plus 390 of movement
     * and a 180 kcal workout from the health app, 2,180 in all.
     */
    val burned: Burned = Burned(RestingEnergy(1610, RestingFrom.Calculated), movementKcal = 390, workoutKcal = 180)

    /**
     * Burned with no health app: the same 1,610 resting, and lightly active everyday movement
     * (600) from everyday activity in its place, 2,210 in all.
     */
    val estimatedBurned: Burned = Burned(RestingEnergy(1610, RestingFrom.Calculated), everydayKcal = EverydayActivity.SomeWalking.kcalFor(1610))

    fun policy(numbers: NumbersSettings): VisibilityPolicy = VisibilityPolicy(numbers, clock, timeZone)

    fun policy(preset: PresetId): VisibilityPolicy = policy(NumbersSettings(preset = preset))

    /**
     * v3 `targets`: 1,900 to 2,300 kcal with the low end where it started (locked) and the high
     * end raised twice from 2,200 (so it can come down), 120 g protein, 250 g carbs, 70 g fat.
     */
    val rangeTargets: Targets = Targets(
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

    /** v3 `targets-deficit`: 300 under what the user burns, from a start low enough that the floor allows 1,880 against [burned]. */
    val deficitTargets: Targets = rangeTargets.copy(
        startingKcal = 1800,
        style = TargetStyle.Deficit(kcal = 300, startingKcal = 300),
    )

    /**
     * A deficit just picked with no burn known: it starts at 0, which is also its cap while
     * `MAX_DEFICIT_KCAL` is unset, so neither step can move it.
     */
    val newDeficitTargets: Targets = rangeTargets.useDeficit(burnKcal = null)

    /** v3 `targets-by-day`: Monday to Wednesday at 2,300 to 2,700, the rest at 1,900 to 2,300. */
    val byDayTargets: Targets = rangeTargets.copy(
        style = TargetStyle.ByDay(
            listOf(
                DayGroup(setOf(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), 1900, 2300),
                DayGroup(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY), 2300, 2700),
            ),
        ),
    )

    /** Different by day just picked: every day in the first group, and an empty second one to pick days for. */
    val newByDayTargets: Targets = requireNotNull(rangeTargets.useByDay().addDayGroup())

    /** A range of 1,000 to 1,400: its low end is under the low-goal line for anyone. */
    val lowRangeTargets: Targets = rangeTargets.copy(energyKcal = 1200, minKcal = 1000, maxKcal = 1400)

    /** A set deficit of 1,200, more than a healthy pace, picked against [activeBurned]. */
    val fastDeficitTargets: Targets = rangeTargets.copy(style = TargetStyle.Deficit(kcal = 1200, startingKcal = 1200))

    /** 3,400 burned on a very active day: a 1,200 deficit still leaves a 2,200 goal. */
    val activeBurned: Burned = Burned(RestingEnergy(1800, RestingFrom.Calculated), movementKcal = 1600)

    /** One number, 2,200, raised from where the user started, so it can step down as well as up. */
    val exactTargets: Targets = rangeTargets.copy(energyKcal = 2200, minKcal = 2000, maxKcal = 2400, style = TargetStyle.Exact)

    /** A set surplus of 300: about 2,480 against [burned], about 2,400 on the usual target before Burned is known. */
    val surplusTargets: Targets = rangeTargets.copy(style = TargetStyle.Surplus(kcal = 300, startingKcal = 300))

    /**
     * Different by day with a shape per group: Thursday and Friday a range, Monday to Wednesday
     * one number (2,500), the weekend 300 over what the user burns.
     */
    val shapedByDayTargets: Targets = rangeTargets.copy(
        style = TargetStyle.ByDay(
            listOf(
                DayGroup(setOf(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), 1900, 2300),
                DayGroup(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY), 2300, 2700, GroupShape.Exact),
                DayGroup(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), 1900, 2300, GroupShape.Surplus(300, 300)),
            ),
        ),
    )

    /**
     * Different by day with every goal type: Thursday and Friday a range, Monday one number
     * (2,500), Tuesday and Wednesday 200 under what the user burns, the weekend 300 over it.
     */
    val everyShapeByDayTargets: Targets = rangeTargets.copy(
        startingKcal = 1800,
        style = TargetStyle.ByDay(
            listOf(
                DayGroup(setOf(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), 1900, 2300),
                DayGroup(setOf(DayOfWeek.MONDAY), 2300, 2700, GroupShape.Exact),
                DayGroup(setOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY), 1900, 2300, GroupShape.Deficit(200, 300)),
                DayGroup(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), 1900, 2300, GroupShape.Surplus(300, 300)),
            ),
        ),
    )

    /** Macros set in grams that add up to 2,360 kcal, 260 over the 2,100 aim. */
    val gramsOverTargets: Targets = rangeTargets.copy(proteinG = 150, carbsG = 260, fatG = 80)

    /** The design's user with [numbers] and [targets]: ratio macros, eight 250 ml glasses. */
    fun settings(numbers: NumbersSettings = NumbersSettings(), targets: Targets = rangeTargets): Settings = Settings(
        numbers = numbers,
        targets = targets,
        macroMode = MacroMode.Ratios,
    )

    /** All numbers with two numbers changed and the pause on, changed on Oct 25 at 9:41 pm. */
    val adjusted: NumbersSettings = NumbersSettings(
        preset = PresetId.Full,
        overrides = mapOf(Metric.Weight to Visibility.Soften, Metric.Remaining to Visibility.Hide),
        pauseBeforeMore = true,
        changedAt = Instant.parse("2026-10-25T21:41:00Z"),
    )

    /**
     * Design sync 4 `modes-v4` (13.5a): All numbers, End after a set time on (2 weeks, then No
     * numbers), the pause on, changed Oct 25 at 8:14 pm.
     */
    val fullForTwoWeeks: NumbersSettings = NumbersSettings(
        preset = PresetId.Full,
        pauseBeforeMore = true,
        endAfter = ModeTiming(startedOn = LocalDate(2026, 10, 25), weeks = 2, then = PresetId.NoNumbers),
        changedAt = Instant.parse("2026-10-25T20:14:00Z"),
    )

    /** Macros only (once Nutrients first) with carbs moved from Soft to Show and the pause on: one change on Each number. */
    val nutrientsFirstWithACarbsChange: NumbersSettings = NumbersSettings(
        preset = PresetId.MacrosOnly,
        overrides = mapOf(Metric.Carbs to Visibility.Show),
        pauseBeforeMore = true,
        changedAt = Instant.parse("2026-10-25T20:14:00Z"),
    )

    /**
     * v3 `reminders`: quiet mode on, the big-day card at Full, meal reminders Subtle with
     * breakfast at 8:30, lunch at 1:00 and dinner at 7:15 on and the snack off.
     */
    val remindersSet: Settings = settings().copy(
        quiet = true,
        bigDayEvent = EventLevel.Full,
        remindersChosen = true,
        remindersEvent = EventLevel.Subtle,
        mealReminders = MealReminders(breakfast = true, lunch = true, snack = false, dinner = true),
        mealTimes = MealTimes(breakfast = LocalTime(8, 30), lunch = LocalTime(13, 0), dinner = LocalTime(19, 15)),
    )
}
