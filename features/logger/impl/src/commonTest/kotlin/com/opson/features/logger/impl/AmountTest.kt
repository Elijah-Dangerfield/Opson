package com.opson.features.logger.impl

import com.opson.libraries.core.fixed
import com.opson.libraries.diary.FoodUnit
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.AmountUnit
import com.opson.libraries.foods.FoodAmount
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.toLogDraft
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * The amount sheet (design sync 2, 3.3 and 3.3b): [AmountStateBuilder]'s cards at each level, and
 * [AmountViewModel] one action at a time. The stepper's arithmetic and unit switching are
 * `PortionAmountTest`'s in `:libraries:foods`.
 */
class AmountTest : CoroutineTest() {
    private val catalog = FakeFoodCatalog(foods = listOf(LoggerFixtures.banana, LoggerFixtures.yogurt, LoggerFixtures.arroz))
    private val customFoods = FakeCustomFoodRepository(listOf(LoggerFixtures.granola))
    private val settings = FakeSettingsRepository()
    private val policySource = FakePolicySource()
    private val basket = BasketStore()
    private val diary = FakeDiaryRepository(listOf(LoggerFixtures.day()))
    private val recentLogEvents = FakeRecentLogEvents()
    private val telemetry = RecordingTelemetry()

    private fun vm(foodId: String = "banana", custom: Boolean = false, source: PickSource = PickSource.Search) = AmountViewModel(
        AmountSheetRoute(foodId, custom, source, LoggerFixtures.today.toString(), MealSlot.Dinner),
        catalog,
        customFoods,
        settings,
        policySource,
        diary,
        FakeCalendar(),
        Clock.fixed(NOON),
        basket,
        BasketCommitter(diary, catalog, basket, recentLogEvents, telemetry, Clock.fixed(NOON)),
    )

    private fun TestScope.events(vm: AmountViewModel): List<AmountEvent> {
        val events = mutableListOf<AmountEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return events
    }

    private fun build(policy: VisibilityPolicy, choice: AmountChoice = AmountChoice(slot = MealSlot.Dinner), settings: Settings = Settings()) =
        AmountStateBuilder.build(
            AmountFood.Catalog(LoggerFixtures.arroz),
            choice,
            LoggerFixtures.amountInputs(policy, settings),
            FoodRef.Catalog("arroz"),
            PickSource.Search,
        )

    private fun Display.text(): String? = (this as? Display.Shown)?.text

    private fun withLevel(metric: Metric, level: Visibility) = VisibilityPolicy(
        NumbersSettings(PresetId.Full, overrides = mapOf(metric to level)),
        Clock.fixed(NOON),
        TimeZone.UTC,
    )

