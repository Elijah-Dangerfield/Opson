package com.opson.features.onboarding.impl

import com.opson.libraries.diary.GoalBook
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.ui.Birthdays
import com.opson.libraries.nutrition.BaselineEnvironment
import com.opson.libraries.nutrition.BaselineInputs
import com.opson.libraries.nutrition.BodyFatFrom
import com.opson.libraries.nutrition.BurnMeasure
import com.opson.libraries.nutrition.FatLoss
import com.opson.libraries.nutrition.Goal
import com.opson.libraries.nutrition.GoalAveraging
import com.opson.libraries.nutrition.GoalBaselines
import com.opson.libraries.nutrition.GoalCatalog
import com.opson.libraries.nutrition.GoalKind
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
import com.opson.libraries.ui.components.text.DateOrder
import com.opson.libraries.ui.components.text.DateParts
import com.opson.libraries.ui.components.text.NumberInput
import com.opson.libraries.diary.MacroMode
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The goal flow (owner feedback, 2026-10-29 and 2026-10-30), walked by onboarding after the
 * starting mode and by the goal flow Today offers an account that never set one. Pure: both view
 * models hold [PlanAnswers] and a [PlanContext], and everything drawn or saved is worked out here.
 *
 * Onboarding ([PlanContext.asks]) starts by asking whether to set a goal at all ([Ask]): "Not now"
 * ends setup with nothing about a body asked. A goal picks its kind ([Kind]) from what the mode
 * shows ([PlanFlow.picks]), and only the kinds that set a calorie target ask about the body: a
 * weight goal and a daily or weekly energy balance get the profile and the activity, the others
 * just their own number. Every goal ends with a streak ([Streak], while celebrations are on) and
 * the commitment. Today's flow is a weight goal, as before: no [Ask], [Kind] or [Streak].
 *
 * Someone who said numbers have been hard, or is 16 or 17, is offered only the gentle kinds:
 * showing up and regular meals. A weight goal is offered only where weight is on show
 * ([PlanFlow.showsGoal]), and every amount goes through the mode's policy.
 */
enum class PlanStep(val eventName: String) {
    /** "Want to set a goal?" Onboarding only. */
    Ask("goal_ask"),

    /** Which kind of goal. Onboarding only. */
    Kind("goal_kind"),
    Profile("profile"),
    Activity("activity"),
    Goal("weight_goal"),
    Rate("goal_rate"),

    /** How far under or over a typical day, each day or across the week. */
    Balance("energy_balance"),

    /** How much active energy a day. */
    Burn("burn_goal"),

    /** How many meals a day. */
    MealCount("meals_goal"),
    Overview("goal_overview"),

    /** "Keep a streak?" Onboarding only, with celebrations on. */
    Streak("streak"),
    Commit("commitment"),
}

/**
 * The goals onboarding offers, each one of the engine's [GoalKind]s. An energy balance comes two
 * ways, judged each day or averaged across the week, as the owner listed them. Steps are left out
 * until something reads them.
 */
enum class GoalPick(val kind: GoalKind, val averaging: GoalAveraging = GoalAveraging.Daily) {
    Weight(GoalKind.Weight),
    Balance(GoalKind.EnergyBalance),
    WeeklyBalance(GoalKind.EnergyBalance, GoalAveraging.Weekly),
    Burn(GoalKind.Burn),
    ShowUp(GoalKind.TrackEveryDay),
    Meals(GoalKind.Meals),
    ;

    /** Whether it sets the day's calorie target, and so needs a typical day's burn: the profile and the activity. */
    val setsCalories: Boolean get() = kind == GoalKind.Weight || kind == GoalKind.EnergyBalance
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
    /** The birthday's boxes as typed; [birthDate] follows them once they make one. */
    val birthday: DateParts = DateParts.of(birthDate),
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

/** A set energy balance: under or over a typical day's burn, by [kcal] a day. */
data class BalanceAnswer(val under: Boolean = true, val kcal: Int = DEFAULT_KCAL) {
    /** Energy a day against the typical day: negative under it. */
    val deltaKcal: Int get() = if (under) -kcal else kcal

