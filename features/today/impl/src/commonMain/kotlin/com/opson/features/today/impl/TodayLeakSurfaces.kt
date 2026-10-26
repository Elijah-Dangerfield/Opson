package com.opson.features.today.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import com.opson.libraries.ui.components.MinTouchTarget
import com.opson.system.AppTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.opson.features.today.impl.calendar.CalendarSheetContent
import com.opson.features.today.impl.cards.TodayCardsSheetContent
import com.opson.features.today.impl.dayclose.DayCloseScreen
import com.opson.features.today.impl.daynote.DayNoteSheetContent
import com.opson.features.today.impl.item.ItemSheetContent
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
 * cover the Meals, Rhythm, Explorer and Journal layouts with these same entries. The menu and
 * the sheets render their content in place, with no popup window.
 */
object TodayLeakSurfaces {
    val all: List<LeakSurface> = listOf(
        LeakSurface(id = "today", label = "Today", showsCaloriesInFull = true) { policy ->
            TodayScreen(state = TodayFixtures.todayState(policy), onAction = {})
        },
        LeakSurface(id = "today-big-day", label = "Today · big day", showsCaloriesInFull = true) { policy ->
            TodayScreen(state = TodayFixtures.todayState(policy, day = TodayFixtures.day(bigDay = true, loose = true)), onAction = {})
        },
        LeakSurface(id = "today-added", label = "Today · just added", showsCaloriesInFull = true) { policy ->
            TodayScreen(state = TodayFixtures.todayState(policy, day = TodayFixtures.day(dinner = true), highlight = MealSlot.Dinner), onAction = {})
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
            CalendarSheetContent(state = TodayFixtures.calendarState(), onAction = {})
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
