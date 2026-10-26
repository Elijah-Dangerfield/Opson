package com.opson.features.onboarding.impl

import com.opson.libraries.nutrition.ui.leak.LeakSurface

/**
 * Onboarding's entries in `BlindModeLeakTest.SURFACES`. Onboarding shows no
 * nutrition number in any mode, so neither surface shows calories in Full and
 * the policy goes unused: the test holds it to showing none.
 */
object OnboardingLeakSurfaces {
    val all: List<LeakSurface> = listOf(
        LeakSurface(
            id = "onboarding-welcome",
            label = "Onboarding · pricing",
            showsCaloriesInFull = false,
        ) { OnboardingScreen(state = OnboardingFixtures.welcome(), onAction = {}) },
        LeakSurface(
            id = "onboarding-recommendation",
            label = "Onboarding · mode preview",
            showsCaloriesInFull = false,
        ) { OnboardingScreen(state = OnboardingFixtures.recommendation(), onAction = {}) },
    )
}
