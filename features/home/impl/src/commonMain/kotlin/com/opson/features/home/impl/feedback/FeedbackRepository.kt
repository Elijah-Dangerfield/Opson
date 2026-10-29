package com.opson.features.home.impl.feedback

import com.opson.libraries.core.AppState
import com.opson.libraries.core.Catching
import com.opson.libraries.core.flatMap
import com.opson.libraries.flowroutines.DispatcherProvider
import com.opson.libraries.opson.Telemetry
import com.opson.libraries.prompts.PromptEvent
import com.opson.libraries.prompts.PromptEvents
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
     *
     * [screenshots] and [sessionLog] are what the page showed under "Sent with it", minus whatever
     * the person took off; a null log sends none.
     */
    suspend fun submitFeedback(
        message: String,
        isBugReport: Boolean,
        logId: String? = null,
        errorCode: Int? = null,
        contextMessage: String? = null,
        screenshots: List<ByteArray> = emptyList(),
        sessionLog: String? = null,
    ): Result<FeedbackDelivery>
}

/**
 * Where a handed-over report is. Crash reporting writes a report it cannot send to its cache on
 * the phone and sends it on a later launch or reconnect, so offline is not a failure, but the
 * person is told it has not gone yet.
 */
enum class FeedbackDelivery { Sent, Queued }

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class FeedbackRepositoryImpl @Inject constructor(
    private val telemetry: Telemetry,
    private val dispatchers: DispatcherProvider,
    private val appState: AppState,
    private val promptEvents: PromptEvents = PromptEvents.None,
) : FeedbackRepository {
    override suspend fun submitFeedback(
        message: String,
        isBugReport: Boolean,
        logId: String?,
        errorCode: Int?,
        contextMessage: String?,
        screenshots: List<ByteArray>,
        sessionLog: String?,
    ): Result<FeedbackDelivery> {
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
                    errorCode = errorCode,
                    screenshots = screenshots,
                    sessionLog = sessionLog,
                )
            }
        }
        return sent.flatMap { delivered ->
            when {
                !delivered -> Catching.failure(FeedbackNotSent())
                appState.isOffline.value -> Catching.success(FeedbackDelivery.Queued)
                else -> Catching.success(FeedbackDelivery.Sent)
            }
        }.onSuccess { promptEvents.emit(PromptEvent.ReportSent) }
    }
}

/** Crash reporting dropped the report, so the person must not be thanked for it. */
class FeedbackNotSent : Exception("feedback was dropped, not sent")
