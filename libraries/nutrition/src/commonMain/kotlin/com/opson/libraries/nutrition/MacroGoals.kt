package com.opson.libraries.nutrition

import kotlin.math.abs
import kotlin.math.roundToInt

// TODO(dietitian): the three splits are the ones other trackers name (MyFitnessPal's default is
// 20 / 50 / 30; see docs/research/goals-ux.md), picked by an engineer. Higher protein and Lower
// carb sit outside the usual adult ranges for carbs on purpose, as theirs do. Confirm or replace
// them before launch.
/** Named splits of the calorie goal, in whole percents of protein, carbs and fat, each adding to 100. */
enum class MacroPreset(val proteinPercent: Int, val carbsPercent: Int, val fatPercent: Int) {
    Balanced(20, 50, 30),
    HigherProtein(30, 40, 30),
    LowerCarb(30, 25, 45),
    ;

    fun percent(metric: Metric): Int = when (metric) {
        Metric.Protein -> proteinPercent
        Metric.Carbs -> carbsPercent
        Metric.Fat -> fatPercent
        else -> error("A preset splits protein, carbs and fat, not $metric")
    }
}

/** The three macros a calorie goal is split into, in the order every list shows them. */
val SplitMacros: List<Metric> = listOf(Metric.Protein, Metric.Carbs, Metric.Fat)

/** The most a macro goal can be, in grams: a guard against a stray extra digit, not advice. */
const val MAX_MACRO_G: Int = 999

/** A gram step, and a percent step, on the macro steppers. */
const val MACRO_STEP_G: Int = 5
const val MACRO_STEP_PERCENT: Int = 5

/**
 * How far the macros' own energy may be from the goal and still count as adding up. Rounding
 * each macro to a whole gram can be up to 8.5 kcal out (2 + 2 + 4.5), so anything closer than
 * this is rounding, not a choice the person has to make.
 */
const val MACRO_FIT_TOLERANCE_KCAL: Int = 10

/** What protein, carbs and fat come to against a calorie goal. [gapKcal] is positive over it. */
data class MacroFit(val macroKcal: Int, val goalKcal: Int) {
    val gapKcal: Int get() = macroKcal - goalKcal
    val fits: Boolean get() = abs(gapKcal) <= MACRO_FIT_TOLERANCE_KCAL

    /** The macros' share of the goal, in whole percents: 100 when they add up exactly. */
    val percentOfGoal: Int get() = if (goalKcal > 0) (macroKcal * 100.0 / goalKcal).roundToInt() else 0
}

/** [metric]'s gram goal: protein, carbs, fat or fiber. */
fun Targets.grams(metric: Metric): Int = when (metric) {
    Metric.Protein -> proteinG
    Metric.Carbs -> carbsG
    Metric.Fat -> fatG
    Metric.Fiber -> fiberG
    else -> error("Targets has no gram goal for $metric")
}

/** Protein, carbs and fat's own energy at 4, 4 and 9 kcal a gram. */
val Targets.macroKcal: Int
    get() = SplitMacros.sumOf { (grams(it) * it.kcalPerGram).roundToInt() }

/** How the macros add up against [goalKcal]. */
fun Targets.macroFit(goalKcal: Int): MacroFit = MacroFit(macroKcal, goalKcal)

/** [metric]'s share of [goalKcal] in whole percents; null without a goal to share. */
fun Targets.macroPercent(metric: Metric, goalKcal: Int): Int? {
    if (goalKcal <= 0) return null
    return (grams(metric) * metric.kcalPerGram * 100 / goalKcal).roundToInt()
}

/** The preset the macros match against [goalKcal], whole percent for whole percent; null for anything else. */
fun Targets.preset(goalKcal: Int): MacroPreset? =
    MacroPreset.entries.firstOrNull { preset -> SplitMacros.all { macroPercent(it, goalKcal) == preset.percent(it) } }

/**
 * [metric] set to [grams], held to 0..[MAX_MACRO_G]. Nothing else moves: the calorie goal stays
 * where it is, and [macroFit] says if the two no longer agree. Ratio mode scales from here on.
 */
fun Targets.withMacroGrams(metric: Metric, grams: Int): Targets {
    val held = grams.coerceIn(0, MAX_MACRO_G)
    return when (metric) {
        Metric.Protein -> copy(proteinG = held, ratioBasis = null)
        Metric.Carbs -> copy(carbsG = held, ratioBasis = null)
        Metric.Fat -> copy(fatG = held, ratioBasis = null)
        Metric.Fiber -> copy(fiberG = held)
        else -> this
    }
}

/** [metric] set to [percent] (0 to 100) of [goalKcal], as whole grams. */
fun Targets.withMacroPercent(metric: Metric, percent: Int, goalKcal: Int): Targets =
    withMacroGrams(metric, gramsFor(metric, percent.coerceIn(0, 100), goalKcal))

/** Protein, carbs and fat as [preset]'s split of [goalKcal]. */
fun Targets.withPreset(preset: MacroPreset, goalKcal: Int): Targets =
    SplitMacros.fold(this) { targets, metric -> targets.withMacroPercent(metric, preset.percent(metric), goalKcal) }

/**
 * The calorie goal drives: protein, carbs and fat scaled together until they add up to
 * [goalKcal], keeping their split. With no macro energy at all there is no split to keep, so
 * they take [MacroPreset.Balanced].
 */
fun Targets.fitMacrosTo(goalKcal: Int): Targets {
    val current = macroKcal
    if (current <= 0) return withPreset(MacroPreset.Balanced, goalKcal)
    val factor = goalKcal.toDouble() / current
    return SplitMacros.fold(this) { targets, metric -> targets.withMacroGrams(metric, (grams(metric) * factor).roundToInt()) }
}

/**
 * The macros drive: the calorie goal becomes what they add up to, rounded to 10, the range moving
 * with it as a step does. Only for a goal that is the person's own number (a range or one
 * number); a deficit or surplus follows Burned, and day groups each have their own. Null there,
 * and null when it would put an end of the goal under the floor or over [MAX_GOAL_KCAL].
 */
fun Targets.useMacroEnergy(): Targets? {
    if (style != TargetStyle.Range && style != TargetStyle.Exact) return null
    val kcal = (macroKcal / 10.0).roundToInt() * 10
    if (minKcal + (kcal - energyKcal) < floorKcal || maxKcal + (kcal - energyKcal) > MAX_GOAL_KCAL) return null
    return withEnergy(kcal)
}

/** One stepper tap on [metric]: [MACRO_STEP_G] grams, or [MACRO_STEP_PERCENT] percent of [goalKcal] when [percent]. Snaps to the step. */
fun Targets.stepMacro(metric: Metric, up: Boolean, percent: Boolean, goalKcal: Int): Targets {
    if (!percent || goalKcal <= 0) return withMacroGrams(metric, snapped(grams(metric), MACRO_STEP_G, up))
    val now = macroPercent(metric, goalKcal) ?: 0
    return withMacroPercent(metric, snapped(now, MACRO_STEP_PERCENT, up), goalKcal)
}

/** Grams that make [percent] of [goalKcal] for [metric]. */
fun gramsFor(metric: Metric, percent: Int, goalKcal: Int): Int =
    (goalKcal.coerceAtLeast(0) * percent / 100.0 / metric.kcalPerGram).roundToInt()

/** The next multiple of [step] above (or below) [value], never under 0. */
private fun snapped(value: Int, step: Int, up: Boolean): Int {
    val next = if (up) (value / step + 1) * step else ((value + step - 1) / step - 1) * step
    return next.coerceAtLeast(0)
}
