package com.opson.features.today.impl

import com.opson.libraries.flowroutines.SEAViewModel
import me.tatarka.inject.annotations.Inject

@Inject
class TodayViewModel : SEAViewModel<TodayState, TodayEvent, TodayAction>(
    initialStateArg = TodayState,
) {
    override suspend fun handleAction(action: TodayAction) = Unit
}

/** Empty until the Today screen is built; it becomes a data class with the screen's fields. */
data object TodayState

sealed interface TodayEvent

sealed interface TodayAction
