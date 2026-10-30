package com.opson.features.settings.impl.goals

import com.opson.libraries.diary.GoalBook
import com.opson.libraries.diary.Settings
import com.opson.libraries.goals.GoalStatuses
import com.opson.libraries.goals.TargetProjections
import com.opson.libraries.goals.ui.ProjectionChartUi
import com.opson.libraries.goals.ui.ProjectionCharts
import com.opson.libraries.nutrition.BaselineInputs
import com.opson.libraries.nutrition.BodyStage
import com.opson.libraries.nutrition.BurnMeasure
import com.opson.libraries.nutrition.FatLossLimit
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.Goal
import com.opson.libraries.nutrition.GoalAveraging
import com.opson.libraries.nutrition.GoalBaselines
import com.opson.libraries.nutrition.GoalCatalog
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.GoalKind
import com.opson.libraries.nutrition.GoalPace
import com.opson.libraries.nutrition.GoalProjections
import com.opson.libraries.nutrition.GoalTargets
import com.opson.libraries.nutrition.GoalType
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PaceAssessment
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Units
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.WeightGoals
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.ui.components.text.NumberInput
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What the goal editor holds while a goal is set (docs/plans/goals-v2.md, "Goal types"): the type,
 * and each type's own answers. Typed amounts stay as typed ([targetText], [balanceText] and the
 * rest), so a half-typed number stays on screen; [GoalEditor.goal] reads them.
 */
data class GoalDraft(
    val kind: GoalKind? = null,
    val direction: GoalDirection = GoalDirection.Lose,
    /** The goal weight, in the person's unit. */
    val targetText: String = "",
    val kgPerWeek: Double? = null,
    val under: Boolean = true,
    val balanceText: String = DEFAULT_BALANCE.toString(),
    val burnText: String = DEFAULT_ACTIVE_BURN.toString(),
    val measure: BurnMeasure = BurnMeasure.Active,
    val stepsText: String = DEFAULT_STEPS.toString(),
    val mealsText: String = GoalType.DEFAULT_MEALS.toString(),
    val averaging: GoalAveraging = GoalAveraging.Daily,
    val toleranceKcal: Int = Goal.DEFAULT_TOLERANCE_KCAL,
) {
    companion object {
        const val DEFAULT_BALANCE = 300
        const val DEFAULT_ACTIVE_BURN = 400
        const val DEFAULT_TOTAL_BURN = 2500
        const val DEFAULT_STEPS = 8000
    }
}

/**
 * Everything the editor reads besides the draft. [inputs] is a typical day right now (null while
 * resting energy can't be worked out); [weightKg] the latest weigh-in; [existing] the goal being
 * changed, null for a new one.
 */
data class GoalEditContext(
    val today: LocalDate,
    val settings: Settings,
    val policy: VisibilityPolicy,
    val inputs: BaselineInputs?,
    val weightKg: Double?,
    val existing: Goal? = null,
) {
    val unit: WeightUnit get() = settings.weightUnit
    val calories: Boolean get() = policy.effectiveLevel(Metric.Calories, Surface.Settings) == Visibility.Show
    val energyOut: Boolean get() = policy.effectiveLevel(Metric.EnergyOut, Surface.Settings) == Visibility.Show
    val weightShown: Boolean get() = policy.effectiveLevel(Metric.Weight, Surface.Settings) == Visibility.Show
}

/** A change to the draft. */
sealed interface GoalEdit {
    data class KindPicked(val kind: GoalKind) : GoalEdit
    data class DirectionPicked(val direction: GoalDirection) : GoalEdit
    data class TargetTyped(val text: String) : GoalEdit
    data class PaceStepped(val faster: Boolean) : GoalEdit
    data class SidePicked(val under: Boolean) : GoalEdit
    data class AmountTyped(val text: String) : GoalEdit
    data class AmountStepped(val up: Boolean) : GoalEdit
    data class MeasurePicked(val measure: BurnMeasure) : GoalEdit
    data class AveragingPicked(val averaging: GoalAveraging) : GoalEdit
    data class BandPicked(val toleranceKcal: Int) : GoalEdit
}

/** One goal amount's typed field and its steps. */
data class AmountField(val text: String, val canLower: Boolean, val canRaise: Boolean, val outOfRange: Boolean = false)

/**
 * The weight goal's part: the directions, the goal weight typed in [unit], the person's weight now
 * ([current], through the policy), the pace ([paceText] a week, with its [pace] tier) and its
 * steps, and what is wrong with the goal weight, if anything.
 */
