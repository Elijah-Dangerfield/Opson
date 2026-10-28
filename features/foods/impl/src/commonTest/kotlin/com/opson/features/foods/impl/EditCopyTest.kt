package com.opson.features.foods.impl

import com.opson.features.foods.EditCopyRoute
import com.opson.libraries.core.fixed
import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.FoodUnit
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.AmountUnit
import com.opson.libraries.foods.amountUnits
import com.opson.libraries.foods.draftFor
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.TimeZone
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.foods_copy_saved
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Edit a copy: what it opens with from an entry, a catalog food or one of the person's own, the
 * serving sizes and Nutrition per, the 4/4/9 check, what each mode lets it ask, and what Save
 * writes. The creamer in tablespoons is the case it exists for.
 */
class EditCopyTest : CoroutineTest() {
    private val entry = FoodsFixtures.peanutButter
    private val diary = FakeDiaryRepository(listOf(Day(date = FoodsFixtures.today, itemsBySlot = mapOf(MealSlot.Snack to listOf(entry)))))
    private val customFoods = FakeCustomFoodRepository(listOf(FoodsFixtures.creamer))
    private val catalog = FakeFoodCatalog(listOf(FoodsFixtures.peanutButterFood))
    private val settings = FakeSettingsRepository()

    private fun vm(route: EditCopyRoute = EditCopyRoute(FoodsFixtures.today.toString(), entryId = entry.id), preset: PresetId = PresetId.Full) =
        vm(route, FakePolicySource(preset))

    private fun vm(route: EditCopyRoute, policySource: VisibilityPolicySource) = EditCopyViewModel(
        route = route,
        mine = "(mine)",
        diary = diary,
        customFoods = customFoods,
        foodCatalog = catalog,
        settingsRepository = settings,
        calendar = FakeCalendar(),
        policySource = policySource,
    )

    private fun fromFood(id: String, custom: Boolean, preset: PresetId = PresetId.Full) =
        vm(EditCopyRoute(FoodsFixtures.today.toString(), foodId = id, isCustom = custom, slot = MealSlot.Breakfast), preset)

    private fun TestScope.events(vm: EditCopyViewModel): List<EditCopyEvent> {
        val events = mutableListOf<EditCopyEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return events
    }

    private fun EditCopyState.field(key: String): CopyField = fields.first { it.nutrient.key == key }

    // From an entry

    @Test
    fun opensWithTheEntrysDetails_namedMine_andUseForThisEntryOn() = runUnitTest {
        val state = vm().state

        assertEquals("Peanut butter (mine)", state.name)
        assertEquals(listOf(ServingRow("2 tbsp", "32", removable = false)), state.servings)
        assertEquals(listOf("2 tbsp", "100 g"), state.perOptions)
        assertEquals("190", state.field(CopyNutrient.KCAL).text)
        assertEquals(listOf("8", "7", "16", "2"), listOf(CopyNutrient.PROTEIN, CopyNutrient.CARBS, CopyNutrient.FAT, CopyNutrient.FIBER).map { state.field(it).text })
        assertEquals("", state.field("sodium_mg").text, "not on the entry: blank, and unknown")
        assertTrue(state.fields.all { it.was == null }, "nothing changed yet")
        assertTrue(state.fromEntry)
        assertTrue(state.useForEntry)
        assertEquals(MealSlot.Snack, state.slot)
    }

    @Test
    fun aChangedNumber_saysWhatItWas() = runUnitTest {
        val vm = vm()

        vm.takeAction(EditCopyAction.NutritionTyped(CopyNutrient.KCAL, "180"))
        vm.takeAction(EditCopyAction.NutritionTyped(CopyNutrient.PROTEIN, "7"))

        assertEquals("190", (vm.state.field(CopyNutrient.KCAL).was as? Display.Shown)?.text)
        assertEquals("8 g", (vm.state.field(CopyNutrient.PROTEIN).was as? Display.Shown)?.text)
        assertNull(vm.state.field(CopyNutrient.CARBS).was)
    }

