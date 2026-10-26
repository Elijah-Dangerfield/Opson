package com.opson.features.onboarding.impl

import com.opson.libraries.nutrition.ui.leak.LeakSurface

/**
 * Onboarding's entries in `BlindModeLeakTest.SURFACES`, ids as design v3 names
 * the screens. Onboarding shows no nutrition number in any mode, so none of
 * them shows calories in Full and the policy goes unused: the test holds each
 * to showing none.
 */
object OnboardingLeakSurfaces {
    val all: List<LeakSurface> = listOf(
        LeakSurface(
            id = "onboarding-welcome",
            label = "Onboarding · welcome and plan",
            showsCaloriesInFull = false,
        ) { OnboardingScreen(state = OnboardingFixtures.welcome(), onAction = {}) },
        LeakSurface(
            id = "onboarding-statement",
            label = "Onboarding · optional statement",
            showsCaloriesInFull = false,
        ) {
            OnboardingScreen(
                state = OnboardingFixtures.question(SetupQuestion.Statement, OnboardingFixtures.DesignAnswers),
                onAction = {},
            )
        },
        LeakSurface(
            id = "onboarding-recommendation",
            label = "Onboarding · mode preview",
            showsCaloriesInFull = false,
        ) {
            OnboardingScreen(state = OnboardingFixtures.recommendation(OnboardingFixtures.DesignAnswers), onAction = {})
        },
        LeakSurface(
            id = "onboarding-account",
            label = "Onboarding · account",
            showsCaloriesInFull = false,
        ) { OnboardingScreen(state = OnboardingFixtures.account(), onAction = {}) },
    )
}
