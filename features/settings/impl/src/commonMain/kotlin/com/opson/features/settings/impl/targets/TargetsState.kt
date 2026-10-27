package com.opson.features.settings.impl.targets

import com.opson.libraries.diary.MacroMode
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.ExerciseAdjustment
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.GroupShape
import com.opson.libraries.nutrition.MAX_SURPLUS_KCAL
import com.opson.libraries.nutrition.Metric
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

/** The goal styles, as the picker names them. [Exact] and [Surplus] are `release.goalStyles`. */
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

/** A day group's shape, as its card names it. Offered only with `release.goalStyles`. */
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
    val style: StyleKind = StyleKind.Range,
    /** The low and high end steppers, for [StyleKind.Range]. */
    val range: RangeCard? = null,
    /** The one number's stepper, for [StyleKind.Exact]. Its "−" is locked at the floor. */
    val number: EndStepper? = null,
    /** The deficit stepper and what it comes to, for [StyleKind.Deficit]. */
    val deficit: DeficitCard? = null,
    /** The surplus stepper and what it comes to, for [StyleKind.Surplus]. */
    val surplus: SurplusCard? = null,
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
)

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
 * The deficit card. [burn] and [goal] are null until a burn is known (0.1 has no source yet),
 * and the card says so instead.
 *
 * [up] is [StepState.Locked] at the cap: while `MAX_DEFICIT_KCAL` is unset that is where the
 * deficit started, which is 0 for one picked with no burn known. The lock and its note say why
 * the step is gone, where a greyed-out "+" looked like a stepper that had stopped working.
 */
data class DeficitCard(
    /** "300", bare: the big number. */
    val amount: Display,
    /** Open, or Closed at 0. */
    val down: StepState,
    /** Open, or Locked at the cap. */
    val up: StepState,
    /** "2,172 kcal", the 7-day average. */
    val burn: Display?,
    /** "1,870 kcal", inside "About %s". */
    val goal: Display?,
)

/**
 * The surplus card. [goal] is always known: what the user burns plus the surplus, or their usual
 * target plus the surplus while [burn] is not known yet ([fromTarget]).
 */
