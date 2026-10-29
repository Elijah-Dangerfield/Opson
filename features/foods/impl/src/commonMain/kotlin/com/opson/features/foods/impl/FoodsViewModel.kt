package com.opson.features.foods.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.logger.FoodSearchRoute
import com.opson.libraries.diary.CustomFoodRepository
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.RepeatRepository
import com.opson.libraries.diary.RepeatRule
import com.opson.libraries.diary.RepeatsRelease
import com.opson.libraries.diary.SavedMealRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.foods.SubmittedFoods
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject

/**
 * The Foods tab: saved meals, repeats (`release.repeats`), the user's own foods and recent ones,
 * each a way into the logger with that food already picked or into its editor. Everything it
 * reads is on the device.
 */
@Inject
class FoodsViewModel(
    customFoods: CustomFoodRepository,
    savedMeals: SavedMealRepository,
    diary: DiaryRepository,
    policySource: VisibilityPolicySource,
    submittedFoods: SubmittedFoods,
    repeats: RepeatRepository,
    repeatsRelease: RepeatsRelease,
) : SEAViewModel<FoodsState, FoodsEvent, FoodsAction>(initialStateArg = FoodsState()) {

    private var inputs: FoodsInputs? = null
    private var policy: VisibilityPolicy = policySource.policy.value

    init {
        viewModelScope.launch {
            val rules: Flow<List<RepeatRule>?> = if (repeatsRelease.isOn()) repeats.observeAll() else flowOf(null)
            combine(customFoods.observeAll(), savedMeals.observeAll(), diary.recentFoods(limit = RECENT_LIMIT), submittedFoods.reviews, rules, ::FoodsInputs)
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
            FoodsAction.NewRepeatTapped -> sendEvent(FoodsEvent.OpenRepeat(ruleId = null))
            is FoodsAction.RepeatTapped -> sendEvent(FoodsEvent.OpenRepeat(action.id))
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
