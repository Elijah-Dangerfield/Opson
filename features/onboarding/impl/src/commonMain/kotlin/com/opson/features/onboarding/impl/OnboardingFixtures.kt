package com.opson.features.onboarding.impl

import com.opson.libraries.core.fixed
import com.opson.libraries.diary.MealTimes
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.WeightUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Onboarding states for previews and `BlindModeLeakTest`. Each one runs
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

    /** Someone who would see Full numbers if they had not agreed numbers were hard. */
    val HardAnswers = SetupAnswers(
        why = setOf(Why.Performance),
        feel = NumbersFeel.LoveThem,
        statement = StatementAnswer.Agree,
    )

    /** Someone who loves numbers and is training: Full numbers, so the goal step. */
    val NumbersAnswers = SetupAnswers(why = setOf(Why.Performance), feel = NumbersFeel.LoveThem)

    /** Resting energy's details filled in, in kilograms: about 1,360 kcal a day at rest. */
    val Body = BodyAnswer(birthYear = "1992", height = "168", weight = "64", sex = BodySex.Female, unit = WeightUnit.Kg)

    /** Noon on the fixtures' day, so Later is still closed. */
    private val NOW = Instant.parse("2026-10-25T12:00:00Z")

    fun policy(preset: PresetId): VisibilityPolicy =
        VisibilityPolicy(NumbersSettings(preset = preset), Clock.fixed(NOW), TimeZone.UTC)

    /** On iOS, as the design draws it. Welcome looks the same on Android. */
    fun welcome(): OnboardingState = OnboardingState(appleEnabled = true).withRecommendation()

    fun question(question: SetupQuestion, answers: SetupAnswers = SetupAnswers()): OnboardingState =
        OnboardingState(step = OnboardingStep.Question(question), answers = answers).withRecommendation()

    /** No answers by default, which recommends Full numbers. */
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
     * The goal step after [NumbersAnswers], in Full numbers. [goalsAndEnergy] adds the styles;
     * without it the step is the range alone. [policy] is the leak test's, or Full numbers'.
     */
    fun goal(
        goal: GoalAnswer = GoalAnswer(),
        goalsAndEnergy: Boolean = true,
        policy: VisibilityPolicy = policy(PresetId.Full),
    ): OnboardingState = setup(OnboardingStep.Goal, NumbersAnswers, goalsAndEnergy, policy).copy(goal = goal).withViews(policy)

    /** The resting energy step, after picking a set deficit. Empty fields unless [body] says otherwise. */
    fun body(
        body: BodyAnswer = BodyAnswer(),
        policy: VisibilityPolicy = policy(PresetId.Full),
    ): OnboardingState = setup(OnboardingStep.Body, NumbersAnswers, goalsAndEnergy = true, policy)
        .copy(goal = GoalAnswer(style = GoalChoice.Deficit), body = body)
        .withViews(policy)

    /** The meal times step after the design's answers (eating regularly), with a later breakfast. */
    fun meals(): OnboardingState = setup(OnboardingStep.Meals, DesignAnswers, goalsAndEnergy = false, policy(PresetId.MacrosOnly))
        .copy(mealTimes = MealTimes(breakfast = LocalTime(9, 30)))

    private fun setup(step: OnboardingStep, answers: SetupAnswers, goalsAndEnergy: Boolean, policy: VisibilityPolicy) = OnboardingState(
        step = step,
        answers = answers,
        setupSteps = true,
        goalsAndEnergy = goalsAndEnergy,
    ).withRecommendation().withViews(policy)

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
