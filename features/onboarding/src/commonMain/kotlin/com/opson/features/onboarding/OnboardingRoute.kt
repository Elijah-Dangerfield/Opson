package com.opson.features.onboarding

import com.opson.libraries.navigation.AnimationType
import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * Welcome, sign-in, then the setup questions and starting mode. The start
 * destination on a new install, and where anyone without an account is sent:
 * once they're signed in and set up we set `hasUserOnboarded`, go to Today, and
 * never return here unless they sign out.
 *
 * [signInFirst] opens on the sign-in step instead of Welcome, for someone who
 * has seen Welcome before (a dead session, a sign-out).
 *
 * Fade transitions because the onboarding → Today jump is conceptually a
 * mode switch, not a "drill into" navigation.
 */
@Serializable
data class OnboardingRoute(val signInFirst: Boolean = false) : Route(
    enter = AnimationType.FadeIn,
    exit = AnimationType.FadeOut,
    popExit = AnimationType.FadeOut,
)
