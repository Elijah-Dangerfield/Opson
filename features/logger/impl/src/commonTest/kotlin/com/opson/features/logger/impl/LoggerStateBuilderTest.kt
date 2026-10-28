package com.opson.features.logger.impl

import com.opson.libraries.core.fixed
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * [LoggerStateBuilder], the + sheet: which actions the grid holds by flag, policy and meal,
 * Recent's rows and their ✓, and the Describe and Saved panes, each number as the policy
 * resolved it. The view model's wiring is in [LoggerViewModelTest].
 */
class LoggerStateBuilderTest {
    private val today = LoggerFixtures.today
    private val yesterday = today.minus(1, DateTimeUnit.DAY)

    private fun item(name: String, slot: MealSlot = MealSlot.Breakfast, foodId: String? = name.lowercase(), day: LocalDate = today) = LogItem(
        id = "$name-$day-$slot",
        day = day,
        slot = slot,
        loggedAt = NOON,
        name = name,
        precision = Precision.Weighed,
        method = LogMethod.Search,
        foodId = foodId,
        amountText = "1 cup",
        kcal = 100.0,
        protein = 5.0,
        carbs = 10.0,
        fat = 2.0,
    )

    private fun day(date: LocalDate = today, vararg items: LogItem) = Day(
        date = date,
        itemsBySlot = MealSlot.entries.associateWith { slot -> items.filter { it.slot == slot } },
    )

    private fun inputs(
        day: Day = day(today),
        dayBefore: Day = Day.empty(yesterday),
        settings: Settings = Settings(),
        recent: List<LogItem> = listOf(item("Oats")),
    ) = LoggerInputs(today = today, day = day, dayBefore = dayBefore, settings = settings, recent = recent, savedMeals = LoggerFixtures.savedMeals)

    private fun build(
        inputs: LoggerInputs = inputs(),
        policy: VisibilityPolicy = policy(PresetId.Full),
        choices: LoggerChoices = LoggerChoices(slot = MealSlot.Dinner),
    ) = LoggerStateBuilder.build(inputs, policy, choices)

    private fun Display.text(): String? = when (this) {
        is Display.Shown -> text
        is Display.Soft -> text
        Display.Hidden -> null
    }

    @Test
    fun theGridInTheDesignsOrderWithEveryFlagOn() {
        val choices = LoggerChoices(
            slot = MealSlot.Dinner,
            scanAvailable = true,
            photoAvailable = true,
            packAvailable = true,
            voiceEnabled = true,
            fastingReleased = true,
        )
        val dayBefore = day(yesterday, item("Adobo", MealSlot.Dinner, day = yesterday))

        val actions = build(inputs(dayBefore = dayBefore, settings = Settings(fasting = true)), choices = choices).actions

        // The two cameras apart: the pack's label beside the scanner, the meal estimate after it.
        assertEquals(
            listOf(
                AddAction.SearchFood, AddAction.ScanBarcode, AddAction.PackagedFood, AddAction.Photo, AddAction.SayOrType(voice = true),
                AddAction.SavedMeals,
                AddAction.CopyMeal(MealSlot.Dinner), AddAction.Water, AddAction.WeighIn, AddAction.Note,
                AddAction.Fasting,
            ),
            actions,
        )
    }

    @Test
    fun withEveryFlagOffTheGridIsWhatShipped() {
        assertEquals(
            listOf(
                AddAction.SearchFood, AddAction.SayOrType(voice = false), AddAction.SavedMeals,
                AddAction.Water, AddAction.WeighIn, AddAction.Note,
            ),
            build().actions,
        )
    }

    @Test
    fun fastingNeedsTheFlagTheModeAndTheTimer() {
        val released = LoggerChoices(slot = MealSlot.Dinner, fastingReleased = true)
        assertFalse(AddAction.Fasting in build(inputs(settings = Settings(fasting = false)), choices = released).actions, "timer off")
        assertFalse(AddAction.Fasting in build(inputs(settings = Settings(fasting = true))).actions, "flag off")
        assertTrue(AddAction.Fasting in build(inputs(settings = Settings(fasting = true)), choices = released).actions)
    }

