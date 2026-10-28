package com.opson.features.home.impl.feedback

import com.opson.libraries.core.Catching
import com.opson.libraries.core.flatMap
import com.opson.libraries.flowroutines.DispatcherProvider
import com.opson.libraries.opson.Telemetry
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

interface FeedbackRepository {
    /**
     * [contextMessage] is what the bug report page shows under "Captured details" (the error the
     * person was looking at, or how they got there). It goes out with the report, so the page
     * doesn't claim to have captured something triage never sees.
     */
    suspend fun submitFeedback(
        message: String,
        isBugReport: Boolean,
        logId: String? = null,
        errorCode: Int? = null,
        contextMessage: String? = null,
    ): Result<Unit>
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class FeedbackRepositoryImpl @Inject constructor(
    private val telemetry: Telemetry,
    private val dispatchers: DispatcherProvider,
) : FeedbackRepository {
    override suspend fun submitFeedback(
        message: String,
        isBugReport: Boolean,
        logId: String?,
        errorCode: Int?,
        contextMessage: String?,
    ): Result<Unit> {
        val report = contextMessage?.takeIf { it.isNotBlank() }?.let { "Context: $it\n\n$message" } ?: message
        // Capturing is synchronous and not small: it copies the in-memory session log (up to 500
        // lines) into an attachment and builds the Sentry event. The view model calls this from
        // the main thread.
        val sent = withContext(dispatchers.io) {
            Catching {
                telemetry.captureUserFeedback(
                    message = report,
                    isBugReport = isBugReport,
                    eventId = logId,
                    errorCode = errorCode
                )
            }
        }
        return sent.flatMap { delivered ->
            if (delivered) Catching.success(Unit) else Catching.failure(FeedbackNotSent())
        }
    }
}

/** Crash reporting dropped the report, so the person must not be thanked for it. */
class FeedbackNotSent : Exception("feedback was dropped, not sent")
