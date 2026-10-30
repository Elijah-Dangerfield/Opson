package com.opson.features.onboarding.impl

import com.opson.libraries.diary.GoalBook
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.BaselineEnvironment
import com.opson.libraries.nutrition.BaselineInputs
import com.opson.libraries.nutrition.BodyFatFrom
import com.opson.libraries.nutrition.FatLoss
import com.opson.libraries.nutrition.Goal
import com.opson.libraries.nutrition.GoalAveraging
import com.opson.libraries.nutrition.GoalBaselines
import com.opson.libraries.nutrition.GoalPace
import com.opson.libraries.nutrition.GoalTargets
import com.opson.libraries.nutrition.GoalType
import com.opson.libraries.nutrition.PaceAssessment
import com.opson.libraries.nutrition.BodyProfile
import com.opson.libraries.nutrition.BodyStage
import com.opson.libraries.nutrition.SexChoice
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.HeightInput
import com.opson.libraries.nutrition.HeightUnit
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.RegionUnits
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.Units
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.WeightGoal
import com.opson.libraries.nutrition.WeightGoals
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.ui.components.text.NumberInput
import com.opson.libraries.ui.time.DateCopy
import com.opson.libraries.diary.MacroMode
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The goal flow (owner feedback, 2026-10-29: "a normal onboarding would help people set their
 * calorie goals"), walked by onboarding after the starting mode and by the goal flow Today offers
 * an account that never set one. Who you are, how active a day is, which way the goal points and
 * how fast, what that means for each day, and a commitment. Pure: both view models hold
 * [PlanAnswers] and a [PlanContext], and everything drawn or saved is worked out here.
 *
 * Someone who said numbers have been hard never sees a weight, a target weight or a pace: they get
 * the profile without weight, the activity, a gentle overview and the commitment. A weight goal is
 * offered only where weight is on show in the mode ([PlanFlow.showsGoal]), and every amount goes
 * through the mode's policy.
 */
enum class PlanStep(val eventName: String) {
    Profile("profile"),
    Activity("activity"),
    Goal("weight_goal"),
    Rate("goal_rate"),
    Overview("goal_overview"),
    Commit("commitment"),
}

/**
 * A weight as typed, in [unit]. Switching units shows a rounded conversion and keeps what was
 * typed ([asTyped]), so switching back gives it back unchanged and what is saved is what was typed.
 */
data class WeightInput(val text: String = "", val unit: WeightUnit = WeightUnit.Kg, val asTyped: AsTyped? = null) {
    data class AsTyped(val text: String, val unit: WeightUnit)

    fun typed(text: String): WeightInput = copy(text = NumberInput.sanitize(text, allowDecimal = true), asTyped = null)

    fun inUnit(next: WeightUnit): WeightInput {
        if (next == unit) return this
        val typed = asTyped ?: AsTyped(text, unit)
        val shown = if (typed.unit == next) {
            typed.text
        } else {
            typed.text.toDoubleOrNull()?.let { tenths(if (next == WeightUnit.Lb) Units.kgToLb(it) else Units.lbToKg(it)) } ?: typed.text
        }
        return WeightInput(text = shown, unit = next, asTyped = typed.takeIf { it.unit != next && it.text.isNotEmpty() })
    }

    /** Kilograms, when what was typed is a weight in [WeightGoals.GOAL_KG]. */
    fun kgOrNull(): Double? {
        val typed = asTyped ?: AsTyped(text, unit)
        val value = typed.text.toDoubleOrNull() ?: return null
        val kg = if (typed.unit == WeightUnit.Lb) Units.lbToKg(value) else value
        return kg.takeIf { it in WeightGoals.GOAL_KG }
    }

    val isBlank: Boolean get() = text.isBlank()

    val invalid: Boolean get() = !isBlank && kgOrNull() == null

    companion object {
        /** [kg] in [unit], to a tenth, for a field that opens filled in. */
        fun of(kg: Double?, unit: WeightUnit): WeightInput =
            WeightInput(kg?.let { tenths(if (unit == WeightUnit.Lb) Units.kgToLb(it) else it) }.orEmpty(), unit)

        /** 141.1 as "141.1", 64.0 as "64". */
        private fun tenths(value: Double): String {
            val tenths = (value * 10).roundToInt()
            return if (tenths % 10 == 0) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
        }
    }
}

/**
 * The profile and the day, as typed. Height in centimetres or feet and inches and weight in
 * kilograms or pounds, each with its own switch, both starting in the phone region's units.
 * Weight is the first weigh-in, never a setting, as everywhere else.
 */
data class BodyAnswer(
    val sex: SexChoice? = null,
    val birthDate: LocalDate? = null,
    val height: HeightInput = HeightInput(HeightUnit.Cm),
    val weight: WeightInput = WeightInput(),
    val activity: EverydayActivity = EverydayActivity.Default,
    /** What was typed before a unit switch, so switching back restores it. */
    val heightAsTyped: HeightInput? = null,
) {
    val weightUnit: WeightUnit get() = weight.unit

    fun withHeightCm(text: String) = copy(height = height.copy(cm = NumberInput.sanitize(text, allowDecimal = true)), heightAsTyped = null)

    fun withHeightFeet(text: String) = copy(height = height.copy(feet = NumberInput.sanitize(text, allowDecimal = false)), heightAsTyped = null)

    fun withHeightInches(text: String) = copy(height = height.copy(inches = NumberInput.sanitize(text, allowDecimal = true)), heightAsTyped = null)

    /** Height in [unit]; each conversion starts from what was typed, so 172 cm comes back as 172. */
    fun withHeightUnit(unit: HeightUnit): BodyAnswer {
        if (unit == height.unit) return this
        val typed = heightAsTyped ?: height
        return copy(height = typed.inUnit(unit), heightAsTyped = typed.takeIf { it.unit != unit && !it.isBlank })
    }

    fun heightCmOrNull(): Double? = (heightAsTyped ?: height).cmOrNull()

    fun weightKgOrNull(): Double? = weight.kgOrNull()?.takeIf { it in RESTING_WEIGHT_KG }

    val heightInvalid: Boolean get() = !height.isBlank && heightCmOrNull() == null

    val weightInvalid: Boolean get() = !weight.isBlank && weightKgOrNull() == null

    /** Age in whole years on [today]: the birthday has to have come this year. */
    fun ageOn(today: LocalDate): Int? = birthDate?.let { born ->
        val had = today.month > born.month || (today.month == born.month && today.day >= born.day)
        today.year - born.year - if (had) 0 else 1
    }

    /**
     * What the resting energy equation is told. Age is the birth year's, as Burned works it out
     * from the year it reads, so the estimate here is the one the app shows later.
     */
    fun profile(today: LocalDate): BodyProfile = BodyProfile(
        weightKg = weightKgOrNull(),
        heightCm = heightCmOrNull(),
        ageYears = birthDate?.let { today.year - it.year },
        sex = sex?.sex,
    )

    companion object {
        /** Weights the resting energy equation takes, as for a weigh-in. */
        private val RESTING_WEIGHT_KG = 25.0..400.0

        /** Empty fields in the phone region's units ([RegionUnits]). */
        fun inRegion(countryCode: String?): BodyAnswer = BodyAnswer(
            height = HeightInput(RegionUnits.height(countryCode)),
            weight = WeightInput(unit = RegionUnits.weight(countryCode)),
        )
    }
}

/** Which way, the target (in the weight's unit) and the pace, null until the person moves it. */
data class GoalAnswer(
    val direction: GoalDirection = GoalDirection.None,
    val target: WeightInput = WeightInput(),
    val kgPerWeek: Double? = null,
)

data class PlanAnswers(val body: BodyAnswer = BodyAnswer(), val goal: GoalAnswer = GoalAnswer()) {
    /** Both weights follow one unit switch. */
    fun withWeightUnit(unit: WeightUnit): PlanAnswers =
        copy(body = body.copy(weight = body.weight.inUnit(unit)), goal = goal.copy(target = goal.target.inUnit(unit)))
}

/** The phone's health app, as the activity step offers it. */
sealed interface HealthOffer {
    /** `release.health` off, or no store on this phone. */
    data object Unavailable : HealthOffer

    data class CanConnect(val source: HealthSource, val connecting: Boolean = false) : HealthOffer

    /** Connected: it reads activity, so there is no level to pick. */
    data class Connected(val source: HealthSource) : HealthOffer

    companion object {
        /** What [status] lets the step offer. A store that needs installing is left to Watch and health apps. */
        fun from(status: HealthStatus, source: HealthSource, connecting: Boolean): HealthOffer = when (status) {
            HealthStatus.Connected -> Connected(source)
            HealthStatus.NotConnected -> CanConnect(source, connecting)
            HealthStatus.Unsupported, HealthStatus.NeedsInstall -> Unavailable
        }
    }
}

/**
 * What the flow knows besides the answers. [fresh] is onboarding: a new account, whose targets
 * are still the app's starting ones. [environment] is where resting energy comes from and what the
 * health app has read, from the account's settings ([Settings.baselineEnvironment]), and
 * [bodyFatPercent] the body fat on Profile: a typical day is worked out from them exactly as the
 * goal repository later works it out, so the target the flow shows is the one the goal keeps.
 */
data class PlanContext(
    val today: LocalDate,
    val hard: Boolean,
    val policy: VisibilityPolicy,
    val fresh: Boolean,
    val health: HealthOffer = HealthOffer.Unavailable,
    val environment: BaselineEnvironment = BaselineEnvironment(),
    val bodyFatPercent: Double? = null,
) {
    val weightShown: Boolean get() = policy.effectiveLevel(Metric.Weight, Surface.Settings) == Visibility.Show

    val caloriesShown: Boolean get() = policy.effectiveLevel(Metric.Calories, Surface.Settings) == Visibility.Show

    /** The target set against what a day uses gives that away, so the comparison needs energy out on show too. */
    val energyOutShown: Boolean get() = caloriesShown && policy.effectiveLevel(Metric.EnergyOut, Surface.Settings) == Visibility.Show
}

/** Every change the flow's steps can make. */
sealed interface PlanAction {
    data class SexPicked(val sex: SexChoice) : PlanAction
    data class BirthDatePicked(val date: LocalDate) : PlanAction
    data class HeightUnitPicked(val unit: HeightUnit) : PlanAction
    data class HeightCmTyped(val text: String) : PlanAction
    data class HeightFeetTyped(val text: String) : PlanAction
    data class HeightInchesTyped(val text: String) : PlanAction
    data class WeightTyped(val text: String) : PlanAction

    /** Kilograms or pounds, for the weight and the target together. */
    data class WeightUnitPicked(val unit: WeightUnit) : PlanAction
    data class ActivityPicked(val level: EverydayActivity) : PlanAction

    /** "Connect Apple Health" (or Health Connect) on the activity step. The view model asks. */
    data object ConnectHealthTapped : PlanAction
    data class DirectionPicked(val direction: GoalDirection) : PlanAction
    data class TargetTyped(val text: String) : PlanAction

    /** One step slower or faster on the pace. */
    data class RateStepped(val faster: Boolean) : PlanAction

    /** "Find support", in the note under a target below a healthy range. */
    data object FindSupportTapped : PlanAction
}

/** Why the target field can't be used yet, said under it. */
enum class TargetHint { NeedsWeight, NotAWeight, AboveCurrent, BelowCurrent }

object PlanFlow {

    /**
     * The answers as an account already has them, for the goal flow Today opens: the profile from
     * settings, the latest weigh-in, and a goal set before. A field Opson knows nothing about
     * starts empty, in [regionHeight] for height and the account's weight unit.
     */
    fun answersFrom(settings: Settings, latestKg: Double?, regionHeight: HeightUnit): PlanAnswers {
        val unit = settings.weightUnit
        val goal = settings.weightGoal
        return PlanAnswers(
            body = BodyAnswer(
                sex = SexChoice.of(settings.bodySex, settings.bodyStage),
                birthDate = settings.knownBirthDate,
                height = HeightInput.of(settings.heightCm, settings.heightUnitOr(regionHeight)),
                weight = WeightInput.of(latestKg, unit),
                activity = settings.everydayActivity,
            ),
            goal = GoalAnswer(
                direction = goal.direction.takeIf { it != GoalDirection.JustTrack } ?: GoalDirection.None,
                target = WeightInput.of(goal.targetKg?.takeIf { goal.direction.changesWeight }, unit),
                kgPerWeek = goal.kgPerWeek.takeIf { it > 0 },
            ),
        )
    }

    /**
     * A weight goal is offered where weight is on show in the mode, never after "numbers have been
     * hard", and never while pregnant or breastfeeding, when weight change isn't Opson's to plan.
     */
    fun showsGoal(answers: PlanAnswers, context: PlanContext): Boolean =
        !context.hard && context.weightShown && (answers.body.sex?.stage ?: BodyStage.None) == BodyStage.None

    /** The steps for these answers, in order. The goal's pace only for losing or gaining. */
    fun steps(answers: PlanAnswers, context: PlanContext): List<PlanStep> = buildList {
        add(PlanStep.Profile)
        add(PlanStep.Activity)
        if (showsGoal(answers, context)) {
            add(PlanStep.Goal)
            if (answers.goal.direction.changesWeight) add(PlanStep.Rate)
        }
        add(PlanStep.Overview)
        add(PlanStep.Commit)
    }

    /**
     * [answers] after [action]. Connecting a health app and Find support are the view model's to
     * do, and change nothing here. A birthday has to make someone 13 to 120, as the equation is for
     * adults and teenagers; the picker offers no other, so anything else is ignored.
     */
    fun reduce(answers: PlanAnswers, action: PlanAction, context: PlanContext): PlanAnswers {
        val body = answers.body
        return when (action) {
            is PlanAction.SexPicked -> answers.copy(body = body.copy(sex = action.sex))
            is PlanAction.BirthDatePicked -> {
                val age = body.copy(birthDate = action.date).ageOn(context.today)
                if (age == null || age !in AGES) answers else answers.copy(body = body.copy(birthDate = action.date))
            }
            is PlanAction.HeightUnitPicked -> answers.copy(body = body.withHeightUnit(action.unit))
            is PlanAction.HeightCmTyped -> answers.copy(body = body.withHeightCm(action.text))
            is PlanAction.HeightFeetTyped -> answers.copy(body = body.withHeightFeet(action.text))
            is PlanAction.HeightInchesTyped -> answers.copy(body = body.withHeightInches(action.text))
            is PlanAction.WeightTyped -> answers.copy(body = body.copy(weight = body.weight.typed(action.text)))
            is PlanAction.WeightUnitPicked -> answers.withWeightUnit(action.unit)
            is PlanAction.ActivityPicked -> answers.copy(body = body.copy(activity = action.level))
            is PlanAction.DirectionPicked -> answers.copy(goal = answers.goal.copy(direction = action.direction))
            is PlanAction.TargetTyped -> answers.copy(goal = answers.goal.copy(target = answers.goal.target.typed(action.text)))
            is PlanAction.RateStepped -> stepRate(answers, context, action.faster)
            PlanAction.ConnectHealthTapped, PlanAction.FindSupportTapped -> answers
        }
    }

    /** Who a birthday can belong to: the resting energy equation is for 13 to 120. */
    val AGES: IntRange = 13..120

    /** Continue on [step]: the goal step needs a direction, and a usable target to lose or gain. */
    fun canContinue(step: PlanStep, answers: PlanAnswers, context: PlanContext): Boolean = when (step) {
        PlanStep.Goal -> answers.goal.direction != GoalDirection.None && targetHint(answers) == null
        else -> true
    }

    /** What stops the target being used, or null when it can be (or none is needed). Blank counts. */
    fun targetHint(answers: PlanAnswers): TargetHint? {
        val goal = answers.goal
        if (!goal.direction.changesWeight) return null
        val current = answers.body.weightKgOrNull() ?: return TargetHint.NeedsWeight
        val target = goal.target.kgOrNull() ?: return TargetHint.NotAWeight
        return when {
            WeightGoals.pointsTheRightWay(goal.direction, current, target) -> null
            goal.direction == GoalDirection.Lose -> TargetHint.AboveCurrent
            else -> TargetHint.BelowCurrent
        }
    }

    /** The line under the field: a blank target holds Continue back but says nothing, as nothing is wrong yet. */
    fun hintShown(answers: PlanAnswers): TargetHint? =
        targetHint(answers)?.takeUnless { it == TargetHint.NotAWeight && answers.goal.target.isBlank }

    /**
     * What a typical day is worked out from for these answers ([GoalBaselines.inputs]): resting
     * energy from the source the account uses, and activity from the health app's last week or the
     * everyday level picked. Null while details are missing.
     */
    fun inputs(answers: PlanAnswers, context: PlanContext): BaselineInputs? = GoalBaselines.inputs(
        environment = context.environment,
        profile = answers.body.profile(context.today),
        everyday = answers.body.activity,
        stage = answers.body.sex?.stage ?: BodyStage.None,
        bodyFatPercent = context.bodyFatPercent,
    )

    /** Resting energy, from the account's source or the equation; null while details are missing. */
    fun restingKcal(answers: PlanAnswers, context: PlanContext): Int? = inputs(answers, context)?.restingKcal

    /** A typical day's activity, everyday or the health app's; null while details are missing. */
    fun activityKcal(answers: PlanAnswers, context: PlanContext): Int? = inputs(answers, context)?.activityKcal

    /**
     * A typical day's burn: resting energy plus activity. What every target here is aimed at.
     * Null while details are missing.
     */
    fun burnKcal(answers: PlanAnswers, context: PlanContext): Int? = inputs(answers, context)?.let { it.restingKcal + it.activityKcal }

    /** The paces on offer, slowest first; empty without a weight or with no room to plan one. */
    fun rates(answers: PlanAnswers, context: PlanContext): List<Double> {
        val weight = answers.body.weightKgOrNull() ?: return emptyList()
        val burn = burnKcal(answers, context)
        return WeightGoals.rates(answers.goal.direction, answers.body.weightUnit, weight, burn, answers.body.sex?.sex)
    }

    /** The pace in use: the one picked, moved onto the steps on offer, or the default. */
    fun rate(answers: PlanAnswers, context: PlanContext): Double? =
        WeightGoals.nearestRate(answers.goal.kgPerWeek, rates(answers, context), answers.body.weightUnit)

    /** One step slower or faster, staying on the steps on offer. */
    fun stepRate(answers: PlanAnswers, context: PlanContext, faster: Boolean): PlanAnswers {
        val rates = rates(answers, context)
        val current = rate(answers, context) ?: return answers
        val index = rates.indexOf(current) + if (faster) 1 else -1
        val next = rates.getOrNull(index) ?: return answers
        return answers.copy(goal = answers.goal.copy(kgPerWeek = next))
    }

    /** The goal these answers set, or [WeightGoal.None] when no goal step was answered. */
    fun weightGoal(answers: PlanAnswers, context: PlanContext): WeightGoal {
        if (!showsGoal(answers, context)) return WeightGoal.None
        val goal = answers.goal
        val start = answers.body.weightKgOrNull()
        return when (goal.direction) {
            GoalDirection.None -> WeightGoal.None
            GoalDirection.JustTrack -> WeightGoal(GoalDirection.JustTrack)
            GoalDirection.Maintain -> WeightGoal(GoalDirection.Maintain, targetKg = start, startKg = start, startDate = context.today)
            GoalDirection.Lose, GoalDirection.Gain -> WeightGoal(
                direction = goal.direction,
                targetKg = goal.target.kgOrNull(),
                kgPerWeek = rate(answers, context) ?: 0.0,
                startKg = start,
                startDate = context.today,
            )
        }
    }

    /**
     * The goal these answers set ([weightGoal]) as a goal of the engine's: the same direction,
     * target and pace, starting today, from the typical day these answers give
     * ([GoalBaselines.of]). How it averages and its band carry over from a weight goal set
     * before. Null for no goal, just tracking, or a goal not offered.
     */
    fun goal(answers: PlanAnswers, context: PlanContext, current: Settings?): Goal? {
        val legacy = weightGoal(answers, context)
        val direction = legacy.direction
        if (direction != GoalDirection.Lose && direction != GoalDirection.Gain && direction != GoalDirection.Maintain) return null
        val before = current?.let(GoalBook::weight)
        return Goal(
            id = Goal.WEIGHT_ID,
            type = GoalType.Weight(direction, legacy.targetKg, legacy.kgPerWeek, legacy.startKg),
            startDate = context.today,
            averaging = before?.averaging ?: GoalAveraging.Daily,
            toleranceKcal = before?.toleranceKcal ?: Goal.DEFAULT_TOLERANCE_KCAL,
            baseline = inputs(answers, context)?.let(GoalBaselines::of),
        )
    }

    /**
     * The targets these answers set, and the goal stamped with the aim it wrote; null targets
     * leave Targets as it is. A goal to lose, keep or gain sets them from a typical day
     * ([GoalTargets.apply]): its own band around the target, whatever range was there. Otherwise
     * only a new account ([PlanContext.fresh]) gets a starting range: from the burn, plus what
     * pregnancy or breastfeeding needs, never under the app's own starting range. Nothing is
     * written for someone who said numbers have been hard, or whose mode offered no goal, as the
     * setup steps never wrote one for anyone who didn't see it.
     */
    fun planTargets(answers: PlanAnswers, context: PlanContext, current: Settings): Pair<WeightGoal, Targets?> {
        val goal = weightGoal(answers, context)
        val burn = burnKcal(answers, context) ?: return goal to null
        val sex = answers.body.sex
        val scale = current.macroMode == MacroMode.Ratios
        val planned = goal(answers, context, current)
        return when {
            // With no pace on offer (a burn already near the low-goal line) the aim is the burn itself.
            planned != null -> {
                val (stamped, targets) = GoalTargets.apply(planned, current.targets, scale) ?: return goal to null
                goal.copy(targetsKcal = stamped.targetsKcal) to targets
            }
            context.fresh && !context.hard && context.caloriesShown -> {
                val aim = burn + (sex?.stage?.extraKcal ?: 0)
                val start = aim.takeIf { it > Targets.Default.energyKcal }?.let { roundToStep(it) }
                goal to start?.let { current.targets.useRange().withEnergy(it, scale) }
            }
            else -> goal to null
        }
    }

    private fun roundToStep(kcal: Int): Int = (kcal.toDouble() / Targets.STEP_KCAL).roundToInt() * Targets.STEP_KCAL

    /**
     * [current] with what the flow's answers set: the profile details given, the day's activity,
     * the goal and the targets it means ([planTargets]). A detail left blank leaves the setting as
     * it is. The weigh-in is the caller's: weight is never a setting.
     */
    fun save(current: Settings, answers: PlanAnswers, context: PlanContext): Settings {
        val body = answers.body
        val (goal, targets) = planTargets(answers, context, current)
        val planned = goal(answers, context, current)
        val withDate = body.birthDate?.let(current::withBirthDate) ?: current
        // The weight field's switch is a choice of unit, as Display's is: it is kept, and the
        // region no longer decides (Settings.withRegionWeightUnit).
        val unitPicked = PlanViews.profile(answers, context).weightShown
        return withDate.copy(
            bodySex = body.sex?.sex ?: current.bodySex,
            bodyStage = body.sex?.stage ?: current.bodyStage,
            heightCm = body.heightCmOrNull() ?: current.heightCm,
            heightUnit = body.height.unit.takeIf { body.heightCmOrNull() != null } ?: current.heightUnit,
            weightUnit = if (unitPicked) body.weightUnit else current.weightUnit,
            weightUnitChosen = unitPicked || current.weightUnitChosen,
            everydayActivity = body.activity,
            targets = if (planned == null) targets ?: current.targets else current.targets,
        ).let { profiled ->
            when {
                // The goal writes its own target ([GoalBook.applyTarget]); one with no typical day
                // yet is kept, and sets the target once one can be worked out.
                planned?.baseline != null -> GoalBook.applyTarget(profiled, planned)
                planned != null -> GoalBook.withGoal(profiled, planned)
                goal.direction == GoalDirection.JustTrack -> GoalBook.without(profiled, Goal.WEIGHT_ID).copy(weightGoal = goal)
                else -> profiled
            }
        }
    }
}

// ---------- What each step draws ----------

data class ProfileView(
    val answer: BodyAnswer,
    /** The weight field, only where weight is on show and never after "numbers have been hard". */
    val weightShown: Boolean,
    /** "May 11, 1998", or null before one is picked. */
    val birthDateText: String?,
    val heightInvalid: Boolean = false,
    val weightInvalid: Boolean = false,
)

data class ActivityView(
    val selected: EverydayActivity,
    val health: HealthOffer,
) {
    /** The levels to pick from; none while a health app reads activity. */
    val levels: List<EverydayActivity> get() = if (health is HealthOffer.Connected) emptyList() else EverydayActivity.entries
}

data class GoalView(
    val directions: List<GoalDirection>,
    val direction: GoalDirection,
    val target: WeightInput,
    /** "80 kg": the latest weight, for the target's line. Hidden without one. */
    val current: Display,
    val hint: TargetHint?,
    /** The target is under a healthy range for their height: a soft note, and Find support. */
    val belowHealthy: Boolean,
    /** The target field: weight is a number, drawn only where the mode shows weight. */
    val targetShown: Boolean = true,
)

data class RateView(
    val direction: GoalDirection,
    /** "0.5 lb", the pace a week; null when there is no room to plan one. */
    val rate: String?,
    val canSlower: Boolean,
    val canFaster: Boolean,
    /** "250 kcal", the energy a day it means; Hidden unless calories and energy out are on show. */
    val dailyKcal: Display,
    /** "Dec 10, 2026", roughly when the target is reached. */
    val forecast: String?,
    /** At the fastest pace on offer, which the step says is as fast as Opson plans. */
    val atFastest: Boolean,
    /** A weight is known and still no pace fits: the step says Opson won't plan one from here. */
    val noRoom: Boolean = false,
    /** The pace's tier note, where weight shows (docs/plans/goals-v2.md, "Pace"). */
    val pace: PaceAssessment? = null,
    /** The (i): the fat-loss limit where calories show, whether body fat was estimated, and whether one could be worked out. */
    val limit: Display = Display.Hidden,
    val limitEstimated: Boolean = false,
    val limitKnown: Boolean = false,
    /** The day's target this pace means is under the low-goal line for their sex: the low-goal note shows. */
    val lowAim: Boolean = false,
)

/** Which overview the answers lead to. */
enum class OverviewKind {
    /** Losing or gaining, with a target and a forecast. */
    Goal,

    /** Keeping their weight where it is. */
    Maintain,

    /** No weight goal, or none offered: the day's target from the burn, where it shows. */
    NoGoal,

    /** Pregnant or breastfeeding: no weight change, and the extra energy counted. */
    Stage,

    /** After "numbers have been hard": no weights and no deficits, only what Opson will help with. */
    Gentle,
}

data class OverviewView(
    val kind: OverviewKind,
    /** "75 kg", the target or the weight being kept. Hidden where weight is not on show. */
    val goalWeight: Display = Display.Hidden,
    val forecast: String? = null,
    /** "1,950 kcal", the day's target; Hidden where calories are not, or nothing was worked out. */
    val energy: Display = Display.Hidden,
    /** "550 kcal" less (or more) than a typical day burns; Hidden at none or where not on show. */
    val difference: Display = Display.Hidden,
    /** The target is under a typical day's burn (a loss), over it (a gain), or about it. */
    val differenceSign: Int = 0,
    /** Whether the step says how the target compares with a typical day: only where energy out shows. */
    val compared: Boolean = false,
    /**
     * How a typical day was worked out, where energy out shows: resting energy (BMR), everyday
     * activity, what pregnancy or breastfeeding adds, and the total burned (TDEE) they make.
     */
    val resting: Display = Display.Hidden,
    val everyday: Display = Display.Hidden,
    val stageExtra: Display = Display.Hidden,
    val burned: Display = Display.Hidden,
    /** Details are missing, so no target could be worked out: the step says which page adds them. */
    val needsDetails: Boolean = false,
    val stage: BodyStage = BodyStage.None,
)

object PlanViews {

    fun profile(answers: PlanAnswers, context: PlanContext): ProfileView = ProfileView(
        answer = answers.body,
        weightShown = context.weightShown && !context.hard,
        birthDateText = answers.body.birthDate?.let(DateCopy::monthDayYear),
        heightInvalid = answers.body.heightInvalid,
        weightInvalid = answers.body.weightInvalid,
    )

    fun activity(answers: PlanAnswers, context: PlanContext): ActivityView = ActivityView(answers.body.activity, context.health)

    fun goal(answers: PlanAnswers, context: PlanContext): GoalView {
        val target = answers.goal.target.kgOrNull()
        return GoalView(
            directions = DIRECTIONS,
            direction = answers.goal.direction,
            target = answers.goal.target,
            current = weight(answers.body.weightKgOrNull(), answers.body.weightUnit, context),
            hint = PlanFlow.hintShown(answers),
            belowHealthy = answers.goal.direction.changesWeight && target != null &&
                WeightGoals.belowHealthyRange(target, answers.body.heightCmOrNull()),
            targetShown = context.weightShown,
        )
    }

    fun rate(answers: PlanAnswers, context: PlanContext): RateView {
        val rates = PlanFlow.rates(answers, context)
        val profile = answers.body.profile(context.today)
        val limit = FatLoss.limitFor(context.bodyFatPercent, profile.weightKg, profile.heightCm, profile.ageYears, profile.sex)
        val rate = PlanFlow.rate(answers, context)
        val goal = PlanFlow.weightGoal(answers, context)
        val index = rate?.let(rates::indexOf) ?: -1
        return RateView(
            direction = answers.goal.direction,
            rate = rate?.let { WeightGoals.weightText(it, answers.body.weightUnit) }?.takeIf { context.weightShown },
            canSlower = index > 0,
            canFaster = index in 0 until rates.lastIndex,
            // A deficit is energy against what a day uses, so it needs energy out on show, as Today's does.
            dailyKcal = rate?.takeIf { context.energyOutShown }?.let { energy(WeightGoals.kcalPerDay(it), context) } ?: Display.Hidden,
            forecast = goal.forecast?.let(DateCopy::monthDayYear)?.takeIf { context.weightShown },
            atFastest = rate != null && index == rates.lastIndex,
            noRoom = context.weightShown && answers.body.weightKgOrNull() != null && rates.isEmpty(),
            pace = rate?.takeIf { context.weightShown }?.let { GoalPace.assess(answers.goal.direction, it, answers.body.weightKgOrNull(), limit) },
            limit = limit?.kcalPerDay?.let { energy(it, context) } ?: Display.Hidden,
            limitEstimated = limit?.bodyFat?.from == BodyFatFrom.Estimated,
            limitKnown = limit != null,
            lowAim = PlanFlow.goal(answers, context, null)?.let(GoalTargets::of)
                ?.let { GoalTargets.belowLowGoal(it.aimKcal, answers.body.sex?.sex) } == true,
        )
    }

    fun overview(answers: PlanAnswers, context: PlanContext, current: Settings): OverviewView {
        val stage = answers.body.sex?.stage ?: BodyStage.None
        if (context.hard) return OverviewView(OverviewKind.Gentle)
        val (goal, targets) = PlanFlow.planTargets(answers, context, current)
        val burn = PlanFlow.burnKcal(answers, context)
        val aim = targets?.energyKcal
        // Against what a day burns eating the target, digesting food included where it counts.
        val planned = PlanFlow.goal(answers, context, current)?.let(GoalTargets::of)
        val difference = when {
            planned != null -> planned.deltaKcal
            aim != null && burn != null -> aim - burn - stage.extraKcal
            else -> 0
        }
        val wouldSetTargets = goal.direction == GoalDirection.Lose || goal.direction == GoalDirection.Gain ||
            goal.direction == GoalDirection.Maintain || (context.fresh && context.caloriesShown)
        val kind = when {
            stage != BodyStage.None -> OverviewKind.Stage
            goal.direction.changesWeight -> OverviewKind.Goal
            goal.direction == GoalDirection.Maintain -> OverviewKind.Maintain
            else -> OverviewKind.NoGoal
        }
        val unit = answers.body.weightUnit
        return OverviewView(
            kind = kind,
            goalWeight = weight(goal.targetKg, unit, context),
            forecast = goal.forecast?.let(DateCopy::monthDayYear)?.takeIf { context.weightShown },
            energy = aim?.let { energy(it, context) } ?: Display.Hidden,
            difference = if (aim != null && context.energyOutShown && abs(difference) >= Targets.STEP_KCAL) {
                energy(abs(difference), context)
            } else {
                Display.Hidden
            },
            differenceSign = difference.coerceIn(-1, 1),
            compared = aim != null && context.energyOutShown && kind != OverviewKind.NoGoal,
            resting = PlanFlow.restingKcal(answers, context)?.let { burnPart(it, context) } ?: Display.Hidden,
            everyday = PlanFlow.activityKcal(answers, context)?.let { burnPart(it, context) } ?: Display.Hidden,
            stageExtra = stage.extraKcal.takeIf { it > 0 && burn != null }?.let { burnPart(it, context) } ?: Display.Hidden,
            burned = burn?.let { burnPart(it + stage.extraKcal, context) } ?: Display.Hidden,
            needsDetails = burn == null && wouldSetTargets,
            stage = stage,
        )
    }

    private fun weight(kg: Double?, unit: WeightUnit, context: PlanContext): Display =
        if (kg == null || !context.weightShown) Display.Hidden else context.policy.resolve(Metric.Weight, kg, Surface.Settings, FormatContext(weightUnit = unit))

    /** A part of what a day burns, energy out, only where energy out and calories both show. */
    private fun burnPart(kcal: Int, context: PlanContext): Display =
        if (!context.energyOutShown) Display.Hidden else context.policy.resolve(Metric.EnergyOut, kcal.toDouble(), Surface.Settings)

    /** Energy a day, only where calories are on show in full: a deficit gives away what is eaten. */
    private fun energy(kcal: Int, context: PlanContext): Display =
        if (!context.caloriesShown) Display.Hidden else context.policy.resolve(Metric.Calories, kcal.toDouble(), Surface.Settings)

    /** Lose first, as most people come for it; "Just track" last, an answer like the others. */
    private val DIRECTIONS = listOf(GoalDirection.Lose, GoalDirection.Maintain, GoalDirection.Gain, GoalDirection.JustTrack)
}
