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
 * The food detail: [FoodDetailStateBuilder] in each mode, and [FoodDetailViewModel] one action at
 * a time, above all the how-sure rule: an amount typed in a weight or volume is Weighed, a count
 * of a household measure, a chip or the unit menu is not, a scanned label is From label, and a
 * pick the person made stays. Unit conversion itself is `PortionAmountTest`'s in `:libraries:foods`.
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
        assertEquals(Precision.Weighed, state.precision, "typed in grams, so weighed")
        assertEquals(listOf("1 tbsp", "2 tbsp", "100 g"), state.servings.map { it.label }, "no weight beside a quick amount")
        assertTrue(state.servings.none { it.selected }, "32 g typed in grams is not 2 tbsp picked")
        assertNull(state.weight, "grams already say what it weighs")
        val breakdown = assertNotNull(state.breakdown)
        assertEquals("190", breakdown.energy.text())
        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat), breakdown.macros.map { it.metric })
        assertEquals(listOf("8 g", "7 g", "16 g"), breakdown.macros.map { it.amount.text() })
        assertTrue(state.canAdd)
    }

    @Test
    fun eachMacrosShareIsOfTheEnergyOfTheMacrosOnShow() {
        val shares = assertNotNull(build(policy(PresetId.Full)).breakdown).macros
        // 4 g protein, 3.5 g carbs, 8 g fat a tablespoon: 16, 14 and 72 kcal.
        assertEquals(listOf("16%", "14%", "71%"), shares.map { it.share.text() })
        assertEquals(100f, shares.sumOf { it.weight.toDouble() }.toFloat(), 0.01f)

        val noFat = assertNotNull(build(withLevel(Metric.Fat, Visibility.Hide)).breakdown).macros
        assertEquals(listOf("53%", "47%"), noFat.map { it.share.text() }, "a hidden macro cannot be read off the gap")
    }

    @Test
    fun theWeightOfAHouseholdAmountIsSaidOnce() {
        val state = build(policy(PresetId.Full), DetailChoice.initial(AmountFood.Catalog(LoggerFixtures.peanutButter), MealSlot.Dinner).copy(quantity = 2.0))

        assertEquals("tbsp", state.unit?.label)
        assertEquals("32 g", state.weight)
        assertEquals("16 g", state.units.first().grams, "the menu gives each unit's weight")
    }

    @Test
    fun todayWithThisSetsTheFoodAgainstEachTarget() {
        val rows = assertNotNull(build(policy(PresetId.Full)).targets).rows

        assertEquals(listOf(Metric.Calories, Metric.Protein, Metric.Carbs, Metric.Fat), rows.map { it.metric })
        val protein = rows[1]
        assertTrue(protein.total.text().orEmpty().matches(Regex("""\d+ / \d+ g""")), "${protein.total}")
        assertTrue(protein.adds.text().orEmpty().endsWith("%"))
        assertTrue(protein.food > 0f && protein.soFar > 0f)
    }

    @Test
    fun todayWithThisAddsTheFoodToTheDaySoFar() {
        // The fixture day holds 100 + 105 + 627 kcal; peanut butter's tablespoon is 95.
        val today = assertNotNull(build(policy(PresetId.Full)).targets?.rows?.first { it.metric == Metric.Calories })

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
            DetailChoice.initial(food, MealSlot.Dinner).copy(picked = precision),
            inputs,
            FoodRef.Catalog(food.food.id),
            PickSource.Search,
        ).targets?.rows?.first()?.plusMinus

        assertEquals("20", plusMinus(Precision.Estimate)?.text(), "95 kcal at 25%")
        assertEquals("10", plusMinus(Precision.Label)?.text(), "at 10%")
        assertEquals(Display.Hidden, plusMinus(Precision.Weighed), "at 5% it rounds to nothing, so no ±")
    }

    @Test
    fun blindHasNoPlusMinusEither() {
        assertNull(build(policy(PresetId.NoNumbers)).breakdown)
        assertNull(build(policy(PresetId.NoNumbers)).targets)
        val soft = build(withLevel(Metric.Calories, Visibility.Soften)).targets?.rows?.first { it.metric == Metric.Calories }
        assertEquals(Display.Hidden, soft?.plusMinus)
        assertEquals(Display.Hidden, soft?.adds)
    }

    @Test
    fun noNumbersIsTheAmountInMeasuresAndTheHungerCheck_nothingElse() {
        val state = LoggerFixtures.noNumbersDetailState(policy(PresetId.NoNumbers))

        assertTrue(state.noNumbers)
        assertEquals("2", state.amount)
        assertEquals(listOf("tbsp"), state.units.map { it.label }, "grams is a number, so the menu holds measures")
        assertTrue(state.units.all { it.grams == null })
        assertEquals(listOf("1 tbsp", "2 tbsp"), state.servings.map { it.label })
        assertNull(state.weight, "a weight is a number too")
        assertEquals(HungerCheck(Hunger.Neutral), state.hunger)
        assertNull(state.breakdown)
        assertNull(state.targets)
        assertNull(state.nutrients)
        assertEquals(SourceLine(FoodOrigin.Usda, nutrientCount = null), state.source)
        assertFalse(state.canAddUnit)
        assertFalse(build(policy(PresetId.Full)).noNumbers, "All numbers keeps its own detail")
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
    fun aHiddenMacroLeavesItsLine() {
        val breakdown = assertNotNull(build(withLevel(Metric.Fat, Visibility.Hide)).breakdown)

        assertEquals(listOf(Metric.Protein, Metric.Carbs), breakdown.macros.map { it.metric })
        assertFalse(assertNotNull(build(withLevel(Metric.Fat, Visibility.Hide)).targets).rows.any { it.metric == Metric.Fat })
    }

    @Test
    fun aSoftenedMacroHidesOnThePortionButIsAWordForToday() {
        val state = build(withLevel(Metric.Protein, Visibility.Soften))

        assertEquals(listOf(Metric.Carbs, Metric.Fat), assertNotNull(state.breakdown).macros.map { it.metric })
        val protein = assertNotNull(state.targets).rows.first { it.metric == Metric.Protein }
        assertIs<Display.Soft>(protein.total)
        assertEquals(Display.Hidden, protein.adds, "a share of a target is an amount in disguise")
    }

    @Test
    fun softenedEnergyHasNoNumberButTodayKeepsItsWord() {
        val state = build(withLevel(Metric.Calories, Visibility.Soften))

        assertEquals(Display.Hidden, assertNotNull(state.breakdown).energy)
        assertIs<Display.Soft>(state.targets?.rows?.first { it.metric == Metric.Calories }?.total)
    }

    @Test
    fun macrosWithGramsShowsNoCaloriesAnywhere() {
        val state = build(policy(PresetId.MacrosWithGrams))

        val breakdown = assertNotNull(state.breakdown)
        assertEquals(Display.Hidden, breakdown.energy)
        assertEquals(listOf("16%", "14%", "71%"), breakdown.macros.map { it.share.text() })
        val rows = assertNotNull(state.targets).rows
        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat), rows.map { it.metric })
        assertTrue(rows.all { it.bare == Display.Hidden && it.plusMinus == Display.Hidden })
    }

    @Test
    fun macrosOnlyIsWordsAndBarsForToday_andNothingForThePortion() {
        val state = build(policy(PresetId.MacrosOnly))

        assertNull(state.breakdown, "a portion has no goal to soften against")
        val rows = assertNotNull(state.targets).rows
        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat), rows.map { it.metric })
        assertTrue(rows.all { it.total is Display.Soft && it.adds == Display.Hidden })
        assertNull(state.nutrients)
        assertNull(state.source?.nutrientCount, "no count where All nutrients is not offered")
    }

    @Test
    fun theSourceSaysHowManyNutrientsItLists() {
        val state = build(policy(PresetId.Full))

        assertEquals(FoodOrigin.Usda, state.source?.source)
        // Energy, protein, carbs and fat (the source has no fiber for it), and five vitamins and minerals.
        assertEquals(9, state.source?.nutrientCount)
    }

    @Test
    fun theUnitMenuShowsEachUnitsWeightAndWeightsHaveNone() {
        val state = LoggerFixtures.detailUnitsState(policy(PresetId.Full))

        assertEquals(listOf("plate", "cup", "serving", "oz", "grams", "lb"), state.units.map { it.label }, "a size named oz stands for ounces")
        assertEquals(listOf("350 g", "200 g", "300 g", "28 g", null, null), state.units.map { it.grams })
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
        val vitaminD = shown.groups.flatMap { it.rows }.single { it.micro == Micronutrient.VitaminD }
        assertTrue(vitaminD.notListed, "unknown is not 0%")
        assertEquals(Display.Hidden, vitaminD.display)
        assertEquals(Micronutrient.entries.size, shown.groups.sumOf { it.rows.size }, "every one, listed or not")
    }

    /** A food that lists a nutrient as 0 has none of it: that is a 0%, not "Not listed". */
    @Test
    fun aListedZero_isZero_notNotListed() {
        val food = AmountFood.Catalog(LoggerFixtures.food("test", "Test", "1 cup", 100.0, 100.0, 1.0, 1.0, 1.0, micros = mapOf("vitamin_d_ug" to 0.0, "iron_mg" to 1.8)))
        val state = FoodDetailStateBuilder.build(
            food,
            DetailChoice.initial(food, MealSlot.Dinner),
            LoggerFixtures.detailInputs(policy(PresetId.Full)),
            FoodRef.Catalog("test"),
            PickSource.Search,
        )
        val rows = assertNotNull(state.nutrients).groups.flatMap { it.rows }.associateBy { it.micro }
        assertEquals("0%", assertIs<Display.Shown>(rows.getValue(Micronutrient.VitaminD).display).text)
        assertFalse(rows.getValue(Micronutrient.VitaminD).notListed)
        assertTrue(rows.getValue(Micronutrient.Zinc).notListed)
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
    fun anAmountTypedInAWeightIsWeighed_aCountOfSpoonsIsNot() = runUnitTest {
        val vm = vm()
        assertEquals(Precision.Estimate, vm.state.precision)

        vm.takeAction(FoodDetailAction.AmountTyped("3"))
        assertEquals("tbsp", vm.state.unit?.label)
        assertEquals(Precision.Estimate, vm.state.precision, "three spoons is a count, not a measurement")

        vm.takeAction(FoodDetailAction.UnitPicked(vm.state.units.indexOfFirst { it.label == "grams" }))
        vm.takeAction(FoodDetailAction.AmountTyped("3a2"))
        assertEquals("32", vm.state.amount)
        assertEquals(Precision.Weighed, vm.state.precision)

        vm.takeAction(FoodDetailAction.UnitPicked(vm.state.units.indexOfFirst { it.label == "oz" }))
        assertEquals("1.1", vm.state.amount)
        assertEquals(Precision.Weighed, vm.state.precision, "32 g weighed is still weighed in ounces")

        vm.takeAction(FoodDetailAction.ServingPicked(0))
        assertEquals(Precision.Estimate, vm.state.precision)
    }

    @Test
    fun aScannedLabelsServingIsFromLabel() = runUnitTest {
        val vm = vm(source = PickSource.Barcode)

        assertEquals(Precision.Label, vm.state.precision)
        vm.takeAction(FoodDetailAction.UnitPicked(vm.state.units.indexOfFirst { it.label == "grams" }))
        vm.takeAction(FoodDetailAction.AmountTyped("40"))
        assertEquals(Precision.Weighed, vm.state.precision)
    }

    @Test
    fun someoneWhoWeighsInPoundsSeesOuncesFirst() {
        val state = LoggerFixtures.detailPoundsState(policy(PresetId.Full))

        assertEquals(listOf("tbsp", "oz", "grams", "lb"), state.units.map { it.label })
    }

    @Test
    fun aLiquidThatStatesItsVolumeIsOfferedInMillilitresAndFluidOunces() {
        val state = LoggerFixtures.liquidDetailState(policy(PresetId.Full))

        assertEquals(listOf("cup", "grams", "oz", "lb", "ml", "fl oz"), state.units.map { it.label })
        assertEquals("ml", state.unit?.label)
        assertEquals(Precision.Weighed, state.precision)
        assertEquals("250 g", state.weight)
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
        assertEquals(listOf("package", "grams", "oz", "lb"), state.units.map { it.label })
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
    fun removeFromRecentsIsOfferedWhileItIsOnRecent_andTakesItOff() = runUnitTest {
        assertFalse(vm().state.inRecents)
        diary.recent.value = listOf(LoggerFixtures.item(LoggerFixtures.peanutButter, MealSlot.Snack))
        val vm = vm()
        assertTrue(vm.state.inRecents)

        vm.takeAction(FoodDetailAction.RemoveFromRecentsTapped)

        assertEquals(NOON.toEpochMilliseconds(), settings.state.value.hiddenRecentFoods["f:peanut-butter"])
    }

    @Test
    fun reportAProblemNamesTheFoodByItsIdOnly() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(FoodDetailAction.ReportTapped)
        runCurrent()

        val report = assertIs<FoodDetailEvent.OpenReport>(events.single())
        assertEquals("Food detail: f:peanut-butter", report.context)
        assertFalse("Peanut" in report.context)
    }

    @Test
    fun editACopyOpensForThisFoodTheDayAndTheMeal() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        vm.takeAction(FoodDetailAction.SlotPicked(MealSlot.Snack))

        vm.takeAction(FoodDetailAction.EditCopyTapped)
        runCurrent()

        assertEquals(listOf<FoodDetailEvent>(FoodDetailEvent.OpenEditCopy(FoodRef.Catalog("peanut-butter"), LoggerFixtures.today, MealSlot.Snack)), events)
    }

    @Test
    fun aCreamerInTablespoonsOffersGramsOnceItsCopyIsWeighed() = runUnitTest {
        customFoods.foods.value = listOf(LoggerFixtures.creamer, LoggerFixtures.creamer.copy(id = "creamer-mine", servingGrams = 15.0))

        assertEquals(listOf("tbsp"), vm("creamer", custom = true).state.units.map { it.label })

        val vm = vm("creamer-mine", custom = true)
        assertEquals(listOf("tbsp", "grams", "oz", "lb"), vm.state.units.map { it.label })
        vm.takeAction(FoodDetailAction.UnitPicked(vm.state.units.indexOfFirst { it.label == "grams" }))
        vm.takeAction(FoodDetailAction.AmountTyped("30"))
        vm.takeAction(FoodDetailAction.AddToMealTapped)

        val item = diary.added.single().items.single()
        assertEquals(30.0, item.grams)
        assertEquals(70.0, item.kcal ?: 0.0, 0.01)
        assertEquals(Precision.Weighed, item.precision)
        assertEquals("30 g", item.amountText)
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

    @Test
    fun addOnADayClosedASecondAgo_logsNothing_andSaysSo_andCanBeTriedAgainAfterReopen() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        diary.close(LoggerFixtures.today)

        vm.takeAction(FoodDetailAction.AddToMealTapped)
        assertTrue(diary.added.isEmpty())
        assertEquals(listOf<FoodDetailEvent>(FoodDetailEvent.DayClosed(LoggerFixtures.today)), events)

        diary.reopenDay(LoggerFixtures.today)
        vm.takeAction(FoodDetailAction.AddToMealTapped)
        assertEquals(1, diary.added.size, "the detail stays put under the closed sheet, so Add works once the day is open")
    }
}
