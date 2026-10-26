package com.opson.features.settings.impl

import com.opson.libraries.diary.MacroMode
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.DayGroup
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Inputs for previews and `BlindModeLeakTest`. Every page builds its state from these through
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

    /** What the deficit fixtures burn, a 7-day average (v3 `targets-deficit`). 0.1 has no real source. */
    const val BURN_KCAL = 2172

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

    /** v3 `targets-deficit`: 300 under what the user burns, from a start low enough that the floor allows 1,870. */
    val deficitTargets: Targets = rangeTargets.copy(
        startingKcal = 1800,
        style = TargetStyle.Deficit(kcal = 300, startingKcal = 300),
    )

    /** v3 `targets-by-day`: Monday to Wednesday at 2,300 to 2,700, the rest at 1,900 to 2,300. */
    val byDayTargets: Targets = rangeTargets.copy(
        style = TargetStyle.ByDay(
            listOf(
                DayGroup(setOf(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), 1900, 2300),
                DayGroup(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY), 2300, 2700),
            ),
        ),
    )

    /** The design's user with [numbers] and [targets]: ratio macros, eight 250 ml glasses. */
    fun settings(numbers: NumbersSettings = NumbersSettings(), targets: Targets = rangeTargets): Settings = Settings(
        numbers = numbers,
        targets = targets,
        macroMode = MacroMode.Ratios,
    )

    /** Full numbers with two numbers changed and the pause on, changed on Oct 25 at 9:41 pm. */
    val adjusted: NumbersSettings = NumbersSettings(
        preset = PresetId.Full,
        overrides = mapOf(Metric.Weight to Visibility.Soften, Metric.Remaining to Visibility.Hide),
        pauseBeforeMore = true,
        changedAt = Instant.parse("2026-10-25T21:41:00Z"),
    )

    /** v3 `modes`: Fuel with carbs moved from Hide to Show, the pause on, changed Oct 25 at 8:14 pm. */
    val fuelWithACarbsChange: NumbersSettings = NumbersSettings(
        preset = PresetId.Fuel,
        overrides = mapOf(Metric.Carbs to Visibility.Show),
        pauseBeforeMore = true,
        changedAt = Instant.parse("2026-10-25T20:14:00Z"),
    )
}
