package com.opson.features.logger.impl

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.CustomFoodRepository
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.FoodUnit
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.MealSlotPicker
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.SelectedDay
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.foods.AmountUnit
import com.opson.libraries.foods.FoodCatalog
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.PortionAmount
import com.opson.libraries.foods.foodRef
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.ui.components.text.NumberInput
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import kotlin.time.Clock

sealed interface FoodDetailAction {
    data class FoodArrived(val food: AmountFood, val last: LogItem?) : FoodDetailAction
    data object FoodMissing : FoodDetailAction
    data class InputsArrived(val settings: Settings, val policy: VisibilityPolicy, val day: Day) : FoodDetailAction
    data class HistoryArrived(val days: List<Day>) : FoodDetailAction

    /** The meal this person logs around now, for a detail opened with no meal. */
    data class SlotSuggested(val slot: MealSlot) : FoodDetailAction
    data class AmountTyped(val text: String) : FoodDetailAction
    data class ServingPicked(val index: Int) : FoodDetailAction
    data class UnitPicked(val index: Int) : FoodDetailAction
    data class PrecisionPicked(val precision: Precision) : FoodDetailAction
    data object CustomSizeTapped : FoodDetailAction
    data class CustomSizeTyped(val name: String, val grams: String) : FoodDetailAction
    data object CustomSizeSaved : FoodDetailAction
    data object CustomSizeDismissed : FoodDetailAction
    data object FavoriteTapped : FoodDetailAction
    data class SlotPicked(val slot: MealSlot) : FoodDetailAction
    data class TimePicked(val time: LocalTime) : FoodDetailAction
    data object AllNutrientsTapped : FoodDetailAction
    data object AddToMealTapped : FoodDetailAction
    data object BackTapped : FoodDetailAction
}

sealed interface FoodDetailEvent {
    /** Back to where the detail was opened. */
    data object Dismiss : FoodDetailEvent

    /** Logged: the detail closes. Search draws ✓, Today takes it from `RecentLogEvents`. */
    data class Logged(val committed: Committed) : FoodDetailEvent
}

/**
 * The food detail (design sync 4, 13.4d): a typed amount and its unit, how sure, what it holds
 * and what the day comes to with it. Typing an amount turns Estimate into Weighed; the serving
 * chips and the unit menu don't, and a how-sure the person picked stays. "Add to {meal}" logs
 * this one food. The favorite and a custom size are the person's own settings, so they save as
 * they are tapped.
 */
