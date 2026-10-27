package com.opson.features.home.impl.feedback

import com.opson.libraries.core.Catching
import com.opson.libraries.core.flatMap
import com.opson.libraries.opson.Telemetry
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

interface FeedbackRepository {
    suspend fun submitFeedback(
        message: String,
        isBugReport: Boolean,
        logId: String? = null,
        errorCode: Int? = null,
    ): Result<Unit>
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class FeedbackRepositoryImpl @Inject constructor(
    private val telemetry: Telemetry
) : FeedbackRepository {
    override suspend fun submitFeedback(
        message: String,
        isBugReport: Boolean,
        logId: String?,
        errorCode: Int?,
    ): Result<Unit> {
        val sent = Catching {
            telemetry.captureUserFeedback(
                message = message,
                isBugReport = isBugReport,
                eventId = logId,
                errorCode = errorCode
            )
        }
        return sent.flatMap { delivered ->
            if (delivered) Catching.success(Unit) else Catching.failure(FeedbackNotSent())
        }
    }
}

/** Crash reporting dropped the report, so the person must not be thanked for it. */
class FeedbackNotSent : Exception("feedback was dropped, not sent")
