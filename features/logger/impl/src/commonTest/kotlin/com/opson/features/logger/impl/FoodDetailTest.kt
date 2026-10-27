package com.opson.features.logger.impl

import com.opson.libraries.core.fixed
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.FoodUnit
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.MicroGroup
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
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
 * The food detail (design sync 4, 13.4d): [FoodDetailStateBuilder] at each level, and
 * [FoodDetailViewModel] one action at a time, above all the how-sure rule: typing an amount
 * turns Estimate into Weighed, a chip or the unit menu does not, and a pick the person made stays.
 * Unit conversion itself is `PortionAmountTest`'s in `:libraries:foods`.
 */
class FoodDetailTest : CoroutineTest() {
    private val catalog = FakeFoodCatalog(
        foods = listOf(LoggerFixtures.banana, LoggerFixtures.yogurt, LoggerFixtures.arroz, LoggerFixtures.peanutButter, LoggerFixtures.rice),
    )
    private val customFoods = FakeCustomFoodRepository(listOf(LoggerFixtures.granola))
    private val settings = FakeSettingsRepository()
    private val policySource = FakePolicySource()
    private val diary = FakeDiaryRepository(listOf(LoggerFixtures.day()))
    private val recentLogEvents = FakeRecentLogEvents()
    private val telemetry = RecordingTelemetry()

    private fun vm(foodId: String = "peanut-butter", custom: Boolean = false, source: PickSource = PickSource.Search, slot: MealSlot? = MealSlot.Dinner) =
        FoodDetailViewModel(
            route = FoodDetailRoute(foodId, custom, source, LoggerFixtures.today.toString(), slot),
            foodCatalog = catalog,
            customFoods = customFoods,
            settingsRepository = settings,
            policySource = policySource,
            diary = diary,
            calendar = FakeCalendar(),
            selectedDay = FakeSelectedDay(),
            clock = Clock.fixed(NOON),
            logger = FoodLogger(diary, catalog, recentLogEvents, telemetry, Clock.fixed(NOON)),
        )

    private fun TestScope.events(vm: FoodDetailViewModel): List<FoodDetailEvent> {
        val events = mutableListOf<FoodDetailEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return events
    }

    private fun build(policy: VisibilityPolicy, choice: DetailChoice? = null) = LoggerFixtures.peanutButter.let { food ->
        val amountFood = AmountFood.Catalog(food)
        FoodDetailStateBuilder.build(
            amountFood,
            choice ?: DetailChoice.initial(amountFood, MealSlot.Dinner),
            LoggerFixtures.detailInputs(policy),
            FoodRef.Catalog(food.id),
            PickSource.Search,
        )
    }

    private fun Display.text(): String? = (this as? Display.Shown)?.text

    private fun withLevel(metric: Metric, level: Visibility) = withLevelOf(PresetId.Full, metric, level)

    private fun withLevelOf(preset: PresetId, metric: Metric, level: Visibility) = VisibilityPolicy(
        NumbersSettings(preset, overrides = mapOf(metric to level)),
        Clock.fixed(NOON),
        TimeZone.UTC,
    )

    // The builder

