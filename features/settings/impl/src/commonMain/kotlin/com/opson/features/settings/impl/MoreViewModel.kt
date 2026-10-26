package com.opson.features.settings.impl

import com.opson.libraries.flowroutines.SEAViewModel
import me.tatarka.inject.annotations.Inject

@Inject
class MoreViewModel : SEAViewModel<MoreState, MoreEvent, MoreAction>(
    initialStateArg = MoreState,
) {
    override suspend fun handleAction(action: MoreAction) = Unit
}

/** Empty until the More screen is built; it becomes a data class with the screen's fields. */
data object MoreState

sealed interface MoreEvent

sealed interface MoreAction
