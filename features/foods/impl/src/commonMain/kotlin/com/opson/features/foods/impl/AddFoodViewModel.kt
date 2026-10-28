package com.opson.features.foods.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.logger.SubmitFoodAvailability
import com.opson.libraries.diary.CustomFoodRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.ui.components.text.NumberInput
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.foods_food_saved

/**
 * Add a food: a name, where it is from, what a serving is, and whatever nutrition the person
 * knows, asked only for the numbers their mode shows. Saved with [CustomFoodRepository]; it
 * then shows on the Foods tab and first in the logger's search. With `release.submitFood` on, the
 * pack's photos come first ([AddFoodAction.PackTapped]), and typing stays for everything else.
 */
@Inject
class AddFoodViewModel(
    private val customFoods: CustomFoodRepository,
    policySource: VisibilityPolicySource,
    submitFood: SubmitFoodAvailability,
) : SEAViewModel<AddFoodState, AddFoodEvent, AddFoodAction>(initialStateArg = AddFoodState(canUsePack = submitFood.available())) {

    /** Read once when the screen opens, like the logger's flags. */
    private val canUsePack: Boolean = submitFood.available()

    private var form = FoodForm()

    /** Set once the food is written, so a second Save tap queued behind the first adds nothing. */
    private var saved = false
    private var policy: VisibilityPolicy = policySource.policy.value

    init {
        viewModelScope.launch {
            policySource.policy.collect { takeAction(AddFoodAction.PolicyArrived(it)) }
        }
    }

    override suspend fun handleAction(action: AddFoodAction) {
        when (action) {
            is AddFoodAction.PolicyArrived -> {
                policy = action.policy
                action.rebuild()
            }
            is AddFoodAction.NameTyped -> action.change { copy(name = action.text) }
            is AddFoodAction.BrandTyped -> action.change { copy(brand = action.text) }
            is AddFoodAction.ServingAmountTyped -> action.change {
                copy(servingAmount = NumberInput.sanitize(action.text, allowDecimal = true))
            }
            is AddFoodAction.ServingUnitPicked -> action.change { copy(servingUnit = action.unit) }
            is AddFoodAction.NutritionTyped -> action.change {
                copy(nutrition = nutrition + (action.metric to NumberInput.sanitize(action.text, allowDecimal = true)))
            }
            AddFoodAction.SaveTapped -> save()
            AddFoodAction.CloseTapped -> sendEvent(AddFoodEvent.Close)
            AddFoodAction.PackTapped -> if (canUsePack && !saved) sendEvent(AddFoodEvent.OpenPack)
        }
    }

    private suspend fun save() {
        // The button is disabled without a name; this keeps a stray tap from saving a nameless food.
        if (saved || form.name.isBlank()) return
        val food = customFoods.create(AddFoodStateBuilder.draft(form, policy))
        saved = true
        sendEvent(AddFoodEvent.ShowToast(Toast(Res.string.foods_food_saved, listOf(food.name))))
        sendEvent(AddFoodEvent.Close)
    }

    private suspend fun AddFoodAction.change(transform: FoodForm.() -> FoodForm) {
        form = form.transform()
        rebuild()
    }

    private suspend fun AddFoodAction.rebuild() {
        val current = form
        val currentPolicy = policy
        updateState { AddFoodStateBuilder.build(current, currentPolicy, canUsePack) }
    }
}
