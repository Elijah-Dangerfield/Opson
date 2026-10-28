package com.opson.features.logger.impl

import com.opson.features.logger.SubmitEntry
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import com.opson.libraries.foods.SubmissionOrigin
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/** How a read of the label ended. */
enum class ReadOutcome(val wire: String) {
    Read("read"),
    NoLabel("no_label"),
    Unavailable("unavailable"),
    TooMany("too_many"),
    Offline("offline"),
    Error("error"),
}

/**
 * The "Add this food" funnel's app events (`docs/practices/app-events.md`):
 * started, a photo taken, the label read, submitted. Approval is the review
 * ledger's, server side, not an app event. Typed so no call site can pass the
 * barcode, the food's name, a photo or a number: counts, sides and outcomes only.
 */
interface SubmitFoodTelemetry {
    /** [origin] is whether a code was scanned, as the server keeps it; [entry] is the screen it was opened from. */
    fun started(origin: SubmissionOrigin, entry: SubmitEntry)

    fun photoTaken(side: PackSide, fromCamera: Boolean)

    /** [checks] the reading fails; [unsure] fields the model was unsure of. */
    fun read(outcome: ReadOutcome, checks: Int, unsure: Int)

    /**
     * Saved (and logged, where there is a meal) and handed to the server.
     * [edited] when the person changed what the model read; [kept] checks
     * they confirmed; [sent] false when only the review copy failed.
     */
    @Suppress("LongParameterList")
    fun submitted(origin: SubmissionOrigin, entry: SubmitEntry, edited: Boolean, kept: Int, numbersShown: Boolean, sent: Boolean)

    /** Left before submitting, at [stage]. */
    fun abandoned(stage: String)
}

@ContributesBinding(AppScope::class)
@Inject
class AppEventsSubmitFoodTelemetry : SubmitFoodTelemetry {
    private val logger = KLog.withTag("SubmitFood")

    override fun started(origin: SubmissionOrigin, entry: SubmitEntry) {
        logger.logEvent("food_add.started", "origin" to origin.wire, "entry" to entry.wire)
    }

    override fun photoTaken(side: PackSide, fromCamera: Boolean) {
        logger.logEvent(
            "food_add.photo_taken",
            "side" to if (side == PackSide.Front) "front" else "label",
            "source" to if (fromCamera) "camera" else "library",
        )
    }

    override fun read(outcome: ReadOutcome, checks: Int, unsure: Int) {
        logger.logEvent("food_add.read", "outcome" to outcome.wire, "checks" to checks, "unsure" to unsure)
    }

    @Suppress("LongParameterList")
    override fun submitted(origin: SubmissionOrigin, entry: SubmitEntry, edited: Boolean, kept: Int, numbersShown: Boolean, sent: Boolean) {
        logger.logEvent(
            "food_add.submitted",
            "origin" to origin.wire,
            "entry" to entry.wire,
            "edited" to edited,
            "kept" to kept,
            "numbers_shown" to numbersShown,
            "sent" to sent,
        )
    }

    override fun abandoned(stage: String) {
        logger.logEvent("food_add.abandoned", "stage" to stage)
    }
}
