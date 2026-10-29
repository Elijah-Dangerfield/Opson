package com.opson.features.foods.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.foods.RepeatRoute
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.MealPicks
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.RepeatRepository
import com.opson.libraries.diary.SavedMeal
import com.opson.libraries.diary.SavedMealRepository
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.repeat_toast_deleted
import opson.libraries.resources.generated.resources.repeat_toast_saved
import opson.libraries.resources.generated.resources.repeat_toast_saved_logged
import kotlin.random.Random

/**
 * A repeat, new or saved (`foods.md`, Repeating): what it logs (foods picked through search in
 * pick-for-this-meal mode, as Create a meal does, or one of the saved meals), the meal, the days,
 * when it starts and ends, and for a saved one, Pause and Delete. Saved with [RepeatRepository],
 * which logs today's foods at once when today is one of its days.
 *
 * From "Repeat this" on Today the route names an entry: that food and amount in its meal, every
 * day from today, and when the entry is on today it counts as today's so it is not logged twice.
 */
@Inject
class RepeatViewModel(
    @Assisted private val route: RepeatRoute,
    private val repeats: RepeatRepository,
    private val mealPicks: MealPicks,
    savedMeals: SavedMealRepository,
    private val diary: DiaryRepository,
    private val calendar: LocalCalendar,
    policySource: VisibilityPolicySource,
) : SEAViewModel<RepeatState, RepeatEvent, RepeatAction>(initialStateArg = RepeatState()) {

    /** This screen's answers from the logger, told apart from Create a meal's and any other repeat's. */
    private val requestId: String = "repeat-${Random.nextLong().toULong().toString(RADIX)}"

    private var draft: RepeatDraft? = null
    private var inputs: RepeatAction.InputsArrived? = null
    private var policy: VisibilityPolicy = policySource.policy.value

    /** Set once a save or delete has run; a second tap queued behind it does nothing. */
    private var done = false

    init {
        viewModelScope.launch {
            policySource.policy.collect { takeAction(RepeatAction.PolicyArrived(it)) }
        }
        viewModelScope.launch {
            val today = calendar.today()
            val ran = route.ruleId?.let { repeats.observeHasRun(it, today) } ?: flowOf(false)
            combine(savedMeals.observeAll(), diary.observeDay(today), ran) { meals, day, ranToday ->
                RepeatAction.InputsArrived(
                    meals = meals,
                    today = today,
                    todayOpen = !day.closedByUser && !day.untracked,
                    ranToday = ranToday,
                    loggedToday = route.ruleId != null && day.allItems.any { it.repeatRuleId == route.ruleId },
                )
            }.collect(::takeAction)
        }
        viewModelScope.launch {
            mealPicks.latest.filterNotNull().filter { it.requestId == requestId }.collect {
                takeAction(RepeatAction.FoodsPicked(it))
            }
        }
        viewModelScope.launch { takeAction(RepeatAction.DraftLoaded(load())) }
    }

    private suspend fun load(): RepeatDraft {
        val today = calendar.today()
        route.ruleId?.let { id -> repeats.get(id)?.let { return RepeatDraft.of(it) } }
        val day = route.fromDayIso?.let(LocalDate::parse)
        val entry = day?.let { d -> diary.observeDay(d).first().allItems.firstOrNull { it.id == route.fromEntryId } }
        if (entry == null) return RepeatDraft(start = today)
        // Uncategorized has no time of its own: the meal the entry's time falls in.
        val slot = entry.slot.takeIf { it.isMeal }
            ?: MealSlot.defaultFor(entry.loggedAt.toLocalDateTime(calendar.timeZone()).time)
        return RepeatDraft(
            items = listOf(entry.toDraft()),
            slot = slot,
            start = today,
            countsToday = if (day == today) listOf(entry.id) else emptyList(),
        )
    }

    @Suppress("CyclomaticComplexMethod")
    override suspend fun handleAction(action: RepeatAction) {
        when (action) {
            is RepeatAction.InputsArrived -> {
                inputs = action
                action.rebuild()
            }
            is RepeatAction.PolicyArrived -> {
                policy = action.policy
                action.rebuild()
            }
            is RepeatAction.DraftLoaded -> {
                draft = action.draft
                action.rebuild()
            }
            RepeatAction.AddFoodTapped -> sendEvent(RepeatEvent.OpenPicker(requestId))
            is RepeatAction.FoodsPicked -> {
                mealPicks.consumed(action.picks)
                // Adding to a saved meal's foods makes them this repeat's own list.
                action.change { copy(items = items + action.picks.items, savedMealId = null) }
            }
            is RepeatAction.ItemRemoveTapped -> action.change {
                copy(items = items.filterIndexed { index, _ -> index != action.index }, savedMealId = null)
            }
            is RepeatAction.SavedMealTapped -> {
                val meal = meals().firstOrNull { it.id == action.id } ?: return
                action.change {
                    if (savedMealId == meal.id) {
                        copy(items = emptyList(), savedMealId = null)
                    } else {
                        copy(items = meal.items, savedMealId = meal.id, slot = meal.usualSlot?.takeIf { it.isMeal } ?: slot)
                    }
                }
            }
            is RepeatAction.SlotPicked -> action.change { copy(slot = action.slot) }
            is RepeatAction.DayToggled -> action.change { copy(days = if (action.day in days) days - action.day else days + action.day) }
            is RepeatAction.DaysPicked -> action.change { copy(days = action.days) }
            is RepeatAction.StartPicked -> action.change {
                val today = calendar.today()
                copy(start = if (action.start == RepeatStart.Tomorrow) today.plus(DatePeriod(days = 1)) else today)
            }
            is RepeatAction.EndPicked -> action.change { copy(end = action.end) }
            RepeatAction.PauseToggled -> action.change { copy(paused = !paused) }
            RepeatAction.SaveTapped -> save()
            RepeatAction.DeleteTapped -> delete()
            RepeatAction.CloseTapped -> sendEvent(RepeatEvent.Close)
        }
    }

    private suspend fun save() {
        val current = draft ?: return
        if (done || current.items.isEmpty() || current.days.isEmpty()) return
        done = true
        val plan = current.toPlan(meals())
        val saved = when (val id = current.ruleId) {
            null -> repeats.create(plan, loggedToday = current.countsToday)
            else -> repeats.update(id, plan, paused = current.paused)
        }
        val toast = if (saved?.loggedToday == true) {
            Toast(Res.string.repeat_toast_saved_logged, listOf(current.slot.sentenceName))
        } else {
            Toast(Res.string.repeat_toast_saved)
        }
        sendEvent(RepeatEvent.ShowToast(toast))
        sendEvent(RepeatEvent.Close)
    }

    private suspend fun delete() {
        val id = draft?.ruleId ?: return
        if (done) return
        done = true
        repeats.delete(id)
        sendEvent(RepeatEvent.ShowToast(Toast(Res.string.repeat_toast_deleted)))
        sendEvent(RepeatEvent.Close)
    }

    private fun meals(): List<SavedMeal> = inputs?.meals.orEmpty()

    private suspend fun RepeatAction.change(transform: RepeatDraft.() -> RepeatDraft) {
        draft = draft?.transform() ?: return
        rebuild()
    }

    private suspend fun RepeatAction.rebuild() {
        val current = draft ?: return
        val read = inputs ?: return
        val currentPolicy = policy
        updateState { RepeatStateBuilder.build(current, read.meals, read.today, currentPolicy, read.todayOpen, read.ranToday, read.loggedToday) }
    }

    private companion object {
        const val RADIX = 36
    }
}