    @Test
    fun copyAMealNeedsYesterdaysMeal() {
        val dayBefore = day(yesterday, item("Adobo", MealSlot.Dinner, day = yesterday))

        assertTrue(AddAction.CopyMeal(MealSlot.Dinner) in build(inputs(dayBefore = dayBefore)).actions)
        assertTrue(build(inputs(dayBefore = dayBefore), choices = LoggerChoices(slot = MealSlot.Lunch)).actions.none { it is AddAction.CopyMeal })
    }

    @Test
    fun mealGroupsOffOffersOnlyUncategorized() {
        val state = build(inputs(settings = Settings(mealGroups = false)))

        assertEquals(listOf(MealSlot.Uncategorized), state.slots)
        assertEquals(MealSlot.Uncategorized, state.slot)
    }

    @Test
    fun theDayIsTheOneItLogsTo() {
        assertEquals(RelativeDay.Today, build().day)
        assertEquals(RelativeDay.Yesterday, build(inputs(day = Day.empty(yesterday))).day)
    }

    @Test
    fun theHeightIsWhatTheChoicesSay() {
        assertEquals(SheetHeight.Partial, build().height)
        assertEquals(SheetHeight.Full, build(choices = LoggerChoices(slot = MealSlot.Dinner, height = SheetHeight.Full)).height)
    }

    @Test
    fun recentRowsShowBareEnergyAndCompactMacros() {
        val row = build().recent.single()

        assertEquals("100", row.calories.text())
        assertEquals(listOf("5P", "10C", "2F"), row.macros.map { it.text })
        assertEquals(PickSource.Recent, row.source)
        assertFalse(row.added)
    }

    @Test
    fun recentLeavesOutEntriesWithNoFood() {
        val recent = listOf(item("Quick add", foodId = null), item("Oats"))
        assertEquals(listOf("Oats"), build(inputs(recent = recent)).recent.map { it.name })
    }

    @Test
    fun aRecentPickIsAnEstimateOfTheSameAmountAgain() {
        val pick = LoggerStateBuilder.recentPicks(inputs()).single()

        assertEquals(Precision.Estimate, pick.draft.precision, "the original was weighed; logging it again is not")
        assertEquals(LogMethod.Recent, pick.draft.method)
        assertEquals("1 cup", pick.draft.amountText)
    }

    @Test
    fun whatWentIntoThisMealSinceTheSheetOpenedIsChecked() {
        val before = day(today, item("Toast"))
        val seen = before.allItems.mapTo(HashSet()) { it.id }
        val after = day(today, item("Toast"), item("Oats", MealSlot.Dinner))

        val state = build(inputs(day = after), choices = LoggerChoices(slot = MealSlot.Dinner, seen = seen))

        assertTrue(state.recent.single { it.name == "Oats" }.added)
        assertFalse(build(inputs(day = after), choices = LoggerChoices(slot = MealSlot.Lunch, seen = seen)).recent.single().added, "another meal")
        assertFalse(build(inputs(day = after)).recent.single().added, "before the day was first read, nothing is new")
    }

    @Test
    fun blindShowsNoNumberAnywhere() {
        val blind = policy(PresetId.NoNumbers)
        val state = LoggerFixtures.sheetState(blind, height = SheetHeight.Full, added = true)

        assertTrue(state.recent.isNotEmpty())
        assertTrue(state.recent.all { it.calories == Display.Hidden && it.macros.isEmpty() })
        assertTrue(LoggerFixtures.savedState(blind).saved.meals.all { it.calories == Display.Hidden })
        val described = LoggerFixtures.describeEditedState(blind).describe
        assertTrue(described.lines.isNotEmpty())
        assertTrue(described.lines.all { it.calories == Display.Hidden })
        assertEquals(Display.Hidden, described.total?.calories)
        assertTrue(LoggerFixtures.describeFindState(blind).describe.find?.results.orEmpty().all { it.calories == Display.Hidden })
    }

