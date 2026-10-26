package com.opson.features.logger.impl

import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.ui.time.RelativeDay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import com.opson.libraries.core.fixed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * [LoggerStateBuilder] across the visibility levels the plan's surface table
 * lists, plus the body and chip rules. Every number is checked as the policy
 * resolved it; formatting itself is `:libraries:nutrition`'s to test. The
 * view model's wiring is in [LoggerViewModelTest].
 */
class LoggerStateBuilderTest {
    private val today = LoggerFixtures.today
    private val yesterday = today.minus(1, DateTimeUnit.DAY)

    private fun item(
        name: String,
        slot: MealSlot = MealSlot.Breakfast,
        kcal: Double? = 100.0,
        protein: Double? = 5.0,
        carbs: Double? = 10.0,
        fat: Double? = 2.0,
        foodId: String? = name.lowercase(),
        day: LocalDate = today,
    ) = LogItem(
        id = "$name-$day-$slot",
        day = day,
        slot = slot,
        loggedAt = NOON,
        name = name,
        precision = Precision.Weighed,
        method = LogMethod.Search,
        foodId = foodId,
        amountText = "1 cup",
        kcal = kcal,
        protein = protein,
        carbs = carbs,
        fat = fat,
    )

    private fun day(date: LocalDate = today, vararg items: LogItem, ate: Set<MealSlot> = emptySet()) = Day(
        date = date,
        itemsBySlot = MealSlot.entries.associateWith { slot -> items.filter { it.slot == slot } },
        ateNoDetails = ate,
    )

    private val bigBreakfast = item("Breakfast plate", kcal = 1245.0, protein = 47.0, carbs = 160.0, fat = 41.0)

    private fun inputs(
        day: Day = day(today, bigBreakfast),
        dayBefore: Day = Day.empty(yesterday),
        settings: Settings = Settings(),
        basket: List<BasketItem> = emptyList(),
        favorites: List<LogItem> = listOf(item("Banana"), item("Yogurt"), item("Rice"), item("Adobo")),
        usual: Map<MealSlot, List<LogItem>> = emptyMap(),
        recent: List<LogItem> = listOf(item("Oats")),
        custom: List<CustomFood> = emptyList(),
    ) = LoggerInputs(today, day, dayBefore, settings, basket, favorites, usual, recent, custom)

    private fun build(
        inputs: LoggerInputs = inputs(),
        policy: VisibilityPolicy = policy(PresetId.Full),
        slot: MealSlot = MealSlot.Dinner,
        query: String = "",
        search: SearchOutcome? = null,
    ) = LoggerStateBuilder.build(inputs, policy, slot, query, search)

    private fun Display.text(): String? = when (this) {
        is Display.Shown -> text
        is Display.Soft -> text
        Display.Hidden -> null
    }

    @Test
    fun fullShowsTheDaysTotalsAgainstTheGoalsWithASlash() {
        val state = build()

        assertEquals(
            listOf("1,245 / 2,100", "47 / 120 g", "160 / 250 g", "41 / 70 g"),
            state.totals?.map { it.display.text() },
        )
        assertNull(state.mealChecks)
    }

    @Test
    fun blindShowsMealChecksInsteadOfTotals() {
        val state = build(inputs(day = day(today, bigBreakfast, ate = setOf(MealSlot.Lunch))), policy(PresetId.Blind))

        assertNull(state.totals)
        assertEquals(
            listOf(
                MealCheck(MealSlot.Breakfast, true),
                MealCheck(MealSlot.Lunch, true),
                MealCheck(MealSlot.Snack, false),
                MealCheck(MealSlot.Dinner, false),
            ),
            state.mealChecks,
        )
    }

    @Test
    fun blindShowsNoNumberAnywhere() {
        val state = build(
            inputs(basket = listOf(FoodPick("f:banana", FoodRef.Catalog("banana"), item("Banana").toDraft(), PickSource.Search).toBasketItem())),
            policy(PresetId.Blind),
        )

        val browse = assertIs<LoggerBody.Browse>(state.body)
        (browse.favorites + browse.usual + browse.recent).forEach { row ->
            assertEquals(Display.Hidden, row.calories)
            assertTrue(row.macros.isEmpty())
        }
        assertEquals(Display.Hidden, state.basket?.calories)
    }

