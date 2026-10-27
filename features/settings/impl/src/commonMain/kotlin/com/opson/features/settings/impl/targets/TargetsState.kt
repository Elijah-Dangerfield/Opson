package com.opson.features.settings.impl.targets

import com.opson.libraries.diary.MacroMode
import com.opson.libraries.diary.Settings
import com.opson.libraries.health.HealthSource
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.ExerciseAdjustment
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.GroupShape
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RestingFrom
import com.opson.libraries.nutrition.RangeEnd
import com.opson.libraries.nutrition.StepBlock
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.macroEnergy
import com.opson.libraries.nutrition.resolveRange
import com.opson.libraries.nutrition.share
import kotlinx.datetime.DayOfWeek

/** The goal styles, as the list names them. [Exact] and [Surplus] are `release.goalsAndEnergy`. */
enum class StyleKind {
    Range,
    Exact,
    Deficit,
    Surplus,
    ByDay,
    ;

    companion object {
        fun of(style: TargetStyle): StyleKind = when (style) {
            TargetStyle.Range -> Range
            TargetStyle.Exact -> Exact
            is TargetStyle.Deficit -> Deficit
            is TargetStyle.Surplus -> Surplus
            is TargetStyle.ByDay -> ByDay
        }
    }
}

/** A day group's shape, as its card names it. Offered only with `release.goalsAndEnergy`. */
enum class ShapeKind {
    Range,
    Exact,
    Surplus,
    ;

    companion object {
        fun of(shape: GroupShape): ShapeKind = when (shape) {
            GroupShape.Range -> Range
            GroupShape.Exact -> Exact
            is GroupShape.Surplus -> Surplus
        }
    }
}

data class TargetsState(
    /** Calories configured to Show. Otherwise the page is the "Targets work in the background" card. */
    val visible: Boolean = false,
    /** The style whose card is open: the saved one, or a range when the mode doesn't offer it. */
    val style: StyleKind = StyleKind.Range,
    /** The goal style list (design sync 4, 13.2e), in order. */
    val styles: List<StyleRow> = emptyList(),
    /** The low and high end steppers, for [StyleKind.Range]. */
    val range: RangeCard? = null,
    /** The one number's stepper, for [StyleKind.Exact]. Its "−" is locked at the floor. */
    val number: EndStepper? = null,
    /** The deficit stepper and today's target, for [StyleKind.Deficit]. */
    val deficit: BurnGoalCard? = null,
    /** The surplus stepper and today's target, for [StyleKind.Surplus]. */
    val surplus: BurnGoalCard? = null,
    /** One card per day group, for [StyleKind.ByDay]. */
    val groups: List<DayGroupCard> = emptyList(),
    /** Each weekday's band, in the user's week order, for [StyleKind.ByDay]. */
    val week: List<WeekBand> = emptyList(),
    val canAddGroup: Boolean = false,
    /** The day group whose steppers are open. */
    val editingGroup: Int? = null,
    val macroMode: MacroMode = MacroMode.Ratios,
    /** Protein, carbs and fat that are effectively Show. A hidden macro drops out of the card. */
    val macros: List<TargetMacroRow> = emptyList(),
    /** The Ratios / Grams editor is open in the macro card. */
    val editingMacros: Boolean = false,
    /**
     * The Exercise adjustment row (v3 `targets.png`, 0.2): the answer that applies, or null when
     * the row is not shown (`release.health` off, no store connected, or numbers have been hard).
     */
    val exercise: ExerciseAdjustment? = null,
    /** "Burned comes from" (design sync 4), with `release.goalsAndEnergy`; null without. */
    val burned: BurnedCard? = null,
)

/**
 * One row of the goal style list. [range] is the Range row's own "1,900 to 2,300 kcal"; the
 * deficit and surplus rows say what they do in words, and the others say nothing.
 */
data class StyleRow(val kind: StyleKind, val selected: Boolean, val range: Display? = null)

/**
 * What a step does: move, show a lock (a limit only a dietitian can move: the floor under a
 * range, the cap over a deficit), or nothing (the ends would touch, or the deficit is at 0).
 */
