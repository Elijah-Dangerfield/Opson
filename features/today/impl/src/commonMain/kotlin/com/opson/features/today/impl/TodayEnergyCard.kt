package com.opson.features.today.impl

import com.opson.libraries.diary.MainNumberChoice
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.SummaryCardPreset
import com.opson.libraries.diary.rememberedMainNumber
import com.opson.libraries.nutrition.DayGoal
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.EnergyRange
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.GroupShape
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.resolveRange
import kotlin.math.abs

/**
 * Design sync 4's summary card, page 1 (13.1, 13.2a, 13.2b, 13.6c): one of four [preset]s around
 * the [main] number, whose label toggles between the gap to the goal and the deficit or surplus
 * so far. Built only with `release.goalsAndEnergy` in a layout whose card comes from Full
 * numbers, and only while calories are on show; every number in it is policy-resolved.
 */
data class EnergyCard(
    /** The preset drawn: Rings needs a single target, and Equation needs Burned, else In and out. */
    val preset: SummaryCardPreset,
    val main: MainNumber,
    /** What a tap on the main number's label switches to; null when there is nothing to switch to. */
    val next: MainNumberChoice?,
    /** "Range 1,900 to 2,300", "Target 2,100", "Goal 500 a day" or "Goal +300 a day". */
    val goal: GoalLine,
    /** Eaten, bare, with its fill fraction on the card's scale. */
    val eaten: Display,
    val eatenParts: List<BarPart>,
    val rangeStart: Float,
    val rangeEnd: Float,
    /** Null while Burned is unknown or energy out isn't on show. */
    val burned: BurnedBars?,
    /** A set deficit or surplus: "Target 1,680 · 435 kcal left at your goal". */
    val target: TargetLine?,
    /** Equation's line under the bar: "655 kcal to your range". */
    val toGoal: MainNumber,
    /** "3 of 4 meals" with More › Today screen's Meals count on. */
    val meals: MealCount?,
    /** Rings' third ring: how much of it [main] fills, 0 to 1. */
    val mainFraction: Float,
    /** Rings' first ring: eaten against the goal, 0 to 1. */
    val eatenFraction: Float,
)

/** The main number: what it is ([kind], which names its label) and the amount, through [metric]'s level. */
data class MainNumber(val kind: MainKind, val metric: Metric, val display: Display)

enum class MainKind { ToRange, LeftToTarget, ToSurplus, Deficit, Surplus, Eaten }

sealed interface GoalLine {
    data class Range(val range: Display) : GoalLine
    data class Target(val kcal: Display) : GoalLine
    data class DeficitGoal(val kcal: Display) : GoalLine
    data class SurplusGoal(val kcal: Display) : GoalLine
}

/**
 * Burned as two bars on the eaten bar's scale: [restingFraction] and [activeFraction] end to end,
 * and where it all ends ([tick]) for the Range bar preset. [breakdown] is More › Today screen's
 * "Burned breakdown".
 */
data class BurnedBars(
    val total: Display,
    val resting: Display,
    val active: Display,
    val restingFraction: Float,
    val activeFraction: Float,
    val tick: Float,
    val breakdown: Boolean,
)

/** [left] is how much is left at the goal, null once it is reached; a surplus's line says it follows the burn instead. */
data class TargetLine(val target: Display, val left: Display?, val surplus: Boolean)

object EnergyCardBuilder {
    /** Both bars run to the larger of the goal and Burned plus a fifth, so they compare at a glance. */
    private const val HEADROOM = 1.2

