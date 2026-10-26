package com.opson.features.logger.impl

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.CustomFoodRepository
import com.opson.libraries.diary.Fullness
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.foods.AmountUnit
import com.opson.libraries.foods.FoodCatalog
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.PortionAmount
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.logger_toast_stop_asking

sealed interface AmountAction {
    data class FoodArrived(val food: AmountFood) : AmountAction
    data object FoodMissing : AmountAction
    data class InputsArrived(val settings: Settings, val policy: VisibilityPolicy) : AmountAction
    data class PrecisionPicked(val precision: Precision) : AmountAction
    data object LessTapped : AmountAction
    data object MoreTapped : AmountAction
    data class UnitPicked(val index: Int) : AmountAction
    data class HungerTapped(val hunger: Hunger) : AmountAction
    data class FullnessTapped(val fullness: Fullness) : AmountAction
    data object StopAskingTapped : AmountAction
    data object PickTapped : AmountAction
    data object CloseTapped : AmountAction
}

sealed interface AmountEvent {
    data class ShowToast(val toast: Toast) : AmountEvent
    data object Dismiss : AmountEvent
}

/**
 * The amount sheet: how sure, how much, and optionally how hungry. "Pick this
 * amount" replaces the food's basket entry and goes back to the logger; the
 * logger does the logging.
 */
@Inject
class AmountViewModel(
    @Assisted route: AmountSheetRoute,
    private val foodCatalog: FoodCatalog,
    private val customFoods: CustomFoodRepository,
    private val settingsRepository: SettingsRepository,
    policySource: VisibilityPolicySource,
    private val basketStore: BasketStore,
) : SEAViewModel<AmountState, AmountEvent, AmountAction>(initialStateArg = AmountState()) {

    private val ref: FoodRef = route.ref
    private val source: PickSource = route.source

    private var model = Model(settings = settingsRepository.settings.value, policy = policySource.policy.value)

    private data class Model(
        val settings: Settings,
        val policy: VisibilityPolicy,
        val food: AmountFood? = null,
        val choice: AmountChoice = AmountChoice(),
    )

    init {
        viewModelScope.launch {
            val food = when (ref) {
                is FoodRef.Catalog -> foodCatalog.detail(ref.id).getOrNull()?.let(AmountFood::Catalog)
                is FoodRef.Custom -> customFoods.get(ref.id)?.let(AmountFood::Custom)
            }
            takeAction(if (food == null) AmountAction.FoodMissing else AmountAction.FoodArrived(food))
        }
        viewModelScope.launch {
            combine(settingsRepository.settings, policySource.policy, ::Pair).collect { (settings, policy) ->
                takeAction(AmountAction.InputsArrived(settings, policy))
            }
        }
    }

    override suspend fun handleAction(action: AmountAction) {
        when (action) {
            is AmountAction.FoodArrived -> {
                val picked = basketStore.basket.value.firstOrNull { it.key == ref.key }
                val choice = picked?.let { AmountChoice.from(action.food, it) } ?: AmountChoice.initial(action.food)
                action.rebuild { copy(food = action.food, choice = choice) }
            }
            AmountAction.FoodMissing -> action.updateState { it.copy(loading = false, missing = true) }
            is AmountAction.InputsArrived -> action.rebuild { copy(settings = action.settings, policy = action.policy) }
            is AmountAction.PrecisionPicked -> action.choose { copy(precision = action.precision) }
            AmountAction.LessTapped -> action.choose { copy(quantity = PortionAmount.less(unit(), quantity)) }
            AmountAction.MoreTapped -> action.choose { copy(quantity = PortionAmount.more(unit(), quantity)) }
            is AmountAction.UnitPicked -> unitPicked(action)
            is AmountAction.HungerTapped -> action.choose { copy(hunger = action.hunger.takeIf { it != hunger }) }
            is AmountAction.FullnessTapped -> action.choose { copy(fullness = action.fullness.takeIf { it != fullness }) }
            AmountAction.StopAskingTapped -> {
                settingsRepository.update { it.copy(askTags = false) }
                action.choose { copy(hunger = null, fullness = null) }
                sendEvent(AmountEvent.ShowToast(Toast(Res.string.logger_toast_stop_asking)))
            }
            AmountAction.PickTapped -> pick()
            AmountAction.CloseTapped -> sendEvent(AmountEvent.Dismiss)
        }
    }

    private fun unit(): AmountUnit = model.food?.units?.getOrNull(model.choice.unitIndex) ?: AmountUnit.Grams

    private suspend fun unitPicked(action: AmountAction.UnitPicked) {
        val units = model.food?.units ?: return
        val to = units.getOrNull(action.index) ?: return
        action.choose { copy(unitIndex = action.index, quantity = PortionAmount.convert(quantity, unit(), to)) }
    }

    private fun pick() {
        val food = model.food ?: return
        val choice = model.choice
        basketStore.upsert(
            BasketItem(
                key = ref.key,
                ref = ref,
                draft = food.draftFor(choice, source, ref),
                source = source,
                food = (food as? AmountFood.Catalog)?.food,
                hunger = choice.hunger.takeIf { model.settings.askTags },
                fullness = choice.fullness.takeIf { model.settings.askTags },
            ),
        )
        sendEvent(AmountEvent.Dismiss)
    }

    private suspend fun AmountAction.choose(change: AmountChoice.() -> AmountChoice) = rebuild { copy(choice = choice.change()) }

    private suspend fun AmountAction.rebuild(change: Model.() -> Model) {
        model = model.change()
        val current = model
        val food = current.food ?: return
        updateState { AmountStateBuilder.build(food, current.choice, current.settings, current.policy, ref, source) }
    }
}
