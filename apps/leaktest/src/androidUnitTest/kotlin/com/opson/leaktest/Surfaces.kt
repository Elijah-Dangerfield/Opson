package com.opson.leaktest

import com.opson.features.foods.impl.FoodsLeakSurfaces
import com.opson.features.logger.impl.LoggerLeakSurfaces
import com.opson.features.onboarding.impl.OnboardingLeakSurfaces
import com.opson.features.settings.impl.SettingsLeakSurfaces
import com.opson.features.today.impl.TodayLeakSurfaces
import com.opson.features.trends.impl.TrendsLeakSurfaces
import com.opson.libraries.core.fixed
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Presets
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.ui.leak.LeakSurface
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Every feature's surfaces, keyed by the object that lists them: the handoff's `SURFACES`, as
 * ported in docs/plans/screens/blind-leak-test.md. A feature impl that grows an `XLeakSurfaces`
 * object has to be added here; [SurfacesCoverageTest] fails until it is.
 */
internal val SURFACES_BY_LIST: Map<String, List<LeakSurface>> = linkedMapOf(
    "OnboardingLeakSurfaces" to OnboardingLeakSurfaces.all,
    "TodayLeakSurfaces" to TodayLeakSurfaces.all,
    "LoggerLeakSurfaces" to LoggerLeakSurfaces.all,
    "FoodsLeakSurfaces" to FoodsLeakSurfaces.all,
    "TrendsLeakSurfaces" to TrendsLeakSurfaces.all,
    "SettingsLeakSurfaces" to SettingsLeakSurfaces.all,
)

internal val SURFACES: List<LeakSurface> = SURFACES_BY_LIST.values.flatten()

/** The calorie amount and the two numbers that follow from it. None of them may show as a number. */
internal val CALORIE_METRICS: List<Metric> = listOf(Metric.Calories, Metric.Remaining, Metric.EnergyOut)

/**
 * Every preset that does not show calories, derived so a new one is covered the day it is added:
 *
 * - Hide (Blind, Nutrient, Rhythm, Explorer, Mindful): nothing at all.
 * - Delay (Delayed, Coast), at [NOW], before the reveal: nothing, apart from [REVEALED_BY_DESIGN].
 * - Soften (Fuel): a word may carry the calories tag, but never a digit.
 *
 * [SurfacesCoverageTest] pins the set, so a preset that starts showing calories is noticed.
 */
internal val PRESETS_UNDER_TEST: List<PresetId> = Presets.all
    .filter { it.level(Metric.Calories) != Visibility.Show }
    .map { it.id }

/**
 * Noon on the fixtures' day (2026-10-25, a Sunday, UTC), before the 9 pm reveal and before Coast's
 * weekly check, so both delayed presets are still holding today back.
 */
internal val NOW: Instant = Instant.parse("2026-10-25T12:00:00Z")

internal fun policyFor(preset: PresetId): VisibilityPolicy =
    VisibilityPolicy(NumbersSettings(preset = preset), Clock.fixed(NOW), TimeZone.UTC)

/** Why a delayed or softened mode shows a calorie amount on a surface, on purpose. */
internal sealed interface Reveal {
    val why: String

    /** The surface is where the numbers open. Not run in that preset; every other preset still runs it. */
    class WholeSurface(override val why: String) : Reveal

    /** Only these exact matches or tagged texts are allowed; anything else on the surface still fails. */
    class Only(val values: Set<String>, override val why: String) : Reveal
}

private val dayCloseOpens = Reveal.WholeSurface("Day close is where Later opens: Surface.DayClose is always revealed.")
private val pastDayOpens = Reveal.WholeSurface("A past day has had its reveal.")
private val weekOpens = Reveal.WholeSurface(
    "Delayed opens the week view all day (Surface.Week). Today is left out of every average on it.",
)
private val lastCheckOpens = Reveal.WholeSurface(
    "Before this week's check, Coast keeps last week's check readable: its daily average is the number shown.",
)
private val typedLabel = Reveal.Only(
    setOf("120 kcal"),
    "Add a food asks for a field unless the mode hides it outright, so Later and soft modes fill the label in. " +
        "120 is what the person typed, not a number the app worked out.",
)

/**
 * The surfaces a delayed or softened preset shows a calorie amount on by design. Every entry is a
 * product decision, not a test convenience: if one stops being true, delete it and let the test
 * hold the surface to nothing. [SurfacesCoverageTest] fails on an entry for a surface or preset
 * that no longer exists.
 */
internal val REVEALED_BY_DESIGN: Map<Pair<PresetId, String>, Reveal> = buildMap {
    listOf("day-close", "day-close-big-day").forEach { put(PresetId.Delayed to it, dayCloseOpens) }
    put(PresetId.Delayed to "today-past", pastDayOpens)
    listOf("trends", "trends-blind", "trends-coast", "nutrients-week", "patterns", "patterns-blind")
        .forEach { put(PresetId.Delayed to it, weekOpens) }

    listOf("trends", "trends-blind", "trends-coast").forEach { put(PresetId.Coast to it, lastCheckOpens) }

    listOf(PresetId.Delayed, PresetId.Coast, PresetId.Fuel).forEach { preset ->
        listOf("foods-add-food", "foods-add-food-blind").forEach { put(preset to it, typedLabel) }
    }
}