data class WeightPart(
    val direction: GoalDirection,
    val targetText: String,
    val unit: WeightUnit,
    val current: Display,
    val paceText: String?,
    val canSlower: Boolean,
    val canFaster: Boolean,
    val pace: PaceAssessment?,
    /** "550 kcal" a day the pace means: only where calories and energy out show. */
    val dailyKcal: Display = Display.Hidden,
    val wrongWay: Boolean = false,
    val belowHealthy: Boolean = false,
    /** A weight is known, and still no pace fits (a typical day already near the low line). */
    val noRoom: Boolean = false,
)

/** An energy balance goal's part: under or over, and how much. [pace] is its tier for a loss. */
data class BalancePart(val under: Boolean, val amount: AmountField, val pace: PaceAssessment?)

data class BurnPart(val measure: BurnMeasure, val amount: AmountField)

/**
 * What the editor draws. [kinds] is the type list for a new goal (empty while one is being
 * changed); [kind] the type picked. [target] is the daily calorie target the goal comes to, with
 * [low] to [high] its band, all through the policy. [chart] is where the goal leads, where weight
 * shows. [needsDetails] says a calorie goal can't work out a target yet; [needsWeight] that a
 * weight goal needs a weigh-in first. [replaces] names a calorie goal this one pauses.
 */
data class GoalEditView(
    val kinds: List<GoalKind>,
    val kind: GoalKind?,
    val editing: Boolean,
    val weight: WeightPart? = null,
    val balance: BalancePart? = null,
    val burn: BurnPart? = null,
    val steps: AmountField? = null,
    val meals: AmountField? = null,
    /** Daily or Weekly average, where it is offered; null otherwise. */
    val averaging: GoalAveraging? = null,
    /** The band's width, where calories show; null otherwise. */
    val toleranceKcal: Int? = null,
    val target: Display = Display.Hidden,
    val low: Display = Display.Hidden,
    val high: Display = Display.Hidden,
    val lowAim: Boolean = false,
    val chart: ProjectionChartUi? = null,
    val needsDetails: Boolean = false,
    val needsWeight: Boolean = false,
    val replaces: GoalKind? = null,
    val canSave: Boolean = false,
)

/**
 * The goal editor, pure: a draft from a goal, each change, the goal a draft sets, and what the
 * page draws. Every goal is built through the engine ([GoalBaselines], [GoalTargets],
 * [GoalProjections]), so the editor shows exactly what the goal will do.
 */
object GoalEditor {

    const val BALANCE_STEP = 50
    val BALANCE_KCAL = 50..1500
    const val BURN_STEP = 50
    val ACTIVE_BURN_KCAL = 50..3000
    val TOTAL_BURN_KCAL = 1000..6000
    const val STEPS_STEP = 500
    val STEPS = 1000..50000
    val MEALS = 1..6

    /** The types a goal may be in this mode, for this person ([GoalCatalog.offered]). */
    fun kinds(context: GoalEditContext): List<GoalKind> =
        GoalCatalog.offered(context.policy, context.settings.onboarding.hard, context.settings.bodyStage)

    /** A draft holding [goal]'s answers, or a fresh one of [kind]. */
    fun draft(goal: Goal?, kind: GoalKind?, context: GoalEditContext): GoalDraft {
        val fresh = GoalDraft(kind = kind)
        if (goal == null) return fresh
        val base = fresh.copy(kind = goal.type.kind, averaging = goal.averaging, toleranceKcal = goal.toleranceKcal)
        return when (val type = goal.type) {
            is GoalType.Weight -> base.copy(
                direction = type.direction,
                targetText = type.targetKg?.let { weightText(it, context.unit) }.orEmpty(),
                kgPerWeek = type.kgPerWeek.takeIf { it > 0 },
            )
            is GoalType.EnergyBalance -> base.copy(under = type.kcalPerDay <= 0, balanceText = abs(type.kcalPerDay).toString())
            is GoalType.Burn -> base.copy(burnText = type.kcal.toString(), measure = type.measure)
            is GoalType.Steps -> base.copy(stepsText = type.count.toString())
            is GoalType.Meals -> base.copy(mealsText = type.count.toString())
            else -> base
        }
    }

