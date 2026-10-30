@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.review

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * Decides whether this phone asks for a store rating at a happy moment, and asks. The review
 * prompt on Today (`:libraries:prompts:impl`, `ReviewPrompt`) calls it when a moment arrives; the
 * prompt processor's rules (signed in, onboarded, not after numbers have been hard, one prompt a
 * session) have already passed by then.
 *
 * Ported from Cards' review coordinator: a quiet first week, a long floor between asks, the ask
 * written down before it is made, and an ask with no window to show in given back. On top of it,
 * an experiment ([ReviewExperiment]) picks which one moment each install asks at, so the moments
 * can be compared. The stores never say whether someone rated; see
 * `docs/practices/review-asks.md` for what can and can't be read from the numbers.
 *
 * Never build a rating dialog of our own as a fallback. If the store declines to show its sheet,
 * that is the store's quota working.
 */
@HiddenFromObjC
interface ReviewPromptCoordinator {

    /**
     * True when [moment] is the one this install asks at: the release flag is on and the
     * experiment gave it this moment. Cheap; assigns the install a moment on first use.
     */
    suspend fun asksAt(moment: ReviewMoment): Boolean

    /**
     * Whether asking now is fine: not in the first week, not within the floor since the last ask,
     * online, and nothing gone wrong in the last half hour (an error logged, a call that never reached
     * us, a failed photo estimate, a barcode that found nothing).
     * Call only for a moment [asksAt] said yes to: a no is logged as `review.skipped`.
     */
    suspend fun clearToAsk(moment: ReviewMoment): Boolean

    /** True when nothing on this phone holds an ask back right now, whatever the moment. For the QA menu. */
    suspend fun couldAsk(): Boolean

    /**
     * Ask the store now, at [moment]. Records the ask before it is made and logs `review.asked`.
     * [fromQa] is the QA menu's Show next, logged under the experiment `qa` so a tester's asks
     * never count toward a moment.
     */
    suspend fun ask(moment: ReviewMoment, fromQa: Boolean = false): ReviewLaunchOutcome
}

/**
 * The happy moments an ask can follow. Each install asks at one of them ([ReviewExperiment]).
 * [wire] is the `moment` and `variant` attribute of the `review.*` events and the remote config's
 * name for it; never rename one.
 */
enum class ReviewMoment(val wire: String) {
    /** "I can do this" on a milestone celebration. */
    Celebration("celebration"),

    /** Wrapped up today and picked "Good" for how it felt. */
    DayFeltGood("day_felt_good"),

    /** The first thing of the day (a log, a wrap-up, a check-in) from someone who comes back most days. */
    ShowingUp("showing_up"),

    /** A food they sent from a pack was approved for everyone. */
    FoodApproved("food_approved"),
    ;

    companion object {
        fun fromWire(value: String?): ReviewMoment? = entries.firstOrNull { it.wire == value }
    }
}

/** Which moment this install asks at, under which experiment. */
@HiddenFromObjC
data class ReviewVariant(val moment: ReviewMoment, val experiment: String)

/**
 * Splits installs between the moments by the weights in remote config, so the mix can change
 * without a release. An install keeps its moment until the experiment is renamed or its moment's
 * weight goes to 0.
 */
@HiddenFromObjC
interface ReviewExperiment {
    /** This install's moment, assigned now if it has none. Null while asking is off. */
    suspend fun variant(): ReviewVariant?
}