@Inject
@Suppress("TooManyFunctions", "LongParameterList")
class FoodDetailViewModel(
    @Assisted route: FoodDetailRoute,
    private val foodCatalog: FoodCatalog,
    private val customFoods: CustomFoodRepository,
    private val settingsRepository: SettingsRepository,
    policySource: VisibilityPolicySource,
    private val diary: DiaryRepository,
    private val calendar: LocalCalendar,
    selectedDay: SelectedDay,
    private val clock: Clock,
    private val logger: FoodLogger,
) : SEAViewModel<FoodDetailState, FoodDetailEvent, FoodDetailAction>(
    initialStateArg = FoodDetailState(slot = route.slot ?: MealSlot.Dinner),
) {
    private val ref: FoodRef = route.ref
    private val source: PickSource = route.source
    private val day: LocalDate = route.dayIso?.let(LocalDate::parse) ?: selectedDay.date.value

    /** Logged and closing: a second tap queued behind the first would log it twice. */
    private var done = false

    private var model = Model(
        settings = settingsRepository.settings.value,
        policy = policySource.policy.value,
        choice = DetailChoice(
            slot = route.slot ?: MealSlot.defaultFor(
                time = clock.now().toLocalDateTime(calendar.timeZone()).time,
                mealGroups = settingsRepository.settings.value.mealGroups,
            ),
        ),
    )

    private data class Model(
        val settings: Settings,
        val policy: VisibilityPolicy,
        val choice: DetailChoice,
        val food: AmountFood? = null,
        val day: Day? = null,
        val history: List<Day> = emptyList(),
    )

    init {
        viewModelScope.launch {
            val food = when (ref) {
                is FoodRef.Catalog -> foodCatalog.detail(ref.id).getOrNull()?.let(AmountFood::Catalog)
                is FoodRef.Custom -> customFoods.get(ref.id)?.let(AmountFood::Custom)
            }
            // The person's last amount of it, as search's + would log it.
            val last = diary.recentFoods(limit = LAST_USED_LIMIT).first().firstOrNull { it.foodRef() == ref }
            takeAction(if (food == null) FoodDetailAction.FoodMissing else FoodDetailAction.FoodArrived(food, last))
        }
        viewModelScope.launch {
            combine(settingsRepository.settings, policySource.policy, diary.observeDay(day), ::Triple).collect { (settings, policy, today) ->
                takeAction(FoodDetailAction.InputsArrived(settings, policy, today))
            }
        }
        // The last two weeks, read once: the ± sizes a meal eaten with no details from them, and with no
        // meal given the picker chooses from them.
        viewModelScope.launch {
            val today = calendar.today()
            val days = diary.observeDays(today.minus(MealSlotPicker.HISTORY_DAYS, DateTimeUnit.DAY), today).first()
            takeAction(FoodDetailAction.HistoryArrived(days))
            if (route.slot == null) {
                val now = clock.now().toLocalDateTime(calendar.timeZone())
                takeAction(FoodDetailAction.SlotSuggested(MealSlotPicker.pick(days, now, calendar.timeZone(), settingsRepository.settings.value.mealGroups)))
            }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    override suspend fun handleAction(action: FoodDetailAction) {
        when (action) {
            is FoodDetailAction.FoodArrived -> {
                val saved = saved()
                val slot = model.choice.slot
                val choice = action.last?.let { DetailChoice.lastUsed(action.food, it, slot, saved) } ?: DetailChoice.initial(action.food, slot, saved)
                action.rebuild { copy(food = action.food, choice = choice.copy(slot = model.choice.slot)) }
            }
            FoodDetailAction.FoodMissing -> action.updateState { it.copy(loading = false, missing = true) }
            is FoodDetailAction.InputsArrived -> action.rebuild { copy(settings = action.settings, policy = action.policy, day = action.day) }
            is FoodDetailAction.HistoryArrived -> action.rebuild { copy(history = action.days) }
            is FoodDetailAction.SlotSuggested -> action.choose { copy(slot = action.slot) }
            is FoodDetailAction.AmountTyped -> action.choose { typed(NumberInput.sanitize(action.text, allowDecimal = true)) }
            is FoodDetailAction.ServingPicked -> {
                val (unit, count) = FoodDetailStateBuilder.servingAmounts(units()).getOrNull(action.index) ?: return
                action.choose { copy(unit = unit, quantity = count, typed = null) }
            }
            is FoodDetailAction.UnitPicked -> unitPicked(action)
            is FoodDetailAction.PrecisionPicked -> action.choose { copy(precision = action.precision, precisionPicked = true) }
            FoodDetailAction.CustomSizeTapped -> if (model.food?.takesSizes == true) action.choose { copy(customSize = CustomSizeUi()) }
            is FoodDetailAction.CustomSizeTyped -> if (model.choice.customSize != null) {
                action.choose { copy(customSize = CustomSizeUi(action.name, action.grams.filter { it.isDigit() || it == '.' })) }
            }
            FoodDetailAction.CustomSizeSaved -> customSizeSaved(action)
            FoodDetailAction.CustomSizeDismissed -> action.choose { copy(customSize = null) }
            FoodDetailAction.FavoriteTapped -> settingsRepository.update { it.toggleFavorite(ref.key) }
            is FoodDetailAction.SlotPicked -> action.choose { copy(slot = action.slot) }
            is FoodDetailAction.TimePicked -> action.choose { copy(time = action.time) }
            FoodDetailAction.AllNutrientsTapped -> action.choose { copy(nutrientsOpen = !nutrientsOpen) }
            FoodDetailAction.AddToMealTapped -> addToMeal()
            FoodDetailAction.BackTapped -> sendEvent(FoodDetailEvent.Dismiss)
        }
    }

    private fun saved(): List<FoodUnit> = model.settings.unitsFor(ref.key)

    private fun units(): List<AmountUnit> = model.food?.units(saved()).orEmpty()

    private fun unit(): AmountUnit = units().takeIf { it.isNotEmpty() }?.let(model.choice::unitIn) ?: AmountUnit.Grams

    /** The amount converts to the new unit when both have a weight; a menu pick is not typing, so how sure stays. */
    private suspend fun unitPicked(action: FoodDetailAction.UnitPicked) {
        val to = units().getOrNull(action.index) ?: return
        val from = unit()
        action.choose { copy(unit = to, quantity = PortionAmount.switch(quantity, from, to), typed = null) }
    }

    /** Saves the size to this food in settings, then counts in it at the same count. */
    private suspend fun customSizeSaved(action: FoodDetailAction) {
        val typed = model.choice.customSize?.takeIf { it.canSave } ?: return
        val size = FoodUnit(typed.name.trim(), typed.grams.toDouble())
        val settings = settingsRepository.update { it.withUnit(ref.key, size) }
        val from = unit()
        val to = AmountUnit.Saved(size)
        model = model.copy(settings = settings)
        action.choose { copy(unit = to, quantity = PortionAmount.switch(quantity, from, to), customSize = null, typed = null) }
    }

    private suspend fun addToMeal() {
        val food = model.food ?: return
        val choice = model.choice
        if (done || choice.quantity <= 0.0) return
        done = true
        // With meal groups off for the day, everything goes to Uncategorized, as the detail shows.
        val groupsOn = model.day?.mealGroupsOn(model.settings) ?: model.settings.mealGroups
        val slot = if (groupsOn) choice.slot else MealSlot.Uncategorized
        val pick = LogPick(
            key = ref.key,
            ref = ref,
            draft = food.draftFor(choice, source, ref, saved()),
            source = source,
            food = (food as? AmountFood.Catalog)?.food,
            loggedAt = choice.time?.let { LocalDateTime(day, it).toInstant(calendar.timeZone()) },
        )
        val committed = logger.log(day, slot, listOf(pick)) ?: return
        sendEvent(FoodDetailEvent.Logged(committed))
    }

    private suspend fun FoodDetailAction.choose(change: DetailChoice.() -> DetailChoice) = rebuild { copy(choice = choice.change()) }

    private suspend fun FoodDetailAction.rebuild(change: Model.() -> Model) {
        model = model.change()
        val current = model
        val food = current.food ?: return
        val inputs = DetailInputs(
            settings = current.settings,
            policy = current.policy,
            day = current.day,
            history = current.history,
            today = calendar.today(),
            now = clock.now().toLocalDateTime(calendar.timeZone()).time,
        )
        updateState { FoodDetailStateBuilder.build(food, current.choice, inputs, ref, source) }
    }

    private companion object {
        /** How far back the person's last amount of a food is looked for, as search's + does. */
        const val LAST_USED_LIMIT = 100
    }
}