enum class StepState { Open, Locked, Closed }

/** One end: "1,900", bare, with its two steps. */
data class EndStepper(val value: Display, val down: StepState, val up: StepState)

/**
 * The range card. [band] is where the range sits on the track below the steppers, 0 to 1 on a
 * shared scale; it is only built when the range itself is shown.
 */
data class RangeCard(
    val low: EndStepper,
    val high: EndStepper,
    val band: ClosedFloatingPointRange<Float>,
    /** "The low end can't go down until a dietitian sets a safe floor." */
    val lowLocked: Boolean,
)

/**
 * A set deficit or surplus: the amount with its steps, and the target it comes to today,
 * "Today's target 1,680 · moves with what you burn".
 *
 * [up] is [StepState.Locked] at the cap: while `MAX_DEFICIT_KCAL` or `MAX_SURPLUS_KCAL` is unset
 * that is where the amount started, which is 0 for one picked with no burn known. The lock and
 * its note say why the step is gone, where a greyed-out "+" looked like a stepper that had
 * stopped working. [down] is Closed at 0.
 *
 * [target] is null for a deficit until Burned is known ("Your range, for now"). A surplus always
 * has one: over the usual target until Burned is known ([fromAim]).
 */
data class BurnGoalCard(
    /** "300", bare: the big number. */
    val amount: Display,
    val down: StepState,
    val up: StepState,
    val target: Display?,
    val fromAim: Boolean = false,
)

/**
 * "Burned comes from": resting energy as it is now and where it comes from, and the activity
 * source. [resting] is energy out, so it follows that metric's level; [restingFrom] is null
 * while no source can say ("Add your details"). [activity] is the health app, null when not
 * connected or `release.health` is off.
 */
data class BurnedCard(
    val resting: Display,
    val restingFrom: RestingFrom?,
    val activity: HealthSource?,
)

/** How a day group is named: its own rule, never a name the user typed (0.1 has no naming). */
sealed interface GroupLabel {
    /** The only group, holding every day. */
    data object EveryDay : GroupLabel

    /** The first group once others exist: whatever days are left. */
    data object OtherDays : GroupLabel

    /** "Day group 2". */
    data class Numbered(val number: Int) : GroupLabel
}

data class DayChip(val day: DayOfWeek, val inGroup: Boolean)

data class DayGroupCard(
    val index: Int,
    val label: GroupLabel,
    val days: List<DayChip>,
    val shape: ShapeKind,
    /** "2,300 to 2,700 kcal" for a range, "2,500 kcal" for one number, "2,800 kcal" (inside "About %s") for a surplus. */
    val summary: Display,
    val editing: Boolean,
    /** The shapes to pick from while editing, with `release.goalsAndEnergy`; empty otherwise. */
    val shapes: List<ShapeKind>,
    /** The end steppers, while a range is being edited. */
    val low: EndStepper?,
    val high: EndStepper?,
    /** The number's stepper, while one number is being edited. */
    val number: EndStepper?,
    /** The surplus stepper ("300"), while a surplus is being edited. */
    val surplus: EndStepper?,
    val removable: Boolean,
)

/** One day in the week strip: which group it is in and where that group's range sits, 0 to 1. */
data class WeekBand(val day: DayOfWeek, val groupIndex: Int, val position: Float)

/** A macro in the split: "120 g protein", with its share of the energy for the bar. */
data class TargetMacroRow(val metric: Metric, val share: Display, val grams: Display, val weight: Float)

object TargetsStateBuilder {

    /** The track's scale for the range band. */
    private const val SCALE_MIN = 1000.0
    private const val SCALE_HEADROOM = 500

