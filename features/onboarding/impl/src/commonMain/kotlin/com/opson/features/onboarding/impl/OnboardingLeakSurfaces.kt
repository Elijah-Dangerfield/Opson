package com.opson.features.onboarding.impl

import com.opson.libraries.nutrition.ui.leak.LeakSurface

/**
 * Onboarding's entries in `BlindModeLeakTest.SURFACES`, ids as design v3 names
 * the screens. The questions, the recommendation and the sign-in step show no
 * nutrition number in any mode, so they ignore the policy and the test holds
 * each to showing none. The setup steps (onboarding v2) do: the goal step's
 * range, the burn it started from and the resting energy step's estimate are
 * built through the policy the test hands them, and show calories in Full.
 */
object OnboardingLeakSurfaces {
    val all: List<LeakSurface> = listOf(
        LeakSurface(
            id = "onboarding-goal",
            label = "Onboarding · goal style and range",
            showsCaloriesInFull = true,
        ) { policy -> OnboardingScreen(state = OnboardingFixtures.goal(policy = policy), onAction = {}) },
        LeakSurface(
            id = "onboarding-goal-from-burn",
            label = "Onboarding · goal started from an ordinary day's burn",
            showsCaloriesInFull = true,
        ) { policy -> OnboardingScreen(state = OnboardingFixtures.goalFromBurn(policy = policy), onAction = {}) },
        LeakSurface(
            id = "onboarding-resting-energy",
            label = "Onboarding · resting energy details",
            showsCaloriesInFull = true,
        ) { policy -> OnboardingScreen(state = OnboardingFixtures.body(OnboardingFixtures.Body, policy = policy), onAction = {}) },
        LeakSurface(
            id = "onboarding-meal-times",
            label = "Onboarding · meal times",
            showsCaloriesInFull = false,
        ) { OnboardingScreen(state = OnboardingFixtures.meals(), onAction = {}) },
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
            label = "Onboarding · sign in",
            showsCaloriesInFull = false,
        ) { OnboardingScreen(state = OnboardingFixtures.signIn(), onAction = {}) },
    )
}
