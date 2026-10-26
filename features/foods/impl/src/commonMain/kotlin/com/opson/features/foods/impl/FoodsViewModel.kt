package com.opson.features.foods.impl

import com.opson.libraries.flowroutines.SEAViewModel
import me.tatarka.inject.annotations.Inject

@Inject
class FoodsViewModel : SEAViewModel<FoodsState, FoodsEvent, FoodsAction>(
    initialStateArg = FoodsState,
) {
    override suspend fun handleAction(action: FoodsAction) = Unit
}

/** Empty until the Foods screen is built; it becomes a data class with the screen's fields. */
data object FoodsState

sealed interface FoodsEvent

sealed interface FoodsAction