    /**
     * Amounts only when calories are configured Show. A delayed mode after the reveal would
     * resolve them, but this page is not where a delayed day opens, so Soft and Delay modes get
     * the background card and no amounts at all, not even in state.
     *
     * [burned] is today's Burned (`BurnedRepository`), null while unknown or without
     * `release.goalsAndEnergy`; a set deficit or surplus is aimed at its total. [editingGroup]
     * and [editingMacros] are the view model's own UI state. [healthConnected] is
     * `release.health` on and a store connected: only then does the Exercise adjustment row
     * show, and never for someone who said numbers have been hard. [goalsAndEnergy] is
     * `release.goalsAndEnergy`: one number and a surplus in the list, a day group's shape, and
     * "Burned comes from"; [healthSource] is the connected store, for its Activity row.
     */
    fun build(
        settings: Settings,
        policy: VisibilityPolicy,
        burned: Burned? = null,
        editingGroup: Int? = null,
        editingMacros: Boolean = false,
        healthConnected: Boolean = false,
        goalsAndEnergy: Boolean = false,
        healthSource: HealthSource? = null,
    ): TargetsState {
        val targets = settings.targets
        if (policy.level(Metric.Calories) != Visibility.Show) {
            // No calorie goal, but macros on show (Macros with grams) keep their gram targets.
            return TargetsState(visible = false, macroMode = settings.macroMode, macros = macros(targets, policy), editingMacros = editingMacros)
        }
        val preset = settings.numbers.preset
        val burnKcal = burned?.totalKcal
        val offered = offered(StyleKind.of(targets.style), preset, goalsAndEnergy)
        val style = StyleKind.of(targets.style).takeIf { it in offered } ?: StyleKind.Range
        val byDay = (targets.style as? TargetStyle.ByDay)?.takeIf { style == StyleKind.ByDay }
        val groups = GroupInputs(policy, settings.weekStart, editingGroup, burnKcal, goalsAndEnergy, preset)
        return TargetsState(
            visible = true,
            style = style,
            styles = offered.map { kind ->
                StyleRow(
                    kind = kind,
                    selected = kind == style,
                    range = if (kind == StyleKind.Range) {
                        policy.resolveRange(Metric.Calories, targets.minKcal.toDouble(), targets.maxKcal.toDouble(), Surface.Settings)
                    } else {
                        null
                    },
                )
            },
            range = if (style == StyleKind.Range) rangeCard(targets, policy) else null,
            number = if (style == StyleKind.Exact) numberStepper(targets, policy, targets.energyKcal, group = null) else null,
            deficit = (targets.style as? TargetStyle.Deficit)?.takeIf { style == StyleKind.Deficit }?.let { deficitCard(targets, it, policy, burnKcal) },
            surplus = (targets.style as? TargetStyle.Surplus)?.takeIf { style == StyleKind.Surplus }?.let { surplusCard(targets, it, policy, burnKcal) },
            groups = byDay?.let { groupCards(targets, it, groups) }.orEmpty(),
            week = byDay?.let { weekBands(targets, it, groups) }.orEmpty(),
            canAddGroup = byDay != null && byDay.groups.size < TargetStyle.MAX_GROUPS,
            editingGroup = editingGroup?.takeIf { byDay != null && it in byDay.groups.indices },
            macroMode = settings.macroMode,
            macros = macros(targets, policy),
            editingMacros = editingMacros,
            exercise = settings.exerciseInRange.takeIf { healthConnected && !settings.onboarding.hard },
            burned = if (goalsAndEnergy) burnedCard(burned, policy, healthSource) else null,
        )
    }

    /**
     * The styles the list offers: a set deficit and a set surplus only where the mode offers them
     * (All numbers), one number and a surplus with `release.goalsAndEnergy` or when already saved.
     */
    fun offered(saved: StyleKind, preset: PresetId, goalsAndEnergy: Boolean): List<StyleKind> = StyleKind.entries.filter {
        when (it) {
            StyleKind.Deficit -> TargetStyle.deficitAllowed(preset)
            StyleKind.Surplus -> TargetStyle.surplusAllowed(preset) && (goalsAndEnergy || it == saved)
            StyleKind.Exact -> goalsAndEnergy || it == saved
            StyleKind.Range, StyleKind.ByDay -> true
        }
    }

    private fun burnedCard(burned: Burned?, policy: VisibilityPolicy, healthSource: HealthSource?) = BurnedCard(
        resting = burned?.let { policy.resolve(Metric.EnergyOut, it.resting.kcal.toDouble(), Surface.Settings, FormatContext(bare = true)) }
            ?: Display.Hidden,
        restingFrom = burned?.resting?.from,
        activity = healthSource,
    )

