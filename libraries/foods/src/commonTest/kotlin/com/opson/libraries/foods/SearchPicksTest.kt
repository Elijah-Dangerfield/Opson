package com.opson.libraries.foods

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class FoodQueryTest {

    @Test
    fun everyWordStartsAWordOfTheNameOrBrand() {
        assertTrue(FoodQuery.matches("chicken breast", "Chicken, breast, raw"))
        assertTrue(FoodQuery.matches("chick", "Chicken, breast, raw"))
        assertTrue(FoodQuery.matches("chobani greek", "Greek yogurt, plain", brand = "Chobani"))
        assertFalse(FoodQuery.matches("chicken thigh", "Chicken, breast, raw"))
    }

    @Test
    fun singularAndPluralFindEachOther() {
        assertTrue(FoodQuery.matches("strawberry", "Strawberries, raw"))
        assertTrue(FoodQuery.matches("strawberries", "Strawberry jam"))
        assertTrue(FoodQuery.matches("peach", "Peaches, yellow, raw"))
        assertTrue(FoodQuery.matches("tomatoes", "Tomato, roma"))
    }

    @Test
    fun whatWasTypedStillMatchesInsideAWord() {
        assertTrue(FoodQuery.matches("brownie", "Protein Brownie"))
        assertTrue(FoodQuery.matches("m&m", "Peanut M&M's"))
        assertFalse(FoodQuery.matches("  ", "Oats"))
    }

    @Test
    fun normalizesTheWayTheServerDoes() {
        assertEquals("brown rice", FoodQuery.normalize("  Brown   Rice "))
    }
}

class LearnedPicksTest {
    private val now = Instant.fromEpochMilliseconds(1_800_000_000_000)

    @Test
    fun aFoodPickedTwiceForTheSameWordsScoresTwo() {
        val learned = LearnedPicks().picked("Yogurt", "f:skyr", now).picked("yogurt ", "f:skyr", now)

        assertEquals(2.0, learned.score("yogurt", "f:skyr", now))
        assertEquals(0.0, learned.score("yogurt", "f:greek", now))
        assertEquals(0.0, learned.score("rice", "f:skyr", now))
    }

    @Test
    fun picksHalveEveryThirtyDays() {
        val learned = LearnedPicks().picked("yogurt", "f:skyr", now, weight = 2.0)

        assertEquals(1.0, learned.score("yogurt", "f:skyr", now + 30.days), 1e-9)
        assertEquals(1.5, learned.picked("yogurt", "f:skyr", now + 30.days, weight = 0.5).score("yogurt", "f:skyr", now + 30.days), 1e-9)
    }

    @Test
    fun aQueryThatStartsAnotherCountsHalf() {
        val learned = LearnedPicks().picked("strawberry", "f:berries", now)

        assertEquals(0.5, learned.score("straw", "f:berries", now))
        assertEquals(0.0, learned.score("st", "f:berries", now), "two letters are not a query yet")
    }

    @Test
    fun staysBounded() {
        var learned = LearnedPicks()
        repeat(LearnedPicks.MAX_PER_QUERY + 4) { learned = learned.picked("milk", "f:$it", now) }
        assertEquals(LearnedPicks.MAX_PER_QUERY, learned.picks.size)
        assertTrue(learned.score("milk", "f:${LearnedPicks.MAX_PER_QUERY + 3}", now) > 0.0, "the newest pick is always kept")

        repeat(LearnedPicks.MAX_PICKS + 10) { learned = learned.picked("food $it", "f:x", now) }
        assertEquals(LearnedPicks.MAX_PICKS, learned.picks.size)
    }

    @Test
    fun oldPicksAreForgotten() {
        val learned = LearnedPicks().picked("yogurt", "f:skyr", now).picked("rice", "f:rice", now + 200.days)

        assertEquals(listOf("rice"), learned.picks.map { it.query })
    }
}
