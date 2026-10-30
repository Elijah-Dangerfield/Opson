@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.features.settings.impl.goals

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import com.opson.libraries.diary.GoalBook
import com.opson.libraries.diary.Settings
import com.opson.libraries.goals.GoalStatuses
import com.opson.libraries.goals.GoalToday
import com.opson.libraries.goals.TargetProjections
import com.opson.libraries.goals.ui.ProjectionChartUi
import com.opson.libraries.goals.ui.ProjectionCharts
import com.opson.libraries.nutrition.BaselineChange
import com.opson.libraries.nutrition.BodyFatFrom
import com.opson.libraries.nutrition.BodyStage
import com.opson.libraries.nutrition.BurnMeasure
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.Goal
import com.opson.libraries.nutrition.GoalAveraging
import com.opson.libraries.nutrition.GoalCatalog
import com.opson.libraries.nutrition.GoalDay
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.GoalKind
import com.opson.libraries.nutrition.GoalProjections
import com.opson.libraries.nutrition.GoalStatus
import com.opson.libraries.nutrition.GoalTargets
import com.opson.libraries.nutrition.GoalType
import com.opson.libraries.nutrition.GoalVerdict
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PaceAssessment
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.WeightGoals
import com.opson.libraries.ui.time.DateCopy
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.math.abs

/**
 * The goals page (owner feedback, 2026-10-30: "goal mode"): every goal kept, the one setting the
 * calorie target first, each with how this week is going, where it leads and what changed its
 * target. [canAdd] when a type on offer has no goal yet.
 */
@HiddenFromObjC
data class GoalsState(
    val loaded: Boolean = false,
    val goals: List<GoalCard> = emptyList(),
    val canAdd: Boolean = false,
    val supportLink: Boolean = false,
    /** The pace note's (i), while open. */
    val paceInfo: PaceInfoView? = null,
)

/** What a goal asks for, in words the card builds: each type's own amount through the policy. */
@HiddenFromObjC
sealed interface GoalAim {
    /** [paceText] a week toward [goalWeight] (Hidden where weight doesn't show). */
    data class Weight(val direction: GoalDirection, val paceText: String?, val goalWeight: Display) : GoalAim
    data class Balance(val amount: Display, val under: Boolean) : GoalAim
    data class Burn(val amount: Display, val measure: BurnMeasure) : GoalAim
    /** [count] already grouped ("8,000"). */
    data class Steps(val count: String) : GoalAim
    data class Meals(val count: Int) : GoalAim
    data object Track : GoalAim
}

/** One day of the week on a goal's strip. */
@HiddenFromObjC
data class WeekDot(val day: DayOfWeek, val verdict: GoalVerdict, val today: Boolean)

/** A recalculation answered, for the history: when, what changed, and the targets either side where calories show. */
@HiddenFromObjC
data class HistoryRow(val date: String, val reasons: List<BaselineChange>, val old: Display, val new: Display, val accepted: Boolean)

/**
 * One goal's card. [setsTarget] is the goal setting the calorie target; [own] a calorie goal kept
 * while the person sets the target. [target], [low] and [high] are its daily target and band,
 * [today] a weekly average's target for today when it differs. [burn] is the typical day it was
 * worked out from (energy out). [chart] is where it leads, where weight shows.
 */
@HiddenFromObjC
data class GoalCard(
    val id: String,
    val kind: GoalKind,
    val status: GoalStatus,
    val aim: GoalAim,
    val since: String,
    val setsTarget: Boolean = false,
    val own: Boolean = false,
    val waiting: Boolean = false,
    val target: Display = Display.Hidden,
    val low: Display = Display.Hidden,
    val high: Display = Display.Hidden,
    val weekly: Boolean = false,
    val today: Display = Display.Hidden,
    val burn: Display = Display.Hidden,
    val week: List<WeekDot> = emptyList(),
    val pace: PaceAssessment? = null,
    val chart: ProjectionChartUi? = null,
    val history: List<HistoryRow> = emptyList(),
) {
    val paused: Boolean get() = status == GoalStatus.Paused
}

/** What the goals page reads. [days] is this week's verdicts by goal id; [statuses] today's, for the weekly budget and pace. */
@HiddenFromObjC
data class GoalsInputs(
    val settings: Settings,
    val policy: VisibilityPolicy,
    val statuses: List<GoalToday>,
    val days: Map<String, List<GoalDay>>,
    val weightKg: Double?,
    val today: LocalDate,
)

@HiddenFromObjC
object GoalsStateBuilder {

    fun build(inputs: GoalsInputs, focus: String?, supportLink: Boolean, paceInfo: PaceInfoView? = null): GoalsState {
        val settings = inputs.settings
        val offered = GoalCatalog.offered(inputs.policy, settings.onboarding.hard, settings.bodyStage)
        // A goal whose type the mode doesn't offer stays kept but isn't drawn: its numbers would leak.
        val kept = GoalBook.goals(settings).filter { it.status != GoalStatus.Done && it.type.kind in offered }
        val ordered = kept.sortedWith(compareByDescending<Goal> { it.id == focus }.thenByDescending { it.type.setsCalories }.thenBy { it.status != GoalStatus.Active })
        return GoalsState(
            loaded = true,
            goals = ordered.map { card(it, inputs) },
            canAdd = offered.any { kind -> kept.none { it.type.kind == kind } },
            supportLink = supportLink,
            paceInfo = paceInfo,
        )
    }

