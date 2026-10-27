package com.opson.features.today.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.components.SheetHandle
import com.opson.libraries.ui.components.dialog.ModalDialogDefaults
import com.opson.system.background
import com.opson.libraries.ui.system.MinTouchTarget
import com.opson.system.AppTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.opson.features.today.impl.calendar.CalendarDropdownContent
import com.opson.features.today.impl.cards.TodayCardsSheetContent
import com.opson.features.today.impl.dayclose.DayCloseScreen
import com.opson.features.today.impl.daynote.DayNoteSheetContent
import com.opson.features.today.impl.item.ItemSheetContent
import com.opson.features.today.impl.item.ItemSheetStateBuilder
import com.opson.features.today.impl.meal.MealFeelSheetContent
import com.opson.features.today.impl.meal.MealNoteSheetContent
import com.opson.features.today.impl.meal.MealSheetContent
import com.opson.features.today.impl.water.WaterSheetContent
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.ui.leak.LeakSurface

/**
 * Today's surfaces for `BlindModeLeakTest` (`docs/plans/screens/blind-leak-test.md`).
 * Each builds its state through the real builder from [TodayFixtures] with the policy the
 * test hands it. The layout comes from that policy too, so the test's calories-hidden presets
 * cover Nutrients first, Later and No numbers' meal timeline with these same entries. The menu and
 * the sheets render their content in place, with no popup window.
 */
