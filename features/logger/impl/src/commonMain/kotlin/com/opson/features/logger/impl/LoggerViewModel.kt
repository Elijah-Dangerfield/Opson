package com.opson.features.logger.impl

import com.opson.libraries.flowroutines.SEAViewModel
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject

@Inject
class LoggerViewModel(
    @Assisted slot: String?,
) : SEAViewModel<LoggerState, LoggerEvent, LoggerAction>(
    initialStateArg = LoggerState(slot = slot),
) {
    override suspend fun handleAction(action: LoggerAction) {
        when (action) {
            LoggerAction.CloseTapped -> sendEvent(LoggerEvent.Close)
        }
    }
}

/** [slot] is the meal the sheet was opened for, `null` when the logger picks. */
data class LoggerState(
    val slot: String? = null,
)

sealed interface LoggerEvent {
    data object Close : LoggerEvent
}

sealed interface LoggerAction {
    data object CloseTapped : LoggerAction
}