    @Test
    fun theDesignsDetailTypedInGrams() {
        val state = LoggerFixtures.detailState(policy(PresetId.Full))

        assertEquals("32", state.amount)
        assertEquals("grams", state.unit?.label)
        assertEquals(Precision.Weighed, state.precision, "typed, so weighed")
        assertEquals(listOf("1 tbsp", "2 tbsp", "100 g"), state.servings.map { it.label })
        assertEquals(listOf("16 g", "32 g", null), state.servings.map { it.grams })
        assertTrue(state.servings.none { it.selected }, "32 g typed in grams is not 2 tbsp picked")
        val energy = assertNotNull(state.energy)
        assertEquals("190", energy.energy.text())
        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat), energy.macros.map { it.metric })
        assertEquals(listOf("8 g", "7 g", "16 g"), energy.macros.map { it.amount.text() })
        assertTrue(state.canAdd)
    }

    @Test
    fun todayWithThisAddsTheFoodToTheDaySoFar() {
        // The fixture day holds 100 + 105 + 627 kcal; peanut butter's tablespoon is 95.
        val today = assertNotNull(build(policy(PresetId.Full)).energy?.today)

        assertEquals("927 kcal", today.total.text())
        assertEquals("927", today.bare.text())
        // Four estimates at 25%: σ of 25, 26, 157 and 24 kcal, √Σσ² rounded to 10.
        assertEquals("160", today.plusMinus.text())
    }

    @Test
    fun theFoodsOwnHowSureMovesTheDaysPlusMinus() {
        val food = AmountFood.Catalog(LoggerFixtures.peanutButter)
        val inputs = LoggerFixtures.detailInputs(policy(PresetId.Full)).copy(day = Day.empty(LoggerFixtures.today))
        fun plusMinus(precision: Precision) = FoodDetailStateBuilder.build(
            food,
            DetailChoice.initial(food, MealSlot.Dinner).copy(precision = precision),
            inputs,
            FoodRef.Catalog(food.food.id),
            PickSource.Search,
        ).energy?.today?.plusMinus

        assertEquals("20", plusMinus(Precision.Estimate)?.text(), "95 kcal at 25%")
        assertEquals("10", plusMinus(Precision.Label)?.text(), "at 10%")
        assertEquals(Display.Hidden, plusMinus(Precision.Weighed), "at 5% it rounds to nothing, so no ±")
    }

    @Test
    fun blindHasNoPlusMinusEither() {
        assertNull(build(policy(PresetId.NoNumbers)).energy)
        val soft = build(withLevel(Metric.Calories, Visibility.Soften)).energy?.today
        assertEquals(Display.Hidden, soft?.plusMinus)
    }

    @Test
    fun noNumbersIsTheAmountInMeasuresAndTheHungerCheck_nothingElse() {
        val state = LoggerFixtures.noNumbersDetailState(policy(PresetId.NoNumbers))

        assertTrue(state.noNumbers)
        assertEquals("2", state.amount)
        assertEquals(listOf("tbsp"), state.units.map { it.label }, "grams is a number, so the menu holds measures")
        assertTrue(state.units.all { it.grams == null })
        assertEquals(listOf("1 tbsp", "2 tbsp"), state.servings.map { it.label })
        assertTrue(state.servings.all { it.grams == null })
        assertEquals(HungerCheck(Hunger.Neutral), state.hunger)
        assertNull(state.energy)
        assertNull(state.nutrients)
        assertEquals(null, state.source?.per)
        assertFalse(state.canAddUnit)
        assertFalse(build(policy(PresetId.Full)).noNumbers, "Full numbers keeps its own detail")
    }

    @Test
    fun noNumbersHungerCheckFollowsTheHungerQuestionsSetting() {
        val food = AmountFood.Catalog(LoggerFixtures.peanutButter)
        val state = FoodDetailStateBuilder.build(
            food,
            DetailChoice.initial(food, MealSlot.Snack),
            LoggerFixtures.detailInputs(policy(PresetId.NoNumbers), Settings(askTags = false)),
            FoodRef.Catalog(food.food.id),
            PickSource.Search,
        )
        assertNull(state.hunger)
    }

    @Test
    fun noNumbersWithANumberTurnedBackOnGetsTheFullDetail() {
        assertFalse(build(withLevelOf(PresetId.NoNumbers, Metric.Calories, Visibility.Show)).noNumbers)
    }

    @Test
    fun inNoNumbersTypingCountsTheAmountAndLeavesHowSureAlone() = runUnitTest {
        policySource.policy.value = policy(PresetId.NoNumbers)
        val vm = vm()

        vm.takeAction(FoodDetailAction.AmountTyped("2,5"))
        assertEquals("2.5", vm.state.amount)
        vm.takeAction(FoodDetailAction.AddToMealTapped)

        val item = diary.added.single().items.single()
        assertEquals(2.5, item.quantity)
        assertEquals(Precision.Estimate, item.precision)
    }

    @Test
    fun aHungerAnswerIsSavedToTheMealWhenTheFoodIsAdded() = runUnitTest {
        policySource.policy.value = policy(PresetId.NoNumbers)
        settings.state.value = Settings(askTags = true)
        val vm = vm()

        vm.takeAction(FoodDetailAction.HungerPicked(Hunger.Hungry))
        vm.takeAction(FoodDetailAction.HungerPicked(Hunger.Neutral))
        assertEquals(Hunger.Neutral, vm.state.hunger?.picked)
        vm.takeAction(FoodDetailAction.HungerPicked(Hunger.Neutral))
        assertEquals(null, vm.state.hunger?.picked, "the same answer again clears it")
        vm.takeAction(FoodDetailAction.HungerPicked(Hunger.Neutral))
        vm.takeAction(FoodDetailAction.AddToMealTapped)

        val (_, slot, feeling) = diary.feelings.single()
        assertEquals(MealSlot.Dinner, slot)
        assertEquals(Hunger.Neutral, feeling?.hunger)
    }

    @Test
    fun noHungerAnswerSavesNoFeeling() = runUnitTest {
        policySource.policy.value = policy(PresetId.NoNumbers)
        vm().takeAction(FoodDetailAction.AddToMealTapped)

        assertTrue(diary.feelings.isEmpty())
    }

    @Test
    fun inNoNumbersALastAmountInGramsStartsFromAMeasure() = runUnitTest {
        policySource.policy.value = policy(PresetId.NoNumbers)
        diary.recent.value = listOf(
            LoggerFixtures.item(LoggerFixtures.peanutButter, MealSlot.Snack).copy(quantity = 32.0, unit = "grams", amountText = "32 g"),
        )

        val state = vm().state

        assertEquals("1", state.amount)
        assertEquals("tbsp", state.unit?.label)
    }

    @Test
    fun aHiddenMacroLeavesItsTile() {
        val energy = assertNotNull(build(withLevel(Metric.Fat, Visibility.Hide)).energy)

        assertEquals(listOf(Metric.Protein, Metric.Carbs), energy.macros.map { it.metric })
    }

    @Test
    fun aSoftenedMacroHidesOnThePortion() {
        val energy = assertNotNull(build(withLevel(Metric.Protein, Visibility.Soften)).energy)

        assertEquals(listOf(Metric.Carbs, Metric.Fat), energy.macros.map { it.metric })
    }

    @Test
    fun softenedEnergyHasNoNumberButTodayKeepsItsWord() {
        val energy = assertNotNull(build(withLevel(Metric.Calories, Visibility.Soften)).energy)

        assertEquals(Display.Hidden, energy.energy)
        assertIs<Display.Soft>(energy.today?.total)
    }

    @Test
    fun theUnitMenuShowsEachUnitsWeightAndGramsHasNone() {
        val state = LoggerFixtures.detailUnitsState(policy(PresetId.Full))

        assertEquals(listOf("plate", "cup", "serving", "oz", "grams"), state.units.map { it.label })
        assertEquals(listOf("350 g", "200 g", "300 g", "28 g", null), state.units.map { it.grams })
        assertTrue(state.canAddUnit)
        assertEquals("1 plate", state.servings.first().label)
        assertTrue(state.servings.first().selected)
    }

    @Test
    fun allNutrientsIsOnlyThereWhenMicrosAreVisible() {
        assertNull(build(withLevel(Metric.Micros, Visibility.Hide)).nutrients)
        val shown = assertNotNull(build(withLevel(Metric.Micros, Visibility.Show)).nutrients)
        assertFalse(shown.open)
        assertEquals(listOf(MicroGroup.Vitamins, MicroGroup.Minerals), shown.groups.map { it.group })
        assertFalse(shown.groups.flatMap { it.rows }.any { it.micro == Micronutrient.VitaminD }, "unknown is not 0%")
    }

    @Test
    fun aFoodWithNoMicroDataHasNoAllNutrients() {
        val rice = AmountFood.Catalog(LoggerFixtures.rice)
        val state = FoodDetailStateBuilder.build(
            rice,
            DetailChoice.initial(rice, MealSlot.Dinner),
            LoggerFixtures.detailInputs(policy(PresetId.Full)),
            FoodRef.Catalog("rice"),
            PickSource.Search,
        )

        assertNull(state.nutrients)
    }

    @Test
    fun aTypedFieldNeverReadsAsAFraction() {
        assertEquals("1.5", FoodDetailStateBuilder.number(1.5))
        assertEquals("32", FoodDetailStateBuilder.number(32.0))
        assertEquals("0.25", FoodDetailStateBuilder.number(0.25))
    }

    // How sure

    @Test
    fun typingAnAmountTurnsEstimateIntoWeighed() = runUnitTest {
        val vm = vm()
        assertEquals(Precision.Estimate, vm.state.precision)

        vm.takeAction(FoodDetailAction.AmountTyped("3a2"))

        assertEquals("32", vm.state.amount)
        assertEquals(Precision.Weighed, vm.state.precision)
    }

    @Test
    fun aChipOrTheUnitMenuIsNotTyping() = runUnitTest {
        val vm = vm()

        vm.takeAction(FoodDetailAction.ServingPicked(1))
        assertEquals("2", vm.state.amount)
        assertEquals("tbsp", vm.state.unit?.label)
        assertTrue(vm.state.servings[1].selected)
        vm.takeAction(FoodDetailAction.UnitPicked(vm.state.units.indexOfFirst { it.label == "grams" }))
        assertEquals("32", vm.state.amount, "two tablespoons are 32 g")

        assertEquals(Precision.Estimate, vm.state.precision)
    }

    @Test
    fun aHowSureThePersonPickedStaysWhenTheyType() = runUnitTest {
        val vm = vm()

        vm.takeAction(FoodDetailAction.PrecisionPicked(Precision.Label))
        vm.takeAction(FoodDetailAction.AmountTyped("2"))
        assertEquals(Precision.Label, vm.state.precision)

        vm.takeAction(FoodDetailAction.PrecisionPicked(Precision.Estimate))
        vm.takeAction(FoodDetailAction.AmountTyped("3"))
        assertEquals(Precision.Estimate, vm.state.precision, "even a pick of Estimate itself sticks")
    }

    @Test
    fun anEmptyFieldCannotBeAdded() = runUnitTest {
        val vm = vm()

        vm.takeAction(FoodDetailAction.AmountTyped(""))
        assertFalse(vm.state.canAdd)
        vm.takeAction(FoodDetailAction.AddToMealTapped)

        assertTrue(diary.added.isEmpty())
    }

    // The view model

    @Test
    fun theFoodArrivesWithItsUnitsTheMealAndNow() = runUnitTest {
        val state = vm("yogurt").state

        assertFalse(state.loading)
        assertEquals("Greek yogurt, plain", state.name)
        assertEquals(listOf("package", "grams"), state.units.map { it.label })
        assertEquals("1", state.amount)
        assertEquals(Precision.Estimate, state.precision)
        assertEquals(MealSlot.Dinner, state.slot)
        assertEquals(MealSlot.Uncategorized, state.slots.last())
        assertTrue(state.timeIsNow)
        assertEquals(LocalTime(12, 0), state.time)
        assertEquals(FoodDetailStateBuilder.PRECISIONS, listOf(Precision.Estimate, Precision.Weighed, Precision.Label))
    }

    @Test
    fun itOpensAtTheLastAmountThisPersonLogged() = runUnitTest {
        diary.recent.value = listOf(
            LoggerFixtures.item(LoggerFixtures.peanutButter, MealSlot.Snack, LoggerFixtures.today.minus(1, DateTimeUnit.DAY))
                .copy(quantity = 2.0, unit = "tbsp", amountText = "2 tbsp", precision = Precision.Weighed),
        )

        val state = vm().state

        assertEquals("2", state.amount)
        assertEquals("tbsp", state.unit?.label)
        assertEquals(Precision.Estimate, state.precision, "an amount again is an estimate")
    }

    @Test
    fun withNoMealTheClockPicksOne() = runUnitTest {
        assertEquals(MealSlot.Lunch, vm(slot = null).state.slot)
    }

    @Test
    fun aFoodThatCannotBeLoadedSaysSo() = runUnitTest {
        val state = vm("not-cached").state

        assertTrue(state.missing)
        assertFalse(state.loading)
    }

    @Test
    fun addLogsThisOneFoodAtTheAmountAndHowSure() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        vm.takeAction(FoodDetailAction.UnitPicked(vm.state.units.indexOfFirst { it.label == "grams" }))
        vm.takeAction(FoodDetailAction.AmountTyped("32"))

        vm.takeAction(FoodDetailAction.AddToMealTapped)
        vm.takeAction(FoodDetailAction.AddToMealTapped)
        runCurrent()

        val added = diary.added.single()
        assertEquals(MealSlot.Dinner, added.slot)
        val item = added.items.single()
        assertEquals("Peanut butter", item.name)
        assertEquals(32.0, item.grams)
        assertEquals(190.0, item.kcal ?: 0.0, 0.5)
        assertEquals(Precision.Weighed, item.precision)
        assertEquals(LogMethod.Search, item.method)
        assertEquals(listOf(LoggerFixtures.peanutButter), catalog.marked)
        val logged = assertIs<FoodDetailEvent.Logged>(events.single())
        assertEquals(recentLogEvents.logged.single(), logged.committed.event)
    }

    @Test
    fun fromLabelIsLoggedAsLabel() = runUnitTest {
        val vm = vm(source = PickSource.Barcode)
        vm.takeAction(FoodDetailAction.PrecisionPicked(Precision.Label))

        vm.takeAction(FoodDetailAction.AddToMealTapped)

        val item = diary.added.single().items.single()
        assertEquals(Precision.Label, item.precision)
        assertEquals(LogMethod.Barcode, item.method)
    }

    @Test
    fun aCustomSizeIsSavedToTheFoodAndPicked() = runUnitTest {
        val vm = vm("arroz")
        vm.takeAction(FoodDetailAction.ServingPicked(1))

        vm.takeAction(FoodDetailAction.CustomSizeTapped)
        assertNotNull(vm.state.customSize)
        vm.takeAction(FoodDetailAction.CustomSizeTyped("my bowl", "420"))
        assertTrue(vm.state.customSize!!.canSave)
        vm.takeAction(FoodDetailAction.CustomSizeSaved)

        assertEquals(listOf(FoodUnit("my bowl", 420.0)), settings.state.value.unitsFor("f:arroz"))
        assertNull(vm.state.customSize)
        assertEquals("my bowl", vm.state.unit?.label)
        assertEquals("2", vm.state.amount)
    }

    @Test
    fun aCustomSizeNeedsANameAndAWeight() = runUnitTest {
        val vm = vm("arroz")
        vm.takeAction(FoodDetailAction.CustomSizeTapped)
        vm.takeAction(FoodDetailAction.CustomSizeTyped("my bowl", ""))

        vm.takeAction(FoodDetailAction.CustomSizeSaved)

        assertTrue(settings.state.value.foodUnits.isEmpty())
        assertNotNull(vm.state.customSize)
        vm.takeAction(FoodDetailAction.CustomSizeDismissed)
        assertNull(vm.state.customSize)
    }

    @Test
    fun aCustomFoodCountedByServingTakesNoSizes() = runUnitTest {
        customFoods.foods.value = listOf(LoggerFixtures.riceBowl)

        val vm = vm("rice-bowl", custom = true)
        vm.takeAction(FoodDetailAction.CustomSizeTapped)

        assertFalse(vm.state.canAddUnit)
        assertNull(vm.state.customSize)
        assertEquals(listOf("1 bowl", "2 bowls"), vm.state.servings.map { it.label })
    }

    @Test
    fun theFavoriteSavesTheFoodAndTakesItOff() = runUnitTest {
        val vm = vm()

        vm.takeAction(FoodDetailAction.FavoriteTapped)
        assertEquals(listOf("f:peanut-butter"), settings.state.value.favoriteFoods)
        assertTrue(vm.state.favorite)

        vm.takeAction(FoodDetailAction.FavoriteTapped)
        assertTrue(settings.state.value.favoriteFoods.isEmpty())
        assertFalse(vm.state.favorite)
    }

    @Test
    fun theMealPickedIsWhereItLogs() = runUnitTest {
        val vm = vm()

        vm.takeAction(FoodDetailAction.SlotPicked(MealSlot.Uncategorized))
        assertEquals(MealSlot.Uncategorized, vm.state.slot)
        vm.takeAction(FoodDetailAction.AddToMealTapped)

        assertEquals(MealSlot.Uncategorized, diary.added.single().slot)
    }

    @Test
    fun withMealGroupsOffItLogsToUncategorized() = runUnitTest {
        settings.state.value = Settings(mealGroups = false)
        val vm = vm()

        assertEquals(MealSlot.Uncategorized, vm.state.slot)
        vm.takeAction(FoodDetailAction.AddToMealTapped)

        assertEquals(MealSlot.Uncategorized, diary.added.single().slot)
    }

    @Test
    fun aTimePickedStampsTheItem() = runUnitTest {
        val vm = vm()

        vm.takeAction(FoodDetailAction.TimePicked(LocalTime(7, 30)))
        assertEquals(LocalTime(7, 30), vm.state.time)
        assertFalse(vm.state.timeIsNow)
        vm.takeAction(FoodDetailAction.AddToMealTapped)

        val stamped = diary.updated.single()
        assertEquals(LocalDateTime(LoggerFixtures.today, LocalTime(7, 30)).toInstant(TimeZone.UTC), stamped.loggedAt)
    }

    @Test
    fun withoutATimeNothingIsRestamped() = runUnitTest {
        vm().takeAction(FoodDetailAction.AddToMealTapped)

        assertTrue(diary.updated.isEmpty())
    }

    @Test
    fun allNutrientsOpensAndCloses() = runUnitTest {
        policySource.policy.value = withLevel(Metric.Micros, Visibility.Show)
        val vm = vm()

        vm.takeAction(FoodDetailAction.AllNutrientsTapped)
        assertTrue(vm.state.nutrients!!.open)
        vm.takeAction(FoodDetailAction.AllNutrientsTapped)
        assertFalse(vm.state.nutrients!!.open)
    }

    @Test
    fun backLeavesWithoutLogging() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(FoodDetailAction.BackTapped)
        runCurrent()

        assertTrue(diary.added.isEmpty())
        assertEquals(listOf<FoodDetailEvent>(FoodDetailEvent.Dismiss), events)
    }
}
