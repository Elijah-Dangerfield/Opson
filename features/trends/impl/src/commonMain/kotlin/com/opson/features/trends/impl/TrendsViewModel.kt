package com.opson.features.trends.impl

import com.opson.libraries.flowroutines.SEAViewModel
import me.tatarka.inject.annotations.Inject

@Inject
class TrendsViewModel : SEAViewModel<TrendsState, TrendsEvent, TrendsAction>(
    initialStateArg = TrendsState,
) {
    override suspend fun handleAction(action: TrendsAction) = Unit
}

/** Empty until the Trends screen is built; it becomes a data class with the screen's fields. */
data object TrendsState

sealed interface TrendsEvent

sealed interface TrendsAction
