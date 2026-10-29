package com.opson.features.home.impl.feedback

import androidx.lifecycle.viewModelScope
import com.opson.features.home.impl.report.AttachmentAction
import com.opson.features.home.impl.report.ReportAttachments
import com.opson.features.home.impl.report.ReportAttachmentsSource
import com.opson.libraries.flowroutines.SEAViewModel
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.common_send
import opson.libraries.resources.generated.resources.common_sending
import opson.libraries.resources.generated.resources.common_sent
import opson.libraries.resources.generated.resources.feedback_empty
import opson.libraries.resources.generated.resources.feedback_failed
import org.jetbrains.compose.resources.StringResource

@Inject
class FeedbackViewModel(
    private val repository: FeedbackRepository,
    attachmentsSource: ReportAttachmentsSource,
) : SEAViewModel<FeedbackState, FeedbackEvent, FeedbackAction>(
    initialStateArg = FeedbackState()
) {

    init {
        viewModelScope.launch { takeAction(FeedbackAction.AttachmentsArrived(attachmentsSource.initial())) }
    }

    override suspend fun handleAction(action: FeedbackAction) {
        when (action) {
            FeedbackAction.Back -> sendEvent(FeedbackEvent.Close)
            is FeedbackAction.MessageChanged -> action.updateState { it.copy(message = action.value, errorMessage = null) }
            FeedbackAction.Submit -> action.submitFeedback()
            is FeedbackAction.AttachmentsArrived -> action.updateState {
                it.copy(attachments = action.attachments.mergedWith(it.attachments))
            }
            is FeedbackAction.Attachment -> {
                if (action.action == AttachmentAction.AddScreenshotTapped && state.attachments.canAddScreenshot) {
                    sendEvent(FeedbackEvent.PickScreenshot)
                }
                action.updateState { it.copy(attachments = it.attachments.reduce(action.action)) }
            }
        }
    }

    /**
     * Only a sent message closes the page; a failed one stays, with a line saying so.
     * Actions run one at a time, so a second tap that landed before the button redrew arrives
     * after the first send has finished: [FeedbackState.isSent] is what stops it sending again.
     */
    private suspend fun FeedbackAction.submitFeedback() {
        val current = state
        if (current.isSubmitting || current.isSent) return
        if (current.message.isBlank()) {
            updateState { it.copy(errorMessage = Res.string.feedback_empty) }
            return
        }
        updateState { it.copy(isSubmitting = true, errorMessage = null) }
        val delivery = repository.submitFeedback(
            message = current.message.trim(),
            isBugReport = false,
            screenshots = current.attachments.screenshots.map { it.jpeg },
            sessionLog = current.attachments.sentLog,
        ).getOrNull()
        if (delivery == null) {
            updateState { it.copy(isSubmitting = false, errorMessage = Res.string.feedback_failed) }
            return
        }
        updateState { it.copy(isSubmitting = false, isSent = true) }
        sendEvent(FeedbackEvent.Sent(queued = delivery == FeedbackDelivery.Queued))
    }
}

data class FeedbackState(
    val message: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: StringResource? = null,
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
}

sealed interface FeedbackEvent {
    /** Thank them and close. [queued]: offline, so the thanks says it goes out later. */
    data class Sent(val queued: Boolean = false) : FeedbackEvent
    data object Close : FeedbackEvent

    /** Open the system photo picker; the answer comes back as an [AttachmentAction]. */
    data object PickScreenshot : FeedbackEvent
}

sealed interface FeedbackAction {
    data object Back : FeedbackAction
    data class MessageChanged(val value: String) : FeedbackAction
    data object Submit : FeedbackAction
    data class AttachmentsArrived(val attachments: ReportAttachments) : FeedbackAction
    data class Attachment(val action: AttachmentAction) : FeedbackAction
}
