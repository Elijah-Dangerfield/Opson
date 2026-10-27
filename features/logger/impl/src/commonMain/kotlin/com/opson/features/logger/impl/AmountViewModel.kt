package com.opson.features.logger.impl

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.FoodUnit
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.CustomFoodRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.foods.AmountUnit
import com.opson.libraries.foods.FoodCatalog
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.PortionAmount
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.diary.Day
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import kotlin.time.Clock
import kotlin.time.Instant

sealed interface AmountAction {
    data class FoodArrived(val food: AmountFood) : AmountAction
    data object FoodMissing : AmountAction
    data class InputsArrived(val settings: Settings, val policy: VisibilityPolicy, val day: Day) : AmountAction
    data class PrecisionPicked(val precision: Precision) : AmountAction
    data object LessTapped : AmountAction
    data object MoreTapped : AmountAction
    data class QuickAmountPicked(val quantity: Double) : AmountAction
    data class UnitPicked(val index: Int) : AmountAction
    data object CustomSizeTapped : AmountAction
    data class CustomSizeTyped(val name: String, val grams: String) : AmountAction
    data object CustomSizeSaved : AmountAction
    data object CustomSizeDismissed : AmountAction
    data object FavoriteTapped : AmountAction
    data class SlotPicked(val slot: MealSlot) : AmountAction
    data class TimePicked(val time: LocalTime) : AmountAction
    data object AllNutrientsTapped : AmountAction
    data object PickAndKeepGoingTapped : AmountAction
    data object AddToMealTapped : AmountAction
    data object BackTapped : AmountAction
}

sealed interface AmountEvent {
    /** Back to the logger, which stays open. */
    data object Dismiss : AmountEvent

    /** Logged: close this sheet and the logger under it. Today takes it from `RecentLogEvents`. */
    data class Logged(val day: LocalDate, val committed: Committed) : AmountEvent
}

/**
 * The amount sheet (design sync 2, 3.3): how much, in which unit, for which meal and when, how
 * sure, and what it does to the day. "Pick and keep going" replaces the food's basket entry and
 * goes back to the logger; "Add to {meal}" does the same and then logs the whole basket. The star
 * and a custom size are the person's own settings, so they save as they are tapped.
 */
