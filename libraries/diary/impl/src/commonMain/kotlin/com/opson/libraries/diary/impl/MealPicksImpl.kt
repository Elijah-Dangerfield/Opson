package com.opson.libraries.diary.impl

import com.opson.libraries.diary.MealPicks
import com.opson.libraries.diary.PickedForMeal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class MealPicksImpl : MealPicks {
    private val state = MutableStateFlow<PickedForMeal?>(null)

    override val latest: StateFlow<PickedForMeal?> = state.asStateFlow()

    override fun picked(picks: PickedForMeal) {
        state.value = picks
    }

    override fun consumed(picks: PickedForMeal) {
        state.compareAndSet(picks, null)
    }
}