    @Test
    fun aNumberWithMoreThanTwoDecimals_opensUnchanged_andIsSavedExactly() = runUnitTest {
        // A catalog food weighed to 37 g: numbers like these, never whole.
        val weighed = entry.copy(id = "pb-weighed", kcal = 219.667, protein = 9.249, carbs = 7.534, fat = 18.5, fiber = 2.0)
        val vm = EditCopyViewModel(
            route = EditCopyRoute(FoodsFixtures.today.toString(), entryId = weighed.id),
            mine = "(mine)",
            diary = FakeDiaryRepository(listOf(Day(date = FoodsFixtures.today, itemsBySlot = mapOf(MealSlot.Snack to listOf(weighed))))),
            customFoods = customFoods,
            foodCatalog = catalog,
            settingsRepository = settings,
            calendar = FakeCalendar(),
            policySource = FakePolicySource(),
        )
        val label = listOf(CopyNutrient.KCAL, CopyNutrient.PROTEIN, CopyNutrient.CARBS, CopyNutrient.FAT, CopyNutrient.FIBER)

        assertEquals(listOf("219.67", "9.25", "7.53", "18.5", "2"), label.map { vm.state.field(it).text })
        assertTrue(vm.state.fields.all { it.was == null }, "nothing changed yet, so nothing is tinted or says Was")

        vm.takeAction(EditCopyAction.NutritionTyped(CopyNutrient.FAT, "17"))
        vm.takeAction(EditCopyAction.SaveTapped)

        val draft = customFoods.created.single()
        assertEquals(219.667, draft.kcal, "left as it opened, so the entry's own number")
        assertEquals(7.534, draft.carbs)
        assertEquals(17.0, draft.fat)
    }

    @Test
    fun save_makesTheCopy_pointsTheEntryAtIt_andLeavesTheOriginalAlone() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        vm.takeAction(EditCopyAction.NutritionTyped(CopyNutrient.KCAL, "180"))
        vm.takeAction(EditCopyAction.BrandTyped("Store brand"))

        vm.takeAction(EditCopyAction.SaveTapped)
        vm.takeAction(EditCopyAction.SaveTapped)