    fun reduce(draft: GoalDraft, edit: GoalEdit, context: GoalEditContext): GoalDraft = when (edit) {
        is GoalEdit.KindPicked -> if (edit.kind == draft.kind) draft else GoalDraft(kind = edit.kind, averaging = draft.averaging, toleranceKcal = draft.toleranceKcal)
        is GoalEdit.DirectionPicked -> draft.copy(direction = edit.direction)
        is GoalEdit.TargetTyped -> draft.copy(targetText = NumberInput.sanitize(edit.text, allowDecimal = true).take(MAX_WEIGHT_CHARS))
        is GoalEdit.PaceStepped -> stepPace(draft, edit.faster, context)
        is GoalEdit.SidePicked -> draft.copy(under = edit.under)
        is GoalEdit.AmountTyped -> typed(draft, NumberInput.sanitize(edit.text, allowDecimal = false).take(MAX_AMOUNT_CHARS))
        is GoalEdit.AmountStepped -> stepped(draft, edit.up)
        is GoalEdit.MeasurePicked -> if (edit.measure == draft.measure) {
            draft
        } else {
            val start = if (edit.measure == BurnMeasure.Total) totalBurnStart(context) else GoalDraft.DEFAULT_ACTIVE_BURN
            draft.copy(measure = edit.measure, burnText = start.toString())
        }
        is GoalEdit.AveragingPicked -> draft.copy(averaging = edit.averaging)
        is GoalEdit.BandPicked -> draft.copy(toleranceKcal = edit.toleranceKcal.takeIf { it in Goal.TOLERANCES_KCAL } ?: draft.toleranceKcal)
    }

    /** A total burn starts at what a typical day burns, rounded to a step, so it reads as a real day. */
    private fun totalBurnStart(context: GoalEditContext): Int =
        context.inputs?.let { roundTo(it.restingKcal + it.activityKcal, BURN_STEP) }?.coerceIn(TOTAL_BURN_KCAL) ?: GoalDraft.DEFAULT_TOTAL_BURN

    private fun typed(draft: GoalDraft, text: String): GoalDraft = when (draft.kind) {
        GoalKind.EnergyBalance -> draft.copy(balanceText = text)
        GoalKind.Burn -> draft.copy(burnText = text)
        GoalKind.Steps -> draft.copy(stepsText = text)
        GoalKind.Meals -> draft.copy(mealsText = text)
        else -> draft
    }

    private fun stepped(draft: GoalDraft, up: Boolean): GoalDraft {
        fun step(text: String, size: Int, range: IntRange, fallback: Int): String {
            val current = text.toIntOrNull() ?: fallback
            val next = if (up) (current / size + 1) * size else ((current + size - 1) / size - 1) * size
            return next.coerceIn(range).toString()
        }
        return when (draft.kind) {
            GoalKind.EnergyBalance -> draft.copy(balanceText = step(draft.balanceText, BALANCE_STEP, BALANCE_KCAL, GoalDraft.DEFAULT_BALANCE))
            GoalKind.Burn -> draft.copy(burnText = step(draft.burnText, BURN_STEP, burnRange(draft.measure), GoalDraft.DEFAULT_ACTIVE_BURN))
            GoalKind.Steps -> draft.copy(stepsText = step(draft.stepsText, STEPS_STEP, STEPS, GoalDraft.DEFAULT_STEPS))
            GoalKind.Meals -> draft.copy(mealsText = step(draft.mealsText, 1, MEALS, GoalType.DEFAULT_MEALS))
            else -> draft
        }
    }

    private fun burnRange(measure: BurnMeasure): IntRange = if (measure == BurnMeasure.Total) TOTAL_BURN_KCAL else ACTIVE_BURN_KCAL

    private fun stepPace(draft: GoalDraft, faster: Boolean, context: GoalEditContext): GoalDraft {
        val rates = rates(draft, context)
        val current = rate(draft, context) ?: return draft
        val next = rates.getOrNull(rates.indexOf(current) + if (faster) 1 else -1) ?: return draft
        return draft.copy(kgPerWeek = next)
    }

    private fun rates(draft: GoalDraft, context: GoalEditContext): List<Double> {
        val weight = context.weightKg ?: return emptyList()
        if (!draft.direction.changesWeight) return emptyList()
        val burn = context.inputs?.let { it.restingKcal + it.activityKcal }
        return WeightGoals.rates(draft.direction, context.unit, weight, burn, context.settings.bodySex)
    }

    private fun rate(draft: GoalDraft, context: GoalEditContext): Double? = WeightGoals.nearestRate(draft.kgPerWeek, rates(draft, context), context.unit)

    /** The goal weight typed, in kilograms, when it is a weight the app takes. */
    fun targetKg(draft: GoalDraft, unit: WeightUnit): Double? {
        val typed = draft.targetText.toDoubleOrNull() ?: return null
        val kg = if (unit == WeightUnit.Lb) Units.lbToKg(typed) else typed
        return kg.takeIf { it in WeightGoals.GOAL_KG }
    }