@Inject
class AmountViewModel(
    @Assisted route: AmountSheetRoute,
    private val foodCatalog: FoodCatalog,
    private val customFoods: CustomFoodRepository,
    private val settingsRepository: SettingsRepository,
    policySource: VisibilityPolicySource,
    diary: DiaryRepository,
    private val calendar: LocalCalendar,
    private val clock: Clock,
    private val basketStore: BasketStore,
    private val committer: BasketCommitter,
) : SEAViewModel<AmountState, AmountEvent, AmountAction>(initialStateArg = AmountState(slot = route.slot)) {

    private val ref: FoodRef = route.ref
    private val source: PickSource = route.source
    private val day: LocalDate = LocalDate.parse(route.dayIso)

    /**
     * The food is in the basket (or logged) and the sheet is closing. A second tap queued behind
     * the first would put it back into a basket the first just logged and cleared, and log it twice.
     */
    private var done = false

    private var model = Model(
        settings = settingsRepository.settings.value,
        policy = policySource.policy.value,
        choice = AmountChoice(slot = route.slot),
    )

    private data class Model(
        val settings: Settings,
        val policy: VisibilityPolicy,
        val choice: AmountChoice,
        val food: AmountFood? = null,
        val day: Day? = null,
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
            combine(settingsRepository.settings, policySource.policy, diary.observeDay(day), ::Triple).collect { (settings, policy, today) ->
                takeAction(AmountAction.InputsArrived(settings, policy, today))
            }
        }
    }

    override suspend fun handleAction(action: AmountAction) {
        when (action) {
            is AmountAction.FoodArrived -> {
                val saved = model.settings.unitsFor(ref.key)
                val slot = model.choice.slot
                val picked = basketStore.basket.value.firstOrNull { it.key == ref.key }
                val choice = picked?.let { AmountChoice.from(action.food, it, slot, saved, it.loggedAt?.let(::timeOf)) }
                    ?: AmountChoice.initial(action.food, slot, saved)
                action.rebuild { copy(food = action.food, choice = choice) }
            }
            AmountAction.FoodMissing -> action.updateState { it.copy(loading = false, missing = true) }
            is AmountAction.InputsArrived -> action.rebuild { copy(settings = action.settings, policy = action.policy, day = action.day) }
            is AmountAction.PrecisionPicked -> action.choose { copy(precision = action.precision) }
            AmountAction.LessTapped -> action.choose { copy(quantity = PortionAmount.less(unit(), quantity)) }
            AmountAction.MoreTapped -> action.choose { copy(quantity = PortionAmount.more(unit(), quantity)) }
            is AmountAction.QuickAmountPicked -> if (unit() !is AmountUnit.Grams && action.quantity > 0.0) {
                action.choose { copy(quantity = action.quantity) }
            }
            is AmountAction.UnitPicked -> unitPicked(action)
            AmountAction.CustomSizeTapped -> if (model.food?.takesSizes == true) action.choose { copy(customSize = CustomSizeUi()) }
            is AmountAction.CustomSizeTyped -> if (model.choice.customSize != null) {
                action.choose { copy(customSize = CustomSizeUi(action.name, action.grams.filter { it.isDigit() || it == '.' })) }
            }
            AmountAction.CustomSizeSaved -> customSizeSaved(action)
            AmountAction.CustomSizeDismissed -> action.choose { copy(customSize = null) }
            AmountAction.FavoriteTapped -> settingsRepository.update { it.toggleFavorite(ref.key) }
            is AmountAction.SlotPicked -> action.choose { copy(slot = action.slot) }
            is AmountAction.TimePicked -> action.choose { copy(time = action.time) }
            AmountAction.AllNutrientsTapped -> action.choose { copy(nutrientsOpen = !nutrientsOpen) }
            AmountAction.PickAndKeepGoingTapped -> if (!done && pick()) {
                done = true
                sendEvent(AmountEvent.Dismiss)
            }
            AmountAction.AddToMealTapped -> addToMeal()
            AmountAction.BackTapped -> sendEvent(AmountEvent.Dismiss)
        }
    }

    private fun saved(): List<FoodUnit> = model.settings.unitsFor(ref.key)

    private fun units(): List<AmountUnit> = model.food?.units(saved()).orEmpty()

    private fun unit(): AmountUnit = units().takeIf { it.isNotEmpty() }?.let(model.choice::unitIn) ?: AmountUnit.Grams

    /** The count stays and the gram total follows; only grams converts (3.3b). */
    private suspend fun unitPicked(action: AmountAction.UnitPicked) {
        val to = units().getOrNull(action.index) ?: return
        val from = unit()
        action.choose { copy(unit = to, quantity = PortionAmount.switch(quantity, from, to)) }
    }

    /** Saves the size to this food in settings, then counts in it at the same count. */
    private suspend fun customSizeSaved(action: AmountAction) {
        val typed = model.choice.customSize?.takeIf { it.canSave } ?: return
        val size = FoodUnit(typed.name.trim(), typed.grams.toDouble())
        val settings = settingsRepository.update { it.withUnit(ref.key, size) }
        val from = unit()
        val to = AmountUnit.Saved(size)
        model = model.copy(settings = settings)
        action.choose { copy(unit = to, quantity = PortionAmount.switch(quantity, from, to), customSize = null) }
    }

    /** Puts this food in the basket at the chosen amount and time, replacing an earlier pick of it. */
    private fun pick(): Boolean {
        val food = model.food ?: return false
        val choice = model.choice
        // An item Describe read keeps its own key, so editing it replaces that line rather than adding one.
        val key = basketStore.basket.value.firstOrNull { it.ref == ref && it.source == source }?.key ?: ref.key
        basketStore.upsert(
            BasketItem(
                key = key,
                ref = ref,
                draft = food.draftFor(choice, source, ref, saved()),
                source = source,
                food = (food as? AmountFood.Catalog)?.food,
                loggedAt = choice.time?.let { LocalDateTime(day, it).toInstant(calendar.timeZone()) },
            ),
        )
        return true
    }

    private suspend fun addToMeal() {
        if (done || !pick()) return
        done = true
        // With meal groups off for the day, everything goes to Uncategorized, as the sheet shows.
        val groupsOn = model.day?.mealGroupsOn(model.settings) ?: model.settings.mealGroups
        val slot = if (groupsOn) model.choice.slot else MealSlot.Uncategorized
        val committed = committer.commit(day, slot) ?: return
        sendEvent(AmountEvent.Logged(day, committed))
    }

    private fun timeOf(instant: Instant): LocalTime = instant.toLocalDateTime(calendar.timeZone()).time

    private suspend fun AmountAction.choose(change: AmountChoice.() -> AmountChoice) = rebuild { copy(choice = choice.change()) }

    private suspend fun AmountAction.rebuild(change: Model.() -> Model) {
        model = model.change()
        val current = model
        val food = current.food ?: return
        val inputs = AmountInputs(
            settings = current.settings,
            policy = current.policy,
            day = current.day,
            today = calendar.today(),
            now = clock.now().toLocalDateTime(calendar.timeZone()).time,
        )
        updateState { AmountStateBuilder.build(food, current.choice, inputs, ref, source) }
    }
}