        val draft = customFoods.created.single()
        assertEquals("Peanut butter (mine)", draft.name)
        assertEquals("Store brand", draft.brand)
        assertEquals(2.0, draft.servingAmount)
        assertEquals("tbsp", draft.servingUnit)
        assertEquals(32.0, draft.servingGrams, "the entry's weight comes too, so the copy logs in grams")
        assertEquals(180.0, draft.kcal)
        assertEquals(8.0, draft.protein)
        val switched = diary.updated.single()
        assertEquals(entry.id, switched.id)
        assertEquals("made-1", switched.customFoodId)
        assertNull(switched.foodId)
        assertEquals(180.0, switched.kcal)
        assertEquals(entry.loggedAt, switched.loggedAt)
        assertEquals(entry.amountText, switched.amountText)
        assertTrue(customFoods.changed.isEmpty(), "no food of theirs was rewritten")
        assertEquals(Res.string.foods_copy_saved, assertIs<EditCopyEvent.ShowToast>(events.first()).toast.message)
        assertEquals(EditCopyEvent.Close, events.last())
    }

    @Test
    fun useForThisEntryOff_savesOnlyTheCopy() = runUnitTest {
        val vm = vm()

        vm.takeAction(EditCopyAction.UseForEntryToggled)
        vm.takeAction(EditCopyAction.SaveTapped)

        assertEquals(1, customFoods.created.size)
        assertTrue(diary.updated.isEmpty())
    }

    @Test
    fun anEntryGone_closes() = runUnitTest {
        val vm = vm(EditCopyRoute(FoodsFixtures.today.toString(), entryId = "nothing"))
        val events = events(vm)

        assertTrue(vm.state.loading)
        assertEquals(listOf<EditCopyEvent>(EditCopyEvent.Close), events)
    }

    // From a food's detail

    @Test
    fun aCatalogFoodCopiesItsWholeLabel_itsPortionsAndTheSizesThisPersonSaved() = runUnitTest {
        settings.state.value = Settings().withUnit("f:peanut-butter", FoodUnit("my spoon", 20.0))

        val state = fromFood("peanut-butter", custom = false).state

        assertEquals("Peanut butter, smooth (mine)", state.name)
        assertEquals(listOf("2 tbsp", "1 cup", "1 my spoon"), state.servings.map { it.name }, "the label's own (31g) is left off the name")
        assertEquals(listOf("32", "258", "20"), state.servings.map { it.grams })
        assertEquals("188.16", state.field(CopyNutrient.KCAL).text, "588 a 100 g, for 32 g")
        assertEquals("3.2", state.field("saturated_fat_g").text)
        assertEquals("19%", (state.field("vitamin_e_mg").daily as? Display.Shown)?.text, "2.9 mg of 15")
        assertFalse(state.fromEntry)
        assertEquals(listOf(CopySectionKind.Label, CopySectionKind.Vitamins, CopySectionKind.Minerals), state.sections.map { it.kind })
    }

    /** The percent beside a field is of the person's own reference, as the food detail's is. */
    @Test
    fun aFieldsDailyShare_isOfThePersonsOwnReference() = runUnitTest {
        settings.state.value = Settings().withMicroTarget(Micronutrient.VitaminE, 29.0)

        val state = fromFood("peanut-butter", custom = false).state

        assertEquals("10%", (state.field("vitamin_e_mg").daily as? Display.Shown)?.text, "2.9 mg of their own 29")
    }

    @Test
    fun nutritionPer100g_retypesTheFields_andSaveStoresThemPerServing() = runUnitTest {
        val vm = fromFood("peanut-butter", custom = false)
        val per100 = vm.state.perOptions.indexOf("100 g")

        vm.takeAction(EditCopyAction.PerPicked(per100))
        assertEquals("588", vm.state.field(CopyNutrient.KCAL).text)
        assertEquals("25", vm.state.field(CopyNutrient.PROTEIN).text)
        vm.takeAction(EditCopyAction.NutritionTyped(CopyNutrient.PROTEIN, "30"))
        vm.takeAction(EditCopyAction.SaveTapped)

        val draft = customFoods.created.single()
        assertEquals(9.6, draft.protein!!, 0.001, "30 g a 100 g is 9.6 g in its 32 g serving")
        assertEquals(188.16, draft.kcal!!, 0.001)
        assertEquals(0.0, draft.micros["caffeine_mg"], "a nutrient the editor has no line for is kept")
    }

    @Test
    fun savedFromADetail_theCopysDetailOpensToLogOnThatDayAndMeal() = runUnitTest {
        val vm = fromFood("peanut-butter", custom = false)
        val events = events(vm)

        vm.takeAction(EditCopyAction.SaveTapped)

        assertEquals(EditCopyEvent.OpenCopy("made-1", FoodsFixtures.today.toString(), MealSlot.Breakfast), events.last())
        assertTrue(diary.updated.isEmpty(), "no entry to switch")
    }

    @Test
    fun aFoodThatCannotBeLoaded_closes() = runUnitTest {
        val vm = fromFood("not-cached", custom = false)
        val events = events(vm)

        assertEquals(listOf<EditCopyEvent>(EditCopyEvent.Close), events)
    }

    /**
     * The owner's case: a creamer listed only in tablespoons. They weigh a tablespoon, add a size
     * in grams, save, and from then on can log it by the gram.
     */
    @Test
    fun theCreamerInTablespoons_weighedOnce_logsByTheGram() = runUnitTest {
        val vm = fromFood("creamer", custom = true)
        assertTrue(vm.state.weighHint, "a tablespoon nobody weighed converts to nothing")
        assertEquals(listOf("1 tbsp"), vm.state.perOptions)

        vm.takeAction(EditCopyAction.ServingGramsTyped(0, "15"))
        assertFalse(vm.state.weighHint)
        vm.takeAction(EditCopyAction.ServingAdded)
        vm.takeAction(EditCopyAction.ServingNameTyped(1, "1 cup"))
        vm.takeAction(EditCopyAction.ServingGramsTyped(1, "240"))
        assertEquals(listOf("1 tbsp", "1 cup", "100 g"), vm.state.perOptions)
        vm.takeAction(EditCopyAction.SaveTapped)

        val copy = customFoods.foods.value.last()
        assertEquals(15.0, copy.servingGrams)
        assertEquals(listOf(FoodUnit("cup", 240.0)), copy.sizes)
        assertEquals(mapOf("sugars_g" to 5.0, "sodium_mg" to 5.0, "saturated_fat_g" to 1.0), copy.micros)
        assertTrue(AmountUnit.Grams in copy.amountUnits())
        val thirtyGrams = copy.draftFor(AmountUnit.Grams, 30.0)
        assertEquals(70.0, thirtyGrams.kcal!!, 0.001)
        assertEquals(30.0, thirtyGrams.grams)
        assertEquals(10.0, thirtyGrams.micros?.get("sugars_g")!!, 0.001)
        assertEquals(FoodsFixtures.creamer, customFoods.foods.value.first(), "the food it came from is untouched")
    }

    @Test
    fun aSizeWithoutAWeightIsNotKept_andARemovedOneIsGone() = runUnitTest {
        val vm = fromFood("creamer", custom = true)
        vm.takeAction(EditCopyAction.ServingAdded)
        vm.takeAction(EditCopyAction.ServingNameTyped(1, "splash"))
        vm.takeAction(EditCopyAction.ServingAdded)
        vm.takeAction(EditCopyAction.ServingNameTyped(2, "1 cup"))
        vm.takeAction(EditCopyAction.ServingGramsTyped(2, "240"))
        vm.takeAction(EditCopyAction.ServingRemoved(2))
        assertEquals(2, vm.state.servings.size)
        vm.takeAction(EditCopyAction.ServingRemoved(0))
        assertEquals(2, vm.state.servings.size, "the label's own serving stays")

        vm.takeAction(EditCopyAction.SaveTapped)

        assertTrue(customFoods.created.single().sizes.isEmpty())
    }

    @Test
    fun removingTheServingTheNumbersArePer_retypesThemForTheLabelsServing() = runUnitTest {
        val vm = fromFood("peanut-butter", custom = false)
        vm.takeAction(EditCopyAction.PerPicked(vm.state.perOptions.indexOf("1 cup")))
        assertEquals("1 cup", vm.state.perOptions[vm.state.perIndex])

        vm.takeAction(EditCopyAction.ServingRemoved(1))

        assertEquals("2 tbsp", vm.state.perOptions[vm.state.perIndex])
        assertEquals("188.16", vm.state.field(CopyNutrient.KCAL).text)
    }

    @Test
    fun aCountedSizeIsKeptAsOneOfItsUnit() = runUnitTest {
        val vm = fromFood("creamer", custom = true)
        vm.takeAction(EditCopyAction.ServingGramsTyped(0, "15"))
        vm.takeAction(EditCopyAction.ServingAdded)
        vm.takeAction(EditCopyAction.ServingNameTyped(1, "2 pods"))
        vm.takeAction(EditCopyAction.ServingGramsTyped(1, "22"))
        vm.takeAction(EditCopyAction.SaveTapped)

        assertEquals(listOf(FoodUnit("pods", 11.0)), customFoods.created.single().sizes)
    }

    // The 4/4/9 check

    @Test
    fun energyThatDisagreesWithTheMacros_isSaid_andUseFixesIt() = runUnitTest {
        val vm = vm()
        assertNull(vm.state.energyCheck, "190 against 204 from 8, 7 and 16 g is a label's rounding")

        vm.takeAction(EditCopyAction.NutritionTyped(CopyNutrient.KCAL, "120"))
        val check = assertNotNull(vm.state.energyCheck)
        assertEquals("204", (check.computed as Display.Shown).text)

        vm.takeAction(EditCopyAction.EnergyFixTapped)
        assertEquals("204", vm.state.field(CopyNutrient.KCAL).text)
        assertNull(vm.state.energyCheck)
    }

    @Test
    fun noCheckWhereEnergyIsNotShown() = runUnitTest {
        val vm = vm(preset = PresetId.MacrosWithGrams)
        vm.takeAction(EditCopyAction.NutritionTyped(CopyNutrient.PROTEIN, "80"))

        assertNull(vm.state.energyCheck)
    }

    // Each mode

    @Test
    fun calorieHidden_onlyShownNumbersAreAsked_andTheRestCopyUnseen() = runUnitTest {
        val hidden = VisibilityPolicy(NumbersSettings(overrides = mapOf(Metric.Calories to Visibility.Hide)), Clock.fixed(NOON), TimeZone.UTC)
        val vm = vm(
            EditCopyRoute(FoodsFixtures.today.toString(), entryId = entry.id),
            object : VisibilityPolicySource {
                override val policy = MutableStateFlow(hidden)
            },
        )

        assertTrue(vm.state.fields.none { it.metric == Metric.Calories })
        vm.takeAction(EditCopyAction.NutritionTyped(CopyNutrient.PROTEIN, "9"))
        vm.takeAction(EditCopyAction.SaveTapped)

        val draft = customFoods.created.single()
        assertEquals(9.0, draft.protein)
        assertEquals(190.0, draft.kcal, "calories copied as they were, never shown")
    }

    @Test
    fun macrosWithGrams_asksTheGramsAndTheVitamins_neverTheCalories() = runUnitTest {
        val state = fromFood("peanut-butter", custom = false, preset = PresetId.MacrosWithGrams).state

        assertTrue(state.fields.isNotEmpty())
        assertTrue(state.fields.none { it.metric == Metric.Calories })
        assertTrue(state.fields.any { it.nutrient.key == "vitamin_e_mg" })
    }

    @Test
    fun macrosOnly_noNumberIsAsked_butTheServingSizesAre() = runUnitTest {
        val vm = vm(preset = PresetId.MacrosOnly)

        assertTrue(vm.state.fields.isEmpty())
        assertTrue(vm.state.perOptions.isEmpty(), "nothing to be per")
        assertEquals(1, vm.state.servings.size)
        vm.takeAction(EditCopyAction.SaveTapped)
        assertEquals(16.0, customFoods.created.single().fat, "everything copied as it was")
    }

    @Test
    fun noNumbers_noNumberIsShown() = runUnitTest {
        val state = vm(preset = PresetId.NoNumbers).state

        assertTrue(state.fields.isEmpty())
        assertNull(state.energyCheck)
    }

    @Test
    fun aBlankShownField_isUnknown_notZero() = runUnitTest {
        val vm = vm()

        vm.takeAction(EditCopyAction.NutritionTyped(CopyNutrient.FIBER, ""))
        vm.takeAction(EditCopyAction.NutritionTyped("sodium_mg", ""))
        vm.takeAction(EditCopyAction.SaveTapped)

        val draft = customFoods.created.single()
        assertNull(draft.fiber)
        assertNull(draft.micros["sodium_mg"])
    }

    @Test
    fun noName_noSave() = runUnitTest {
        val vm = vm()

        vm.takeAction(EditCopyAction.NameTyped("  "))
        vm.takeAction(EditCopyAction.SaveTapped)

        assertTrue(customFoods.created.isEmpty())
    }

    @Test
    fun aCopyOfTheirOwnFood_keepsItsBrand_andNeverChangesIt() = runUnitTest {
        val own = CustomFood(id = "granola", name = "Granola", servingAmount = 1.0, servingUnit = "cup", brand = "Corner bakery", kcal = 400.0)
        customFoods.foods.value = listOf(own)

        val vm = fromFood("granola", custom = true)
        assertEquals("Corner bakery", vm.state.brand)
        vm.takeAction(EditCopyAction.SaveTapped)

        assertEquals(own, customFoods.foods.value.first())
        assertTrue(customFoods.changed.isEmpty())
    }
}
