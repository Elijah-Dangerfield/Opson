package com.opson.features.onboarding

import com.opson.libraries.navigation.AnimationType
import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * First-launch welcome, setup questions and starting mode, plus the identity
 * bootstrap. Only the start destination on first install: once the user
 * finishes the flow we set `hasUserOnboarded`, go to Today, and never return
 * here unless onboarding is reset.
 *
 * Fade transitions because the onboarding → Today jump is conceptually a
 * mode switch, not a "drill into" navigation.
 */
@Serializable
class OnboardingRoute : Route(
    enter = AnimationType.FadeIn,
    exit = AnimationType.FadeOut,
    popExit = AnimationType.FadeOut,
)
