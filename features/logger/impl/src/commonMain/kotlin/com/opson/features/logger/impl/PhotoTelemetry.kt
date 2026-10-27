package com.opson.features.logger.impl

import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/** Why a photo ended somewhere other than the review. */
enum class PhotoFailure(val wire: String) {
    Unavailable("unavailable"),
    TooMany("too_many"),
    Offline("offline"),
    Error("error"),
    NothingFound("nothing_found"),
}

/**
 * Photo estimate's app events (`docs/practices/app-events.md`). Typed so no
 * call site can pass the photo, the note, a food name or an amount: what
 * someone photographs is what they eat. Counts and reasons only.
 */
interface PhotoTelemetry {
    /** A photo came back and the estimate was asked for. */
    fun started(fromCamera: Boolean)

    /** The review shows [items] lines, [matched] of them found in the catalog. */
    fun estimated(items: Int, matched: Int)

    /** Add to {meal} wrote [items] lines. */
    fun added(items: Int)

    fun failed(reason: PhotoFailure)
}

@ContributesBinding(AppScope::class)
@Inject
class AppEventsPhotoTelemetry : PhotoTelemetry {
    private val logger = KLog.withTag("Photo")

    override fun started(fromCamera: Boolean) {
        logger.logEvent("photo.started", "source" to if (fromCamera) "camera" else "library")
    }

    override fun estimated(items: Int, matched: Int) {
        logger.logEvent("photo.estimated", "items" to items, "matched" to matched)
    }

    override fun added(items: Int) {
        logger.logEvent("photo.added", "items" to items)
    }

    override fun failed(reason: PhotoFailure) {
        logger.logEvent("photo.failed", "reason" to reason.wire)
    }
}
