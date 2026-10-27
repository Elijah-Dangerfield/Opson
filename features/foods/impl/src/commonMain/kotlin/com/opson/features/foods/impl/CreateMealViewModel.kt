package com.opson.features.foods.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.foods.CreateMealRoute
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.MealPicks
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.SavedMealRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.foods_meal_saved
import kotlin.random.Random

/**
 * Create a meal: a name, the meal it is usually at, and foods picked through the logger sheet
 * in pick-for-this-meal mode, so there is one search to build. Saved with [SavedMealRepository].
 *
 * From Today's "Save as a meal" the route names a day and a slot, or a day and the selected
 * entries; those items are copied in once, with [prefillName] ("My breakfast", resolved by the
 * entry point from the slot) as the name.
 */
@Inject
class CreateMealViewModel(
    @Assisted route: CreateMealRoute,
    @Assisted private val prefillName: String?,
    private val savedMeals: SavedMealRepository,
    private val mealPicks: MealPicks,
    diary: DiaryRepository,
    policySource: VisibilityPolicySource,
) : SEAViewModel<CreateMealState, CreateMealEvent, CreateMealAction>(initialStateArg = CreateMealState()) {

    /** This screen's answers from the logger, told apart from any other Create a meal's. */
    private val requestId: String = "meal-${Random.nextLong().toULong().toString(RADIX)}"

    private var draft = MealDraft()

    /**
     * Set once the meal is written. A second Save tap queued behind the first (the screen is
     * still up until Close lands) would otherwise save the same meal twice.
     */
    private var saved = false
    private var policy: VisibilityPolicy = policySource.policy.value

    init {
        viewModelScope.launch {
            policySource.policy.collect { takeAction(CreateMealAction.PolicyArrived(it)) }
        }
        viewModelScope.launch {
            mealPicks.latest.filterNotNull().filter { it.requestId == requestId }.collect {
                takeAction(CreateMealAction.FoodsPicked(it))
            }
        }
        val day = route.fromDayIso?.let(LocalDate::parse)
        val slot = route.fromSlot
        val entryIds = route.entryIds?.toSet()
        if (day != null && (slot != null || entryIds != null)) {
            viewModelScope.launch {
                val loaded = diary.observeDay(day).first()
                val items = when {
                    entryIds != null -> loaded.allItems.filter { it.id in entryIds }
                    slot != null -> loaded.items(slot)
                    else -> emptyList()
                }
                takeAction(CreateMealAction.PrefillArrived(slot, items.map { it.toDraft() }))
            }
        }
    }

    override suspend fun handleAction(action: CreateMealAction) {
        when (action) {
            is CreateMealAction.PolicyArrived -> {
                policy = action.policy
                action.rebuild()
            }
            is CreateMealAction.PrefillArrived -> action.change {
                copy(
                    name = prefillName ?: name,
                    // "Usually at" has no Uncategorized; foods from several meals keep the default.
                    usualSlot = when {
                        action.slot == null -> usualSlot
                        action.slot.isMeal -> action.slot
                        else -> MealSlot.Lunch
                    },
                    items = action.items,
                )
            }
            is CreateMealAction.NameTyped -> action.change { copy(name = action.text) }
            is CreateMealAction.UsualSlotPicked -> action.change { copy(usualSlot = action.slot) }
            is CreateMealAction.ItemRemoveTapped -> action.change {
                copy(items = items.filterIndexed { index, _ -> index != action.index })
            }
            CreateMealAction.AddFoodTapped -> sendEvent(CreateMealEvent.OpenPicker(requestId))
            is CreateMealAction.FoodsPicked -> {
                mealPicks.consumed(action.picks)
                action.change { copy(items = items + action.picks.items) }
            }
            CreateMealAction.SaveTapped -> save()
            CreateMealAction.CloseTapped -> sendEvent(CreateMealEvent.Close)
        }
    }

    private suspend fun save() {
        val current = draft
        // The button is disabled until then; this keeps a stray tap from saving a nameless meal.
        if (saved || current.name.isBlank() || current.items.isEmpty()) return
        savedMeals.create(current.name.trim(), current.usualSlot, current.items)
        saved = true
        sendEvent(CreateMealEvent.ShowToast(Toast(Res.string.foods_meal_saved)))
        sendEvent(CreateMealEvent.Close)
    }

    private suspend fun CreateMealAction.change(transform: MealDraft.() -> MealDraft) {
        draft = draft.transform()
        rebuild()
    }

    private suspend fun CreateMealAction.rebuild() {
        val current = draft
        val currentPolicy = policy
        updateState { CreateMealStateBuilder.build(current, currentPolicy) }
    }

    private companion object {
        const val RADIX = 36
    }
}
