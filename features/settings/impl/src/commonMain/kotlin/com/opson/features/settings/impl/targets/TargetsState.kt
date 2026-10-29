package com.opson.features.settings.impl.targets

import com.opson.libraries.diary.MacroMode
import com.opson.libraries.diary.Settings
import com.opson.libraries.health.HealthSource
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.BodyStage
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.WeightGoal
import com.opson.libraries.nutrition.WeightGoals
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.ExerciseAdjustment
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.GoalSafety
import com.opson.libraries.nutrition.GroupShape
import com.opson.libraries.nutrition.MAX_GOAL_KCAL
import com.opson.libraries.nutrition.MAX_MACRO_G
import com.opson.libraries.nutrition.MacroPreset
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RangeEnd
import com.opson.libraries.nutrition.RestingFrom
import com.opson.libraries.nutrition.SplitMacros
import com.opson.libraries.nutrition.StepBlock
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.grams
import com.opson.libraries.nutrition.macroEnergy
import com.opson.libraries.nutrition.macroFit
import com.opson.libraries.nutrition.macroPercent
import com.opson.libraries.nutrition.preset
import com.opson.libraries.nutrition.resolveRange
import com.opson.libraries.nutrition.share
import com.opson.libraries.nutrition.useMacroEnergy
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.math.abs

/** The goal styles, as the list names them. [Exact], [Deficit] and [Surplus] are `release.goalsAndEnergy`. */
enum class StyleKind {
    Range,
    Exact,
    Deficit,
    Surplus,
    ByDay,
    ;

    /** Aimed at what the person burns. */
    val needsBurn: Boolean get() = this == Deficit || this == Surplus

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

/** A day group's shape, as its card names it. Every style but Different by day itself. */
enum class ShapeKind {
    Range,
    Exact,
    Deficit,
    Surplus,
    ;

    companion object {
        fun of(shape: GroupShape): ShapeKind = when (shape) {
            GroupShape.Range -> Range
            GroupShape.Exact -> Exact
            is GroupShape.Deficit -> Deficit
            is GroupShape.Surplus -> Surplus
        }
    }
}

data class TargetsState(
    /** False until the settings, the policy and Burned have all arrived; the page shows a spinner, not the background card. */
    val loaded: Boolean = false,
    /** Calories configured to Show. Otherwise the page is the "Targets work in the background" card. */
    val visible: Boolean = false,
    /** The style whose card is open: the saved one, or a range when the mode doesn't offer it. */
    val style: StyleKind = StyleKind.Range,
    /** The goal style list (design sync 4, 13.2e), in order. */
    val styles: List<StyleRow> = emptyList(),
    /** The low and high end steppers, for [StyleKind.Range]. */
    val range: RangeCard? = null,
    /** The one number's stepper and typed field, for [StyleKind.Exact]. Its "−" is locked at the minimum. */
    val number: NumberCard? = null,
    /** The deficit stepper and today's target, for [StyleKind.Deficit]. */
    val deficit: BurnGoalCard? = null,
    /** The surplus stepper and today's target, for [StyleKind.Surplus]. */
    val surplus: BurnGoalCard? = null,
    /** One card per day group, for [StyleKind.ByDay], each with its controls open. */
    val groups: List<DayGroupCard> = emptyList(),
    /** Each weekday's band, in the user's week order, for [StyleKind.ByDay]. */
    val week: List<WeekBand> = emptyList(),
    val canAddGroup: Boolean = false,
    /** A gentle note under the goal when it is low or its pace fast; never a block. */
    val safety: SafetyNote? = null,
    /** The note links to Find support (`release.findSupport`). */
    val supportLink: Boolean = false,
    val macroMode: MacroMode = MacroMode.Ratios,
    /** Protein, carbs and fat that are effectively Show. A hidden macro drops out of the card. */
    val macros: List<TargetMacroRow> = emptyList(),
    /** The macro editor, while it is open in the macro card. */
    val macroEditor: MacroEditor? = null,
    /**
     * The Exercise adjustment row (v3 `targets.png`, 0.2): the answer that applies, or null when
     * the row is not shown (`release.health` off, no store connected, or numbers have been hard).
     */
    val exercise: ExerciseAdjustment? = null,
    /** "Burned comes from" (design sync 4), with `release.goalsAndEnergy`; null without. */
    val burned: BurnedCard? = null,
    /** The Vitamins and minerals row: wherever the mode shows them, calories or not. */
    val micros: Boolean = false,
    /** The weight goal's row at the top: whether it sets the target, or an offer to set one. Null where none is offered. */
    val weightGoal: WeightGoalRow? = null,
) {
    val editingMacros: Boolean get() = macroEditor != null
}

/**
 * Whether the weight goal sets the day's target (owner feedback, 2026-10-29: no silent conflicts).
 * A goal stamps the aim it wrote ([WeightGoal.drives]); once the person moves the target by hand
 * it is theirs, and the row says so instead of the goal quietly taking it back.
 */
sealed interface WeightGoalRow {
    /** The goal sets it. [pace] is "0.5 lb" a week, where weight shows; null while keeping weight steady. */
    data class Driving(val direction: GoalDirection, val pace: String?) : WeightGoalRow