    @Test
    fun softenedEnergyHidesOnARowWhileAShownMacroStays() {
        val softened = VisibilityPolicy(
            NumbersSettings(
                preset = PresetId.Full,
                overrides = mapOf(Metric.Calories to Visibility.Soften, Metric.Carbs to Visibility.Hide, Metric.Fat to Visibility.Hide),
            ),
            Clock.fixed(NOON),
            TimeZone.UTC,
        )
        val row = build(policy = softened).recent.single()

        assertEquals(Display.Hidden, row.calories, "a row has no goal, so a softened energy hides")
        assertEquals(listOf(Metric.Protein), row.macros.map { it.metric })
    }

    @Test
    fun describeLinesAndTheirTotal() {
        val state = LoggerFixtures.describeState(policy(PresetId.Full))

        assertEquals(LoggerPane.Describe, state.pane)
        assertEquals(MealSlot.Breakfast, state.slot)
        assertEquals(listOf("Egg, large", "Toast, white"), state.describe.lines.map { it.name })
        assertEquals(listOf("2 large", "1 slice"), state.describe.lines.map { it.amount })
        assertEquals(listOf("156", "80"), state.describe.lines.map { it.calories.text() })
        val eggs = state.describe.lines.first()
        assertTrue(eggs.matched)
        assertEquals(listOf("large", "cup", "grams"), eggs.units)
        assertEquals(listOf("Egg, scrambled", "Egg, fried"), eggs.alternatives)
        assertEquals(2, state.describe.total?.count, "Add writes exactly the lines")
        assertEquals("236 kcal", state.describe.total?.calories?.text())
    }

    @Test
    fun withCaloriesHiddenALineIsNeverCountedInGrams() {
        val hidden = LoggerFixtures.describeState(policy(PresetId.NoNumbers)).describe.lines.first()
        assertEquals(listOf("large", "cup"), hidden.units)
    }

    @Test
    fun aLineTheCatalogDidNotKnowHasNoAmountControls() {
        val line = LoggerFixtures.describeEditedState(policy(PresetId.Full)).describe.lines.first { it.name == "grandma's kugel" }
        assertFalse(line.matched)
        assertTrue(line.units.isEmpty())
        assertFalse(line.canLess)
    }

    @Test
    fun theFindPaneShowsTheCatalogsAnswer() {
        val find = assertNotNull(LoggerFixtures.describeFindState(policy(PresetId.Full)).describe.find)
        assertNull(find.replacing)
        assertEquals("coffee", find.query)
        assertEquals(listOf("Coffee, brewed", "Coffee, latte"), find.results?.map { it.name })
    }

    // Quick add: the fields each mode takes, and the energy check

    private fun quick(policy: VisibilityPolicy, form: QuickAddForm = LoggerFixtures.proteinBar): QuickAddUi? =
        LoggerFixtures.quickAddState(policy, form).describe.quickAdd

    @Test
    fun allNumbers_takesEveryNumber_andSpellsOutAMismatch() {
        val quick = assertNotNull(quick(policy(PresetId.Full)))
        assertEquals(listOf(Metric.Calories, Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber), quick.fields.map { it.metric })
        assertEquals(listOf("400", "30", "50", "20", "4"), quick.fields.map { it.text }, "what was typed, never reformatted")
        assertEquals("Protein bar", quick.name)
        assertEquals(QuickAddMismatch(macroKcal = Display.Shown("500 kcal"), kcal = Display.Shown("400 kcal")), quick.mismatch)
        assertTrue(quick.canAdd)
    }

