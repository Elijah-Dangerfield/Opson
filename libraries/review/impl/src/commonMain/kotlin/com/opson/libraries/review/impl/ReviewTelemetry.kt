package com.opson.libraries.review.impl

import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import com.opson.libraries.review.ReviewLaunchOutcome
import com.opson.libraries.review.ReviewVariant
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/**
 * The rating ask's app events (`docs/practices/app-events.md`, Asking for a rating): moments and
 * experiment names only, never the account or anything about what was logged.
 */
interface ReviewTelemetry {
    fun assigned(variant: ReviewVariant)

    /** This install's moment came and an ask was held back, for [reason] ([ReviewHold.wire]). */
    fun skipped(variant: ReviewVariant, reason: ReviewHold)

    fun asked(variant: ReviewVariant, outcome: ReviewLaunchOutcome)
}

@ContributesBinding(AppScope::class)
@Inject
class AppEventsReviewTelemetry : ReviewTelemetry {
    private val logger = KLog.withTag("Review")

    override fun assigned(variant: ReviewVariant) {
        logger.logEvent("review.assigned", "variant" to variant.moment.wire, "experiment" to variant.experiment)
    }

    override fun skipped(variant: ReviewVariant, reason: ReviewHold) {
        logger.logEvent(
            "review.skipped",
            "variant" to variant.moment.wire,
            "experiment" to variant.experiment,
            "reason" to reason.wire,
        )
    }

    override fun asked(variant: ReviewVariant, outcome: ReviewLaunchOutcome) {
        logger.logEvent(
            "review.asked",
            "variant" to variant.moment.wire,
            "experiment" to variant.experiment,
            "outcome" to outcome.wire,
        )
    }
}

/** Why an ask was held back at its moment. [wire] is the `reason` of `review.skipped`. */
enum class ReviewHold(val wire: String) {
    /** The install is under [RealReviewPromptCoordinator.MIN_INSTALL_AGE] old. */
    InstallTooNew("install_new"),

    /** Asked within [RealReviewPromptCoordinator.MIN_GAP]. */
    AskedRecently("asked_recently"),

    /** No connection: the store's sheet needs one, and something may have just failed. */
    Offline("offline"),

    /** Something went wrong in the last [RecentTrouble.WINDOW]. */
    Trouble("trouble"),
}
