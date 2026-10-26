package com.opson.features.onboarding.impl

import com.opson.libraries.nutrition.PresetId

/**
 * Onboarding states for previews and `BlindModeLeakTest`. Each one runs
 * through [OnboardingState.withRecommendation], the code the ViewModel runs,
 * so the recommendation and alternatives are the real ones.
 */
object OnboardingFixtures {

    /**
     * The answers design v3 draws (1.2 to 1.5): eating enough and eating
     * regularly, some numbers are fine, and yes to the statement. Recommends
     * Fuel, with Rhythm, Nutrient focus and Blind beside it.
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
        notice = Notice.Amounts,
        statement = StatementAnswer.Agree,
    )

    fun welcome(): OnboardingState = OnboardingState().withRecommendation()

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

    /** The account step after the design's answers, the guest saved. Apple shows on iOS only. */
    fun account(
        appleEnabled: Boolean = false,
        error: OnboardingAccountError? = null,
    ): OnboardingState = OnboardingState(
        step = OnboardingStep.Account,
        answers = DesignAnswers,
        creationStarted = true,
        appleEnabled = appleEnabled,
        accountError = error,
    ).withRecommendation()
}
