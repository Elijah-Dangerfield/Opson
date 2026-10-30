package com.opson.features.foods.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.foods.LogRecipeRoute
import com.opson.libraries.diary.DayWrite
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.RecipeRepository
import com.opson.libraries.diary.ui.label
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.foods.PublicRecipeLookup
import com.opson.libraries.foods.PublicRecipes
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.adding_added
import opson.libraries.resources.generated.resources.recipes_log_day_closed
import opson.libraries.resources.generated.resources.recipes_public_failed_body
import kotlin.time.Clock

/** Log a serving: how many, which meal, today. [amount] reads "1 serving", "1½ servings". */
data class LogRecipeState(
    val loading: Boolean = true,
    val name: String = "",
    val amount: String = "",
    val canFewer: Boolean = false,
    val numbers: AmountNumbersUi = AmountNumbersUi(),
    val slot: MealSlot = MealSlot.Lunch,
    val slots: List<MealSlot> = MealSlot.meals,
    val logging: Boolean = false,
)

sealed interface LogRecipeAction {
    data class Loaded(val serving: ((Double) -> LogDraft)?) : LogRecipeAction
    data class PolicyArrived(val policy: VisibilityPolicy) : LogRecipeAction
    data object FewerTapped : LogRecipeAction
    data object MoreTapped : LogRecipeAction
    data class SlotPicked(val slot: MealSlot) : LogRecipeAction
    data object LogTapped : LogRecipeAction
}

sealed interface LogRecipeEvent {
    data class ShowToast(val toast: Toast) : LogRecipeEvent
    data object Close : LogRecipeEvent
}

/**
 * Logs [count] servings of a recipe as one entry today, to the meal picked (the clock's meal to
 * begin with). The person's own recipe is read from the device; a shared one from the server.
 */
@Inject
class LogRecipeViewModel(
    @Assisted private val route: LogRecipeRoute,
    private val recipes: RecipeRepository,
    private val publicRecipes: PublicRecipes,
    private val diary: DiaryRepository,
    private val calendar: LocalCalendar,
    private val clock: Clock,
    policySource: VisibilityPolicySource,
) : SEAViewModel<LogRecipeState, LogRecipeEvent, LogRecipeAction>(initialStateArg = LogRecipeState()) {

    private var serving: ((Double) -> LogDraft)? = null
    private var count = 1.0
    private var slot = MealSlot.defaultFor(clock.now().toLocalDateTime(calendar.timeZone()).time)
    private var policy: VisibilityPolicy = policySource.policy.value
    private var logged = false

    init {
        viewModelScope.launch {
            val id = route.recipeId
            val code = route.code
            val draft: ((Double) -> LogDraft)? = when {
                id != null -> recipes.get(id)?.let { recipe -> { n: Double -> recipe.servingDraft(n) } }
                code != null -> (publicRecipes.get(code) as? PublicRecipeLookup.Found)?.recipe?.let { recipe -> { n: Double -> recipe.servingDraft(n) } }
                else -> null
            }
            takeAction(LogRecipeAction.Loaded(draft))
        }
        viewModelScope.launch { policySource.policy.collect { takeAction(LogRecipeAction.PolicyArrived(it)) } }
    }

    override suspend fun handleAction(action: LogRecipeAction) {
        when (action) {
            is LogRecipeAction.Loaded -> {
                serving = action.serving ?: run {
                    sendEvent(LogRecipeEvent.ShowToast(Toast(Res.string.recipes_public_failed_body)))
                    return sendEvent(LogRecipeEvent.Close)
                }
                action.rebuild()
            }
            is LogRecipeAction.PolicyArrived -> {
                policy = action.policy
                action.rebuild()
            }
            LogRecipeAction.FewerTapped -> {
                count = (count - STEP).coerceAtLeast(STEP)
                action.rebuild()
            }
            LogRecipeAction.MoreTapped -> {
                count = (count + STEP).coerceAtMost(MAX_COUNT)
                action.rebuild()
            }
            is LogRecipeAction.SlotPicked -> {
                slot = action.slot
                action.rebuild()
            }
            LogRecipeAction.LogTapped -> log(action)
        }
    }

    private suspend fun log(action: LogRecipeAction) {
        val make = serving ?: return
        if (logged) return
        logged = true
        action.updateState { it.copy(logging = true) }
        val draft = make(count)
        when (diary.addItems(calendar.today(), slot, listOf(draft), LogMethod.Saved)) {
            is DayWrite.Written -> sendEvent(LogRecipeEvent.ShowToast(Toast(Res.string.adding_added, listOf(draft.name, slot.label))))
            is DayWrite.DayClosed -> sendEvent(LogRecipeEvent.ShowToast(Toast(Res.string.recipes_log_day_closed)))
        }
        sendEvent(LogRecipeEvent.Close)
    }

    private suspend fun LogRecipeAction.rebuild() {
        val make = serving ?: return
        val draft = make(count)
        val numbers = RecipeNumbers.amounts(draft.kcal, draft.protein, draft.carbs, draft.fat, draft.fiber, policy)
        val current = slot
        val amount = draft.amountLabel
        updateState {
            it.copy(
                loading = false,
                name = draft.name,
                amount = amount,
                canFewer = count > STEP,
                numbers = numbers,
                slot = current,
            )
        }
    }

    private companion object {
        const val STEP = 0.5
        const val MAX_COUNT = 20.0
    }
}
