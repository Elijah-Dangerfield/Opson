package com.opson.features.logger.impl

import com.opson.libraries.core.fixed
import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.foods.FoodSearchFilter
import com.opson.libraries.foods.FoodSource
import com.opson.libraries.foods.FoodTier
import com.opson.libraries.foods.ReviewStatus
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * [FoodSearchStateBuilder], full-screen search (design sync 4, 13.4c): the body before and
 * while typing, the scopes, last amounts and "Your usual", the ✓, picking for a meal, and the
 * policy on every number. The search options are [SearchOptionsStateBuilderTest]'s.
 */
class FoodSearchStateBuilderTest {
    private val today = LoggerFixtures.today
    private val full = policy(PresetId.Full)

    private fun item(name: String, slot: MealSlot = MealSlot.Breakfast, foodId: String? = name.lowercase(), day: LocalDate = today, amount: String = "1 cup") = LogItem(
        id = "$name-$day-$slot",
        day = day,
        slot = slot,
        loggedAt = NOON,
        name = name,
        precision = Precision.Weighed,
        method = LogMethod.Search,
        foodId = foodId,
        amountText = amount,
        kcal = 100.0,
        protein = 5.0,
        carbs = 10.0,
        fat = 2.0,
    )

    private fun inputs(
        day: Day = Day.empty(today),
        usual: Map<MealSlot, List<LogItem>> = emptyMap(),
        recent: List<LogItem> = listOf(item("Oats")),
        custom: List<CustomFood> = emptyList(),
        history: List<LogItem> = emptyList(),
        frequent: List<LogItem> = emptyList(),
        settings: Settings = Settings(),
    ) = SearchInputs(
        today = today,
        day = day,
        settings = settings,
        usualBySlot = usual,
        recent = recent,
        customFoods = custom,
        savedMeals = LoggerFixtures.savedMeals,
        history = history,
        frequent = frequent,
    )

    private fun choices(query: String = "", search: SearchOutcome? = null, scope: SearchScope = SearchScope.All) =
        SearchChoices(slot = MealSlot.Dinner, query = query, search = search, scope = scope)

    private fun build(inputs: SearchInputs = inputs(), policy: VisibilityPolicy = full, choices: SearchChoices = choices()) =
        FoodSearchStateBuilder.build(inputs, policy, choices)

    private fun Display.text(): String? = when (this) {
        is Display.Shown -> text
        is Display.Soft -> text
        Display.Hidden -> null
    }

    @Test
    fun beforeTypingUsualAtTheMealComesFirstAndIsNotRepeatedUnderRecent() {
        val browse = assertIs<SearchBody.Browse>(
            build(inputs(usual = mapOf(MealSlot.Dinner to listOf(item("Adobo"))), recent = listOf(item("Adobo"), item("Oats")))).body,
        )

        assertEquals(MealSlot.Dinner, browse.slot)
        assertEquals(listOf("Adobo"), browse.usual.map { it.name })
        assertEquals(PickSource.Usual, browse.usual.single().source)
        assertEquals(listOf("Oats"), browse.recent.map { it.name })
        assertNull(browse.usualAround)
    }

    @Test
    fun nothingLoggedYetIsFirstRun() {
        assertEquals(SearchBody.FirstRun, build(inputs(recent = emptyList())).body)
    }

    @Test
    fun uncategorizedListsWhatIsUsuallyLoggedAroundNow() {
        val inputs = LoggerFixtures.searchInputs().copy(recentDays = LoggerFixtures.midMorningHistory(), now = LoggerFixtures.midMorning, timeZone = TimeZone.UTC)
        val browse = assertIs<SearchBody.Browse>(FoodSearchStateBuilder.build(inputs, full, SearchChoices(slot = MealSlot.Uncategorized)).body)

        assertEquals(LocalTime(10, 30), browse.usualAround)
        assertEquals(listOf("Arroz con pollo", "Garlic fried rice (sinangag)", "Side salad"), browse.usual.map { it.name })
    }

    @Test
    fun yourOwnFoodsComeFirstWithTheBrandLine() {
        val granola = CustomFood(id = "g1", name = "Rice granola", servingAmount = 1.0, servingUnit = "bar", kcal = 190.0)
        val search = SearchOutcome("rice", listOf(LoggerFixtures.rice, LoggerFixtures.sinangag), offline = false)

        val results = assertIs<SearchBody.Results>(build(inputs(custom = listOf(granola)), choices = choices("rice", search)).body)

        assertEquals(listOf("c:g1", "f:rice", "f:sinangag"), results.rows.map { it.key })
        assertEquals("Filipino", results.rows.last().subtitle)
        assertFalse(results.offline)
    }