    @Test
    fun fullShowsThePortionWithEnergyMacrosAndTheirShares() {
        val state = build(policy(PresetId.Full))

        val portion = assertNotNull(state.portion)
        assertEquals("525", portion.energy.text())
        assertEquals("350 g", portion.grams)
        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat), portion.macros.map { it.metric })
        assertEquals("32 g", portion.macros.first().amount.text())
        assertEquals("24%", portion.macros.first().share.text())
        assertTrue(portion.showsSplit)
        assertEquals("Latin American", state.tag)
        assertEquals("1", state.count)
    }

    /**
     * Smoked salmon, labelled 150 kcal with 18 g protein and 9 g fat: against the label that read 48%
     * and 54%. Against the macros' own energy, 72 and 81 kcal, it is 47% and 53%, as Today's macros page says.
     */
    @Test
    fun theSharesOfEnergyAreOfTheMacrosOwnEnergySoTheyMakeAHundred() {
        val salmon = LoggerFixtures.food(id = "salmon", name = "Smoked salmon", portion = "1 pack", grams = 100.0, kcal = 150.0, protein = 18.0, carbs = 0.0, fat = 9.0)
        val food = AmountFood.Catalog(salmon)
        val state = AmountStateBuilder.build(
            food,
            AmountChoice.initial(food, MealSlot.Dinner),
            LoggerFixtures.amountInputs(policy(PresetId.Full), Settings()),
            FoodRef.Catalog("salmon"),
            PickSource.Search,
        )

        val shares = assertNotNull(state.portion).macros.associate { it.metric to it.share.text() }
        assertEquals("47%", shares[Metric.Protein])
        assertEquals("53%", shares[Metric.Fat])
    }

    /** With fat hidden, the shares are of protein and carbs alone, so the gap they leave says nothing about fat. */
    @Test
    fun aHiddenMacroIsLeftOutOfTheShares() {
        val portion = assertNotNull(build(withLevel(Metric.Fat, Visibility.Hide)).portion)

        val percents = portion.macros.mapNotNull { (it.share as? Display.Shown)?.text?.removeSuffix("%")?.toInt() }
        assertEquals(100, percents.sum(), "rounded, protein and carbs make the whole")
    }

    /** A portion has no goal for a soft word to measure against, so a softened macro hides rather than say "Plenty today". */
    @Test
    fun aSoftenedMacroHidesOnThePortion() {
        val portion = assertNotNull(build(withLevel(Metric.Protein, Visibility.Soften)).portion)

        assertEquals(listOf(Metric.Carbs, Metric.Fat), portion.macros.filter { it.metric != Metric.Fiber }.map { it.metric })
    }

    @Test
    fun theUnitMenuShowsEachUnitsWeightAndGramsHasNone() {
        val state = build(policy(PresetId.Full), settings = Settings().withUnit("f:arroz", FoodUnit("my bowl", 420.0)))

        assertEquals(listOf("plate", "cup", "my bowl", "grams"), state.units.map { it.label })
        assertEquals(listOf("350 g", "200 g", "420 g", null), state.units.map { it.grams })
        assertTrue(state.canAddUnit)
        assertEquals(listOf("½", "1", "1½", "2"), state.quick.map { it.label })
        assertEquals("1", state.quick.single { it.selected }.label)
    }

    @Test
    fun yourDayWithThisAddsTheFoodToTodaySoFar() {
        val day = assertNotNull(build(policy(PresetId.Full)).day)

        assertEquals(listOf(Metric.Calories, Metric.Protein, Metric.Carbs, Metric.Fat), day.rows.map { it.metric })
        val energy = day.rows.first()
        // Breakfast (yogurt and a banana) and lunch (kabsa) so far: 832 kcal.
        assertEquals("832", energy.before.text())
        assertEquals("525", energy.added.text())
        assertEquals("1,357 kcal", energy.after.text())
        val protein = day.rows[1]
        assertTrue(protein.after.text()!!.contains(" of "), protein.after.toString())
        assertTrue((protein.after as Display.Shown).fraction!! > (protein.before as Display.Shown).fraction!!)
    }

    @Test
    fun blindRemovesThePortionAndTheDayLeavingAmountMealTimeAndPrecision() {
        val state = build(policy(PresetId.Blind))

        assertNull(state.portion)
        assertNull(state.day)
        assertNull(state.nutrients)
        assertTrue(state.showAmount)
        assertEquals("1", state.count)
        assertEquals(listOf("350 g", "200 g", null), state.units.map { it.grams }, "a unit's weight is an amount of food, not a nutrient")
    }

    @Test
    fun aHiddenMacroLeavesThePortionAndItsDayRowAndTheSplitBar() {
        val state = build(withLevel(Metric.Carbs, Visibility.Hide))

        assertEquals(listOf(Metric.Protein, Metric.Fat), state.portion!!.macros.map { it.metric })
        assertFalse(state.portion!!.showsSplit)
        assertEquals(listOf(Metric.Calories, Metric.Protein, Metric.Fat), state.day!!.rows.map { it.metric })
    }

    @Test
    fun softenedEnergyHasNoPortionNumberButKeepsTheDaysWord() {
        val state = build(withLevel(Metric.Calories, Visibility.Soften))

        assertEquals(Display.Hidden, state.portion!!.energy)
        val energy = state.day!!.rows.first()
        assertIs<Display.Soft>(energy.after)
        assertEquals(Display.Hidden, energy.added)
    }

    @Test
    fun allNutrientsIsOnlyThereWhenMicrosAreVisible() {
        assertNull(build(withLevel(Metric.Micros, Visibility.Hide)).nutrients)
        val shown = assertNotNull(build(withLevel(Metric.Micros, Visibility.Show)).nutrients)
        assertFalse(shown.open)
    }

    @Test
    fun nameOnlyHidesTheAmountAndTheNumbers() {
        val state = build(policy(PresetId.Full), AmountChoice(precision = Precision.Named, slot = MealSlot.Dinner))

        assertFalse(state.showAmount)
        assertNull(state.portion)
        assertNull(state.day)
    }

    @Test
    fun theFoodArrivesWithItsUnitsTheMealAndNow() = runUnitTest {
        val state = vm("yogurt").state

        assertFalse(state.loading)
        assertEquals("Greek yogurt, plain", state.name)
        assertEquals(listOf("package", "grams"), state.units.map { it.label })
        assertEquals("1", state.count)
        assertEquals(Precision.Estimate, state.precision)
        assertEquals(MealSlot.Dinner, state.slot)
        assertEquals(MealSlot.Uncategorized, state.slots.last())
        assertTrue(state.timeIsNow)
        assertEquals(LocalTime(12, 0), state.time)
    }

    @Test
    fun aFoodThatCannotBeLoadedSaysSo() = runUnitTest {
        val state = vm("not-cached").state

        assertTrue(state.missing)
        assertFalse(state.loading)
    }

    @Test
    fun lessAndMoreStepByAHalf() = runUnitTest {
        val vm = vm()

        vm.takeAction(AmountAction.MoreTapped)
        assertEquals("1½", vm.state.count)
        vm.takeAction(AmountAction.LessTapped)
        vm.takeAction(AmountAction.LessTapped)
        assertEquals("½", vm.state.count)
        assertFalse(vm.state.canLess)
    }

    @Test
    fun aQuickAmountSetsTheCount() = runUnitTest {
        val vm = vm()

        vm.takeAction(AmountAction.QuickAmountPicked(2.0))

        assertEquals("2", vm.state.count)
        assertEquals("2", vm.state.quick.single { it.selected }.label)
    }

    @Test
    fun switchingUnitsKeepsTheCountAndTheGramsFollow() = runUnitTest {
        val vm = vm("arroz")
        vm.takeAction(AmountAction.MoreTapped)

        vm.takeAction(AmountAction.UnitPicked(1))

        assertEquals("1½", vm.state.count)
        assertEquals("cup", vm.state.unit?.label)
        assertEquals("300 g", vm.state.portion?.grams)
    }

    @Test
    fun switchingToGramsConvertsThroughTheWeight() = runUnitTest {
        val vm = vm()

        vm.takeAction(AmountAction.UnitPicked(1))

        assertEquals("118", vm.state.count)
        assertEquals(1, vm.state.unitIndex)
        assertTrue(vm.state.quick.isEmpty())
    }

    @Test
    fun aCustomSizeIsSavedToTheFoodAndPicked() = runUnitTest {
        val vm = vm("arroz")
        vm.takeAction(AmountAction.MoreTapped)

        vm.takeAction(AmountAction.CustomSizeTapped)
        assertNotNull(vm.state.customSize)
        vm.takeAction(AmountAction.CustomSizeTyped("my bowl", "420"))
        assertTrue(vm.state.customSize!!.canSave)
        vm.takeAction(AmountAction.CustomSizeSaved)

        assertEquals(listOf(FoodUnit("my bowl", 420.0)), settings.state.value.unitsFor("f:arroz"))
        assertNull(vm.state.customSize)
        assertEquals("my bowl", vm.state.unit?.label)
        assertEquals("1½", vm.state.count)
        assertEquals("630 g", vm.state.portion?.grams)
    }

    @Test
    fun aCustomSizeNeedsANameAndAWeight() = runUnitTest {
        val vm = vm("arroz")
        vm.takeAction(AmountAction.CustomSizeTapped)
        vm.takeAction(AmountAction.CustomSizeTyped("my bowl", ""))

        vm.takeAction(AmountAction.CustomSizeSaved)

        assertTrue(settings.state.value.foodUnits.isEmpty())
        assertNotNull(vm.state.customSize)
        vm.takeAction(AmountAction.CustomSizeDismissed)
        assertNull(vm.state.customSize)
    }

    @Test
    fun aCustomFoodCountedByServingTakesNoSizes() = runUnitTest {
        customFoods.foods.value = listOf(LoggerFixtures.riceBowl)

        val vm = vm("rice-bowl", custom = true)
        vm.takeAction(AmountAction.CustomSizeTapped)

        assertFalse(vm.state.canAddUnit)
        assertNull(vm.state.customSize)
    }

    @Test
    fun theStarSavesTheFoodToFavoritesAndTakesItOff() = runUnitTest {
        val vm = vm()

        vm.takeAction(AmountAction.FavoriteTapped)
        assertEquals(listOf("f:banana"), settings.state.value.favoriteFoods)
        assertTrue(vm.state.favorite)

        vm.takeAction(AmountAction.FavoriteTapped)
        assertTrue(settings.state.value.favoriteFoods.isEmpty())
        assertFalse(vm.state.favorite)
    }

    @Test
    fun theMealPickedIsWhereItLogs() = runUnitTest {
        val vm = vm()

        vm.takeAction(AmountAction.SlotPicked(MealSlot.Uncategorized))
        assertEquals(MealSlot.Uncategorized, vm.state.slot)
        vm.takeAction(AmountAction.AddToMealTapped)

        assertEquals(MealSlot.Uncategorized, diary.added.single().slot)
    }

    @Test
    fun aTimePickedStampsTheItem() = runUnitTest {
        val vm = vm()

        vm.takeAction(AmountAction.TimePicked(LocalTime(7, 30)))
        assertEquals(LocalTime(7, 30), vm.state.time)
        assertFalse(vm.state.timeIsNow)
        vm.takeAction(AmountAction.AddToMealTapped)

        val stamped = diary.updated.single()
        assertEquals(LocalDateTime(LoggerFixtures.today, LocalTime(7, 30)).toInstant(TimeZone.UTC), stamped.loggedAt)
    }

    @Test
    fun withoutATimeNothingIsRestamped() = runUnitTest {
        vm().takeAction(AmountAction.AddToMealTapped)

        assertTrue(diary.updated.isEmpty())
    }

    @Test
    fun allNutrientsOpensAndCloses() = runUnitTest {
        policySource.policy.value = withLevel(Metric.Micros, Visibility.Show)
        val vm = vm()

        vm.takeAction(AmountAction.AllNutrientsTapped)
        assertTrue(vm.state.nutrients!!.open)
        vm.takeAction(AmountAction.AllNutrientsTapped)
        assertFalse(vm.state.nutrients!!.open)
    }

    @Test
    fun precisionIsPicked() = runUnitTest {
        val vm = vm()

        vm.takeAction(AmountAction.PrecisionPicked(Precision.Weighed))

        assertEquals(Precision.Weighed, vm.state.precision)
    }

    @Test
    fun pickAndKeepGoingReplacesTheBasketEntryAndGoesBack() = runUnitTest {
        val banana = LoggerFixtures.banana
        basket.upsert(BasketItem("f:banana", FoodRef.Catalog("banana"), banana.toLogDraft(FoodAmount.Servings(1.0)), PickSource.Usual, banana))
        val vm = vm(source = PickSource.Usual)
        val events = events(vm)

        vm.takeAction(AmountAction.MoreTapped)
        vm.takeAction(AmountAction.PickAndKeepGoingTapped)

        val item = basket.basket.value.single()
        assertEquals("1½ medium", item.draft.amountText)
        assertEquals(157.5, item.draft.kcal!!, 0.1)
        assertEquals(LogMethod.Favorite, item.draft.method)
        assertEquals(banana, item.food)
        assertTrue(diary.added.isEmpty(), "keep going logs nothing yet")
        assertEquals(listOf<AmountEvent>(AmountEvent.Dismiss), events)
    }

    @Test
    fun addToMealPicksThenLogsTheWholeBasket() = runUnitTest {
        basket.upsert(LoggerFixtures.pickedBasket().first())
        val vm = vm()
        val events = events(vm)

        vm.takeAction(AmountAction.AddToMealTapped)

        val added = diary.added.single()
        assertEquals(MealSlot.Dinner, added.slot)
        assertEquals(listOf("Arroz con pollo", "Banana"), added.items.map { it.name })
        assertTrue(basket.basket.value.isEmpty())
        val logged = recentLogEvents.logged.single()
        val event = assertIs<AmountEvent.Logged>(events.single())
        assertEquals(LoggerFixtures.today, event.day)
        assertEquals(Committed(MealSlot.Dinner, logged.itemIds), event.committed)
    }

    /** The sheet animates down after Add; a second tap in that time is queued behind the first and must log nothing. */
    @Test
    fun aSecondTapOnAddWhileTheSheetClosesLogsNothingMore() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(AmountAction.AddToMealTapped)
        vm.takeAction(AmountAction.AddToMealTapped)
        vm.takeAction(AmountAction.PickAndKeepGoingTapped)

        assertEquals(1, diary.added.size)
        assertTrue(basket.basket.value.isEmpty(), "the food is not put back in the basket the add just cleared")
        assertEquals(1, events.size)
    }

    @Test
    fun editingADescribedLineReplacesThatLine() = runUnitTest {
        val line = LoggerFixtures.parsed.first().toDescribedItem(0).copy(ref = FoodRef.Catalog("banana"), key = "f:banana")
        basket.upsert(line)
        val vm = vm(source = PickSource.Describe)

        vm.takeAction(AmountAction.PickAndKeepGoingTapped)

        val item = basket.basket.value.single()
        assertEquals(PickSource.Describe, item.source)
        assertEquals(LogMethod.Describe, item.draft.method)
    }

    @Test
    fun reopeningAPickedFoodStartsFromThePickAndItsTime() = runUnitTest {
        val ref = FoodRef.Catalog("banana")
        val choice = AmountChoice(Precision.Weighed, unit = AmountUnit.Grams, quantity = 150.0, slot = MealSlot.Dinner)
        val draft = AmountFood.Catalog(LoggerFixtures.banana).draftFor(choice, PickSource.Search, ref)
        val at = LocalDateTime(LoggerFixtures.today, LocalTime(8, 15)).toInstant(TimeZone.UTC)
        basket.upsert(BasketItem(key = "f:banana", ref = ref, draft = draft, source = PickSource.Search, loggedAt = at))

        val state = vm().state

        assertEquals("150", state.count)
        assertEquals(1, state.unitIndex)
        assertEquals(Precision.Weighed, state.precision)
        assertEquals(LocalTime(8, 15), state.time)
    }

    @Test
    fun aNameOnlyPickLogsNoNumbers() = runUnitTest {
        val vm = vm()

        vm.takeAction(AmountAction.PrecisionPicked(Precision.Named))
        vm.takeAction(AmountAction.PickAndKeepGoingTapped)

        val draft = basket.basket.value.single().draft
        assertEquals(Precision.Named, draft.precision)
        assertNull(draft.kcal)
        assertNull(draft.amountText)
        assertEquals("banana", draft.foodId)
    }

    @Test
    fun aCustomFoodStepsInItsOwnUnit() = runUnitTest {
        val vm = vm("granola", custom = true)

        assertEquals(listOf("grams"), vm.state.units.map { it.label })
        assertEquals("45", vm.state.count)
        vm.takeAction(AmountAction.MoreTapped)
        assertEquals("55", vm.state.count)
    }

    @Test
    fun backLeavesTheBasketAlone() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(AmountAction.BackTapped)

        assertTrue(basket.basket.value.isEmpty())
        assertEquals(listOf<AmountEvent>(AmountEvent.Dismiss), events)
    }
}