    @Test
    fun fuelShowsSoftEnergyAndProteinInTheStripAndNoEnergyOnRows() {
        val state = build(policy = policy(PresetId.Fuel))

        assertEquals(listOf(Metric.Calories, Metric.Protein), state.totals?.map { it.metric })
        assertIs<Display.Soft>(state.totals?.first()?.display)
        assertIs<Display.Shown>(state.totals?.last()?.display)

        val row = assertIs<LoggerBody.Browse>(state.body).recent.single()
        assertEquals(Display.Hidden, row.calories, "a row has no goal, so a softened energy hides")
        assertEquals(listOf(Metric.Protein), row.macros.map { it.metric })
    }

    @Test
    fun compactMacrosDropAHiddenMacro() {
        val overridden = VisibilityPolicy(
            NumbersSettings(preset = PresetId.Full, overrides = mapOf(Metric.Carbs to Visibility.Hide)),
            Clock.fixed(NOON),
            TimeZone.UTC,
        )

        val row = assertIs<LoggerBody.Browse>(build(policy = overridden).body).recent.single()

        assertEquals(listOf("5P", "2F"), row.macros.map { it.text })
    }

    @Test
    fun delayedNumbersStayClosedTodayAndOpenOnAPastDay() {
        val delayed = policy(PresetId.Delayed)

        assertNull(build(policy = delayed).totals)
        assertTrue(build(inputs(day = day(yesterday, bigBreakfast.copy(day = yesterday))), delayed).totals.orEmpty().isNotEmpty())
    }

    @Test
    fun methodsFollowTheWaterAndCalorieLevels() {
        assertEquals(
            listOf(
                LoggerMethod.DescribeIt,
                LoggerMethod.Water,
                LoggerMethod.SavedMeals,
                LoggerMethod.AteNoDetails,
                LoggerMethod.WeighIn,
                LoggerMethod.JustANumber,
            ),
            build().methods,
        )

        val blind = build(policy = policy(PresetId.Blind)).methods
        assertTrue(LoggerMethod.AddANote in blind)
        assertFalse(LoggerMethod.JustANumber in blind)
        assertTrue(LoggerMethod.Water in blind, "Blind softens water rather than hiding it")

        val noWater = VisibilityPolicy(
            NumbersSettings(preset = PresetId.Full, overrides = mapOf(Metric.Water to Visibility.Hide)),
            Clock.fixed(NOON),
            TimeZone.UTC,
        )
        assertFalse(LoggerMethod.Water in build(policy = noWater).methods)
    }

    @Test
    fun theYesterdayChipNeedsYesterdaysSlot() {
        val dayBefore = day(yesterday, item("Adobo", MealSlot.Dinner, day = yesterday))

        assertTrue(LoggerMethod.Yesterday(MealSlot.Dinner) in build(inputs(dayBefore = dayBefore)).methods)
        assertTrue(build(inputs(dayBefore = dayBefore), slot = MealSlot.Lunch).methods.none { it is LoggerMethod.Yesterday })
    }

    @Test
    fun mealGroupsOffOffersOnlyUncategorized() {
        val state = build(inputs(settings = Settings(mealGroups = false)))

        assertEquals(listOf(MealSlot.Uncategorized), state.slots)
        assertEquals(MealSlot.Uncategorized, state.slot)
        assertFalse(LoggerMethod.AteNoDetails in state.methods)
        assertEquals(listOf(MealCheck(MealSlot.Uncategorized, false)), build(inputs(settings = Settings(mealGroups = false)), policy(PresetId.Blind)).mealChecks)
    }

    @Test
    fun theHeaderNamesTheDayOnlyWhenItIsNotToday() {
        assertNull(build().otherDay)
        assertEquals(RelativeDay.Yesterday, build(inputs(day = Day.empty(yesterday))).otherDay)
    }