    private fun amount(text: String, range: IntRange): Int? = text.toIntOrNull()?.takeIf { it in range }

    /**
     * The goal [draft] sets, or null while it isn't one yet (no type, a goal weight that is missing
     * or points the wrong way, an amount out of range). A goal that sets the calorie target carries
     * a typical day from [GoalEditContext.inputs] when one can be worked out, and waits for one
     * otherwise. Changing a goal keeps its id, start and history.
     */
    fun goal(draft: GoalDraft, context: GoalEditContext): Goal? {
        val kind = draft.kind ?: return null
        val existing = context.existing?.takeIf { it.type.kind == kind }
        val type: GoalType = when (kind) {
            GoalKind.Weight -> weightType(draft, context, existing) ?: return null
            GoalKind.EnergyBalance -> GoalType.EnergyBalance(amount(draft.balanceText, BALANCE_KCAL)?.let { if (draft.under) -it else it } ?: return null)
            GoalKind.Burn -> GoalType.Burn(amount(draft.burnText, burnRange(draft.measure)) ?: return null, draft.measure)
            GoalKind.Steps -> GoalType.Steps(amount(draft.stepsText, STEPS) ?: return null)
            GoalKind.TrackEveryDay -> GoalType.TrackEveryDay
            GoalKind.Meals -> GoalType.Meals(amount(draft.mealsText, MEALS) ?: return null)
            GoalKind.Unknown -> return null
        }
        return Goal(
            id = existing?.id ?: kind.key,
            type = type,
            startDate = existing?.startDate ?: context.today,
            averaging = if (type.setsCalories) draft.averaging else GoalAveraging.Daily,
            toleranceKcal = draft.toleranceKcal,
            baseline = context.inputs?.takeIf { type.setsCalories }?.let(GoalBaselines::of),
            history = existing?.history.orEmpty(),
        )
    }

    private fun weightType(draft: GoalDraft, context: GoalEditContext, existing: Goal?): GoalType.Weight? {
        val current = context.weightKg ?: return null
        val before = existing?.weight?.takeIf { it.direction == draft.direction }
        if (draft.direction == GoalDirection.Maintain) return GoalType.Weight(GoalDirection.Maintain, targetKg = current, startKg = before?.startKg ?: current)
        val target = targetKg(draft, context.unit) ?: return null
        if (!WeightGoals.pointsTheRightWay(draft.direction, current, target)) return null
        val rate = rate(draft, context) ?: return null
        return GoalType.Weight(draft.direction, target, rate, before?.startKg ?: current)
    }

    /** Settings with [goal] saved: it writes its target when it sets one and a typical day is known. */
    fun save(settings: Settings, goal: Goal): Settings =
        if (goal.type.setsCalories && goal.baseline != null) GoalBook.applyTarget(settings, goal) else GoalBook.withGoal(settings, goal)

    fun view(draft: GoalDraft, context: GoalEditContext): GoalEditView {
        val editing = context.existing != null
        // A type the mode doesn't offer (the mode changed since it was picked) isn't drawn: its amounts would show.
        val kind = draft.kind?.takeIf { it in kinds(context) } ?: return GoalEditView(kinds = kinds(context), kind = null, editing = editing)
        val goal = goal(draft, context)
        val setsCalories = kind == GoalKind.Weight || kind == GoalKind.EnergyBalance
        val target = goal?.let(GoalTargets::of)
        fun kcal(value: Int?): Display = if (context.calories && value != null) context.policy.resolve(Metric.Calories, value.toDouble(), Surface.Settings) else Display.Hidden
        val limit = GoalStatuses.limit(context.settings, context.weightKg, context.today)
        val leading = GoalBook.calorieGoal(context.settings)?.takeIf { it.isActive && it.id != goal?.id && it.type.kind != kind }
        return GoalEditView(
            kinds = if (editing) emptyList() else kinds(context),
            kind = kind,
            editing = editing,
            weight = if (kind == GoalKind.Weight) weightPart(draft, context, limit) else null,
            balance = if (kind == GoalKind.EnergyBalance) balancePart(draft, context, limit) else null,
            burn = if (kind == GoalKind.Burn) BurnPart(draft.measure, field(draft.burnText, burnRange(draft.measure))) else null,
            steps = if (kind == GoalKind.Steps) field(draft.stepsText, STEPS) else null,
            meals = if (kind == GoalKind.Meals) field(draft.mealsText, MEALS) else null,
            averaging = draft.averaging.takeIf { setsCalories && GoalCatalog.weeklyOffered(context.policy, context.settings.onboarding.hard) },
            toleranceKcal = draft.toleranceKcal.takeIf { setsCalories && context.calories },
            target = kcal(target?.aimKcal),
            low = kcal(target?.minKcal),
            high = kcal(target?.maxKcal),
            lowAim = target?.let { GoalTargets.belowLowGoal(it.aimKcal, context.settings.bodySex) } == true,
            chart = goal?.let { chart(it, context) },
            needsDetails = setsCalories && context.inputs == null,
            needsWeight = kind == GoalKind.Weight && context.weightKg == null,
            replaces = leading?.type?.kind?.takeIf { setsCalories },
            canSave = goal != null,
        )
    }

