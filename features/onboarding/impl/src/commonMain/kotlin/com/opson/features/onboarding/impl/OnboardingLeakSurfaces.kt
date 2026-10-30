package com.opson.features.onboarding.impl

import com.opson.libraries.nutrition.ui.leak.LeakSurface

/**
 * Onboarding's entries in `CaloriesHiddenLeakTest.SURFACES`, ids as design v3 names
 * the screens. The questions, the recommendation and the sign-in step show no
 * nutrition number in any mode, so they ignore the policy and the test holds
 * each to showing none. The goal flow's steps do: the profile's weight, the
 * target weight, the pace and what it means a day, and the overview's target are
 * built through the policy the test hands them, and the pace and the overview
 * show calories in Full.
 */
object OnboardingLeakSurfaces {
    val all: List<LeakSurface> = listOf(
        LeakSurface(
            id = "onboarding-profile",
            label = "Onboarding · a little about you",
            showsCaloriesInFull = false,
        ) { policy -> OnboardingScreen(state = OnboardingFixtures.plan(PlanStep.Profile, PlanAnswers(OnboardingFixtures.ImperialBody), policy = policy), onAction = {}) },
        LeakSurface(
            id = "onboarding-weight-goal",
            label = "Onboarding · weight goal",
            showsCaloriesInFull = false,
        ) { policy -> OnboardingScreen(state = OnboardingFixtures.plan(PlanStep.Goal, OnboardingFixtures.LoseTen, policy = policy), onAction = {}) },
        LeakSurface(
            id = "onboarding-goal-rate",
            label = "Onboarding · pace",
            showsCaloriesInFull = true,
        ) { policy -> OnboardingScreen(state = OnboardingFixtures.plan(PlanStep.Rate, OnboardingFixtures.LoseTen, policy = policy), onAction = {}) },
        LeakSurface(
            id = "onboarding-goal-overview",
            label = "Onboarding · your plan",
            showsCaloriesInFull = true,
        ) { policy -> OnboardingScreen(state = OnboardingFixtures.plan(PlanStep.Overview, OnboardingFixtures.LoseTen, policy = policy), onAction = {}) },
        LeakSurface(
            id = "onboarding-goal-ask",
            label = "Onboarding · want to set a goal?",
            showsCaloriesInFull = false,
        ) { policy -> OnboardingScreen(state = OnboardingFixtures.plan(PlanStep.Ask, policy = policy), onAction = {}) },
        LeakSurface(
            id = "onboarding-goal-kind",
            label = "Onboarding · what kind of goal",
            showsCaloriesInFull = false,
        ) { policy -> OnboardingScreen(state = OnboardingFixtures.plan(PlanStep.Kind, PlanAnswers(wants = true), policy = policy), onAction = {}) },
        LeakSurface(
            id = "onboarding-energy-balance",
            label = "Onboarding · how much under or over",
            showsCaloriesInFull = true,
        ) { policy -> OnboardingScreen(state = OnboardingFixtures.plan(PlanStep.Balance, OnboardingFixtures.balance(), policy = policy), onAction = {}) },
        LeakSurface(
            id = "onboarding-burn-goal",
            label = "Onboarding · how much activity",
            showsCaloriesInFull = true,
        ) { policy -> OnboardingScreen(state = OnboardingFixtures.plan(PlanStep.Burn, PlanAnswers(pick = GoalPick.Burn), policy = policy), onAction = {}) },
        LeakSurface(
            id = "onboarding-meals-goal",
            label = "Onboarding · how many meals",
            showsCaloriesInFull = false,
        ) { policy -> OnboardingScreen(state = OnboardingFixtures.plan(PlanStep.MealCount, PlanAnswers(pick = GoalPick.Meals), policy = policy), onAction = {}) },
        LeakSurface(
            id = "onboarding-streak",
            label = "Onboarding · keep a streak?",
            showsCaloriesInFull = false,
        ) { policy -> OnboardingScreen(state = OnboardingFixtures.plan(PlanStep.Streak, PlanAnswers(pick = GoalPick.ShowUp), policy = policy), onAction = {}) },
        LeakSurface(
            id = "onboarding-commitment",
            label = "Onboarding · ready to start",
            showsCaloriesInFull = false,
        ) { policy -> OnboardingScreen(state = OnboardingFixtures.plan(PlanStep.Commit, OnboardingFixtures.LoseTen, policy = policy), onAction = {}) },
        LeakSurface(
            id = "onboarding-meal-times",
            label = "Onboarding · meal times",
            showsCaloriesInFull = false,
        ) { OnboardingScreen(state = OnboardingFixtures.meals(), onAction = {}) },
        LeakSurface(
            id = "onboarding-welcome",
            label = "Onboarding · welcome and plan",
            showsCaloriesInFull = false,
        ) { OnboardingScreen(state = OnboardingFixtures.welcome(beta = true), onAction = {}) },
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
