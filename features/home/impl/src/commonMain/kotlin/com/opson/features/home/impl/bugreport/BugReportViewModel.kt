package com.opson.features.home.impl.bugreport

import com.opson.features.home.impl.feedback.FeedbackRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.opson.AppCache
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.feedback_empty
import opson.libraries.resources.generated.resources.feedback_failed
import org.jetbrains.compose.resources.StringResource

@Inject
class BugReportViewModel(
    private val repository: FeedbackRepository,
    private val appCache: AppCache,
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

    /** Only a sent report counts and closes the page; a failed one stays, with a line saying so. */
    private suspend fun BugReportAction.submitBugReport() {
        val current = state
        if (current.isSubmitting) return
        if (current.message.isBlank()) {
            updateState { it.copy(errorMessage = Res.string.feedback_empty) }
            return
        }
        updateState { it.copy(isSubmitting = true, errorMessage = null) }
        val sent = repository.submitFeedback(
            message = current.message.trim(),
            isBugReport = true,
            logId = current.logId,
            errorCode = current.errorCode
        ).isSuccess
        if (!sent) {
            updateState { it.copy(isSubmitting = false, errorMessage = Res.string.feedback_failed) }
            return
        }
        appCache.update { it.copy(bugsReported = it.bugsReported + 1) }
        updateState { it.copy(isSubmitting = false) }
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
) {
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