    private fun weightPart(draft: GoalDraft, context: GoalEditContext, limit: FatLossLimit?): WeightPart {
        val current = context.weightKg
        val rates = rates(draft, context)
        val rate = rate(draft, context)
        val index = rate?.let(rates::indexOf) ?: -1
        val target = targetKg(draft, context.unit)
        return WeightPart(
            direction = draft.direction,
            targetText = draft.targetText,
            unit = context.unit,
            current = current?.let { context.policy.resolve(Metric.Weight, it, Surface.Settings, FormatContext(weightUnit = context.unit)) } ?: Display.Hidden,
            paceText = rate?.let { WeightGoals.weightText(it, context.unit) },
            canSlower = index > 0,
            canFaster = index in 0 until rates.lastIndex,
            pace = rate?.let { GoalPace.assess(draft.direction, it, current, limit) },
            dailyKcal = if (rate != null && context.calories && context.energyOut) {
                context.policy.resolve(Metric.Calories, WeightGoals.kcalPerDay(rate).toDouble(), Surface.Settings)
            } else {
                Display.Hidden
            },
            wrongWay = target != null && current != null && !WeightGoals.pointsTheRightWay(draft.direction, current, target),
            belowHealthy = draft.direction == GoalDirection.Lose && target != null && WeightGoals.belowHealthyRange(target, context.settings.heightCm),
            noRoom = current != null && draft.direction.changesWeight && rates.isEmpty(),
        )
    }

    private fun balancePart(draft: GoalDraft, context: GoalEditContext, limit: FatLossLimit?): BalancePart {
        val amount = amount(draft.balanceText, BALANCE_KCAL)
        return BalancePart(
            under = draft.under,
            amount = field(draft.balanceText, BALANCE_KCAL),
            pace = amount?.let { GoalPace.assessDelta(if (draft.under) -it else it, context.weightKg, limit) }?.takeIf { draft.under },
        )
    }

    private fun field(text: String, range: IntRange): AmountField {
        val value = text.toIntOrNull()
        return AmountField(
            text = text,
            canLower = (value ?: range.first) > range.first,
            canRaise = (value ?: range.first) < range.last,
            // "15" may yet be "1500": only past the top, or empty, is it plainly out of range.
            outOfRange = value == null || value > range.last,
        )
    }

    /**
     * Where [goal] leads, from the latest weigh-in, where weight shows: a weight goal's projection to
     * its goal weight, or an energy balance's for twelve weeks. Null otherwise, or with no weigh-in.
     */
    fun chart(goal: Goal, context: GoalEditContext): ProjectionChartUi? {
        val weightKg = context.weightKg ?: return null
        if (!context.weightShown || context.settings.bodyStage != BodyStage.None) return null
        val weight = goal.weight
        val projection = if (weight != null) {
            if (!weight.direction.changesWeight) return null
            GoalProjections.weight(goal, weightKg, context.today)
        } else {
            TargetProjections.of(goal, weightKg, context.today)
        } ?: return null
        return ProjectionCharts.ui(projection, weight?.targetKg, weight?.direction, context.unit, context.policy)
    }

    /** [kg] in [unit], to a tenth, for the goal weight's field. */
    private fun weightText(kg: Double, unit: WeightUnit): String {
        val value = if (unit == WeightUnit.Lb) Units.kgToLb(kg) else kg
        val tenths = (value * 10).roundToInt()
        return if (tenths % 10 == 0) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
    }

    private fun roundTo(value: Int, step: Int): Int = (value.toDouble() / step).roundToInt() * step

    private const val MAX_WEIGHT_CHARS = 5
    private const val MAX_AMOUNT_CHARS = 5
}