    companion object {
        /** A small start: about a quarter of a kilogram a week. */
        const val DEFAULT_KCAL = 250
        const val STEP_KCAL = 50

        // TODO(clinician): the most a set balance offers either way. The edge of a weight goal's
        // Steady tier when the weight isn't known (GoalPace's FAST_DEFICIT_KCAL).
        const val MAX_KCAL = 1000
    }
}

data class PlanAnswers(
    val body: BodyAnswer = BodyAnswer(),
    val goal: GoalAnswer = GoalAnswer(),
    /** "Want to set a goal?": yes, "Not now", or not answered yet. */
    val wants: Boolean? = null,
    /** The kind of goal, once picked. */
    val pick: GoalPick? = null,
    val balance: BalanceAnswer = BalanceAnswer(),
    /** Active energy a day, for a burn goal. */
    val burnKcal: Int = DEFAULT_BURN_KCAL,
    /** Meals a day, for a meals goal. */
    val meals: Int = GoalType.DEFAULT_MEALS,
    /** "Keep a streak?": yes, no, or not asked. */
    val streak: Boolean? = null,
) {
    /** Both weights follow one unit switch. */
    fun withWeightUnit(unit: WeightUnit): PlanAnswers =
        copy(body = body.copy(weight = body.weight.inUnit(unit)), goal = goal.copy(target = goal.target.inUnit(unit)))

    companion object {
        // TODO(clinician): a burn goal's starting point and its reach. An engineer picked them: a
        // brisk half hour's walk to start, and no more than a long run a day.
        const val DEFAULT_BURN_KCAL = 300
        const val BURN_STEP_KCAL = 50
        val BURN_KCAL: IntRange = 50..1500
        val MEALS: IntRange = 1..6
    }
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
    /** Onboarding: whether to set a goal, and which kind, are asked first. Today's flow is a weight goal. */
    val asks: Boolean = false,
    /** `release.celebrations` on, so a goal is followed by "Keep a streak?". Onboarding only. */
    val streakOffered: Boolean = false,
    /** The birthday was asked before this flow (onboarding's age question), so the profile leaves it out. */
    val birthdayAsked: Boolean = false,
    /** The order the birthday's boxes come in, from the phone's region. */
    val dateOrder: DateOrder = DateOrder.DayMonthYear,
) {
    val weightShown: Boolean get() = policy.effectiveLevel(Metric.Weight, Surface.Settings) == Visibility.Show

    val caloriesShown: Boolean get() = policy.effectiveLevel(Metric.Calories, Surface.Settings) == Visibility.Show

    /** The target set against what a day uses gives that away, so the comparison needs energy out on show too. */
    val energyOutShown: Boolean get() = caloriesShown && policy.effectiveLevel(Metric.EnergyOut, Surface.Settings) == Visibility.Show
}

/** Every change the flow's steps can make. */
sealed interface PlanAction {
    /** "Yes, help me set one" or "Not now, just let me log". The view model moves on from it. */
    data class WantsGoal(val wants: Boolean) : PlanAction
    data class GoalPicked(val pick: GoalPick) : PlanAction
    data class SexPicked(val sex: SexChoice) : PlanAction

    /** The profile's birthday boxes, in Today's flow; onboarding asked it before sign-in. */
    data class BirthdayTyped(val parts: DateParts) : PlanAction
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

    /** Under or over a typical day, for an energy balance. */
    data class BalanceSidePicked(val under: Boolean) : PlanAction

    /** A step less or more: the balance, the active energy, the meals. */
    data class BalanceStepped(val more: Boolean) : PlanAction
    data class BurnStepped(val more: Boolean) : PlanAction
    data class MealsStepped(val more: Boolean) : PlanAction