    /**
     * A goal is set, and it is not setting the target: changed by hand since ([byHand]), or never
     * set because what a day burns wasn't known. [canUseGoal] once a typical day's burn is known.
     */
    data class NotDriving(val canUseGoal: Boolean, val byHand: Boolean = true) : WeightGoalRow

    /** No weight goal yet: an offer to set one. */
    data object None : WeightGoalRow
}

/**
 * One row of the goal style list. [range] is the Range row's own "1,900 to 2,300 kcal"; the
 * deficit and surplus rows say what they do in words, and the others say nothing.
 */
data class StyleRow(val kind: StyleKind, val selected: Boolean, val range: Display? = null)

/**
 * What a step does: move, show a lock (a limit the person can't move: the lowest or highest a
 * goal goes, the cap over a deficit), or nothing (the ends would touch, or the deficit is at 0).
 */
enum class StepState { Open, Locked, Closed }

/**
 * The note under the goal (`GoalSafety`): [LowGoal] when the least a day's goal asks for is under
 * the low-goal line for the person's sex, [FastDeficit] when a set deficit is more than a healthy
 * pace. Only one shows, the low goal first. Neither stops anything; both link to Find support where `release.findSupport` is on.
 */
enum class SafetyNote { LowGoal, FastDeficit }

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
    /** The low end is as low as a goal goes ([Targets.floorKcal]), so its "−" is a lock and the note says why. */
    val lowLocked: Boolean,
)

/**
 * One number: its stepper, and [text], what the typed field holds (a draft while one is being
 * typed, else the number). [floor] and [ceiling] are the lowest and highest it can be, said when a
 * typed number is past one ([tooLow], [tooHigh]). A typed number is only too low once no more
 * digits could bring it up (or the keyboard's Done was pressed), so "15" on the way to "1500"
 * says nothing.
 */
data class NumberCard(
    val stepper: EndStepper,
    val text: String,
    val floor: Display,
    val tooLow: Boolean = false,
    val ceiling: Display = Display.Hidden,
    val tooHigh: Boolean = false,
)

/**
 * A set deficit or surplus: the amount with its steps, and the target it comes to today,
 * "Today's target 1,680 · moves with what you burn".
 *
 * [up] is [StepState.Locked] at the cap: while `MAX_DEFICIT_KCAL` or `MAX_SURPLUS_KCAL` is unset
 * that is where the amount started. The lock and its note say what the number is based on, where
 * a greyed-out "+" looked like a stepper that had stopped working. [down] is Closed at 0.
 *
 * [burned] is what the target is measured against, energy out ("Based on about 2,180 burned
 * today"), and [estimated] says its movement is the everyday activity estimate rather than a
 * health app's reading. [needsDetails]: no Burned yet because the resting energy calculation is
 * missing a detail; the card asks for them instead of a target. A deficit's [target] is null
 * then; a surplus still has one, over the usual target ([fromAim]).
 *
 * [canStartFromBurn]: the amount started at 0 because nothing was known about the burn when it
 * was picked, and now something is. Starting again from today's burn is the same as picking the
 * style afresh, so it can't go past what the goal already implies.
 */
data class BurnGoalCard(
    /** "300", bare: the big number. */
    val amount: Display,
    val down: StepState,
    val up: StepState,
    val target: Display?,
    val fromAim: Boolean = false,
    val burned: Display? = null,
    val estimated: Boolean = false,
    val needsDetails: Boolean = false,
    val canStartFromBurn: Boolean = false,
)