    fun build(
        inputs: TodayInputs,
        policy: VisibilityPolicy,
        context: FormatContext,
        eaten: Double,
        range: EnergyRange,
        eatenParts: List<BarPart>,
    ): EnergyCard {
        val settings = inputs.settings
        val day = inputs.day
        val preset = settings.numbers.preset
        val goal = settings.targets.rangeFor(day.date, preset, inputs.burned?.totalKcal)
        val energyOutShown = policy.effectiveLevel(Metric.EnergyOut, Surface.Today, context) == Visibility.Show
        val burned = inputs.burned?.takeIf { energyOutShown }
        val scale = maxOf(range.maxKcal, burned?.totalKcal ?: 0) * HEADROOM
        val bare = context.copy(of = null, bare = true)
        val goalKind = goalKind(settings.targets.style, goal)

        val options = buildList {
            add(MainNumberChoice.Goal)
            if (burned != null) add(MainNumberChoice.Balance)
            if (settings.todayMainNumber == MainNumberChoice.Eaten) add(MainNumberChoice.Eaten)
        }
        val styleDefault = if (goalKind == MainKind.LeftToTarget && settings.targets.style is TargetStyle.Deficit) {
            MainNumberChoice.Balance
        } else {
            MainNumberChoice.Goal
        }
        val wanted = settings.rememberedMainNumber ?: settings.todayMainNumber.takeUnless { it == MainNumberChoice.Follow } ?: styleDefault
        val choice = wanted.takeIf { it in options } ?: MainNumberChoice.Goal
        val target = range.targetKcal.toDouble()
        val main = when (choice) {
            MainNumberChoice.Balance -> {
                val difference = requireNotNull(burned).totalKcal - eaten
                MainNumber(
                    kind = if (difference < 0) MainKind.Surplus else MainKind.Deficit,
                    metric = Metric.EnergyOut,
                    display = policy.resolve(Metric.EnergyOut, abs(difference), Surface.Today, bare),
                )
            }
            MainNumberChoice.Eaten -> MainNumber(MainKind.Eaten, Metric.Calories, policy.resolve(Metric.Calories, eaten, Surface.Today, bare))
            else -> gap(policy, context.copy(bare = true), eaten, range, goalKind)
        }
        val drawn = when {
            settings.todayCard == SummaryCardPreset.Rings && !range.single -> SummaryCardPreset.InOut
            settings.todayCard == SummaryCardPreset.Equation && burned == null -> SummaryCardPreset.InOut
            else -> settings.todayCard
        }
        return EnergyCard(
            preset = drawn,
            main = main,
            next = options.getOrNull((options.indexOf(choice) + 1) % options.size)?.takeIf { options.size > 1 },
            goal = goalLine(settings.targets.style, goal, policy, bare, range),
            eaten = policy.resolve(Metric.Calories, eaten, Surface.Today, bare.copy(of = scale)),
            eatenParts = eatenParts,
            rangeStart = (range.minKcal / scale).toFloat(),
            rangeEnd = (range.maxKcal / scale).toFloat(),
            burned = burned?.let { b ->
                BurnedBars(
                    total = policy.resolve(Metric.EnergyOut, b.totalKcal.toDouble(), Surface.Today, bare),
                    resting = policy.resolve(Metric.EnergyOut, b.resting.kcal.toDouble(), Surface.Today, bare),
                    // Digesting food, when counted, rides on the active bar so the two still end at the tick.
                    active = policy.resolve(Metric.EnergyOut, b.aboveRestingKcal.toDouble(), Surface.Today, bare),
                    restingFraction = (b.resting.kcal / scale).toFloat().coerceIn(0f, 1f),
                    activeFraction = (b.aboveRestingKcal / scale).toFloat().coerceIn(0f, 1f),
                    tick = (b.totalKcal / scale).toFloat().coerceIn(0f, 1f),
                    breakdown = settings.todayBurnedBreakdown,
                )
            },
            target = if (goal is DayGoal.About && burned != null) {
                val left = target - eaten
                TargetLine(
                    target = policy.resolve(Metric.Calories, target, Surface.Today, bare),
                    left = if (left >= 1.0) policy.resolve(Metric.Calories, left, Surface.Today, context.copy(of = null, bare = false)) else null,
                    surplus = goalKind == MainKind.ToSurplus,
                )
            } else {
                null
            },
            toGoal = gap(policy, context.copy(bare = false), eaten, range, goalKind),
            meals = if (settings.todayMealsCount) MealCount(day.doneMealCount, MealSlot.meals.size) else null,
            mainFraction = when (main.kind) {
                MainKind.Deficit, MainKind.Surplus -> burned?.let { (abs(it.totalKcal - eaten) / it.totalKcal).toFloat() } ?: 0f
                MainKind.Eaten -> (eaten / target).toFloat()
                else -> ((target - eaten) / target).toFloat()
            }.coerceIn(0f, 1f),
            eatenFraction = (eaten / target).toFloat().coerceIn(0f, 1f),
        )
    }

    /** Which gap the goal view shows: to a range's low end, to one number, or to a surplus's target. */
    private fun goalKind(style: TargetStyle, goal: DayGoal): MainKind = when {
        goal is DayGoal.Range -> MainKind.ToRange
        style is TargetStyle.Surplus -> MainKind.ToSurplus
        style is TargetStyle.ByDay && goal is DayGoal.About &&
            (style.groups.getOrNull(goal.groupIndex ?: -1)?.shape is GroupShape.Surplus) -> MainKind.ToSurplus
        else -> MainKind.LeftToTarget
    }

    /** As Today's "To your range" stat: the amount still to go, or where eaten sits once the goal is reached. */
    private fun gap(policy: VisibilityPolicy, context: FormatContext, eaten: Double, range: EnergyRange, kind: MainKind): MainNumber {
        val to = if (kind == MainKind.ToRange) range.minKcal else range.targetKcal
        val gap = to - eaten
        val plain = context.copy(of = null)
        return if (gap >= 1.0) {
            MainNumber(kind, Metric.Calories, policy.resolve(Metric.Calories, gap, Surface.Today, plain))
        } else {
            val bounds = plain.copy(eaten = eaten, min = range.minKcal.toDouble(), max = range.maxKcal.toDouble())
            MainNumber(kind, Metric.Remaining, policy.resolve(Metric.Remaining, gap, Surface.Today, bounds))
        }
    }

    private fun goalLine(style: TargetStyle, goal: DayGoal, policy: VisibilityPolicy, bare: FormatContext, range: EnergyRange): GoalLine {
        fun kcal(value: Int) = policy.resolve(Metric.Calories, value.toDouble(), Surface.Today, bare)
        return when {
            style is TargetStyle.Deficit && goal is DayGoal.About -> GoalLine.DeficitGoal(kcal(style.kcal))
            style is TargetStyle.Surplus && goal is DayGoal.About -> GoalLine.SurplusGoal(kcal(style.kcal))
            range.single -> GoalLine.Target(kcal(range.targetKcal))
            else -> GoalLine.Range(policy.resolveRange(Metric.Calories, range.minKcal.toDouble(), range.maxKcal.toDouble(), Surface.Today, bare))
        }
    }
}