    /** "Yes, keep a streak" or "No streak". The view model moves on from it. */
    data class StreakPicked(val keep: Boolean) : PlanAction

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
        val born = settings.knownBirthDate
        return PlanAnswers(
            body = BodyAnswer(
                sex = SexChoice.of(settings.bodySex, settings.bodyStage),
                birthDate = born,
                height = HeightInput.of(settings.heightCm, settings.heightUnitOr(regionHeight)),
                weight = WeightInput.of(latestKg, unit),
                activity = settings.everydayActivity,
                // An older build may have kept only the year: the boxes open on it.
                birthday = born?.let(DateParts::of) ?: DateParts(year = settings.birthYear?.toString().orEmpty()),
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
     * hard" or under 18, and never while pregnant or breastfeeding, when weight change isn't
     * Opson's to plan.
     */
    fun showsGoal(answers: PlanAnswers, context: PlanContext): Boolean =
        !context.hard && context.weightShown && stage(answers) == BodyStage.None

    /**
     * An energy balance needs calories and what a day uses on show, and the same care as a weight
     * goal: not after numbers have been hard, under 18, or while pregnant or breastfeeding.
     */
    fun showsBalance(answers: PlanAnswers, context: PlanContext): Boolean =
        !context.hard && context.energyOutShown && stage(answers) == BodyStage.None

    private fun stage(answers: PlanAnswers): BodyStage = answers.body.sex?.stage ?: BodyStage.None

    /**
     * The kinds of goal onboarding offers through [context]'s mode ([GoalCatalog.offered]): the
     * gentle ones only after numbers have been hard or under 18, a weekly average only where
     * [GoalCatalog.weeklyOffered]. Pregnancy isn't known yet when the kind is picked; the steps
     * after it allow for it.
     */
    fun picks(context: PlanContext): List<GoalPick> {
        val kinds = GoalCatalog.offered(context.policy, context.hard)
        return buildList {
            if (GoalKind.Weight in kinds) add(GoalPick.Weight)
            if (GoalKind.EnergyBalance in kinds) {
                add(GoalPick.Balance)
                if (GoalCatalog.weeklyOffered(context.policy, context.hard)) add(GoalPick.WeeklyBalance)
            }
            // Its number is energy in kcal, so calories have to be on show too, as for the overview's working.
            if (GoalKind.Burn in kinds && context.energyOutShown) add(GoalPick.Burn)
            // TODO(steps): offer GoalKind.Steps once a steps source reads them (goals-v2, "Goal types").
            if (GoalKind.TrackEveryDay in kinds) add(GoalPick.ShowUp)
            if (GoalKind.Meals in kinds) add(GoalPick.Meals)
        }
    }

    /** The kind these answers set a goal of: onboarding's pick, or Today's weight goal. */
    fun pick(answers: PlanAnswers, context: PlanContext): GoalPick? = when {
        !context.asks -> GoalPick.Weight
        answers.wants != true -> null
        else -> answers.pick?.takeIf { it in picks(context) }
    }

    /** The steps for these answers, in order. */
    fun steps(answers: PlanAnswers, context: PlanContext): List<PlanStep> = buildList {
        if (context.asks) {
            add(PlanStep.Ask)
            if (answers.wants != true) return@buildList
            add(PlanStep.Kind)
        }
        val pick = pick(answers, context) ?: return@buildList
        when (pick) {
            GoalPick.Weight -> {
                add(PlanStep.Profile)
                add(PlanStep.Activity)
                if (showsGoal(answers, context)) {
                    add(PlanStep.Goal)
                    if (answers.goal.direction.changesWeight) add(PlanStep.Rate)
                }
                add(PlanStep.Overview)
            }
            GoalPick.Balance, GoalPick.WeeklyBalance -> {
                add(PlanStep.Profile)
                add(PlanStep.Activity)
                if (showsBalance(answers, context)) add(PlanStep.Balance)
                add(PlanStep.Overview)
            }
            GoalPick.Burn -> add(PlanStep.Burn)
            GoalPick.Meals -> add(PlanStep.MealCount)
            GoalPick.ShowUp -> Unit
        }
        if (context.asks && context.streakOffered) add(PlanStep.Streak)
        add(PlanStep.Commit)
    }

    /**
     * [answers] after [action]. Connecting a health app and Find support are the view model's to
     * do, and change nothing here. A typed birthday sets the date once it makes one for someone 13
     * to 120, as the equation is for adults and teenagers.
     */
    fun reduce(answers: PlanAnswers, action: PlanAction, context: PlanContext): PlanAnswers {
        val body = answers.body
        return when (action) {
            is PlanAction.WantsGoal -> answers.copy(wants = action.wants)
            is PlanAction.GoalPicked -> if (action.pick in picks(context)) answers.copy(pick = action.pick) else answers
            is PlanAction.SexPicked -> answers.copy(body = body.copy(sex = action.sex))
            is PlanAction.BirthdayTyped -> answers.copy(
                body = body.copy(birthday = action.parts, birthDate = Birthdays.dateOrNull(action.parts, context.today, Birthdays.EQUATION_AGES)),
            )
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
            is PlanAction.BalanceSidePicked -> answers.copy(balance = answers.balance.copy(under = action.under).fitted(answers, context))
            is PlanAction.BalanceStepped -> stepBalance(answers, context, action.more)
            is PlanAction.BurnStepped -> answers.copy(
                burnKcal = (answers.burnKcal + if (action.more) PlanAnswers.BURN_STEP_KCAL else -PlanAnswers.BURN_STEP_KCAL)
                    .coerceIn(PlanAnswers.BURN_KCAL),
            )
            is PlanAction.MealsStepped -> answers.copy(meals = (answers.meals + if (action.more) 1 else -1).coerceIn(PlanAnswers.MEALS))
            is PlanAction.StreakPicked -> answers.copy(streak = action.keep)
            PlanAction.ConnectHealthTapped, PlanAction.FindSupportTapped -> answers
        }
    }

    /**
     * Continue on [step]: the kind needs one picked, the weight goal a direction and a usable
     * target to lose or gain. [PlanStep.Ask] and [PlanStep.Streak] answer with their own buttons.
     */
    fun canContinue(step: PlanStep, answers: PlanAnswers, context: PlanContext): Boolean = when (step) {
        PlanStep.Ask -> answers.wants != null
        PlanStep.Kind -> pick(answers, context) != null
        PlanStep.Goal -> goalDirections(context).contains(answers.goal.direction) && targetHint(answers) == null
        PlanStep.Streak -> answers.streak != null
        else -> true
    }

    /** The directions the weight goal step lists: onboarding has "Track every day" as a kind of its own. */
    fun goalDirections(context: PlanContext): List<GoalDirection> =
        if (context.asks) listOf(GoalDirection.Lose, GoalDirection.Maintain, GoalDirection.Gain) else DIRECTIONS

    /** Lose first, as most people come for it; "Just track" last, an answer like the others. */
    private val DIRECTIONS = listOf(GoalDirection.Lose, GoalDirection.Maintain, GoalDirection.Gain, GoalDirection.JustTrack)

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
        stage = stage(answers),
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

    /**
     * The most an energy balance offers on its side: [BalanceAnswer.MAX_KCAL], and under a
     * typical day never so far that the target would be worked out under
     * [GoalTargets.WORKED_OUT_FLOOR_KCAL], the one line the engine holds for everyone.
     */
    fun balanceMax(answers: PlanAnswers, context: PlanContext, under: Boolean = answers.balance.under): Int {
        if (!under) return BalanceAnswer.MAX_KCAL
        val burn = burnKcal(answers, context) ?: return BalanceAnswer.MAX_KCAL
        val room = (burn + stage(answers).extraKcal - GoalTargets.WORKED_OUT_FLOOR_KCAL) / BalanceAnswer.STEP_KCAL * BalanceAnswer.STEP_KCAL
        return room.coerceIn(0, BalanceAnswer.MAX_KCAL)
    }

    private fun BalanceAnswer.fitted(answers: PlanAnswers, context: PlanContext): BalanceAnswer =
        copy(kcal = kcal.coerceIn(BalanceAnswer.STEP_KCAL, max(BalanceAnswer.STEP_KCAL, balanceMax(answers, context, under))))

    private fun stepBalance(answers: PlanAnswers, context: PlanContext, more: Boolean): PlanAnswers {
        val step = if (more) BalanceAnswer.STEP_KCAL else -BalanceAnswer.STEP_KCAL
        val next = answers.balance.copy(kcal = answers.balance.kcal + step)
        val top = balanceMax(answers, context)
        if (next.kcal < BalanceAnswer.STEP_KCAL || next.kcal > max(top, BalanceAnswer.STEP_KCAL)) return answers
        return answers.copy(balance = next)
    }

    /** The goal these answers set, or [WeightGoal.None] when no goal step was answered. */
    fun weightGoal(answers: PlanAnswers, context: PlanContext): WeightGoal {
        if (pick(answers, context) != GoalPick.Weight || !showsGoal(answers, context)) return WeightGoal.None
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
     * The goal these answers set as one of the engine's, starting today. A weight goal keeps
     * [weightGoal]'s direction, target and pace; an energy balance its amount and how it is
     * judged; both carry the typical day these answers give ([GoalBaselines.of]), and a weight
     * goal set before keeps its averaging and band. The others are just their number. Null for
     * no goal, just tracking, or a kind its step no longer offers (pregnancy found on the profile).
     */
    fun goal(answers: PlanAnswers, context: PlanContext, current: Settings?): Goal? {
        val baseline = inputs(answers, context)?.let(GoalBaselines::of)
        return when (pick(answers, context)) {
            null -> null
            GoalPick.Weight -> {
                val legacy = weightGoal(answers, context)
                val direction = legacy.direction
                if (direction != GoalDirection.Lose && direction != GoalDirection.Gain && direction != GoalDirection.Maintain) return null
                val before = current?.let(GoalBook::weight)
                Goal(
                    id = Goal.WEIGHT_ID,
                    type = GoalType.Weight(direction, legacy.targetKg, legacy.kgPerWeek, legacy.startKg),
                    startDate = context.today,
                    averaging = before?.averaging ?: GoalAveraging.Daily,
                    toleranceKcal = before?.toleranceKcal ?: Goal.DEFAULT_TOLERANCE_KCAL,
                    baseline = baseline,
                )
            }
            GoalPick.Balance, GoalPick.WeeklyBalance -> if (showsBalance(answers, context)) {
                Goal(
                    id = GoalKind.EnergyBalance.key,
                    type = GoalType.EnergyBalance(answers.balance.deltaKcal),
                    startDate = context.today,
                    averaging = answers.pick?.averaging ?: GoalAveraging.Daily,
                    baseline = baseline,
                )
            } else {
                null
            }
            GoalPick.Burn -> Goal(GoalKind.Burn.key, GoalType.Burn(answers.burnKcal, BurnMeasure.Active), context.today)
            GoalPick.Meals -> Goal(GoalKind.Meals.key, GoalType.Meals(answers.meals), context.today)
            GoalPick.ShowUp -> Goal(GoalKind.TrackEveryDay.key, GoalType.TrackEveryDay, context.today)
        }
    }

    /**
     * The targets these answers set, and the goal stamped with the aim it wrote; null targets
     * leave Targets as it is. A goal that sets the calorie target sets them from a typical day
     * ([GoalTargets.apply]): its own band around the target, whatever range was there. Otherwise
     * only a new account ([PlanContext.fresh]) that went through the profile gets a starting
     * range: from the burn, plus what pregnancy or breastfeeding needs, never under the app's own
     * starting range. Nothing is written for someone who said numbers have been hard, or whose
     * mode offered no goal, as the setup steps never wrote one for anyone who didn't see it.
     */
    fun planTargets(answers: PlanAnswers, context: PlanContext, current: Settings): Pair<WeightGoal, Targets?> {
        val goal = weightGoal(answers, context)
        val burn = burnKcal(answers, context)?.takeIf { PlanStep.Profile in steps(answers, context) } ?: return goal to null
        val sex = answers.body.sex
        val scale = current.macroMode == MacroMode.Ratios
        val planned = goal(answers, context, current)?.takeIf { it.type.setsCalories }
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
     * [current] with what the flow's answers set: the profile details given (only where the
     * profile was one of the steps, so a body typed before a change of mind isn't kept), the day's
     * activity, the goal and the targets it means ([planTargets]), and the streak. A detail left
     * blank leaves the setting as it is. "Not now" in onboarding is an answer too: no weight goal,
     * so Today doesn't ask again. The weigh-in is the caller's: weight is never a setting.
     */
    fun save(current: Settings, answers: PlanAnswers, context: PlanContext): Settings {
        val steps = steps(answers, context)
        val bodyAsked = PlanStep.Profile in steps
        val body = answers.body
        val (goal, targets) = planTargets(answers, context, current)
        val planned = goal(answers, context, current)
        val withDate = body.birthDate?.takeIf { bodyAsked || context.birthdayAsked }?.let(current::withBirthDate) ?: current
        // The weight field's switch is a choice of unit, as Display's is: it is kept, and the
        // region no longer decides (Settings.withRegionWeightUnit).
        val unitPicked = bodyAsked && PlanViews.profile(answers, context).weightShown
        val profiled = if (!bodyAsked) {
            withDate
        } else {
            withDate.copy(
                bodySex = body.sex?.sex ?: current.bodySex,
                bodyStage = body.sex?.stage ?: current.bodyStage,
                heightCm = body.heightCmOrNull() ?: current.heightCm,
                heightUnit = body.height.unit.takeIf { body.heightCmOrNull() != null } ?: current.heightUnit,
                weightUnit = if (unitPicked) body.weightUnit else current.weightUnit,
                weightUnitChosen = unitPicked || current.weightUnitChosen,
                everydayActivity = if (PlanStep.Activity in steps) body.activity else current.everydayActivity,
                targets = if (planned?.type?.setsCalories == true) current.targets else targets ?: current.targets,
            )
        }
        val goaled = when {
            // A calorie goal writes its own target ([GoalBook.applyTarget]); one with no typical day
            // yet is kept, and sets the target once one can be worked out.
            planned != null && planned.type.setsCalories && planned.baseline != null -> GoalBook.applyTarget(profiled, planned)
            planned != null -> GoalBook.withGoal(profiled, planned)
            goal.direction == GoalDirection.JustTrack -> GoalBook.without(profiled, Goal.WEIGHT_ID).copy(weightGoal = goal)
            else -> profiled
        }
        // Answered in onboarding without a weight goal: nothing to offer later on Today.
        val answered = if (context.asks && answers.wants != null && !goaled.weightGoal.isSet) {
            goaled.copy(weightGoal = WeightGoal(GoalDirection.JustTrack))
        } else {
            goaled
        }
        val streak = answers.streak?.takeIf { PlanStep.Streak in steps }
        return if (streak != null) answered.copy(celebrationsEnabled = streak) else answered
    }
}

// ---------- What each step draws ----------

data class ProfileView(
    val answer: BodyAnswer,
    /** The weight field, only where weight is on show and never after "numbers have been hard". */
    val weightShown: Boolean,
    /** The birthday's boxes, unless onboarding asked it already. */
    val birthdayShown: Boolean = true,
    val dateOrder: DateOrder = DateOrder.DayMonthYear,
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

/** The kinds of goal on offer, and the one picked. */
data class KindView(val picks: List<GoalPick>, val picked: GoalPick?)

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

/**
 * An energy balance's step. Only offered where calories and energy out are on show, so its amounts
 * are always calories; they still go through the policy.
 */
data class BalanceView(
    val under: Boolean,
    /** "250 kcal" a day under or over. */
    val amount: Display,
    val canLess: Boolean,
    val canMore: Boolean,
    /** "1,950 kcal", the day's target this means; Hidden while details are missing. */
    val target: Display,
    /** Averaged across the week. */
    val weekly: Boolean,
    /** The tier note: gentle, steady, faster, or past the fat-loss limit. */
    val pace: PaceAssessment? = null,
    val limit: Display = Display.Hidden,
    val limitEstimated: Boolean = false,
    val limitKnown: Boolean = false,
    val lowAim: Boolean = false,
)

/** A burn goal's step: active energy a day, through the policy as energy out. */
data class BurnView(val amount: Display, val canLess: Boolean, val canMore: Boolean)

/** A meals goal's step: meals a day. A count, not a nutrition number. */
data class MealCountView(val count: Int, val canLess: Boolean, val canMore: Boolean)

/** Which overview the answers lead to. */
enum class OverviewKind {
    /** Losing or gaining, with a target and a forecast. */
    Goal,

    /** Keeping their weight where it is. */
    Maintain,

    /** A set amount under or over a typical day, each day or across the week. */
    Balance,

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
    /** An energy balance averaged across the week. */
    val weekly: Boolean = false,
)

object PlanViews {

    fun profile(answers: PlanAnswers, context: PlanContext): ProfileView = ProfileView(
        answer = answers.body,
        weightShown = context.weightShown && !context.hard,
        birthdayShown = !context.birthdayAsked,
        dateOrder = context.dateOrder,
        heightInvalid = answers.body.heightInvalid,
        weightInvalid = answers.body.weightInvalid,
    )

    fun activity(answers: PlanAnswers, context: PlanContext): ActivityView = ActivityView(answers.body.activity, context.health)

    fun kind(answers: PlanAnswers, context: PlanContext): KindView = KindView(PlanFlow.picks(context), PlanFlow.pick(answers, context))

    fun goal(answers: PlanAnswers, context: PlanContext): GoalView {
        val target = answers.goal.target.kgOrNull()
        return GoalView(
            directions = PlanFlow.goalDirections(context),
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
            forecast = goal.forecast?.let(com.opson.libraries.ui.time.DateCopy::monthDayYear)?.takeIf { context.weightShown },
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

    fun balance(answers: PlanAnswers, context: PlanContext): BalanceView {
        val profile = answers.body.profile(context.today)
        val limit = FatLoss.limitFor(context.bodyFatPercent, profile.weightKg, profile.heightCm, profile.ageYears, profile.sex)
        val balance = answers.balance
        val target = PlanFlow.goal(answers, context, null)?.let(GoalTargets::of)
        return BalanceView(
            under = balance.under,
            amount = energy(balance.kcal, context),
            canLess = balance.kcal > BalanceAnswer.STEP_KCAL,
            canMore = balance.kcal < PlanFlow.balanceMax(answers, context),
            target = target?.let { energy(it.aimKcal, context) } ?: Display.Hidden,
            weekly = answers.pick == GoalPick.WeeklyBalance,
            pace = GoalPace.assessDelta(balance.deltaKcal, profile.weightKg, limit),
            limit = limit?.kcalPerDay?.let { energy(it, context) } ?: Display.Hidden,
            limitEstimated = limit?.bodyFat?.from == BodyFatFrom.Estimated,
            limitKnown = limit != null,
            lowAim = target != null && GoalTargets.belowLowGoal(target.aimKcal, answers.body.sex?.sex),
        )
    }

    fun burn(answers: PlanAnswers, context: PlanContext): BurnView = BurnView(
        amount = burnPart(answers.burnKcal, context),
        canLess = answers.burnKcal > PlanAnswers.BURN_KCAL.first,
        canMore = answers.burnKcal < PlanAnswers.BURN_KCAL.last,
    )

    fun mealCount(answers: PlanAnswers): MealCountView = MealCountView(
        count = answers.meals,
        canLess = answers.meals > PlanAnswers.MEALS.first,
        canMore = answers.meals < PlanAnswers.MEALS.last,
    )

    fun overview(answers: PlanAnswers, context: PlanContext, current: Settings): OverviewView {
        val stage = answers.body.sex?.stage ?: BodyStage.None
        if (context.hard) return OverviewView(OverviewKind.Gentle)
        val (goal, targets) = PlanFlow.planTargets(answers, context, current)
        val burn = PlanFlow.burnKcal(answers, context)
        val aim = targets?.energyKcal
        val pick = PlanFlow.pick(answers, context)
        // Against what a day burns eating the target, digesting food included where it counts.
        val planned = PlanFlow.goal(answers, context, current)?.let(GoalTargets::of)
        val difference = when {
            planned != null -> planned.deltaKcal
            aim != null && burn != null -> aim - burn - stage.extraKcal
            else -> 0
        }
        val balance = (pick == GoalPick.Balance || pick == GoalPick.WeeklyBalance) && PlanFlow.showsBalance(answers, context)
        val wouldSetTargets = goal.direction == GoalDirection.Lose || goal.direction == GoalDirection.Gain ||
            goal.direction == GoalDirection.Maintain || balance || (context.fresh && context.caloriesShown)
        val kind = when {
            stage != BodyStage.None -> OverviewKind.Stage
            balance -> OverviewKind.Balance
            goal.direction.changesWeight -> OverviewKind.Goal
            goal.direction == GoalDirection.Maintain -> OverviewKind.Maintain
            else -> OverviewKind.NoGoal
        }
        val unit = answers.body.weightUnit
        return OverviewView(
            kind = kind,
            goalWeight = weight(goal.targetKg, unit, context),
            forecast = goal.forecast?.let(com.opson.libraries.ui.time.DateCopy::monthDayYear)?.takeIf { context.weightShown },
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
            weekly = balance && pick == GoalPick.WeeklyBalance,
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
}