    @Test
    fun usualFallsBackToTheFirstFavoritesAsSuggested() {
        val suggested = assertIs<LoggerBody.Browse>(build().body)
        assertEquals(UsualTitle.Suggested, suggested.usualTitle)
        assertEquals(listOf("Banana", "Yogurt", "Rice"), suggested.usual.map { it.name })

        val usual = assertIs<LoggerBody.Browse>(build(inputs(usual = mapOf(MealSlot.Dinner to listOf(item("Adobo"))))).body)
        assertEquals(UsualTitle.UsualAt(MealSlot.Dinner), usual.usualTitle)
        assertEquals(listOf("Adobo"), usual.usual.map { it.name })
        assertEquals(PickSource.Usual, usual.usual.single().source)
    }

    @Test
    fun entriesWithNoFoodAreLeftOutAndNothingAtAllIsFirstRun() {
        val recent = listOf(item("Quick add", foodId = null), item("Oats"))
        assertEquals(listOf("Oats"), assertIs<LoggerBody.Browse>(build(inputs(recent = recent)).body).recent.map { it.name })

        assertEquals(LoggerBody.FirstRun, build(inputs(favorites = emptyList(), recent = emptyList())).body)
    }

    @Test
    fun aDiaryPickIsAnEstimateOfTheSameAmountAgain() {
        val inputs = inputs()
        val pick = LoggerStateBuilder.picks(inputs, MealSlot.Dinner, "", null).first { it.source == PickSource.Recent }

        assertEquals(Precision.Estimate, pick.draft.precision, "the original was weighed; logging it again is not")
        assertEquals(LogMethod.Recent, pick.draft.method)
    }

    @Test
    fun searchShowsTheUsersOwnFoodsFirst() {
        val granola = CustomFood(id = "g1", name = "Rice granola", servingAmount = 1.0, servingUnit = "bar", kcal = 190.0)
        val search = SearchOutcome("rice", listOf(LoggerFixtures.rice, LoggerFixtures.friedRice), offline = false)

        val results = assertIs<LoggerBody.Results>(build(inputs(custom = listOf(granola)), query = "rice", search = search).body)

        assertEquals(listOf("c:g1", "f:rice", "f:sinangag"), results.rows.map { it.key })
        assertEquals("Garlic fried rice", results.rows.last().shortName)
        assertFalse(results.offline)
    }

    @Test
    fun searchWaitsForTheCatalogAndSaysWhenItIsOffline() {
        assertEquals(LoggerBody.Searching, build(query = "rice").body)
        assertEquals(LoggerBody.Searching, build(query = "rice", search = SearchOutcome("ric", emptyList(), false)).body, "an older answer")
        assertEquals(LoggerBody.NoResults(offline = true), build(query = "pancit", search = SearchOutcome("pancit", emptyList(), true)).body)
        assertTrue(assertIs<LoggerBody.Results>(build(query = "rice", search = SearchOutcome("rice", listOf(LoggerFixtures.rice), true)).body).offline)
    }

    @Test
    fun pickedFoodsAreCheckedEverywhereAndSummedInTheFooter() {
        val state = LoggerFixtures.loggerState(policy(PresetId.Full), basket = LoggerFixtures.pickedBasket())

        val browse = assertIs<LoggerBody.Browse>(state.body)
        assertTrue(browse.favorites.first { it.key == "f:banana" }.picked)
        assertTrue(browse.recent.first { it.key == "f:banana" }.picked)
        assertFalse(browse.favorites.first { it.key == "f:rice" }.picked)
        val footer = state.basket!!
        assertEquals(2, footer.count)
        assertEquals("Banana, Greek yogurt", footer.names)
        assertEquals("205 kcal", footer.calories.text())
        assertEquals(MealSlot.Dinner, footer.slot)
    }

    @Test
    fun shortNamesCutAtTheFirstCommaOrBracket() {
        assertEquals("Greek yogurt", shortName("Greek yogurt, plain"))
        assertEquals("Garlic fried rice", shortName("Garlic fried rice (sinangag)"))
        assertEquals("Banana", shortName("Banana"))
        assertEquals("(odd)", shortName("(odd)"), "a name that starts with a bracket keeps itself")
    }
}
