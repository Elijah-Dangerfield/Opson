package com.opson.features.settings.impl

import com.opson.libraries.diary.MacroMode
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Inputs for previews and `BlindModeLeakTest`. Every page builds its state from these through
 * its real state builder, so the leak test exercises the same mapping the view model runs.
 * Nothing here is a resolved number: the targets are the handoff's (`GOAL` in
 * `phone-logic.js`), and the policy decides what shows.
 */
object SettingsFixtures {

    /** Oct 25 2026 at noon UTC: before the 9 pm reveal, so a delayed number stays shut. */
    val clock: Clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-10-25T12:00:00Z")
    }

    val timeZone: TimeZone = TimeZone.UTC

    fun policy(numbers: NumbersSettings): VisibilityPolicy = VisibilityPolicy(numbers, clock, timeZone)

    fun policy(preset: PresetId): VisibilityPolicy = policy(NumbersSettings(preset = preset))

    /**
     * The handoff's user with [numbers]: 1,900 to 2,300 kcal raised once from where they
     * started (so Lower is open), ratio macros, eight 250 ml glasses.
     */
    fun settings(numbers: NumbersSettings = NumbersSettings()): Settings = Settings(
        numbers = numbers,
        targets = Targets.Default.raise(scaleMacros = true),
        macroMode = MacroMode.Ratios,
    )

    /** Full numbers with two numbers changed and the pause on, changed on Oct 25 at 9:41 pm. */
    val adjusted: NumbersSettings = NumbersSettings(
        preset = PresetId.Full,
        overrides = mapOf(Metric.Weight to Visibility.Soften, Metric.Remaining to Visibility.Hide),
        pauseBeforeMore = true,
        changedAt = Instant.parse("2026-10-25T21:41:00Z"),
    )
}
