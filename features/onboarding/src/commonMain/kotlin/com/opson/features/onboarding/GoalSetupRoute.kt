package com.opson.features.onboarding

import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * The goal flow on its own, for an account already set up: the profile, a usual day, the weight
 * goal and pace, the plan and the commitment, as onboarding walks them. Today's "Set up your goal
 * in a minute" card opens it, and so do Targets' goal rows. Back on the first step, or finishing,
 * returns to where it was opened.
 *
 * An arg-less route is still a class (AGENTS.md: a `data object` route crashes iOS).
 */
@Serializable
data class GoalSetupRoute(val from: String = FROM_TODAY) : Route() {
    companion object {
        const val FROM_TODAY = "today"
        const val FROM_TARGETS = "targets"
    }
}
