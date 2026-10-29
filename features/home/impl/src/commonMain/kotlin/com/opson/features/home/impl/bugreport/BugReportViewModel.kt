package com.opson.features.home.impl.bugreport

import androidx.lifecycle.viewModelScope
import com.opson.features.home.impl.feedback.FeedbackDelivery
import com.opson.features.home.impl.feedback.FeedbackRepository
import com.opson.features.home.impl.report.AttachmentAction
import com.opson.features.home.impl.report.ReportAttachments
import com.opson.features.home.impl.report.ReportAttachmentsSource
import com.opson.libraries.flowroutines.SEAViewModel
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.common_send
import opson.libraries.resources.generated.resources.common_sending
import opson.libraries.resources.generated.resources.common_sent
import opson.libraries.resources.generated.resources.feedback_empty
import opson.libraries.resources.generated.resources.feedback_failed
import org.jetbrains.compose.resources.StringResource

@Inject
class BugReportViewModel(
    private val repository: FeedbackRepository,
    attachmentsSource: ReportAttachmentsSource,
    @Assisted logId: String? = null,
    @Assisted errorCode: Int? = null,
    @Assisted contextMessage: String? = null,
    @Assisted shakeScreenshot: Boolean = false,
) : SEAViewModel<BugReportState, BugReportEvent, BugReportAction>(
    initialStateArg = BugReportState(
        logId = logId,
        errorCode = errorCode,
        contextMessage = contextMessage
    )
) {

    init {
        viewModelScope.launch {
            takeAction(BugReportAction.AttachmentsArrived(attachmentsSource.initial(shakeScreenshot)))
        }
    }

    override suspend fun handleAction(action: BugReportAction) {
        when (action) {
            BugReportAction.Back -> sendEvent(BugReportEvent.Close)
            is BugReportAction.MessageChanged -> action.updateState { it.copy(message = action.value, errorMessage = null) }
            BugReportAction.Submit -> action.submitBugReport()
            // Keeps anything picked before the log arrived.
            is BugReportAction.AttachmentsArrived -> action.updateState {
                it.copy(attachments = action.attachments.mergedWith(it.attachments))
            }
            is BugReportAction.Attachment -> {
                if (action.action == AttachmentAction.AddScreenshotTapped && state.attachments.canAddScreenshot) {
                    sendEvent(BugReportEvent.PickScreenshot)
                }
                action.updateState { it.copy(attachments = it.attachments.reduce(action.action)) }
            }
        }
    }

    /**
     * Only a sent report closes the page; a failed one stays, with a line saying so.
     * A second tap queued behind the first send is dropped by [BugReportState.isSent].
     */
    private suspend fun BugReportAction.submitBugReport() {
        val current = state
        if (current.isSubmitting || current.isSent) return
        if (current.message.isBlank()) {
            updateState { it.copy(errorMessage = Res.string.feedback_empty) }
            return
        }
        updateState { it.copy(isSubmitting = true, errorMessage = null) }
        val delivery = repository.submitFeedback(
            message = current.message.trim(),
            isBugReport = true,
            logId = current.logId,
            errorCode = current.errorCode,
            contextMessage = current.contextMessage,
            screenshots = current.attachments.screenshots.map { it.jpeg },
            sessionLog = current.attachments.sentLog,
        ).getOrNull()
        if (delivery == null) {
            updateState { it.copy(isSubmitting = false, errorMessage = Res.string.feedback_failed) }
            return
        }
        updateState { it.copy(isSubmitting = false, isSent = true) }
        sendEvent(BugReportEvent.Sent(queued = delivery == FeedbackDelivery.Queued))
    }
}

data class BugReportState(
    val message: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: StringResource? = null,
    val logId: String? = null,
    val errorCode: Int? = null,
    val contextMessage: String? = null,
    val isSent: Boolean = false,
    val attachments: ReportAttachments = ReportAttachments(),
) {
    val canSubmit: Boolean get() = message.isNotBlank() && !isSubmitting && !isSent

    /**
     * Sent stays on the button: if the page outlives its close (its Sent event expired while the
     * app was in the background), Send is greyed out for good, and this says why.
     */
    val sendLabel: StringResource
        get() = when {
            isSent -> Res.string.common_sent
            isSubmitting -> Res.string.common_sending
            else -> Res.string.common_send
        }

    val hasContext: Boolean
        get() = !contextMessage.isNullOrBlank() || !logId.isNullOrBlank() || errorCode != null
}

sealed interface BugReportEvent {
    /** Thank them and close. [queued]: offline, so the thanks says it goes out later. */
    data class Sent(val queued: Boolean = false) : BugReportEvent
    data object Close : BugReportEvent

    /** Open the system photo picker; the answer comes back as an [AttachmentAction]. */
    data object PickScreenshot : BugReportEvent
}

sealed interface BugReportAction {
    data object Back : BugReportAction
    data class MessageChanged(val value: String) : BugReportAction
    data object Submit : BugReportAction
    data class AttachmentsArrived(val attachments: ReportAttachments) : BugReportAction
    data class Attachment(val action: AttachmentAction) : BugReportAction
}
