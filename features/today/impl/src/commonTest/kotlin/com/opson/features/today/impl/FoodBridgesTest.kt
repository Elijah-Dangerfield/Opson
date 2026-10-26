package com.opson.features.today.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [FoodBridges]: a step from a food someone eats often to one they barely tried, from their own diary only. */
class FoodBridgesTest {
    private val today = TodayFixtures.today

    private var next = 0

    private fun food(name: String, daysAgo: Int, foodId: String? = null, customFoodId: String? = null, method: LogMethod = LogMethod.Search): LogItem {
        val date = today.minus(DatePeriod(days = daysAgo))
        return LogItem(
            id = "item-${next++}",
            day = date,
            slot = MealSlot.Lunch,
            loggedAt = LocalDateTime(date, LocalTime(12, 0)).toInstant(TimeZone.UTC),
            name = name,
            precision = Precision.Estimate,
            method = method,
            foodId = foodId,
            customFoodId = customFoodId,
        )
    }

    private fun days(vararg items: LogItem): List<Day> =
        items.groupBy { it.day }.map { (date, list) -> Day(date, itemsBySlot = mapOf(MealSlot.Lunch to list)) }

    private fun suggest(frequent: List<LogItem>, vararg logged: LogItem) = FoodBridges.suggest(frequent, days(*logged), today)

    @Test
    fun stepsFromAnEverydayFoodToOneTriedOnceThatSharesAWord() {
        val rice = food("Jasmine rice, cooked", 1)
        val pudding = food("Rice pudding", 9, foodId = "pudding")

        val bridge = suggest(listOf(rice), rice, food("Jasmine rice, cooked", 2), pudding, food("Toast", 3))

        assertEquals(FoodBridge(from = "Jasmine rice", to = "Rice pudding", shared = "rice", foodId = "pudding"), bridge)
    }

    @Test
    fun prefersTheLeastTriedThenTheMostRecent() {
        val rice = food("Rice", 1)
        val twice = listOf(food("Fried rice", 3), food("Fried rice", 4))
        val older = food("Rice noodles", 20)
        val recent = food("Rice cakes", 5, customFoodId = "cakes")

        val bridge = suggest(listOf(rice), rice, *twice.toTypedArray(), older, recent)

        assertEquals("Rice cakes", bridge?.to)
        assertEquals("cakes", bridge?.customFoodId)
    }

    @Test
    fun aFoodEatenOftenIsNotAStep() {
        val rice = food("Rice", 1)
        val often = (2..4).map { food("Fried rice", it) }

        assertNull(suggest(listOf(rice), rice, *often.toTypedArray()))
    }

    @Test
    fun skipsWhatWasEatenTodayQuickNumbersAndOtherSpellingsOfTheSameFood() {
        val rice = food("Rice", 1, foodId = "rice")
        val eatenToday = food("Rice pudding", 0)
        val quick = food("Rice dinner", 5, method = LogMethod.QuickNumber)
        val sameFood = food("Rice, white", 6)

        assertNull(suggest(listOf(rice), rice, eatenToday, food("Rice pudding", 8), quick, sameFood))
    }

    @Test
    fun walksTheSafeFoodsInOrderUntilOnePairs() {
        val toast = food("Toast", 1)
        val apples = food("Apples", 1)
        val crumble = food("Apple crumble", 12)

        val bridge = suggest(listOf(toast, apples), toast, apples, crumble)

        assertEquals(FoodBridge(from = "Apples", to = "Apple crumble", shared = "apple"), bridge)
    }

    @Test
    fun nothingPairsMeansNoBridge() {
        val toast = food("Toast", 1)
        assertNull(suggest(listOf(toast), toast, food("Poutine", 4)))
        assertNull(suggest(emptyList(), food("Poutine", 4)))
    }

    @Test
    fun wordsAreWhatTheFoodIsNotHowItWasServed() {
        assertEquals(listOf("greek", "yogurt"), FoodBridges.words("Greek yogurt, plain"))
        assertEquals(listOf("egg"), FoodBridges.words("Eggs"))
        assertEquals(listOf("hummus"), FoodBridges.words("Hummus"))
        assertEquals(listOf("chicken"), FoodBridges.words("Chicken (homemade)"))
        assertEquals(listOf("rice"), FoodBridges.words("Rice, cooked"))
    }
}
