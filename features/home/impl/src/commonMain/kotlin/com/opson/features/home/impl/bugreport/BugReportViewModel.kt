package com.opson.features.home.impl.bugreport

import com.opson.features.home.impl.feedback.FeedbackRepository
import com.opson.libraries.flowroutines.SEAViewModel
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
    @Assisted logId: String? = null,
    @Assisted errorCode: Int? = null,
    @Assisted contextMessage: String? = null,
) : SEAViewModel<BugReportState, BugReportEvent, BugReportAction>(
    initialStateArg = BugReportState(
        logId = logId,
        errorCode = errorCode,
        contextMessage = contextMessage
    )
) {

    override suspend fun handleAction(action: BugReportAction) {
        when (action) {
            BugReportAction.Back -> sendEvent(BugReportEvent.Close)
            is BugReportAction.MessageChanged -> action.updateState { it.copy(message = action.value, errorMessage = null) }
            BugReportAction.Submit -> action.submitBugReport()
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
        val sent = repository.submitFeedback(
            message = current.message.trim(),
            isBugReport = true,
            logId = current.logId,
            errorCode = current.errorCode,
            contextMessage = current.contextMessage,
        ).isSuccess
        if (!sent) {
            updateState { it.copy(isSubmitting = false, errorMessage = Res.string.feedback_failed) }
            return
        }
        updateState { it.copy(isSubmitting = false, isSent = true) }
        sendEvent(BugReportEvent.Sent)
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
    /** Thank them and close. */
    data object Sent : BugReportEvent
    data object Close : BugReportEvent
}

sealed interface BugReportAction {
    data object Back : BugReportAction
    data class MessageChanged(val value: String) : BugReportAction
    data object Submit : BugReportAction
}
