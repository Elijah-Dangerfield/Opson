package com.opson.features.today.impl

import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.core.fixed
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Cards other modules add (`TodayCardContribution`): where they go in Today's list. */
class ContributedCardsTest {

    private val state = TodayFixtures.todayState(VisibilityPolicy(NumbersSettings(preset = PresetId.Full), Clock.fixed(TodayFixtures.noon), TodayFixtures.timeZone))

    @Test
    fun aContributedCardGoesBeforeWaterAndTheMeals_inTheOrderGiven() {
        val rows = todayRows(state, contributed = listOf("medication", "other"))
        val keys = rows.map { it.key }
        val medication = keys.indexOf("contributed-medication")
        assertTrue(medication >= 0, keys.toString())
        assertEquals(medication + 1, keys.indexOf("contributed-other"))
        val firstMeal = rows.indexOfFirst { it is TodayRow.Meal }
        assertTrue(medication < firstMeal, keys.toString())
        rows.indexOfFirst { it is TodayRow.Water }.takeIf { it >= 0 }?.let { assertTrue(medication < it, keys.toString()) }
    }

    @Test
    fun noContributionsLeaveTheListAsItWas() {
        assertEquals(todayRows(state), todayRows(state, contributed = emptyList()))
        assertTrue(todayRows(state).none { it is TodayRow.Contributed })
    }
}
