package com.opson.features.onboarding.impl

import com.opson.libraries.core.fixed
import com.opson.libraries.diary.MealTimes
import com.opson.libraries.health.HealthSource
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.HeightInput
import com.opson.libraries.nutrition.HeightUnit
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.SexChoice
import com.opson.libraries.nutrition.Units
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.ui.components.text.DateOrder
import com.opson.libraries.ui.components.text.DateParts
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Onboarding states for previews and `CaloriesHiddenLeakTest`. Each one runs
 * through [OnboardingState.withRecommendation] and, on a setup step,
 * [OnboardingState.withViews], the code the ViewModel runs, so the
 * recommendation, the alternatives and every amount are the real ones.
 */
object OnboardingFixtures {

    /**
     * The answers design v3 draws (1.2 to 1.5): eating enough and eating
     * regularly, some numbers are fine, and yes to the statement. Recommends
     * Macros only, with No numbers and Macros with grams beside it.
     */
    val DesignAnswers = SetupAnswers(
        why = setOf(Why.EatingEnough, Why.EatingRegularly),
        feel = NumbersFeel.SomeAreFine,
        statement = StatementAnswer.Agree,
    )

    /** Someone who would see All numbers if they had not agreed numbers were hard. */
    val HardAnswers = SetupAnswers(
        why = setOf(Why.Performance),
        feel = NumbersFeel.LoveThem,
        statement = StatementAnswer.Agree,
    )

    /** Someone who loves numbers and wants to change their weight: All numbers, so the goal steps. */
    val NumbersAnswers = SetupAnswers(why = setOf(Why.ChangingWeight), feel = NumbersFeel.LoveThem)

    /**
     * The profile filled in, in centimetres and kilograms: about 1,350 kcal a day at rest and
     * 1,620 on a mostly sitting day.
     */
    val Body = BodyAnswer(
        sex = SexChoice.Female,
        birthDate = LocalDate(1992, 4, 18),
        height = HeightInput(HeightUnit.Cm, cm = "168"),
        weight = WeightInput("64", WeightUnit.Kg),
    )

    /**
     * The same as a US phone types it, 5'11.5" and 180 lb, with some walking: about 1,780 kcal
     * at rest and 2,450 on a usual day.
     */
    val ImperialBody = BodyAnswer(
        sex = SexChoice.Male,
        birthDate = LocalDate(1990, 7, 2),
        height = HeightInput(HeightUnit.FtIn, feet = "5", inches = "11.5"),
        weight = WeightInput("180", WeightUnit.Lb),
        activity = EverydayActivity.SomeWalking,
    )

    /** 400 a day under a typical day, in [ImperialBody]. [weekly] averages it across the week. */
    fun balance(weekly: Boolean = false): PlanAnswers = PlanAnswers(
        body = ImperialBody,
        pick = if (weekly) GoalPick.WeeklyBalance else GoalPick.Balance,
        balance = BalanceAnswer(under = true, kcal = 400),
    )

    /** [ImperialBody] aiming for 170 lb at half a pound a week. */
    val LoseTen = PlanAnswers(
        body = ImperialBody,
        goal = GoalAnswer(GoalDirection.Lose, target = WeightInput("170", WeightUnit.Lb), kgPerWeek = Units.lbToKg(0.5)),
    )

    /** Noon on the fixtures' day, so Later is still closed. */
    private val NOW = Instant.parse("2026-10-25T12:00:00Z")

    fun policy(preset: PresetId): VisibilityPolicy =
        VisibilityPolicy(NumbersSettings(preset = preset), Clock.fixed(NOW), TimeZone.UTC)

    /** On iOS, as the design draws it. Welcome looks the same on Android. [beta] is `release.betaNotice`. */
    fun welcome(beta: Boolean = false): OnboardingState = OnboardingState(appleEnabled = true, betaNotice = beta).withRecommendation()

    /** The birthday question after Welcome, with [answer] typed so far, in [order]. */
    fun age(answer: LocalDate? = null, order: DateOrder = DateOrder.MonthDayYear): OnboardingState =
        OnboardingState(step = OnboardingStep.Age, ageParts = DateParts.of(answer), ageAnswer = answer, dateOrder = order).withRecommendation()

    /** Someone under 16: no account. */
    fun tooYoung(): OnboardingState = OnboardingState(step = OnboardingStep.TooYoung).withRecommendation()

    fun question(question: SetupQuestion, answers: SetupAnswers = SetupAnswers()): OnboardingState =
        OnboardingState(step = OnboardingStep.Question(question), answers = answers).withRecommendation()

    /** No answers by default, which recommends All numbers. */
    fun recommendation(
        answers: SetupAnswers = SetupAnswers(),
        picked: PresetId? = null,
        allModes: Boolean = false,
    ): OnboardingState = OnboardingState(
        step = OnboardingStep.Recommendation,
        answers = answers,
        picked = picked,
        showAllModes = allModes,
    ).withRecommendation()

    /**
     * One of the goal flow's steps after [answers] (by default [NumbersAnswers], in All numbers),
     * with [plan] answered so far: a goal wanted and, unless [plan] says otherwise, a weight goal.
     * The birthday came from the age question, and celebrations are on. [policy] is the leak
     * test's, or the starting mode's.
     */
    fun plan(
        step: PlanStep,
        plan: PlanAnswers = PlanAnswers(),
        answers: SetupAnswers = NumbersAnswers,
        picked: PresetId? = null,
        health: HealthOffer = HealthOffer.Unavailable,
        policy: VisibilityPolicy? = null,
    ): OnboardingState {
        val wanted = if (step == PlanStep.Ask || plan.wants != null) plan else plan.copy(wants = true, pick = plan.pick ?: GoalPick.Weight)
        val state = OnboardingState(
            step = OnboardingStep.Plan(step),
            answers = answers,
            picked = picked,
            setupSteps = true,
            plan = wanted,
            health = health,
            ageAsked = true,
            streakOffered = true,
        ).withRecommendation()
        return state.withViews(policy ?: policy(state.chosen))
    }

    /** The activity step, offering the phone's health app. */
    fun activityConnect(): OnboardingState =
        plan(PlanStep.Activity, PlanAnswers(body = ImperialBody), health = HealthOffer.CanConnect(HealthSource.AppleHealth))

    /** The meal times step after the design's answers (eating regularly), with a later breakfast. */
    fun meals(): OnboardingState = OnboardingState(
        step = OnboardingStep.Meals,
        answers = DesignAnswers,
        setupSteps = true,
        mealTimes = MealTimes(breakfast = LocalTime(9, 30)),
    ).withRecommendation().withViews(policy(PresetId.MacrosOnly))

    /**
     * The sign-in step after Welcome. Apple shows on iOS only. [guest] is someone from a build
     * where accounts were optional; [conflict] is their link hitting an account that exists.
     */
    fun signIn(
        appleEnabled: Boolean = false,
        guest: Boolean = false,
        inFlight: AccountMethod? = null,
        error: OnboardingAccountError? = null,
        conflict: AccountMethod? = null,
    ): OnboardingState = OnboardingState(
        step = OnboardingStep.SignIn,
        appleEnabled = appleEnabled,
        guest = guest,
        signInOnly = guest,
        accountInFlight = inFlight,
        accountError = if (conflict != null) OnboardingAccountError.AccountInUse else error,
        conflict = conflict,
    ).withRecommendation()
}