    @Test
    fun searchWaitsForTheCatalogAndAMissCarriesTheTerm() {
        assertEquals(SearchBody.Searching, build(choices = choices("rice")).body)
        assertEquals(SearchBody.Searching, build(choices = choices("rice", SearchOutcome("ric", emptyList(), false))).body, "an older answer")
        assertEquals(
            SearchBody.NoResults(query = "kare-kare", offline = true),
            build(choices = choices(" kare-kare ", SearchOutcome("kare-kare", emptyList(), true))).body,
        )
    }

    @Test
    fun aFoodLoggedBeforeComesAtItsLastAmountAndAHabitSaysYourUsual() {
        val lastRice = LoggerFixtures.item(LoggerFixtures.rice, MealSlot.Dinner).copy(amountText = "1½ cups", quantity = 1.5, unit = "cup", kcal = 307.0)
        val state = LoggerFixtures.searchState(full, added = false)
        val plain = assertIs<SearchBody.Results>(state.body).rows

        assertTrue(plain.first { it.key == "f:rice" }.usual, "rice is among the foods logged most")
        assertFalse(plain.first { it.key == "f:brown-rice" }.usual)

        val inputs = LoggerFixtures.searchInputs().copy(history = listOf(lastRice))
        val pick = FoodSearchStateBuilder.picks(inputs, SearchChoices(slot = MealSlot.Dinner, query = "rice", search = LoggerFixtures.search())).first { it.key == "f:rice" }
        assertEquals("1½ cups", pick.draft.amountText, "the last amount, not a serving")
        assertEquals(Precision.Estimate, pick.draft.precision)
        assertEquals(LogMethod.Search, pick.draft.method)
        val fresh = FoodSearchStateBuilder.picks(inputs, SearchChoices(slot = MealSlot.Dinner, query = "rice", search = LoggerFixtures.search())).first { it.key == "f:brown-rice" }
        assertEquals("1 cup", fresh.draft.amountText, "never logged: one serving")
    }

    @Test
    fun whatWentIntoThisMealSinceSearchOpenedIsChecked() {
        val rows = assertIs<SearchBody.Results>(LoggerFixtures.searchState(full).body).rows

        assertEquals(listOf("f:rice"), rows.filter { it.added }.map { it.key })
    }

    @Test
    fun yourFoodsListsYourOwnThenWhatYouLoggedWithNoServer() {
        val history = listOf(item("Oats"), item("Adobo"), item("Oats"))
        val granola = CustomFood(id = "g1", name = "Lola's granola", servingAmount = 1.0, servingUnit = "bar", kcal = 190.0)
        val yours = choices(scope = SearchScope.YourFoods)

        val listed = assertIs<SearchBody.Listed>(build(inputs(custom = listOf(granola), history = history), choices = yours).body)

        assertEquals(listOf("c:g1", "f:oats", "f:adobo"), listed.rows.map { it.key })
        assertNull(FoodSearchStateBuilder.catalogFilter(inputs(), yours))
        assertEquals(SearchBody.NoFoodsYet, build(inputs(), choices = yours).body)
        val matched = assertIs<SearchBody.Results>(build(inputs(custom = listOf(granola), history = history), choices = yours.copy(query = "oat")).body)
        assertEquals(listOf("f:oats"), matched.rows.map { it.key })
    }

    @Test
    fun savedMealsMatchByNameOrFood() {
        val saved = choices(scope = SearchScope.SavedMeals)

        assertEquals(listOf("Sunday breakfast", "Rice plate"), assertIs<SearchBody.SavedMeals>(build(choices = saved).body).meals.map { it.name })
        assertEquals(listOf("Sunday breakfast"), assertIs<SearchBody.SavedMeals>(build(choices = saved.copy(query = "banana")).body).meals.map { it.name })
        assertEquals("205", assertIs<SearchBody.SavedMeals>(build(choices = saved).body).meals.first().calories.text())
        assertNull(FoodSearchStateBuilder.catalogFilter(inputs(), saved))
    }