object TodayLeakSurfaces {
    val all: List<LeakSurface> = listOf(
        LeakSurface(id = "today", label = "Today", showsCaloriesInFull = true) { policy ->
            TodayScreen(state = TodayFixtures.todayState(policy), onAction = {})
        },
        // Design sync 4: End after a set time ran out. The banner names modes, never an amount.
        LeakSurface(id = "today-timer-ended", label = "Today · the mode timer ran out", showsCaloriesInFull = true) { policy ->
            TodayScreen(state = TodayFixtures.todayState(policy, settings = TodayFixtures.timerEnded), onAction = {})
        },
        LeakSurface(id = "today-timer-waiting", label = "Today · the mode timer waiting on the pause", showsCaloriesInFull = true) { policy ->
            TodayScreen(state = TodayFixtures.todayState(policy, settings = TodayFixtures.timerWaiting(policy)), onAction = {})
        },
        LeakSurface(id = "today-big-day", label = "Today · big day", showsCaloriesInFull = true) { policy ->
            TodayScreen(state = TodayFixtures.todayState(policy, day = TodayFixtures.day(bigDay = true, loose = true)), onAction = {})
        },
        LeakSurface(id = "today-added", label = "Today · just added", showsCaloriesInFull = true) { policy ->
            TodayScreen(
                state = TodayFixtures.todayState(policy, day = TodayFixtures.day(dinner = true), highlight = MealSlot.Dinner, added = TodayFixtures.added()),
                onAction = {},
            )
        },
        // 0.2: Expended so far from a connected health app, behind release.health.
        LeakSurface(id = "today-expended", label = "Today, with a health app connected", showsCaloriesInFull = true) { policy ->
            TodayScreen(state = TodayFixtures.todayState(policy, expended = TodayFixtures.expended), onAction = {})
        },
        // 0.2: All of the watch's activity in the range. The range moves; nothing says by how much.
        LeakSurface(id = "today-exercise", label = "Today, exercise in the range", showsCaloriesInFull = true) { policy ->
            TodayScreen(state = TodayFixtures.todayState(policy, settings = TodayFixtures.exerciseAll, expended = TodayFixtures.expended), onAction = {})
        },
        LeakSurface(id = "today-untracked", label = "Today, marked untracked", showsCaloriesInFull = true) { policy ->
            TodayScreen(state = TodayFixtures.todayState(policy, day = TodayFixtures.untrackedDay()), onAction = {})
        },
        LeakSurface(id = "today-groups-off", label = "Today, meal groups off", showsCaloriesInFull = true) { policy ->
            TodayScreen(state = TodayFixtures.todayState(policy, day = TodayFixtures.day(loose = true), settings = Settings(mealGroups = false)), onAction = {})
        },
        LeakSurface(id = "today-past", label = "Today, a past day", showsCaloriesInFull = true) { policy ->
            TodayScreen(state = TodayFixtures.todayState(policy, day = TodayFixtures.pastDay()), onAction = {})
        },
        LeakSurface(id = "today-future", label = "Today, planning tomorrow", showsCaloriesInFull = true) { policy ->
            TodayScreen(state = TodayFixtures.todayState(policy, day = TodayFixtures.plannedDay()), onAction = {})
        },
        LeakSurface(id = "today-day-menu", label = "Day menu", showsCaloriesInFull = true) { policy ->
            TodayWithMenu(policy)
        },
        LeakSurface(id = "today-day-menu-armed", label = "Day menu, clear armed", showsCaloriesInFull = true) { policy ->
            TodayWithMenu(policy, clearArmed = true)
        },
        LeakSurface(id = "today-cards", label = "Edit Today's cards", showsCaloriesInFull = false) { policy ->
            TodayCardsSheetContent(state = TodayFixtures.todayCardsState(policy), onAction = {})
        },
        LeakSurface(id = "calendar", label = "Calendar", showsCaloriesInFull = false) {
            CalendarDropdownContent(state = TodayFixtures.calendarState(), onAction = {})
        },
        LeakSurface(id = "meal-sheet", label = "Meal options", showsCaloriesInFull = false) {
            MealSheetContent(state = TodayFixtures.mealSheetState(), onAction = {})
        },
        LeakSurface(id = "meal-note", label = "Meal note", showsCaloriesInFull = false) {
            MealNoteSheetContent(state = TodayFixtures.mealNoteState(), onAction = {})
        },
        LeakSurface(id = "meal-feel", label = "How it felt", showsCaloriesInFull = false) {
            MealFeelSheetContent(state = TodayFixtures.mealFeelState(), onAction = {})
        },
        LeakSurface(id = "water-sheet", label = "Water options", showsCaloriesInFull = false) {
            WaterSheetContent(state = TodayFixtures.waterSheetState(), onAction = {})
        },
        LeakSurface(id = "item-sheet", label = "Item", showsCaloriesInFull = true) { policy ->
            ItemSheetContent(state = TodayFixtures.itemSheetState(policy), onAction = {})
        },
        LeakSurface(id = "day-note", label = "Day note", showsCaloriesInFull = false) {
            DayNoteSheetContent(state = TodayFixtures.dayNoteState(), onAction = {})
        },
        LeakSurface(id = "day-close", label = "Day close", showsCaloriesInFull = true) { policy ->
            DayCloseScreen(state = TodayFixtures.dayCloseState(policy), onAction = {})
        },
        LeakSurface(id = "day-close-big-day", label = "Day close · big day", showsCaloriesInFull = true) { policy ->
            DayCloseScreen(state = TodayFixtures.dayCloseState(policy, bigDay = true), onAction = {})
        },

        // Design sync 2, behind release.todaySummary, release.todaySelect and release.checkIn.
        LeakSurface(id = "today-summary", label = "Today, the summary card's stats", showsCaloriesInFull = true) { policy ->
            TodayScreen(state = designSync(policy), onAction = {})
        },
        LeakSurface(id = "today-macros", label = "Today, the macros page", showsCaloriesInFull = true) { policy ->
            TodayScreen(state = designSync(policy, Settings(todaySummaryPage = 1)), onAction = {})
        },
        // The bar the card shrinks into while scrolled, for every page, as it would pin under the header.
        LeakSurface(id = "today-sticky", label = "Today, the sticky summary bar", showsCaloriesInFull = true) { policy ->
            val compact = designSync(policy).compact
            Column {
                compact?.pages?.indices?.forEach { page -> StickyBar(compact, page, onScrollToTop = {}) }
            }
        },
        LeakSurface(id = "today-collapsed", label = "Today, the summary collapsed", showsCaloriesInFull = true) { policy ->
            TodayScreen(state = designSync(policy, Settings(todaySummaryCollapsed = true)), onAction = {})
        },
        LeakSurface(id = "today-weight", label = "Today, a weigh-in on the day", showsCaloriesInFull = true) { policy ->
            TodayScreen(state = designSync(policy), onAction = {})
        },
        LeakSurface(id = "today-select", label = "Today, foods selected", showsCaloriesInFull = true) { policy ->
            TodayScreen(state = designSync(policy, ui = TodayUi(selected = TodayFixtures.picked)), onAction = {})
        },
        LeakSurface(id = "today-select-remove", label = "Today, removing selected foods", showsCaloriesInFull = true) { policy ->
            TodayScreen(
                state = designSync(policy, ui = TodayUi(selected = TodayFixtures.picked, step = SelectStep.ConfirmRemove)),
                onAction = {},
            )
        },
        LeakSurface(id = "today-swipe-delete", label = "Today, a food swiped to Delete", showsCaloriesInFull = true) { policy ->
            TodayScreen(state = designSync(policy, ui = TodayUi(swipe = SwipeState(open = TodayFixtures.banana))), onAction = {})
        },
        LeakSurface(id = "today-delete-confirm", label = "Today, a food's removal confirmed in place", showsCaloriesInFull = true) { policy ->
            TodayScreen(state = designSync(policy, ui = TodayUi(swipe = SwipeState(confirming = TodayFixtures.banana))), onAction = {})
        },
        LeakSurface(id = "today-cards-summary", label = "Edit Today's cards, the summary card", showsCaloriesInFull = false) { policy ->
            TodayCardsSheetContent(state = TodayFixtures.todayCardsState(policy, TodayFixtures.changingWeight, summaryOn = true), onAction = {})
        },
        // Design sync 4, Today actions: the calendar over Today needs no flag; select mode and the
        // drag sit behind release.todaySelect, the food's ⋮ behind release.foodActions.
        LeakSurface(id = "today-calendar", label = "Today, the calendar dropped from the title", showsCaloriesInFull = true) { policy ->
            TodayWithCalendar(policy)
        },
        LeakSurface(id = "today-select-v4", label = "Today, select mode's bottom bar", showsCaloriesInFull = true) { policy ->
            TodayScreen(state = designSync(policy, ui = TodayUi(selected = TodayFixtures.snackPicked)), onAction = {})
        },
        LeakSurface(id = "today-drag-move", label = "Today, foods dragged to dinner", showsCaloriesInFull = true) { policy ->
            TodayDragging(policy)
        },
        LeakSurface(id = "today-moved", label = "Today, the Undo after a move", showsCaloriesInFull = true) { policy ->
            TodayScreen(state = designSync(policy, ui = TodayUi(moved = MovedNotice(2, null, MealSlot.Dinner))), onAction = {})
        },
        LeakSurface(id = "food-actions", label = "A food's ⋮ over Today", showsCaloriesInFull = true) { policy ->
            TodayWithFoodActions(policy)
        },
        LeakSurface(id = "food-actions-amount", label = "A food's ⋮, Change amount", showsCaloriesInFull = false) { policy ->
            val item = TodayFixtures.day().allItems.first { it.id == TodayFixtures.peanutButter }
            ItemSheetContent(state = TodayFixtures.foodActionsState(policy, ItemSheetStateBuilder.amountStep(item)), onAction = {})
        },
        LeakSurface(id = "day-close-check-in", label = "Day close, the check-in", showsCaloriesInFull = true) { policy ->
            DayCloseScreen(
                state = TodayFixtures.dayCloseState(policy, expended = TodayFixtures.expended, checkInOn = true, checkIn = TodayFixtures.checkIn),
                onAction = {},
            )
        },
    )