    private fun rangeCard(targets: Targets, policy: VisibilityPolicy): RangeCard {
        val top = (targets.maxKcal + SCALE_HEADROOM).coerceAtLeast(3000).toDouble()
        fun place(kcal: Int) = ((kcal - SCALE_MIN) / (top - SCALE_MIN)).toFloat().coerceIn(0f, 1f)
        return RangeCard(
            low = stepper(targets, policy, RangeEnd.Low, targets.minKcal, group = null),
            high = stepper(targets, policy, RangeEnd.High, targets.maxKcal, group = null),
            band = place(targets.minKcal)..place(targets.maxKcal),
            lowLocked = targets.stepBlock(RangeEnd.Low, up = false) == StepBlock.Floor,
        )
    }

    private fun stepper(targets: Targets, policy: VisibilityPolicy, end: RangeEnd, kcal: Int, group: Int?) = EndStepper(
        value = policy.resolve(Metric.Calories, kcal.toDouble(), Surface.Settings, FormatContext(bare = true)),
        down = targets.stepBlock(end, up = false, group).toStepState(),
        up = targets.stepBlock(end, up = true, group).toStepState(),
    )

    private fun bare(policy: VisibilityPolicy, kcal: Int): Display =
        policy.resolve(Metric.Calories, kcal.toDouble(), Surface.Settings, FormatContext(bare = true))

    /** One number: "−" locked at the floor, "+" always open. */
    private fun numberStepper(targets: Targets, policy: VisibilityPolicy, kcal: Int, group: Int?) = EndStepper(
        value = bare(policy, kcal),
        down = if (targets.canStepNumber(up = false, group)) StepState.Open else StepState.Locked,
        up = StepState.Open,
    )

    /** A surplus: "−" closed at 0, "+" locked at the cap. */
    private fun surplusStepper(targets: Targets, policy: VisibilityPolicy, kcal: Int, group: Int?) = EndStepper(
        value = bare(policy, kcal),
        down = if (targets.stepSurplus(up = false, group) != null) StepState.Open else StepState.Closed,
        up = if (targets.stepSurplus(up = true, group) != null) StepState.Open else StepState.Locked,
    )

    private fun surplusCard(targets: Targets, surplus: TargetStyle.Surplus, policy: VisibilityPolicy, burnKcal: Int?): BurnGoalCard {
        val stepper = surplusStepper(targets, policy, surplus.kcal, group = null)
        return BurnGoalCard(
            amount = stepper.value,
            down = stepper.down,
            up = stepper.up,
            target = policy.resolve(Metric.Calories, targets.surplusGoal(targets.energyKcal, burnKcal, surplus.kcal).toDouble(), Surface.Settings),
            fromAim = burnKcal == null,
        )
    }

    private fun StepBlock?.toStepState(): StepState = when (this) {
        null -> StepState.Open
        StepBlock.Floor -> StepState.Locked
        StepBlock.Gap -> StepState.Closed
    }

    private fun deficitCard(targets: Targets, deficit: TargetStyle.Deficit, policy: VisibilityPolicy, burnKcal: Int?) = BurnGoalCard(
        amount = bare(policy, deficit.kcal),
        down = if (targets.stepDeficit(up = false) != null) StepState.Open else StepState.Closed,
        up = if (targets.stepDeficit(up = true) != null) StepState.Open else StepState.Locked,
        target = burnKcal?.let { policy.resolve(Metric.Calories, targets.deficitGoal(it, deficit.kcal).toDouble(), Surface.Settings) },
    )

    /** What every group card is built with, apart from the group. */
    private class GroupInputs(
        val policy: VisibilityPolicy,
        val weekStart: DayOfWeek,
        val editingGroup: Int?,
        val burnKcal: Int?,
        val goalsAndEnergy: Boolean,
        val preset: PresetId,
    )