    @Test
    fun allAsksTheCatalogAs01Did() {
        assertEquals(FoodSearchFilter.None, FoodSearchStateBuilder.catalogFilter(inputs(), choices("rice")))
    }

    @Test
    fun pickingForAMealChecksThePicksAndLeavesOutWhatWouldLog() {
        val state = LoggerFixtures.searchState(full, pickingForMeal = true, flags = true)

        assertEquals(listOf(SearchScope.All, SearchScope.YourFoods), state.scopes)
        assertFalse(state.canScan)
        assertFalse(state.canAddFood)
        assertFalse(state.canDescribe)
        assertEquals(2, state.picks?.count)
        assertEquals(2, assertIs<SearchBody.Results>(state.body).rows.count { it.added })
    }

    @Test
    fun theFlagsDecideTheScannerAndAddingAFood() {
        val on = LoggerFixtures.searchState(full)
        assertTrue(on.canScan)
        assertTrue(on.canAddFood)
        val off = LoggerFixtures.searchState(full, flags = false)
        assertFalse(off.canScan)
        assertFalse(off.canAddFood)
        assertTrue(off.canDescribe, "Describe shipped in 0.1")
    }

    @Test
    fun theirOwnFoodWaitingForReviewReadsUnverifiedUntilItIsApproved() {
        val granola = LoggerFixtures.granola
        val pending = inputs(custom = listOf(granola)).copy(reviews = mapOf(granola.id to ReviewStatus.Pending))
        val search = SearchOutcome("granola", foods = emptyList(), offline = false)

        val rows = assertIs<SearchBody.Results>(build(pending, choices = choices("granola", search)).body).rows
        assertEquals(listOf(true), rows.map { it.unverified })

        val approved = pending.copy(reviews = mapOf(granola.id to ReviewStatus.Approved))
        assertEquals(listOf(false), assertIs<SearchBody.Results>(build(approved, choices = choices("granola", search)).body).rows.map { it.unverified })
    }

    @Test
    fun aPendingCatalogFoodIsUnverifiedAndAnApprovedOneIsOpsonsAndVerified() {
        val bar = LoggerFixtures.rice.copy(id = "bar", name = "Oat bar", source = FoodSource.UserSubmitted, tier = FoodTier.Community)
        val pending = bar.copy(reviewStatus = ReviewStatus.Pending)
        val approved = bar.copy(id = "bar-2", verified = true)
        val asked = choices("oat bar").copy(searchOptions = true)
        val filter = checkNotNull(FoodSearchStateBuilder.catalogFilter(inputs(), asked))
        val withOptions = asked.copy(search = SearchOutcome("oat bar", foods = listOf(pending, approved), offline = false, filter = filter))

        val rows = assertIs<SearchBody.Results>(build(choices = withOptions).body).rows
        assertEquals(listOf(true, false), rows.map { it.unverified })
        assertEquals(SourceBadge.Opson, rows[1].badge)
        assertTrue(rows[1].verified)
    }

    @Test
    fun blindShowsNoNumberAnywhere() {
        val blind = policy(PresetId.NoNumbers)
        val results = assertIs<SearchBody.Results>(LoggerFixtures.searchState(blind).body)
        assertTrue(results.rows.all { it.calories == Display.Hidden && it.macros.isEmpty() })
        val meals = assertIs<SearchBody.SavedMeals>(LoggerFixtures.searchState(blind, query = "", scope = SearchScope.SavedMeals).body)
        assertTrue(meals.meals.all { it.calories == Display.Hidden })
    }

    @Test
    fun compactMacrosDropAHiddenMacro() {
        val overridden = VisibilityPolicy(
            NumbersSettings(preset = PresetId.Full, overrides = mapOf(Metric.Carbs to Visibility.Hide)),
            Clock.fixed(NOON),
            TimeZone.UTC,
        )

        val row = assertIs<SearchBody.Browse>(build(policy = overridden).body).recent.single()

        assertEquals(listOf("5P", "2F"), row.macros.map { it.text })
    }

    @Test
    fun mealGroupsOffOffersOnlyUncategorized() {
        val state = build(inputs(settings = Settings(mealGroups = false)))

        assertEquals(listOf(MealSlot.Uncategorized), state.slots)
        assertEquals(MealSlot.Uncategorized, state.slot)
    }
}