data class SurplusCard(
    /** "300", bare: the big number. */
    val amount: Display,
    /** Open, or Closed at 0. */
    val down: StepState,
    /** Open, or Locked at `MAX_SURPLUS_KCAL`. */
    val up: StepState,
    /** "2,172 kcal", the 7-day average; null until known. */
    val burn: Display?,
    /** "2,400 kcal", inside "About %s". */
    val goal: Display,
    val fromTarget: Boolean,
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
    /** The shapes to pick from while editing, with `release.goalStyles`; empty otherwise. */
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
     * [burnKcal] is what the user burns, a 7-day average, when the app knows it (0.1 never
     * does); [editingGroup] and [editingMacros] are the view model's own UI state.
     * [healthConnected] is `release.health` on and a store connected: only then does the
     * Exercise adjustment row show, and never for someone who said numbers have been hard.
     * [goalStyles] is `release.goalStyles`: whether a day group can be given a shape.
     */
    fun build(
        settings: Settings,
        policy: VisibilityPolicy,
        burnKcal: Int? = null,
        editingGroup: Int? = null,
        editingMacros: Boolean = false,
        healthConnected: Boolean = false,
        goalStyles: Boolean = false,
    ): TargetsState {
        val targets = settings.targets
        if (policy.level(Metric.Calories) != Visibility.Show) {
            return TargetsState(visible = false, macroMode = settings.macroMode)
        }
        val style = StyleKind.of(targets.style)
        val byDay = targets.style as? TargetStyle.ByDay
        val groups = GroupInputs(policy, settings.weekStart, editingGroup, burnKcal, goalStyles)
        return TargetsState(
            visible = true,
            style = style,
            range = if (style == StyleKind.Range) rangeCard(targets, policy) else null,
            number = if (style == StyleKind.Exact) numberStepper(targets, policy, targets.energyKcal, group = null) else null,
            deficit = (targets.style as? TargetStyle.Deficit)?.let { deficitCard(targets, it, policy, burnKcal) },
            surplus = (targets.style as? TargetStyle.Surplus)?.let { surplusCard(targets, it, policy, burnKcal) },
            groups = byDay?.let { groupCards(targets, it, groups) }.orEmpty(),
            week = byDay?.let { weekBands(targets, it, settings.weekStart, burnKcal) }.orEmpty(),
            canAddGroup = byDay != null && byDay.groups.size < TargetStyle.MAX_GROUPS,
            editingGroup = editingGroup?.takeIf { byDay != null && it in byDay.groups.indices },
            macroMode = settings.macroMode,
            macros = macros(targets, policy),
            editingMacros = editingMacros,
            exercise = settings.exerciseInRange.takeIf { healthConnected && !settings.onboarding.hard },
        )
    }

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
        up = if (kcal < MAX_SURPLUS_KCAL) StepState.Open else StepState.Locked,
    )

    private fun surplusCard(targets: Targets, surplus: TargetStyle.Surplus, policy: VisibilityPolicy, burnKcal: Int?): SurplusCard {
        val stepper = surplusStepper(targets, policy, surplus.kcal, group = null)
        return SurplusCard(
            amount = stepper.value,
            down = stepper.down,
            up = stepper.up,
            burn = burnKcal?.let { policy.resolve(Metric.EnergyOut, it.toDouble(), Surface.Settings) },
            goal = policy.resolve(Metric.Calories, targets.surplusGoal(targets.energyKcal, burnKcal, surplus.kcal).toDouble(), Surface.Settings),
            fromTarget = burnKcal == null,
        )
    }

    private fun StepBlock?.toStepState(): StepState = when (this) {
        null -> StepState.Open
        StepBlock.Floor -> StepState.Locked
        StepBlock.Gap -> StepState.Closed
    }

    private fun deficitCard(
        targets: Targets,
        deficit: TargetStyle.Deficit,
        policy: VisibilityPolicy,
        burnKcal: Int?,
    ) = DeficitCard(
        amount = policy.resolve(Metric.Calories, deficit.kcal.toDouble(), Surface.Settings, FormatContext(bare = true)),
        down = if (targets.stepDeficit(up = false) != null) StepState.Open else StepState.Closed,
        up = if (targets.stepDeficit(up = true) != null) StepState.Open else StepState.Locked,
        // What the user burns is energy out, so it follows that metric's level.
        burn = burnKcal?.let { policy.resolve(Metric.EnergyOut, it.toDouble(), Surface.Settings) },
        goal = burnKcal?.let {
            policy.resolve(Metric.Calories, targets.deficitGoal(it, deficit.kcal).toDouble(), Surface.Settings)
        },
    )

    /** What every group card is built with, apart from the group. */
    private class GroupInputs(
        val policy: VisibilityPolicy,
        val weekStart: DayOfWeek,
        val editingGroup: Int?,
        val burnKcal: Int?,
        val goalStyles: Boolean,
    )

    private fun groupCards(targets: Targets, byDay: TargetStyle.ByDay, inputs: GroupInputs): List<DayGroupCard> =
        byDay.groups.mapIndexed { index, group ->
            val policy = inputs.policy
            val editing = inputs.editingGroup == index
            val shape = ShapeKind.of(group.shape)
            val goal = requireNotNull(targets.groupGoal(index, inputs.burnKcal))
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
                shapes = if (editing && inputs.goalStyles) ShapeKind.entries else emptyList(),
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
    private fun weekBands(targets: Targets, byDay: TargetStyle.ByDay, weekStart: DayOfWeek, burnKcal: Int?): List<WeekBand> {
        val mids = byDay.groups.indices.map { requireNotNull(targets.groupGoal(it, burnKcal)).targetKcal }
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