    /** Today as design sync 2 and 4 draw it: the flags on, the watch connected, a weigh-in this morning. */
    private fun designSync(policy: VisibilityPolicy, settings: Settings = Settings(), ui: TodayUi = TodayUi()): TodayState =
        TodayFixtures.todayState(
            policy,
            day = TodayFixtures.day(loose = true),
            settings = settings,
            expended = TodayFixtures.expended,
            summaryOn = true,
            selectOn = true,
            weighIns = TodayFixtures.weighIns,
            ui = ui,
            foodActionsOn = true,
        )
}

/** Today with the ⋮ menu open over it, the menu drawn in place where the popover would sit. */
@Composable
internal fun TodayWithMenu(policy: VisibilityPolicy, clearArmed: Boolean = false) {
    val state = TodayFixtures.todayState(policy, menuOpen = true, clearArmed = clearArmed)
    Box(Modifier.fillMaxSize()) {
        TodayScreen(state = state.copy(menu = null), onAction = {})
        state.menu?.let {
            DayMenuContent(
                menu = it,
                onAction = {},
                // Under the ⋮, where the popover drops from.
                modifier = Modifier.align(Alignment.TopEnd).padding(top = MinTouchTarget + AppTheme.spacing.section, end = AppTheme.spacing.row),
            )
        }
    }
}

/** Today under a scrim with the month dropped from its title (13.3a), as the calendar dialog draws it. */
@Composable
internal fun TodayWithCalendar(policy: VisibilityPolicy) {
    Box(Modifier.fillMaxSize()) {
        TodayScreen(state = TodayFixtures.todayState(policy, summaryOn = true, selectOn = true, foodActionsOn = true), onAction = {})
        Box(Modifier.fillMaxSize().background(ModalDialogDefaults.scrimColor()))
        CalendarDropdownContent(state = TodayFixtures.calendarState(), onAction = {})
    }
}

/**
 * The apple and the peanut butter mid-drag over dinner (13.3c), held still: the list scrolled to
 * lunch, the finger about where dinner's card sits.
 */
@Composable
internal fun TodayDragging(policy: VisibilityPolicy) {
    val y = with(LocalDensity.current) { DinnerUnderFinger.toPx() }
    val drag = remember { FoodDrag().apply { showing(y, MealSlot.Dinner) } }
    TodayScreen(
        state = TodayFixtures.todayState(
            policy,
            summaryOn = true,
            selectOn = true,
            foodActionsOn = true,
            ui = TodayUi(selected = TodayFixtures.snackPicked),
        ),
        onAction = {},
        drag = drag,
        listState = rememberLazyListState(initialFirstVisibleItemIndex = LUNCH_ROW_FLAGS_ON),
    )
}

/** Today under a scrim with the peanut butter's actions in a sheet at the bottom (13.3d). */
@Composable
internal fun TodayWithFoodActions(policy: VisibilityPolicy) {
    Box(Modifier.fillMaxSize()) {
        TodayScreen(state = TodayFixtures.todayState(policy, summaryOn = true, selectOn = true, foodActionsOn = true), onAction = {})
        Box(Modifier.fillMaxSize().background(ModalDialogDefaults.scrimColor()))
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(AppTheme.colors.surface),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SheetHandle(Modifier.padding(vertical = 12.dp))
            ItemSheetContent(state = TodayFixtures.foodActionsState(policy), onAction = {})
        }
    }
}

/** With the summary flag on: the card, water, weight, breakfast, then lunch. */
private const val LUNCH_ROW_FLAGS_ON = 4

/** Where dinner's card sits with the list scrolled to lunch. */
private val DinnerUnderFinger = 500.dp
