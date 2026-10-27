package com.opson.features.foods.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.logger.FoodSearchRoute
import com.opson.libraries.diary.CustomFoodRepository
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.SavedMealRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject

/**
 * The Foods tab: saved meals, the user's own foods and recent ones, each a way into the logger
 * with that food already picked. Everything it reads is on the device.
 */
@Inject
class FoodsViewModel(
    customFoods: CustomFoodRepository,
    savedMeals: SavedMealRepository,
    diary: DiaryRepository,
    policySource: VisibilityPolicySource,
) : SEAViewModel<FoodsState, FoodsEvent, FoodsAction>(initialStateArg = FoodsState()) {

    private var inputs: FoodsInputs? = null
    private var policy: VisibilityPolicy = policySource.policy.value

    init {
        viewModelScope.launch {
            combine(customFoods.observeAll(), savedMeals.observeAll(), diary.recentFoods(limit = RECENT_LIMIT), ::FoodsInputs)
                .collect { takeAction(FoodsAction.InputsArrived(it)) }
        }
        viewModelScope.launch {
            policySource.policy.collect { takeAction(FoodsAction.PolicyArrived(it)) }
        }
    }

    override suspend fun handleAction(action: FoodsAction) {
        when (action) {
            is FoodsAction.InputsArrived -> {
                inputs = action.inputs
                action.rebuild()
            }
            is FoodsAction.PolicyArrived -> {
                policy = action.policy
                action.rebuild()
            }
            FoodsAction.SearchTapped -> sendEvent(FoodsEvent.OpenLogger(FoodSearchRoute()))
            FoodsAction.CreateMealTapped -> sendEvent(FoodsEvent.OpenCreateMeal)
            FoodsAction.AddFoodTapped -> sendEvent(FoodsEvent.OpenAddFood)
            is FoodsAction.RowTapped -> sendEvent(FoodsEvent.OpenLogger(action.target.toLoggerRoute()))
        }
    }

    private suspend fun FoodsAction.rebuild() {
        val current = inputs ?: return
        val currentPolicy = policy
        updateState { FoodsStateBuilder.build(current, currentPolicy) }
    }

    companion object {
        const val RECENT_LIMIT = 6
    }
}
