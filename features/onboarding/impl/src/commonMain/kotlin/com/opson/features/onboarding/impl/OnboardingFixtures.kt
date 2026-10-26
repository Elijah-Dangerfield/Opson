package com.opson.features.onboarding.impl

import com.opson.libraries.nutrition.PresetId

/**
 * Onboarding states for previews and `BlindModeLeakTest`. Each one runs
 * through [OnboardingState.withRecommendation], the code the ViewModel runs,
 * so the recommendation and alternatives are the real ones.
 */
object OnboardingFixtures {

    /** Someone who would see Full numbers if they had not said numbers were hard. */
    val HardAnswers = SetupAnswers(
        why = setOf(Why.Performance),
        feel = NumbersFeel.LoveThem,
        notice = setOf(Notice.Amounts),
        hard = true,
    )

    fun welcome(): OnboardingState = OnboardingState(appleEnabled = true).withRecommendation()

    fun question(question: SetupQuestion, answers: SetupAnswers = SetupAnswers()): OnboardingState =
        OnboardingState(step = OnboardingStep.Question(question), answers = answers).withRecommendation()

    /** No answers by default, which recommends Delayed reveal with four alternatives. */
    fun recommendation(answers: SetupAnswers = SetupAnswers(), picked: PresetId? = null): OnboardingState {
        val recommended = OnboardingState(step = OnboardingStep.Recommendation, answers = answers)
            .withRecommendation()
        return if (picked == null) recommended else recommended.copy(picked = picked).withRecommendation()
    }
}
