package com.opson.features.foods.impl

import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.PickedForMeal
import com.opson.libraries.diary.RepeatDays
import com.opson.libraries.diary.RepeatPlan
import com.opson.libraries.diary.RepeatRule
import com.opson.libraries.diary.SavedMeal
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** The repeat editor. Built by [RepeatStateBuilder]; every number in it is policy-resolved. */
data class RepeatState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val items: List<MealItemUi> = emptyList(),
    /** The saved meal it logs, or null for foods picked one by one. */
    val mealName: String? = null,
    val savedMeals: List<SavedMealChipUi> = emptyList(),
    val slot: MealSlot = MealSlot.Breakfast,
    val slots: List<MealSlot> = MealSlot.meals,
    val days: Set<DayOfWeek> = RepeatDays.EveryDay,
    /** Today and Tomorrow, while the start is still to come; empty once it has started. */
    val starts: List<RepeatStart> = emptyList(),
    val start: RepeatStart? = null,
    val ends: List<RepeatEnd> = emptyList(),
    val end: RepeatEnd = RepeatEnd.Never,
    /** Only a saved rule can be paused or deleted. */
    val paused: Boolean = false,
    val note: RepeatNote = RepeatNote.LogsToday,
    /** Something to log on at least one day. */
    val canSave: Boolean = false,
)

data class SavedMealChipUi(val id: String, val name: String, val selected: Boolean)

enum class RepeatStart { Today, Tomorrow }

/** How long it runs, counted from the start. [On] is an end set some other way, kept as it was. */
sealed interface RepeatEnd {
    data object Never : RepeatEnd
    data object Week : RepeatEnd
    data object TwoWeeks : RepeatEnd
    data object Month : RepeatEnd
    data class On(val day: LocalDate) : RepeatEnd

    /** The last day, inclusive, of a run that starts on [start]. */
    fun lastDay(start: LocalDate): LocalDate? = when (this) {
        Never -> null
        Week -> start.plus(DatePeriod(days = 6))
        TwoWeeks -> start.plus(DatePeriod(days = 13))
        Month -> start.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
        is On -> day
    }

    companion object {
        // A getter, not a stored list: stored, it is built while the interface initialises, before
        // the objects it names exist, and holds nulls on the JVM.
        val offered: List<RepeatEnd> get() = listOf(Never, Week, TwoWeeks, Month)

        /** Which choice [end] is, for a run from [start]. */
        fun of(start: LocalDate, end: LocalDate?): RepeatEnd =
            if (end == null) Never else offered.firstOrNull { it.lastDay(start) == end } ?: On(end)
    }
}

/** What saving will do, said under the form. */
sealed interface RepeatNote {
    data object LogsToday : RepeatNote

    /** "Repeat this" from an entry logged today: that entry is today's. */
    data object CountsToday : RepeatNote

    /** An edit to a rule that logged today already. */
    data object LoggedToday : RepeatNote

    /** An edit to a rule that has had today, and its foods were removed since: it won't log them again. */
    data object DoneToday : RepeatNote
    data object Tomorrow : RepeatNote
    data class NextOn(val day: LocalDate) : RepeatNote
    data object Never : RepeatNote
    data object Paused : RepeatNote
}

/** What the person has set so far. The view model holds it; [RepeatStateBuilder] draws it. */
data class RepeatDraft(
    val items: List<LogDraft> = emptyList(),
    val savedMealId: String? = null,
    val slot: MealSlot = MealSlot.Breakfast,
    val days: Set<DayOfWeek> = RepeatDays.EveryDay,
    val start: LocalDate,
    val end: RepeatEnd = RepeatEnd.Never,
    val paused: Boolean = false,
    /** The rule being edited; null for a new one. */
    val ruleId: String? = null,
    /** Entries already logged today that count as today's ("Repeat this" on Today). */
    val countsToday: List<String> = emptyList(),
) {
    fun toPlan(meals: List<SavedMeal>): RepeatPlan {
        val meal = meals.firstOrNull { it.id == savedMealId }
        return RepeatPlan(
            name = meal?.name ?: items.joinToString(", ") { shortName(it.name) },
            items = items,
            slot = slot,
            days = days,
            startDay = start,
            endDay = end.lastDay(start),
            savedMealId = meal?.id,
        )
    }

    companion object {
        fun of(rule: RepeatRule): RepeatDraft = RepeatDraft(
            items = rule.plan.items,
            savedMealId = rule.plan.savedMealId,
            slot = rule.plan.slot,
            days = rule.plan.days,
            start = rule.plan.startDay,
            end = RepeatEnd.of(rule.plan.startDay, rule.plan.endDay),
            paused = rule.paused,
            ruleId = rule.id,
        )
    }
}

sealed interface RepeatAction {
    /**
     * Everything the form reads besides the draft. [ranToday] is whether this rule has had today
     * (`RepeatRepository.observeHasRun`), [loggedToday] whether its foods are still on it.
     */
    data class InputsArrived(
        val meals: List<SavedMeal>,
        val today: LocalDate,
        val todayOpen: Boolean,
        val ranToday: Boolean,
        val loggedToday: Boolean,
    ) : RepeatAction

