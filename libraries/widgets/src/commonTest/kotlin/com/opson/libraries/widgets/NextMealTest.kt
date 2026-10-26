package com.opson.libraries.widgets

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.MealSlot
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NextMealTest {

    private val settings = WidgetFixtures.settings()
    private val empty = Day.empty(WidgetFixtures.today)

    @Test
    fun anEmptyMorning_logsBreakfast() {
        assertEquals(MealSlot.Breakfast, NextMeal.of(empty, settings, LocalTime(8, 0)))
    }

    @Test
    fun theEvening_skipsAMissedBreakfast_forDinner() {
        assertEquals(MealSlot.Dinner, NextMeal.of(empty, settings, LocalTime(19, 0)))
    }

    @Test
    fun aDoneMeal_movesOnToTheNext() {
        // The fixture day has breakfast, lunch and snack: at lunchtime the next one is dinner.
        assertEquals(MealSlot.Dinner, NextMeal.of(WidgetFixtures.day, settings, LocalTime(12, 30)))
    }

    @Test
    fun everythingFromNowOnDone_leavesItToTheLogger() {
        val done = WidgetFixtures.day.copy(ateNoDetails = setOf(MealSlot.Dinner))
        assertNull(NextMeal.of(done, settings, LocalTime(20, 0)))
    }

    @Test
    fun mealGroupsOff_leavesItToTheLogger() {
        assertNull(NextMeal.of(empty, WidgetFixtures.settings(mealGroups = false), LocalTime(8, 0)))
    }

    @Test
    fun theLogLink_isRecognised_withOrWithoutExtras() {
        assertTrue(WidgetLinks.isLog(WidgetLinks.LOG))
        assertTrue(WidgetLinks.isLog("${WidgetLinks.LOG}/"))
        assertTrue(WidgetLinks.isLog("${WidgetLinks.LOG}?from=small"))
        assertFalse(WidgetLinks.isLog("opson://login-callback#token"))
        assertFalse(WidgetLinks.isLog("opson://widget/logger"))
    }
}