/**
 * "Burned comes from": resting energy as it is now and where it comes from, and everyday
 * activity. [resting] is energy out, so it follows that metric's level; [restingFrom] is null
 * while no source can say ("Add your details"). [activity] is the health app, null when not
 * connected or `release.health` is off; then [everydayActivity] is what stands in for it, and
 * [levels] is its picker while open.
 */
data class BurnedCard(
    val resting: Display,
    val restingFrom: RestingFrom?,
    val activity: HealthSource?,
    val everydayActivity: EverydayActivity = EverydayActivity.Default,
    val levels: List<EverydayActivity> = emptyList(),
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

/**
 * One weekday in a group's card. [tappable] is false only for the first group's own days, which
 * have nowhere else to go (`Targets.toggleDay`); every other day moves here, or back, on a tap.
 */
data class DayChip(val day: DayOfWeek, val inGroup: Boolean, val tappable: Boolean = true)

/**
 * A day group's card, its controls always open (no edit mode: fewer taps, and nothing hidden).
 * [days] is empty for the only group, where there is nothing to pick until another group exists;
 * [empty] is a group that has no days yet, which is harmless (no day reads it) and says how to
 * fill it. Every group but the first is [removable], its days going back to the first.
 */
data class DayGroupCard(
    val index: Int,
    val label: GroupLabel,
    val days: List<DayChip>,
    val shape: ShapeKind,
    /** "2,300 to 2,700 kcal" for a range, "2,500 kcal" for one number, "2,800 kcal" (inside "About %s") for a deficit or surplus. */
    val summary: Display,
    /** The shapes to pick from, with `release.goalsAndEnergy`; empty otherwise. */
    val shapes: List<ShapeKind>,
    /** The end steppers, for a range. */
    val low: EndStepper?,
    val high: EndStepper?,
    /** The number's stepper, for one number. */
    val number: EndStepper?,
    /** The surplus stepper ("300"), for a surplus. */
    val surplus: EndStepper?,
    /** The deficit stepper ("300"), for a deficit. */
    val deficit: EndStepper? = null,
    /** A deficit day with no Burned yet reads as its range; the card says so. */
    val waitingOnBurn: Boolean = false,
    val removable: Boolean,
    val empty: Boolean = false,
)

/** One day in the week strip: which group it is in and where that group's range sits, 0 to 1. */
data class WeekBand(val day: DayOfWeek, val groupIndex: Int, val position: Float)

/** A macro in the split: "120 g protein", with its share of the energy for the bar. */
data class TargetMacroRow(val metric: Metric, val share: Display, val grams: Display, val weight: Float)

/**
 * The macro editor (like Cronometer's and MyFitnessPal's): protein, carbs and fat in grams or in
 * percents of the calorie goal, a preset or Custom, and what doesn't add up.
 *
 * [percents] is whether percents are on offer at all: only where calories show, since a percent
 * of a hidden goal is the goal in disguise. Where they don't, the editor is grams alone, with no
 * presets and no comparison with calories. [inPercent] is the mode in use.
 */
data class MacroEditor(
    val mode: MacroMode,
    val percents: Boolean,
    val presets: List<PresetChip>,
    val rows: List<MacroEditRow>,
    val fit: MacroFitLine?,
) {
    val inPercent: Boolean get() = percents && mode == MacroMode.Ratios
}

/** A preset and whether the macros match it now, or Custom ([preset] null), there only while none matches. */
data class PresetChip(val preset: MacroPreset?, val selected: Boolean)

/**
 * One macro's row. [text] is what its field holds: a draft while one is being typed, else whole
 * grams or a whole percent. [grams] and [percent] are both said beside it, through the policy;
 * [percent] is Hidden where percents aren't offered.
 */
data class MacroEditRow(
    val metric: Metric,
    val text: String,
    val grams: Display,
    val percent: Display,
    val canLower: Boolean,
    val canRaise: Boolean,
)

/**
 * What the macros come to against the calorie goal, when the two don't agree: "Your macros come
 * to 2,210 kcal, 110 over your goal of 2,100." Then the person picks which drives: fit the macros
 * to the goal, or ([canUseAsGoal]) make what they add up to the goal. [goalKind] says which goal
 * it is, since under a deficit or by day it is today's.
 */
data class MacroFitLine(
    val macroKcal: Display,
    val goal: Display,
    val gap: Display,
    val over: Boolean,
    val goalKind: MacroGoalKind,
    val canUseAsGoal: Boolean,
)

/** Which calorie goal the macros are measured against. */
enum class MacroGoalKind {
    /** One number. */
    Number,

    /** A range's middle, its aim. */
    RangeMiddle,

    /** Today's goal, which follows Burned or the day of the week. */
    Today,
}

/**
 * What the view model keeps while fields are typed in, so a half-typed number stays on screen.
 * [numberDone]: the keyboard's Done was pressed on [number], so a number under the minimum says so.
 */
data class TargetsDrafts(
    val number: String? = null,
    val macros: Map<Metric, String> = emptyMap(),
    val numberDone: Boolean = false,
)

object TargetsStateBuilder {

    /** The track's scale for the range band. */
    private const val SCALE_MIN = 1000.0
    private const val SCALE_HEADROOM = 500

    /**
     * Amounts only when calories are configured Show. A delayed mode after the reveal would
     * resolve them, but this page is not where a delayed day opens, so Soft and Delay modes get
     * the background card and no amounts at all, not even in state. Macros that show still get
     * their gram goals, editable in grams.
     *
     * [burned] is today's Burned (`BurnedRepository`), null while unknown or without
     * `release.goalsAndEnergy`; a set deficit or surplus is aimed at its total. [editingMacros],
     * [choosingActivity] and [drafts] are the view model's own UI state.
     * [healthConnected] is `release.health` on and a store connected: only then does the
     * Exercise adjustment row show, and never for someone who said numbers have been hard.
     * [goalsAndEnergy] is `release.goalsAndEnergy`: one number, a deficit and a surplus in the
     * list, a day group's shape, and "Burned comes from"; [healthSource] is the connected store,
     * for its Activity row. [today] picks the day whose goal the macros are measured against;
     * without it, the aim. The [SafetyNote] reads the sex Resting energy collects, and links to
     * Find support with [findSupport] (`release.findSupport`).
     */
    fun build(
        settings: Settings,
        policy: VisibilityPolicy,
        burned: Burned? = null,
        editingMacros: Boolean = false,
        healthConnected: Boolean = false,
        goalsAndEnergy: Boolean = false,
        healthSource: HealthSource? = null,
        today: LocalDate? = null,
        choosingActivity: Boolean = false,
        drafts: TargetsDrafts = TargetsDrafts(),
        findSupport: Boolean = true,
        /** `release.onboardingSetup`: the weight goal and its flow. Off, there is no goal row. */
        weightGoals: Boolean = false,
    ): TargetsState {
        val targets = settings.targets
        val preset = settings.numbers.preset
        val burnKcal = burned?.totalKcal
        val goalRow = if (weightGoals) weightGoalRow(settings, policy, burned) else null
        if (!percentsShown(policy)) {
            // No calorie goal, but macros on show (Macros with grams) keep their gram targets.
            return TargetsState(
                loaded = true,
                visible = false,
                weightGoal = goalRow,
                micros = microsShown(policy),
                macroMode = settings.macroMode,
                macros = macros(targets, policy),
                macroEditor = if (editingMacros) macroEditor(settings, policy, MacroGoal(targets.energyKcal, MacroGoalKind.Today), drafts, percents = false) else null,
            )
        }
        val offered = offered(StyleKind.of(targets.style), preset, goalsAndEnergy)
        val style = shownStyle(targets, preset, goalsAndEnergy)
        val byDay = (targets.style as? TargetStyle.ByDay)?.takeIf { style == StyleKind.ByDay }
        val groups = GroupInputs(policy, settings.weekStart, burnKcal, goalsAndEnergy, preset)
        val macroGoal = macroGoal(targets, style, preset, burnKcal, today)
        return TargetsState(
            loaded = true,
            visible = true,
            weightGoal = goalRow,
            micros = microsShown(policy),
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
            number = if (style == StyleKind.Exact) numberCard(targets, policy, drafts) else null,
            deficit = (targets.style as? TargetStyle.Deficit)?.takeIf { style == StyleKind.Deficit }?.let { deficitCard(targets, it, policy, burned) },
            surplus = (targets.style as? TargetStyle.Surplus)?.takeIf { style == StyleKind.Surplus }?.let { surplusCard(targets, it, policy, burned) },
            groups = byDay?.let { groupCards(targets, it, groups) }.orEmpty(),
            week = byDay?.let { weekBands(targets, it, groups) }.orEmpty(),
            canAddGroup = byDay != null && byDay.groups.size < TargetStyle.MAX_GROUPS,
            safety = safetyNote(targets, preset, burnKcal, settings.bodySex),
            supportLink = findSupport,
            macroMode = settings.macroMode,
            macros = macros(targets, policy),
            macroEditor = if (editingMacros) macroEditor(settings, policy, macroGoal, drafts, percents = true) else null,
            exercise = settings.exerciseInRange.takeIf { healthConnected && !settings.onboarding.hard },
            burned = if (goalsAndEnergy) burnedCard(burned, policy, healthSource, settings.everydayActivity, choosingActivity) else null,
        )
    }

    /**
     * The styles the list offers: one number with `release.goalsAndEnergy`, and a set deficit and
     * surplus with it where the mode offers them (All numbers). Without the flag nothing reads
     * Burned, so a deficit would never be more than the range; a style already saved still shows.
     */
    fun offered(saved: StyleKind, preset: PresetId, goalsAndEnergy: Boolean): List<StyleKind> = StyleKind.entries.filter {
        when (it) {
            StyleKind.Deficit -> TargetStyle.deficitAllowed(preset) && (goalsAndEnergy || it == saved)
            StyleKind.Surplus -> TargetStyle.surplusAllowed(preset) && (goalsAndEnergy || it == saved)
            StyleKind.Exact -> goalsAndEnergy || it == saved
            StyleKind.Range, StyleKind.ByDay -> true
        }
    }

    private fun microsShown(policy: VisibilityPolicy): Boolean = policy.level(Metric.Micros) != Visibility.Hide

    /**
     * The weight goal's row, where a weight goal is offered at all: weight on show, numbers not
     * hard, and not while pregnant or breastfeeding. "Just track" is an answer, so it gets the
     * offer again rather than a goal it never set.
     */
    fun weightGoalRow(settings: Settings, policy: VisibilityPolicy, burned: Burned?): WeightGoalRow? {
        if (policy.effectiveLevel(Metric.Weight, Surface.Settings) != Visibility.Show) return null
        if (settings.onboarding.hard || settings.bodyStage != BodyStage.None) return null
        val goal = settings.weightGoal
        return when {
            goal.direction == GoalDirection.Lose || goal.direction == GoalDirection.Gain || goal.direction == GoalDirection.Maintain ->
                if (goal.drives(settings.targets)) {
                    WeightGoalRow.Driving(
                        goal.direction,
                        pace = goal.kgPerWeek.takeIf { goal.direction.changesWeight && it > 0 }?.let { WeightGoals.weightText(it, settings.weightUnit) },
                    )
                } else {
                    WeightGoalRow.NotDriving(canUseGoal = typicalBurnKcal(burned, settings) != null, byHand = goal.targetsKcal != null)
                }
            else -> WeightGoalRow.None
        }
    }

    /**
     * A typical day's burn: resting energy plus everyday activity, as the goal flow works it out,
     * so "Use my goal" lands where the goal first did rather than on whatever today has synced.
     */
    fun typicalBurnKcal(burned: Burned?, settings: Settings): Int? =
        burned?.resting?.kcal?.let { it + settings.everydayActivity.kcalFor(it) }

    /** Calories show on this page, and with them every amount and percent of the calorie goal. */
    fun percentsShown(policy: VisibilityPolicy): Boolean = policy.level(Metric.Calories) == Visibility.Show

    /** The calorie goal the macro editor measures against, for the view model's writes. */
    fun macroGoalKcal(settings: Settings, burnKcal: Int?, today: LocalDate?, goalsAndEnergy: Boolean): Int {
        val preset = settings.numbers.preset
        return macroGoal(settings.targets, shownStyle(settings.targets, preset, goalsAndEnergy), preset, burnKcal, today).kcal
    }

    /** The saved style, or a range where it is not offered. */
    private fun shownStyle(targets: Targets, preset: PresetId, goalsAndEnergy: Boolean): StyleKind =
        StyleKind.of(targets.style).takeIf { it in offered(it, preset, goalsAndEnergy) } ?: StyleKind.Range

    /** The calorie goal the macros are measured against, and which goal that is. */
    private data class MacroGoal(val kcal: Int, val kind: MacroGoalKind)

    private fun macroGoal(targets: Targets, style: StyleKind, preset: PresetId, burnKcal: Int?, today: LocalDate?): MacroGoal = when (style) {
        StyleKind.Range -> MacroGoal(targets.energyKcal, MacroGoalKind.RangeMiddle)
        StyleKind.Exact -> MacroGoal(targets.energyKcal, MacroGoalKind.Number)
        else -> MacroGoal(today?.let { targets.rangeFor(it, preset, burnKcal).targetKcal } ?: targets.energyKcal, MacroGoalKind.Today)
    }

    /**
     * The low goal first: the least any day asks for ([Targets.spanGoal]'s low end, so a range is
     * judged by the low end it counts as on goal, and a deficit by today's burn), against the line
     * for [sex]. Then a set deficit, the style's or any day group's, past a healthy pace. No
     * weight here: Targets doesn't read weigh-ins, so the deficit is judged by its size alone.
     */
    private fun safetyNote(targets: Targets, preset: PresetId, burnKcal: Int?, sex: BodySex?): SafetyNote? {
        if (GoalSafety.isLowGoal(targets.spanGoal(preset, burnKcal).minKcal, sex)) return SafetyNote.LowGoal
        if (!TargetStyle.deficitAllowed(preset)) return null
        val deficits = when (val style = targets.style) {
            is TargetStyle.Deficit -> listOf(style.kcal)
            is TargetStyle.ByDay -> style.groups.filter { it.days.isNotEmpty() }.mapNotNull { (it.shape as? GroupShape.Deficit)?.kcal }
            else -> emptyList()
        }
        return SafetyNote.FastDeficit.takeIf { deficits.any(GoalSafety::isFastDeficit) }
    }

    private fun burnedCard(burned: Burned?, policy: VisibilityPolicy, healthSource: HealthSource?, level: EverydayActivity, choosing: Boolean) = BurnedCard(
        resting = burned?.let { policy.resolve(Metric.EnergyOut, it.resting.kcal.toDouble(), Surface.Settings, FormatContext(bare = true)) }
            ?: Display.Hidden,
        restingFrom = burned?.resting?.from,
        activity = healthSource,
        everydayActivity = level,
        levels = if (choosing && healthSource == null) EverydayActivity.entries else emptyList(),
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

    /** One number: "−" locked at the minimum, "+" at the maximum. */
    private fun numberStepper(targets: Targets, policy: VisibilityPolicy, kcal: Int, group: Int?) = EndStepper(
        value = bare(policy, kcal),
        down = if (targets.canStepNumber(up = false, group)) StepState.Open else StepState.Locked,
        up = if (targets.canStepNumber(up = true, group)) StepState.Open else StepState.Locked,
    )

    private fun numberCard(targets: Targets, policy: VisibilityPolicy, drafts: TargetsDrafts): NumberCard {
        val draft = drafts.number
        val typed = draft?.toIntOrNull()
        // Under the minimum only once another digit can't help: "15" may yet be "1500".
        val settled = drafts.numberDone || (typed != null && typed * 10 > MAX_GOAL_KCAL)
        return NumberCard(
            stepper = numberStepper(targets, policy, targets.energyKcal, group = null),
            text = draft ?: targets.energyKcal.toString(),
            floor = policy.resolve(Metric.Calories, targets.floorKcal.toDouble(), Surface.Settings),
            tooLow = typed != null && settled && typed < targets.floorKcal,
            ceiling = policy.resolve(Metric.Calories, MAX_GOAL_KCAL.toDouble(), Surface.Settings),
            tooHigh = typed != null && typed > MAX_GOAL_KCAL,
        )
    }

    /** A deficit or surplus: "−" closed at 0, "+" locked at the cap, the style's or day group [group]'s. */
    private fun amountStepper(targets: Targets, policy: VisibilityPolicy, kcal: Int, group: Int?, deficit: Boolean): EndStepper {
        fun step(up: Boolean) = if (deficit) targets.stepDeficit(up, group) else targets.stepSurplus(up, group)
        return EndStepper(
            value = bare(policy, kcal),
            down = if (step(up = false) != null) StepState.Open else StepState.Closed,
            up = if (step(up = true) != null) StepState.Open else StepState.Locked,
        )
    }

    private fun burnedTotal(policy: VisibilityPolicy, burned: Burned?): Display? =
        burned?.let { policy.resolve(Metric.EnergyOut, it.totalKcal.toDouble(), Surface.Settings) }?.takeUnless { it is Display.Hidden }

    private fun surplusCard(targets: Targets, surplus: TargetStyle.Surplus, policy: VisibilityPolicy, burned: Burned?): BurnGoalCard {
        val stepper = amountStepper(targets, policy, surplus.kcal, group = null, deficit = false)
        val burnKcal = burned?.totalKcal
        return BurnGoalCard(
            amount = stepper.value,
            down = stepper.down,
            up = stepper.up,
            target = policy.resolve(Metric.Calories, targets.surplusGoal(targets.energyKcal, burnKcal, surplus.kcal).toDouble(), Surface.Settings),
            fromAim = burnKcal == null,
            burned = burnedTotal(policy, burned),
            estimated = burned?.movementEstimated == true,
            needsDetails = burned == null,
            canStartFromBurn = surplus.startingKcal == 0 && burnKcal != null && targets.useRange().useSurplus(burnKcal).style != TargetStyle.Surplus(0, 0),
        )
    }

    private fun StepBlock?.toStepState(): StepState = when (this) {
        null -> StepState.Open
        StepBlock.Floor, StepBlock.Ceiling -> StepState.Locked
        StepBlock.Gap -> StepState.Closed
    }

    private fun deficitCard(targets: Targets, deficit: TargetStyle.Deficit, policy: VisibilityPolicy, burned: Burned?): BurnGoalCard {
        val stepper = amountStepper(targets, policy, deficit.kcal, group = null, deficit = true)
        val burnKcal = burned?.totalKcal
        return BurnGoalCard(
            amount = stepper.value,
            down = stepper.down,
            up = stepper.up,
            target = burnKcal?.let { policy.resolve(Metric.Calories, targets.deficitGoal(it, deficit.kcal).toDouble(), Surface.Settings) },
            burned = burnedTotal(policy, burned),
            estimated = burned?.movementEstimated == true,
            needsDetails = burned == null,
            canStartFromBurn = deficit.startingKcal == 0 && burnKcal != null && targets.useRange().useDeficit(burnKcal).style != TargetStyle.Deficit(0, 0),
        )
    }

    /** What every group card is built with, apart from the group. */
    private class GroupInputs(
        val policy: VisibilityPolicy,
        val weekStart: DayOfWeek,
        val burnKcal: Int?,
        val goalsAndEnergy: Boolean,
        val preset: PresetId,
    )

    private fun groupCards(targets: Targets, byDay: TargetStyle.ByDay, inputs: GroupInputs): List<DayGroupCard> =
        byDay.groups.mapIndexed { index, group ->
            val policy = inputs.policy
            val shape = ShapeKind.of(group.shape)
            val goal = requireNotNull(targets.groupGoal(index, inputs.preset, inputs.burnKcal))
            val only = byDay.groups.size == 1
            DayGroupCard(
                index = index,
                label = when {
                    index > 0 -> GroupLabel.Numbered(index + 1)
                    only -> GroupLabel.EveryDay
                    else -> GroupLabel.OtherDays
                },
                days = if (only) {
                    emptyList()
                } else {
                    week(inputs.weekStart).map { day ->
                        val inGroup = day in group.days
                        DayChip(day, inGroup, tappable = !(inGroup && index == 0))
                    }
                },
                shape = shape,
                summary = policy.resolveRange(Metric.Calories, goal.minKcal.toDouble(), goal.maxKcal.toDouble(), Surface.Settings),
                shapes = if (inputs.goalsAndEnergy) shapes(inputs.preset) else emptyList(),
                low = if (shape == ShapeKind.Range) stepper(targets, policy, RangeEnd.Low, group.minKcal, index) else null,
                high = if (shape == ShapeKind.Range) stepper(targets, policy, RangeEnd.High, group.maxKcal, index) else null,
                number = if (shape == ShapeKind.Exact) numberStepper(targets, policy, group.midKcal, index) else null,
                surplus = (group.shape as? GroupShape.Surplus)?.let { amountStepper(targets, policy, it.kcal, index, deficit = false) },
                deficit = (group.shape as? GroupShape.Deficit)?.let { amountStepper(targets, policy, it.kcal, index, deficit = true) },
                waitingOnBurn = shape == ShapeKind.Deficit && inputs.burnKcal == null,
                removable = index > 0,
                empty = group.days.isEmpty(),
            )
        }

    /** A deficit and a surplus per group only where the mode offers them, as the styles. */
    private fun shapes(preset: PresetId): List<ShapeKind> = ShapeKind.entries.filter {
        when (it) {
            ShapeKind.Deficit -> TargetStyle.deficitAllowed(preset)
            ShapeKind.Surplus -> TargetStyle.surplusAllowed(preset)
            ShapeKind.Range, ShapeKind.Exact -> true
        }
    }

    /**
     * Each day's band on one scale, so a lopsided week shows before it is saved. The lowest
     * group's goal sits at 0 and the highest at 1; with one level, every day sits in the middle.
     */
    private fun weekBands(targets: Targets, byDay: TargetStyle.ByDay, inputs: GroupInputs): List<WeekBand> {
        val weekStart = inputs.weekStart
        val mids = byDay.groups.indices.map { requireNotNull(targets.groupGoal(it, inputs.preset, inputs.burnKcal)).targetKcal }
        // Only groups that hold a day set the scale: an empty one is drawn nowhere.
        val held = byDay.groups.indices.filter { byDay.groups[it].days.isNotEmpty() }.map { mids[it] }.ifEmpty { mids }
        val lowest = held.min()
        val spread = (held.max() - lowest).takeIf { it > 0 }
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

    /**
     * The editor. Rows only for macros that show. Percents, presets and the comparison with
     * calories only with [percents] (calories show), and the comparison only with all three
     * macros on show, since it is their sum.
     */
    private fun macroEditor(settings: Settings, policy: VisibilityPolicy, goal: MacroGoal, drafts: TargetsDrafts, percents: Boolean): MacroEditor? {
        val targets = settings.targets
        val shown = SplitMacros.filter { policy.effectiveLevel(it, Surface.Settings) == Visibility.Show }
        if (shown.isEmpty()) return null
        val inPercent = percents && settings.macroMode == MacroMode.Ratios
        val rows = shown.map { metric ->
            val grams = targets.grams(metric)
            val percent = targets.macroPercent(metric, goal.kcal)
            MacroEditRow(
                metric = metric,
                text = drafts.macros[metric] ?: (if (inPercent) percent ?: 0 else grams).toString(),
                grams = policy.resolve(metric, grams.toDouble(), Surface.Settings),
                percent = if (percents && percent != null) policy.share(metric, percent.toDouble(), Surface.Settings) else Display.Hidden,
                canLower = if (inPercent) (percent ?: 0) > 0 else grams > 0,
                canRaise = if (inPercent) (percent ?: 0) < 100 else grams < MAX_MACRO_G,
            )
        }
        val allShown = shown.size == SplitMacros.size
        val current = targets.preset(goal.kcal)
        return MacroEditor(
            mode = settings.macroMode,
            percents = percents,
            presets = if (percents && allShown) MacroPreset.entries.map { PresetChip(it, it == current) } + listOfNotNull(PresetChip(null, true).takeIf { current == null }) else emptyList(),
            rows = rows,
            fit = if (percents && allShown) fitLine(targets, policy, goal) else null,
        )
    }

    private fun fitLine(targets: Targets, policy: VisibilityPolicy, goal: MacroGoal): MacroFitLine? {
        val fit = targets.macroFit(goal.kcal)
        if (fit.fits || goal.kcal <= 0) return null
        val kcal = { value: Int -> policy.resolve(Metric.Calories, value.toDouble(), Surface.Settings) }
        val bare = policy.resolve(Metric.Calories, abs(fit.gapKcal).toDouble(), Surface.Settings, FormatContext(bare = true))
        if (bare is Display.Hidden) return null
        return MacroFitLine(
            macroKcal = kcal(fit.macroKcal),
            goal = kcal(goal.kcal),
            gap = bare,
            over = fit.gapKcal > 0,
            goalKind = goal.kind,
            canUseAsGoal = targets.useMacroEnergy() != null,
        )
    }
}
