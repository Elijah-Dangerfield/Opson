package com.opson.features.logger.impl

import com.opson.libraries.diary.LoggedFood
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import com.opson.libraries.foods.LearnedPicks
import com.opson.libraries.nutrition.PresetId
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/**
 * Search in the person's order ([PersonalRanking] through [FoodSearchStateBuilder]): their own
 * foods rise when their use says so, the rest keep the server's order, and each row says why.
 */
class PersonalRankingTest {
    private val full = policy(PresetId.Full)
    private val base = LoggerFixtures.searchInputs().copy(logged = emptyList(), history = emptyList(), frequent = emptyList())
    private val now = base.now.toInstant(base.timeZone)
    private val serverOrder = listOf("f:rice", "f:brown-rice", "f:rice-noodles", "f:fried-rice", "f:rice-cakes", "f:pudding")

    @Test
    fun withNothingLearnedTheServersOrderStands() {
        assertEquals(serverOrder, rows(base).map { it.key })
    }

    @Test
    fun aFoodLoggedTwiceComesFirstAndSaysHowOften() {
        val rows = rows(base.copy(logged = listOf(logged(LoggerFixtures.pudding, times = 3, daysAgo = 40))))

        assertEquals("f:pudding", rows.first().key)
        assertEquals(RowReason.Logged(3), rows.first().reason)
        assertNull(rows.first { it.key == "f:rice" }.reason)
    }

    @Test
    fun oneLogLongAgoDoesNotPushTheObviousFoodDown() {
        val rows = rows(base.copy(logged = listOf(logged(LoggerFixtures.friedRice, times = 1, daysAgo = 40))))

        assertEquals(serverOrder, rows.map { it.key })
        assertEquals(RowReason.Logged(1), rows.first { it.key == "f:fried-rice" }.reason, "it still says why it is familiar")
    }

    @Test
    fun oneLogThisWeekIsEnough() {
        val rows = rows(base.copy(logged = listOf(logged(LoggerFixtures.friedRice, times = 1, daysAgo = 3))))

        assertEquals("f:fried-rice", rows.first().key)
    }

    @Test
    fun aResultChosenTwiceForTheseWordsComesFirstForThisPersonOnly() {
        val once = LearnedPicks().picked("rice", "f:rice-cakes", now)
        assertEquals(serverOrder, rows(base.copy(learned = once)).map { it.key }, "once could be a slip")

        val twice = once.picked("rice", "f:rice-cakes", now)
        val rows = rows(base.copy(learned = twice))
        assertEquals("f:rice-cakes", rows.first().key)
        assertNull(rows.first().reason, "chosen but never logged: nothing to say")
        assertEquals(serverOrder, rows(base.copy(learned = twice), query = "grain").map { it.key }, "other words are not affected")
    }

    @Test
    fun chosenForTheseWordsBeatsLoggedOften() {
        val learned = LearnedPicks().picked("rice", "f:brown-rice", now).picked("rice", "f:brown-rice", now)
        val inputs = base.copy(learned = learned, logged = listOf(logged(LoggerFixtures.pudding, times = 6, daysAgo = 1)))

        assertEquals(listOf("f:brown-rice", "f:pudding"), rows(inputs).take(2).map { it.key })
    }

    @Test
    fun aFavoriteComesFirstAndSaysFavorite() {
        val rows = rows(base.copy(settings = Settings(favoriteFoods = listOf("f:rice-noodles"))))

        assertEquals("f:rice-noodles", rows.first().key)
        assertEquals(RowReason.Favorite, rows.first().reason)
    }

    @Test
    fun yourOwnFoodSaysSoUnlessABadgeDoes() {
        val inputs = base.copy(customFoods = listOf(LoggerFixtures.granola))
        val plain = rows(inputs, query = "granola")
        assertEquals(RowReason.YourFood, plain.single { it.key == "c:granola" }.reason)

        val withOptions = LoggerFixtures.optionsState(full, query = "granola")
        val row = assertIs<SearchBody.Results>(withOptions.body).rows.single { it.key == "c:granola" }
        assertEquals(SourceBadge.YourFood, row.badge)
        assertNull(row.reason)
    }

    @Test
    fun offlineTheLongTailIsSearched() {
        val inputs = base.copy(logged = listOf(logged(LoggerFixtures.brownRice, times = 2, daysAgo = 90), logged(LoggerFixtures.kabsa, times = 5, daysAgo = 1)))
        val body = FoodSearchStateBuilder.build(inputs, full, choices(search = LoggerFixtures.search(offline = true))).body

        val results = assertIs<SearchBody.Results>(body)
        assertTrue(results.offline)
        assertEquals(listOf("f:brown-rice", "f:rice"), results.rows.map { it.key }, "the kabsa does not match rice")
        assertEquals("1 cup", results.rows.first().amount, "at the amount it was last logged")
    }

    @Test
    fun thePreviewShowsEveryReason() {
        val rows = assertIs<SearchBody.Results>(LoggerFixtures.searchPersonalState(full).body).rows

        assertEquals(listOf("f:rice-cakes", "f:brown-rice", "f:rice", "c:rice-bowl"), rows.take(4).map { it.key })
        assertEquals(listOf(null, RowReason.Favorite, RowReason.Logged(4), RowReason.YourFood), rows.take(4).map { it.reason })
    }

    @Test
    fun theStrongestReasonWins() {
        val ranking = PersonalRanking(
            query = "rice",
            learned = LearnedPicks(),
            logged = mapOf("f:rice" to logged(LoggerFixtures.rice, times = 2, daysAgo = 1)),
            favorites = setOf("f:rice"),
            now = now,
        )
        val pick = FoodPicks.run { LoggerFixtures.rice.toPick() }

        assertEquals(RowReason.Favorite, ranking.reason(pick, badged = false))
        assertTrue(ranking.signal(pick).promoted(now))
        assertFalse(PersonalSignal(times = 1, lastLoggedAt = now - 30.days).promoted(now))
    }

    private fun logged(food: com.opson.libraries.foods.Food, times: Int, daysAgo: Int) =
        LoggedFood(LoggerFixtures.item(food, MealSlot.Dinner, LoggerFixtures.today.minus(daysAgo, DateTimeUnit.DAY)).copy(loggedAt = now - daysAgo.days), times)

    private fun choices(query: String = "rice", search: SearchOutcome = LoggerFixtures.search(query)) =
        SearchChoices(slot = MealSlot.Dinner, query = query, search = search)

    /** The catalog answers every query with the same six rice foods, in [serverOrder]. */
    private fun rows(inputs: SearchInputs, query: String = "rice"): List<FoodRowUi> =
        assertIs<SearchBody.Results>(FoodSearchStateBuilder.build(inputs, full, choices(query)).body).rows
}