    private fun card(goal: Goal, inputs: GoalsInputs): GoalCard {
        val policy = inputs.policy
        val calories = policy.effectiveLevel(Metric.Calories, Surface.Settings) == Visibility.Show
        val energyOut = policy.effectiveLevel(Metric.EnergyOut, Surface.Settings) == Visibility.Show
        fun kcal(value: Int?): Display = if (calories && value != null) policy.resolve(Metric.Calories, value.toDouble(), Surface.Settings) else Display.Hidden
        val status = inputs.statuses.firstOrNull { it.goal.id == goal.id }
        val target = GoalTargets.of(goal)
        val week = status?.week
        val drives = GoalBook.driving(inputs.settings)?.id == goal.id
        return GoalCard(
            id = goal.id,
            kind = goal.type.kind,
            status = goal.status,
            aim = aim(goal, inputs),
            since = DateCopy.monthDayYear(goal.startDate),
            setsTarget = drives,
            own = goal.type.setsCalories && goal.isActive && !drives && goal.baseline != null,
            waiting = goal.type.setsCalories && goal.isActive && goal.baseline == null,
            target = if (drives) kcal(target?.aimKcal) else Display.Hidden,
            low = if (drives) kcal(target?.minKcal) else Display.Hidden,
            high = if (drives) kcal(target?.maxKcal) else Display.Hidden,
            weekly = goal.averaging == GoalAveraging.Weekly && goal.type.setsCalories,
            today = week?.takeIf { drives && it.averaging == GoalAveraging.Weekly && it.adjustmentKcal != 0 }?.let { kcal(it.todayTargetKcal) } ?: Display.Hidden,
            burn = goal.baseline?.takeIf { energyOut }?.let { policy.resolve(Metric.EnergyOut, it.burnKcal.toDouble(), Surface.Settings) } ?: Display.Hidden,
            week = dots(goal, inputs),
            // A pace is a weight a week or energy a day: only where it can be said.
            pace = status?.pace?.takeIf { goal.isActive && (calories || goal.type is GoalType.Weight) },
            chart = chart(goal, inputs),
            history = goal.history.reversed().map { entry ->
                HistoryRow(DateCopy.monthDay(entry.date), entry.changes.sortedBy { it.ordinal }, kcal(entry.oldAimKcal), kcal(entry.newAimKcal), entry.accepted)
            },
        )
    }

    private fun aim(goal: Goal, inputs: GoalsInputs): GoalAim {
        val policy = inputs.policy
        val unit = inputs.settings.weightUnit
        return when (val type = goal.type) {
            is GoalType.Weight -> GoalAim.Weight(
                direction = type.direction,
                paceText = type.kgPerWeek.takeIf { it > 0 }?.let { WeightGoals.weightText(it, unit) },
                goalWeight = type.targetKg?.takeIf { type.direction.changesWeight }
                    ?.let { policy.resolve(Metric.Weight, it, Surface.Settings, FormatContext(weightUnit = unit)) } ?: Display.Hidden,
            )
            is GoalType.EnergyBalance -> GoalAim.Balance(policy.resolve(Metric.Calories, abs(type.kcalPerDay).toDouble(), Surface.Settings), under = type.kcalPerDay < 0)
            is GoalType.Burn -> GoalAim.Burn(policy.resolve(Metric.EnergyOut, type.kcal.toDouble(), Surface.Settings), type.measure)
            is GoalType.Steps -> GoalAim.Steps(grouped(type.count))
            is GoalType.Meals -> GoalAim.Meals(type.count)
            else -> GoalAim.Track
        }
    }

    /** The week's days for [goal], in the person's week order, as the repository judged them; days past today wait. */
    private fun dots(goal: Goal, inputs: GoalsInputs): List<WeekDot> {
        val days = inputs.days[goal.id].orEmpty()
        if (days.isEmpty() || !goal.isActive) return emptyList()
        return days.map { WeekDot(it.date.dayOfWeek, it.verdict, it.date == inputs.today) }
    }

    /** A weight goal's projection to its goal weight, or an energy balance's twelve weeks, from the latest weigh-in. */
    private fun chart(goal: Goal, inputs: GoalsInputs): ProjectionChartUi? {
        if (!goal.isActive || inputs.settings.bodyStage != BodyStage.None) return null
        val weightKg = inputs.weightKg ?: return null
        val weight = goal.weight
        val projection = when {
            weight != null && weight.direction.changesWeight -> GoalProjections.weight(goal, weightKg, inputs.today)
            goal.type is GoalType.EnergyBalance && GoalBook.driving(inputs.settings)?.id == goal.id -> TargetProjections.of(goal, weightKg, inputs.today)
            else -> null
        } ?: return null
        return ProjectionCharts.ui(projection, weight?.targetKg, weight?.direction, inputs.settings.weightUnit, inputs.policy)
    }

    /** 8000 as "8,000", the way the app writes amounts. */
    internal fun grouped(value: Int): String = value.toString().reversed().chunked(3).joinToString(",").reversed()

    /** The fat-loss limit's (i) for this person. */
    fun paceInfo(inputs: GoalsInputs): PaceInfoView {
        val limit = GoalStatuses.limit(inputs.settings, inputs.weightKg, inputs.today)
        val calories = inputs.policy.effectiveLevel(Metric.Calories, Surface.Settings) == Visibility.Show
        return PaceInfoView(
            limit = if (calories && limit != null) inputs.policy.resolve(Metric.Calories, limit.kcalPerDay.toDouble(), Surface.Settings) else Display.Hidden,
            estimated = limit?.bodyFat?.from == BodyFatFrom.Estimated,
            canWorkOut = limit != null,
        )
    }
}
