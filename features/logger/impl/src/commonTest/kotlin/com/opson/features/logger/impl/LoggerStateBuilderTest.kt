package com.opson.features.logger.impl

import com.opson.libraries.core.fixed
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * [LoggerStateBuilder] across the visibility levels the plan's surface table
 * lists, plus the body, pane and chip rules. Every number is checked as the
 * policy resolved it; formatting itself is `:libraries:nutrition`'s to test.
 * The view model's wiring is in [LoggerViewModelTest].
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

    private fun day(date: LocalDate = today, vararg items: LogItem) = Day(
        date = date,
        itemsBySlot = MealSlot.entries.associateWith { slot -> items.filter { it.slot == slot } },
    )

    private fun inputs(
        day: Day = day(today),
        dayBefore: Day = Day.empty(yesterday),
        settings: Settings = Settings(),
        basket: List<BasketItem> = emptyList(),
        usual: Map<MealSlot, List<LogItem>> = emptyMap(),
        recent: List<LogItem> = listOf(item("Oats")),
        custom: List<CustomFood> = emptyList(),
    ) = LoggerInputs(today, day, dayBefore, settings, basket, usual, recent, custom, LoggerFixtures.savedMeals)

    private fun build(
        inputs: LoggerInputs = inputs(),
        policy: VisibilityPolicy = policy(PresetId.Full),
        slot: MealSlot = MealSlot.Dinner,
        query: String = "",
        search: SearchOutcome? = null,
        pane: LoggerPane = LoggerPane.Search,
    ) = LoggerStateBuilder.build(inputs, policy, LoggerChoices(slot = slot, pane = pane, query = query, search = search))

    private fun Display.text(): String? = when (this) {
        is Display.Shown -> text
        is Display.Soft -> text
        Display.Hidden -> null
    }

    @Test
    fun rowsShowBareEnergyAndCompactMacrosInFull() {
        val row = assertIs<LoggerBody.Browse>(build().body).recent.single()

        assertEquals("100", row.calories.text(), "v3 draws a row's energy without the unit")
        assertEquals(listOf("5P", "10C", "2F"), row.macros.map { it.text })
    }

    @Test
    fun blindShowsNoNumberAnywhere() {
        val banana = FoodPick("f:banana", FoodRef.Catalog("banana"), item("Banana").toDraft(), PickSource.Search).toBasketItem()
        val described = LoggerFixtures.parsed.mapIndexed { index, parsed -> parsed.toDescribedItem(index) }
        val blind = policy(PresetId.Blind)
        val state = build(inputs(basket = listOf(banana) + described), blind)

        val browse = assertIs<LoggerBody.Browse>(state.body)
        (browse.usual + browse.recent).forEach { row ->
            assertEquals(Display.Hidden, row.calories)
            assertTrue(row.macros.isEmpty())
        }
        assertEquals(Display.Hidden, state.basket?.calories)
        assertTrue(state.describe.lines.all { it.calories == Display.Hidden })
        assertTrue(state.saved.meals.all { it.calories == Display.Hidden })
        val results = assertIs<LoggerBody.Results>(LoggerFixtures.searchState(blind).body)
        assertTrue(results.rows.all { it.calories == Display.Hidden && it.macros.isEmpty() })
    }

    @Test
    fun fuelHidesRowEnergyButKeepsProtein() {
        val row = assertIs<LoggerBody.Browse>(build(policy = policy(PresetId.Fuel)).body).recent.single()

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
    fun methodsFollowTheCalorieLevelAndTheSlot() {
        assertEquals(
            listOf(LoggerMethod.DescribeIt, LoggerMethod.SavedMeals, LoggerMethod.AteNoDetails, LoggerMethod.JustANumber),
            build().methods,
        )

        val blind = build(policy = policy(PresetId.Blind)).methods
        assertTrue(LoggerMethod.AddANote in blind)
        assertFalse(LoggerMethod.JustANumber in blind)
    }

    @Test
    fun theChipsAndTheDayStepAsideWhileTyping() {
        val typing = build(query = "ri")

        assertTrue(typing.methods.isEmpty())
        assertTrue(typing.typing)
        assertFalse(build().typing)
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
    }

    @Test
    fun theDayLabelSaysWhichDay() {
        assertEquals(RelativeDay.Today, build().day)
        assertEquals(RelativeDay.Yesterday, build(inputs(day = Day.empty(yesterday))).day)
    }

    @Test
    fun usualAtTheMealComesFirstAndIsNotRepeatedUnderRecent() {
        val browse = assertIs<LoggerBody.Browse>(
            build(inputs(usual = mapOf(MealSlot.Dinner to listOf(item("Adobo"))), recent = listOf(item("Adobo"), item("Oats")))).body,
        )

        assertEquals(MealSlot.Dinner, browse.slot)
        assertEquals(listOf("Adobo"), browse.usual.map { it.name })
        assertEquals(PickSource.Usual, browse.usual.single().source)
        assertEquals(listOf("Oats"), browse.recent.map { it.name })

        assertTrue(assertIs<LoggerBody.Browse>(build().body).usual.isEmpty(), "nothing usual yet: Recent only")
    }

    @Test
    fun entriesWithNoFoodAreLeftOutAndNothingAtAllIsFirstRun() {
        val recent = listOf(item("Quick add", foodId = null), item("Oats"))
        assertEquals(listOf("Oats"), assertIs<LoggerBody.Browse>(build(inputs(recent = recent)).body).recent.map { it.name })

        assertEquals(LoggerBody.FirstRun, build(inputs(recent = emptyList())).body)
    }

    @Test
    fun aDiaryPickIsAnEstimateOfTheSameAmountAgain() {
        val pick = LoggerStateBuilder.picks(inputs(), MealSlot.Dinner, "", null).first { it.source == PickSource.Recent }

        assertEquals(Precision.Estimate, pick.draft.precision, "the original was weighed; logging it again is not")
        assertEquals(LogMethod.Recent, pick.draft.method)
    }

    @Test
    fun searchShowsTheUsersOwnFoodsFirstWithTheBrandLine() {
        val granola = CustomFood(id = "g1", name = "Rice granola", servingAmount = 1.0, servingUnit = "bar", kcal = 190.0)
        val search = SearchOutcome("rice", listOf(LoggerFixtures.rice, LoggerFixtures.sinangag), offline = false)

        val results = assertIs<LoggerBody.Results>(build(inputs(custom = listOf(granola)), query = "rice", search = search).body)

        assertEquals(listOf("c:g1", "f:rice", "f:sinangag"), results.rows.map { it.key })
        assertEquals("Filipino", results.rows.last().subtitle)
        assertNull(results.rows[1].subtitle)
        assertFalse(results.offline)
    }

    @Test
    fun searchWaitsForTheCatalogAndAMissCarriesTheTerm() {
        assertEquals(LoggerBody.Searching, build(query = "rice").body)
        assertEquals(LoggerBody.Searching, build(query = "rice", search = SearchOutcome("ric", emptyList(), false)).body, "an older answer")
        assertEquals(
            LoggerBody.NoResults(query = "kare-kare", offline = true),
            build(query = " kare-kare ", search = SearchOutcome("kare-kare", emptyList(), true)).body,
        )
        assertTrue(assertIs<LoggerBody.Results>(build(query = "rice", search = SearchOutcome("rice", listOf(LoggerFixtures.rice), true)).body).offline)
    }

    @Test
    fun pickedFoodsAreCheckedAndSummedInTheFooter() {
        val state = LoggerFixtures.loggerState(policy(PresetId.Full), basket = LoggerFixtures.pickedBasket())

        val browse = assertIs<LoggerBody.Browse>(state.body)
        assertTrue(browse.usual.first { it.key == "f:arroz" }.picked)
        assertFalse(browse.usual.first { it.key == "f:salad" }.picked)
        val footer = state.basket!!
        assertEquals(2, footer.count)
        assertEquals("Arroz con pollo, Garlic fried rice", footer.names)
        assertEquals("813 kcal", footer.calories.text())
        assertEquals(MealSlot.Dinner, footer.slot)
    }

    @Test
    fun describeAndSavedHaveNoFooter() {
        val basket = LoggerFixtures.pickedBasket()

        assertNull(build(inputs(basket = basket), pane = LoggerPane.Describe).basket)
        assertNull(build(inputs(basket = basket), pane = LoggerPane.Saved).basket)
    }

    @Test
    fun describeLinesAreTheDescribedPicks() {
        val state = LoggerFixtures.describeState(policy(PresetId.Full))

        assertEquals(LoggerPane.Describe, state.pane)
        assertEquals(MealSlot.Breakfast, state.slot)
        assertEquals(listOf("Egg, large", "Toast, white"), state.describe.lines.map { it.name })
        assertEquals(listOf("2", "1 slice"), state.describe.lines.map { it.amount })
        assertEquals(listOf("156", "80"), state.describe.lines.map { it.calories.text() })
        assertEquals(FoodRef.Catalog("egg"), state.describe.lines.first().ref)
    }

    @Test
    fun justANumberIsOnlyOfferedWhenCaloriesShow() {
        assertTrue(LoggerFixtures.describeState(policy(PresetId.Full)).describe.canNumber)
        assertFalse(LoggerFixtures.describeState(policy(PresetId.Blind)).describe.canNumber)
        assertFalse(LoggerFixtures.describeState(policy(PresetId.Mindful)).describe.canNumber)
    }

    @Test
    fun savedMealsListTheirFoodsAndEnergy() {
        val meal = LoggerFixtures.savedState(policy(PresetId.Full)).saved.meals.first()

        assertEquals("Sunday breakfast", meal.name)
        assertEquals("Greek yogurt, Banana", meal.foods)
        assertEquals("205 kcal", meal.calories.text())
    }

    @Test
    fun theBarcodeButtonFollowsTheFlagAndStaysOutOfPickMode() {
        val choices = LoggerChoices(slot = MealSlot.Dinner)
        val policy = policy(PresetId.Full)

        assertFalse(LoggerStateBuilder.build(inputs(), policy, choices).canScan, "flag off: not rendered, as in 0.1")
        assertTrue(LoggerStateBuilder.build(inputs(), policy, choices.copy(scanAvailable = true)).canScan)
        assertFalse(
            LoggerStateBuilder.build(inputs(), policy, choices.copy(scanAvailable = true, pickingForMeal = true)).canScan,
            "a meal being put together logs nothing, so it has nothing to scan into",
        )
        // Blind changes no part of it: the button carries no number.
        assertTrue(LoggerStateBuilder.build(inputs(), policy(PresetId.Blind), choices.copy(scanAvailable = true)).canScan)
    }

    @Test
    fun shortNamesCutAtTheFirstCommaOrBracket() {
        assertEquals("Greek yogurt", shortName("Greek yogurt, plain"))
        assertEquals("Garlic fried rice", shortName("Garlic fried rice (sinangag)"))
        assertEquals("Banana", shortName("Banana"))
        assertEquals("(odd)", shortName("(odd)"), "a name that starts with a bracket keeps itself")
    }
}