    data class PolicyArrived(val policy: VisibilityPolicy) : RepeatAction
    data class DraftLoaded(val draft: RepeatDraft) : RepeatAction
    data object AddFoodTapped : RepeatAction
    data class FoodsPicked(val picks: PickedForMeal) : RepeatAction
    data class ItemRemoveTapped(val index: Int) : RepeatAction
    data class SavedMealTapped(val id: String) : RepeatAction
    data class SlotPicked(val slot: MealSlot) : RepeatAction
    data class DayToggled(val day: DayOfWeek) : RepeatAction
    data class DaysPicked(val days: Set<DayOfWeek>) : RepeatAction
    data class StartPicked(val start: RepeatStart) : RepeatAction
    data class EndPicked(val end: RepeatEnd) : RepeatAction
    data object PauseToggled : RepeatAction
    data object SaveTapped : RepeatAction
    data object DeleteTapped : RepeatAction
    data object CloseTapped : RepeatAction
}

sealed interface RepeatEvent {
    data class OpenPicker(val requestId: String) : RepeatEvent
    data class ShowToast(val toast: Toast) : RepeatEvent
    data object Close : RepeatEvent
}

/** The editor's state from the draft and what it reads, so previews and the leak test run the app's mapping. */
object RepeatStateBuilder {

    @Suppress("LongParameterList")
    fun build(
        draft: RepeatDraft,
        meals: List<SavedMeal>,
        today: LocalDate,
        policy: VisibilityPolicy,
        todayOpen: Boolean = true,
        ranToday: Boolean = false,
        loggedToday: Boolean = ranToday,
    ): RepeatState {
        val bare = FormatContext(bare = true)
        val meal = meals.firstOrNull { it.id == draft.savedMealId }
        val tomorrow = today.plus(DatePeriod(days = 1))
        val upcoming = draft.ruleId == null || draft.start > today
        return RepeatState(
            loading = false,
            isNew = draft.ruleId == null,
            items = draft.items.map { item ->
                MealItemUi(
                    name = item.name,
                    amount = item.amountLabel,
                    calories = policy.resolve(Metric.Calories, item.kcal, Surface.Foods, bare),
                )
            },
            mealName = meal?.name,
            savedMeals = meals.map { SavedMealChipUi(id = it.id, name = it.name, selected = it.id == meal?.id) },
            slot = draft.slot,
            days = draft.days,
            starts = if (upcoming) RepeatStart.entries else emptyList(),
            start = when (draft.start) {
                today -> RepeatStart.Today
                tomorrow -> RepeatStart.Tomorrow
                else -> null
            },
            ends = RepeatEnd.offered + listOfNotNull(draft.end as? RepeatEnd.On),
            end = draft.end,
            paused = draft.paused,
            note = note(draft, today, todayOpen, ranToday, loggedToday),
            canSave = draft.items.isNotEmpty() && draft.days.isNotEmpty(),
        )
    }

    /** A rule that has had today logs nothing more on it, whatever is in the log now; the ledger says so, not the entries. */
    private fun note(draft: RepeatDraft, today: LocalDate, todayOpen: Boolean, ranToday: Boolean, loggedToday: Boolean): RepeatNote {
        if (draft.paused) return RepeatNote.Paused
        val plan = RepeatPlan(name = "", items = draft.items, slot = draft.slot, days = draft.days, startDay = draft.start, endDay = draft.end.lastDay(draft.start))
        if (plan.fallsOn(today)) {
            when {
                draft.countsToday.isNotEmpty() -> return RepeatNote.CountsToday
                ranToday && loggedToday -> return RepeatNote.LoggedToday
                ranToday -> return RepeatNote.DoneToday
                todayOpen -> return RepeatNote.LogsToday
            }
        }
        val next = plan.nextFrom(today.plus(DatePeriod(days = 1))) ?: return RepeatNote.Never
        return if (next == today.plus(DatePeriod(days = 1))) RepeatNote.Tomorrow else RepeatNote.NextOn(next)
    }

    /** The Foods tab's row for [rule]. */
    fun row(rule: RepeatRule, meals: List<SavedMeal>, policy: VisibilityPolicy): RepeatRowUi {
        val items = rule.plan.savedMealId?.let { id -> meals.firstOrNull { it.id == id }?.items } ?: rule.plan.items
        val kcal = items.mapNotNull { it.kcal }.takeIf { it.isNotEmpty() }?.sum()
        return RepeatRowUi(
            id = rule.id,
            name = rule.plan.name,
            slot = rule.plan.slot,
            days = rule.plan.days,
            endDay = rule.plan.endDay,
            paused = rule.paused,
            calories = policy.resolve(Metric.Calories, kcal, Surface.Foods, FormatContext(bare = true)),
        )
    }
}

/** A repeat on the Foods tab: "Oat porridge" over "Breakfast · Weekdays", or "Paused". */
data class RepeatRowUi(
    val id: String,
    val name: String,
    val slot: MealSlot,
    val days: Set<DayOfWeek>,
    val endDay: LocalDate?,
    val paused: Boolean,
    val calories: Display,
)