    private fun groupCards(targets: Targets, byDay: TargetStyle.ByDay, inputs: GroupInputs): List<DayGroupCard> =
        byDay.groups.mapIndexed { index, group ->
            val policy = inputs.policy
            val editing = inputs.editingGroup == index
            val shape = ShapeKind.of(group.shape)
            val goal = requireNotNull(targets.groupGoal(index, inputs.preset, inputs.burnKcal))
            DayGroupCard(
                index = index,
                label = when {
                    index > 0 -> GroupLabel.Numbered(index + 1)
                    byDay.groups.size == 1 -> GroupLabel.EveryDay
                    else -> GroupLabel.OtherDays
                },
                days = week(inputs.weekStart).map { DayChip(it, inGroup = it in group.days) },
                shape = shape,
                summary = policy.resolveRange(Metric.Calories, goal.minKcal.toDouble(), goal.maxKcal.toDouble(), Surface.Settings),
                editing = editing,
                shapes = if (editing && inputs.goalsAndEnergy) shapes(inputs.preset) else emptyList(),
                low = if (editing && shape == ShapeKind.Range) stepper(targets, policy, RangeEnd.Low, group.minKcal, index) else null,
                high = if (editing && shape == ShapeKind.Range) stepper(targets, policy, RangeEnd.High, group.maxKcal, index) else null,
                number = if (editing && shape == ShapeKind.Exact) numberStepper(targets, policy, group.midKcal, index) else null,
                surplus = (group.shape as? GroupShape.Surplus)?.takeIf { editing }?.let { surplusStepper(targets, policy, it.kcal, index) },
                removable = editing && index > 0,
            )
        }

    /**
     * Each day's band on one scale, so a lopsided week shows before it is saved. The lowest
     * group's goal sits at 0 and the highest at 1; with one level, every day sits in the middle.
     */
    /** A surplus per group only where the mode offers one, as the style. */
    private fun shapes(preset: PresetId): List<ShapeKind> =
        ShapeKind.entries.filter { it != ShapeKind.Surplus || TargetStyle.surplusAllowed(preset) }

    private fun weekBands(targets: Targets, byDay: TargetStyle.ByDay, inputs: GroupInputs): List<WeekBand> {
        val weekStart = inputs.weekStart
        val mids = byDay.groups.indices.map { requireNotNull(targets.groupGoal(it, inputs.preset, inputs.burnKcal)).targetKcal }
        val lowest = mids.min()
        val spread = (mids.max() - lowest).takeIf { it > 0 }
        return week(weekStart).map { day ->
            val index = byDay.groups.indexOfFirst { day in it.days }.coerceAtLeast(0)
            val position = spread?.let { (mids[index] - lowest).toFloat() / it } ?: 0.5f
            WeekBand(day, index, position)
        }
    }

    private fun week(start: DayOfWeek): List<DayOfWeek> =
        DayOfWeek.entries.let { days -> days.drop(start.ordinal) + days.take(start.ordinal) }

    /** Each macro's energy as a share of the energy target. Fiber is a floor, not a share. */
    private fun macros(targets: Targets, policy: VisibilityPolicy): List<TargetMacroRow> {
        val energies = macroEnergy(
            protein = targets.grams(Metric.Protein).toDouble(),
            carbs = targets.grams(Metric.Carbs).toDouble(),
            fat = targets.grams(Metric.Fat).toDouble(),
        )
        return energies.mapNotNull { (metric, energy) ->
            if (policy.effectiveLevel(metric, Surface.Settings) != Visibility.Show) return@mapNotNull null
            val grams = targets.grams(metric).toDouble()
            val percent = if (targets.energyKcal > 0) energy / targets.energyKcal * 100 else Double.NaN
            TargetMacroRow(
                metric = metric,
                share = policy.share(metric, percent, Surface.Settings),
                grams = policy.resolve(metric, grams, Surface.Settings),
                weight = energy.toFloat(),
            )
        }
    }

    private fun Targets.grams(metric: Metric): Int = when (metric) {
        Metric.Protein -> proteinG
        Metric.Carbs -> carbsG
        Metric.Fat -> fatG
        else -> error("Targets has no gram goal for $metric")
    }
}
