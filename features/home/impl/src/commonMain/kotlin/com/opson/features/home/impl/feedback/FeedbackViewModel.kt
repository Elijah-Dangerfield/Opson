package com.opson.features.home.impl.feedback

import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.opson.AppCache
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.feedback_empty
import opson.libraries.resources.generated.resources.feedback_failed
import org.jetbrains.compose.resources.StringResource

@Inject
class FeedbackViewModel(
    private val repository: FeedbackRepository,
    private val appCache: AppCache,
) : SEAViewModel<FeedbackState, FeedbackEvent, FeedbackAction>(
    initialStateArg = FeedbackState()
) {

    override suspend fun handleAction(action: FeedbackAction) {
        when (action) {
            FeedbackAction.Back -> sendEvent(FeedbackEvent.Close)
            is FeedbackAction.MessageChanged -> action.updateState { it.copy(message = action.value, errorMessage = null) }
            FeedbackAction.Submit -> action.submitFeedback()
        }
    }

    /** Only a sent message counts and closes the page; a failed one stays, with a line saying so. */
    private suspend fun FeedbackAction.submitFeedback() {
        val current = state
        if (current.isSubmitting) return
        if (current.message.isBlank()) {
            updateState { it.copy(errorMessage = Res.string.feedback_empty) }
            return
        }
        updateState { it.copy(isSubmitting = true, errorMessage = null) }
        val sent = repository.submitFeedback(message = current.message.trim(), isBugReport = false).isSuccess
        if (!sent) {
            updateState { it.copy(isSubmitting = false, errorMessage = Res.string.feedback_failed) }
            return
        }
        appCache.update { it.copy(feedbacksGiven = it.feedbacksGiven + 1) }
        updateState { it.copy(isSubmitting = false) }
        sendEvent(FeedbackEvent.Sent)
    }
}

data class FeedbackState(
    val message: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: StringResource? = null,
)

sealed interface FeedbackEvent {
    /** Thank them and close. */
    data object Sent : FeedbackEvent
    data object Close : FeedbackEvent
}

sealed interface FeedbackAction {
    data object Back : FeedbackAction
    data class MessageChanged(val value: String) : FeedbackAction
    data object Submit : FeedbackAction
}