    @Test
    fun energyThatAgreesWithItsMacros_saysNothing() {
        val agreeing = LoggerFixtures.proteinBar.copy(numbers = LoggerFixtures.proteinBar.numbers + (Metric.Calories to "510"))
        assertNull(quick(policy(PresetId.Full), agreeing)?.mismatch)
        // Energy alone, or macros alone, have nothing to disagree with.
        assertNull(quick(policy(PresetId.Full), QuickAddForm(numbers = mapOf(Metric.Calories to "400")))?.mismatch)
        assertNull(quick(policy(PresetId.Full), QuickAddForm(numbers = mapOf(Metric.Protein to "30")))?.mismatch)
    }

    @Test
    fun macrosWithGrams_takesTheGramsOnly_andWorksOutNoEnergyOnScreen() {
        val quick = assertNotNull(quick(policy(PresetId.MacrosWithGrams)))
        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber), quick.fields.map { it.metric })
        assertNull(quick.mismatch, "no energy field, so nothing to compare and no calorie amount to show")
        val draft = assertNotNull(QuickAdd.draft(LoggerFixtures.proteinBar, policy(PresetId.MacrosWithGrams)))
        assertEquals(500.0, draft.kcal, "stored at 4/4/9 so the totals agree with the macros")
    }

    @Test
    fun macrosOnlyAndNoNumbers_offerNoNumbers_soJustSaveTheNameRemains() {
        assertNull(quick(policy(PresetId.MacrosOnly)))
        assertNull(quick(policy(PresetId.NoNumbers)))
        assertNull(QuickAdd.draft(LoggerFixtures.proteinBar, policy(PresetId.MacrosOnly)))
        assertNull(QuickAdd.draft(LoggerFixtures.proteinBar, policy(PresetId.NoNumbers)))
    }

    @Test
    fun later_takesTheNumbers_butKeepsTheWorkedOutEnergyUntilTheReveal() {
        val later = VisibilityPolicy(NumbersSettings.later(), Clock.fixed(NOON), TimeZone.UTC)
        val before = assertNotNull(quick(later))
        assertEquals(5, before.fields.size, "the person is copying their own label, as on Add a food")
        assertNull(before.mismatch, "500 kcal is a calorie amount nobody typed")

        val revealed = VisibilityPolicy(NumbersSettings.later(), Clock.fixed(Instant.parse("2026-10-25T22:00:00Z")), TimeZone.UTC)
        assertNotNull(quick(revealed)?.mismatch)
    }

    @Test
    fun aSoftenedNumber_isNotAField() {
        val softened = VisibilityPolicy(
            NumbersSettings(preset = PresetId.Full, overrides = mapOf(Metric.Calories to Visibility.Soften)),
            Clock.fixed(NOON),
            TimeZone.UTC,
        )
        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber), quick(softened)?.fields?.map { it.metric })
    }

    @Test
    fun aNameAndNoNumber_isNotAQuickAdd() {
        assertEquals(false, quick(policy(PresetId.Full), QuickAddForm(name = "Tea"))?.canAdd)
        assertEquals(false, quick(policy(PresetId.Full), QuickAddForm(numbers = mapOf(Metric.Calories to "0")))?.canAdd)
        assertEquals(true, quick(policy(PresetId.Full), QuickAddForm(numbers = mapOf(Metric.Fiber to "3")))?.canAdd)
    }

    @Test
    fun savedMealsListTheirFoodsAndEnergy() {
        val meal = LoggerFixtures.savedState(policy(PresetId.Full)).saved.meals.first()

        assertEquals("Sunday breakfast", meal.name)
        assertEquals("Greek yogurt, Banana", meal.foods)
        assertEquals("205 kcal", meal.calories.text())
    }

    @Test
    fun shortNamesCutAtTheFirstCommaOrBracket() {
        assertEquals("Greek yogurt", shortName("Greek yogurt, plain"))
        assertEquals("Garlic fried rice", shortName("Garlic fried rice (sinangag)"))
        assertEquals("Banana", shortName("Banana"))
        assertEquals("(odd)", shortName("(odd)"), "a name that starts with a bracket keeps itself")
    }
}
