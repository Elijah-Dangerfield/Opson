package com.opson.libraries.foods

import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.FoodUnit
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The amount sheet's stepper and unit switching, and [FoodRef] keys. Pure; the
 * sheet's view model is tested in the logger.
 */
class PortionAmountTest {

    private val cup = AmountUnit.OfPortion(Portion("cup", 158.0))
    private val rice = Food(
        id = "rice",
        source = FoodSource.UsdaSr,
        name = "Jasmine rice, cooked",
        per100g = NutrientProfile(kcal = 130.0, protein = 2.7, carbs = 28.0, fat = 0.3, fiber = 0.4),
        portions = listOf(Portion("cup", 158.0)),
    )

    @Test
    fun chipsDropTheLeadingCountAndTheStepperPutsItBack() {
        val medium = AmountUnit.OfPortion(Portion("1 medium", 118.0))
        assertEquals("medium", medium.chipLabel)
        assertEquals("cup", cup.chipLabel)
        assertEquals("grams", AmountUnit.Grams.chipLabel)
        assertEquals("1½ medium", medium.amountText(1.5))
        assertEquals("1½ cups", cup.amountText(1.5))
        assertEquals("¼ cup", AmountUnit.OfPortion(Portion("0.25 cup", 20.0)).chipLabel)
        assertEquals("½ cup", AmountUnit.OfPortion(Portion("0.25 cup", 20.0)).amountText(2.0))
        assertEquals("150 g", AmountUnit.Grams.amountText(150.0))
        assertEquals("2 bars", AmountUnit.Serving("bar").amountText(2.0))
    }

    @Test
    fun gramsStepByTenAndStopAtTen() {
        assertEquals(110.0, PortionAmount.more(AmountUnit.Grams, 100.0))
        assertEquals(10.0, PortionAmount.less(AmountUnit.Grams, 15.0))
        assertEquals(10.0, PortionAmount.less(AmountUnit.Grams, 10.0))
        assertFalse(PortionAmount.canLess(AmountUnit.Grams, 10.0))
    }

    @Test
    fun otherUnitsStepByAHalfAndStopAtAHalf() {
        assertEquals(1.5, PortionAmount.more(cup, 1.0))
        assertEquals(0.5, PortionAmount.less(cup, 0.5))
        assertTrue(PortionAmount.canLess(cup, 1.0))
        // Ten half steps up from a half land exactly on 5.5, not 5.499999.
        assertEquals(5.5, (1..10).fold(0.5) { q, _ -> PortionAmount.more(cup, q) })
    }

    @Test
    fun switchingToGramsRoundsTheWeight() {
        assertEquals(237.0, PortionAmount.convert(1.5, cup, AmountUnit.Grams))
    }

    @Test
    fun switchingAwayFromGramsRoundsToTheNearestHalf() {
        assertEquals(1.5, PortionAmount.convert(240.0, AmountUnit.Grams, cup))
        assertEquals(0.5, PortionAmount.convert(10.0, AmountUnit.Grams, cup), "never below a half")
    }

    @Test
    fun aUnitWithNoKnownWeightStartsFresh() {
        assertEquals(1.0, PortionAmount.convert(3.0, AmountUnit.Serving("bar"), cup))
    }

    @Test
    fun aFoodWithNoPortionsOffersGramsOnly() {
        assertEquals(listOf(AmountUnit.Grams), rice.copy(portions = emptyList()).amountUnits())
        assertEquals(listOf(cup, AmountUnit.Grams), rice.amountUnits())
        assertEquals(100.0, PortionAmount.initial(AmountUnit.Grams))
    }

    @Test
    fun aCatalogDraftScalesFromTheUnit() {
        val draft = rice.draftFor(cup, 2.0, Precision.Weighed, LogMethod.Search)

        assertEquals(316.0, draft.grams)
        assertEquals(410.8, draft.kcal!!, 0.01)
        assertEquals(Precision.Weighed, draft.precision)
        assertEquals(LogMethod.Search, draft.method)
    }

    @Test
    fun aCustomFoodCountsItsOwnUnit() {
        val granola = CustomFood(id = "c1", name = "Granola", servingAmount = 30.0, servingUnit = "g", kcal = 140.0)
        val bar = CustomFood(id = "c2", name = "Oat bar", servingAmount = 1.0, servingUnit = "bar", kcal = 190.0)

        assertEquals(listOf(AmountUnit.Grams), granola.amountUnits())
        assertEquals(listOf(AmountUnit.Serving("bar")), bar.amountUnits())
        assertEquals(30.0, granola.initialQuantity)
        assertEquals(210.0, granola.draftFor(45.0).kcal!!, 0.01)
        assertEquals("45 g", granola.draftFor(45.0).amountText)
        assertEquals("2 bars", bar.draftFor(2.0).amountText)
        assertEquals(LogMethod.Custom, bar.draftFor(1.0).method)
    }

    @Test
    fun refsMatchTheDiaryKeys() {
        val item = LogItem(
            id = "1",
            day = LocalDate(2026, 10, 25),
            slot = MealSlot.Lunch,
            loggedAt = Instant.fromEpochMilliseconds(0),
            name = "Rice",
            precision = Precision.Estimate,
            method = LogMethod.Search,
            foodId = "rice",
        )
        assertEquals(FoodRef.Catalog("rice"), item.foodRef())
        assertEquals("f:rice", item.foodRef()?.key)
        assertEquals("c:c1", FoodRef.Custom("c1").key)
        assertNull(item.copy(foodId = null).foodRef())
    }

    @Test
    fun savedSizesSitBetweenThePortionsAndGramsAndSayTheirWeight() {
        val bowl = FoodUnit("my bowl", 420.0)
        val units = rice.amountUnits(listOf(bowl))
        assertEquals(listOf("cup", "my bowl", "grams"), units.map { it.chipLabel })
        assertEquals(listOf("158 g", "420 g", null), units.map { it.gramsLabel })
        assertEquals("1½ my bowl", units[1].amountText(1.5))
    }

    @Test
    fun aSavedSizeLogsAsAPortionOfThatWeight() {
        val draft = rice.draftFor(AmountUnit.Saved(FoodUnit("my bowl", 420.0)), 1.0)
        assertEquals(420.0, draft.grams)
        assertEquals("1 my bowl", draft.amountLabel)
        assertEquals(546.0, draft.kcal!!, 0.5)
    }

    @Test
    fun switchingUnitsKeepsTheCountExceptForGrams() {
        val bowl = AmountUnit.Saved(FoodUnit("my bowl", 420.0))
        assertEquals(1.5, PortionAmount.switch(1.5, cup, bowl))
        assertEquals(237.0, PortionAmount.switch(1.5, cup, AmountUnit.Grams))
        assertEquals(1.5, PortionAmount.switch(237.0, AmountUnit.Grams, cup))
    }

    @Test
    fun aCustomFoodByWeightTakesSavedSizesAndOneByServingDoesNot() {
        val granola = CustomFood(id = "g", name = "Granola", servingAmount = 45.0, servingUnit = "g", kcal = 210.0)
        val bar = CustomFood(id = "b", name = "Bar", servingAmount = 1.0, servingUnit = "bar", kcal = 200.0)
        val scoop = FoodUnit("scoop", 90.0)
        assertEquals(listOf("scoop", "grams"), granola.amountUnits(listOf(scoop)).map { it.chipLabel })
        assertEquals(listOf("bar"), bar.amountUnits(listOf(scoop)).map { it.chipLabel })
        val draft = granola.draftFor(AmountUnit.Saved(scoop), 1.0)
        assertEquals(420.0, draft.kcal!!, 0.5)
        assertEquals("1 scoop", draft.amountLabel)
    }

    @Test
    fun everyFoodWithAWeightOffersOuncesAndPounds_ouncesFirstForPounds() {
        assertEquals(listOf("cup", "grams", "oz", "lb"), rice.detailUnits().map { it.chipLabel })
        assertEquals(listOf("cup", "oz", "grams", "lb"), rice.detailUnits(prefersOunces = true).map { it.chipLabel })
    }

    @Test
    fun ouncesAndPoundsConvertThroughGrams() {
        val oz = AmountUnit.Measure(MeasureKind.Ounces)
        val lb = AmountUnit.Measure(MeasureKind.Pounds)
        assertEquals(113.0, PortionAmount.switch(4.0, oz, AmountUnit.Grams))
        assertEquals(3.5, PortionAmount.switch(100.0, AmountUnit.Grams, oz))
        assertEquals(0.25, PortionAmount.switch(4.0, oz, lb))
        assertEquals(1.0, PortionAmount.switch(158.0, AmountUnit.Grams, cup))
        assertEquals(5.6, PortionAmount.switch(1.0, cup, oz))

        val draft = rice.draftFor(oz, 4.0)
        assertEquals("4 oz", draft.amountText)
        assertEquals(113.4, draft.grams!!, 0.05)
        assertEquals(147.4, draft.kcal!!, 0.1)
        assertEquals("oz", draft.unit)
    }

    @Test
    fun aPortionThatStatesItsVolumeGivesMillilitresAndFluidOunces() {
        val milk = rice.copy(id = "milk", name = "Milk", portions = listOf(Portion("1 cup (244 g)", 244.0), Portion("1 cup (8 fl oz)", 244.0)))
        assertEquals(244.0 / (8 * 29.5735295625), milk.gramsPerMl!!, 0.0001)
        assertEquals(listOf("cup", "cup", "grams", "oz", "lb", "ml", "fl oz"), milk.detailUnits().map { it.chipLabel })

        val ml = milk.detailUnits().first { it.label == "ml" }
        val flOz = milk.detailUnits().first { it.label == "fl oz" }
        assertEquals(237.0, PortionAmount.switch(8.0, flOz, ml))
        assertEquals("250 ml", milk.draftFor(ml, 250.0).amountText)
        assertEquals(257.8, milk.draftFor(ml, 250.0).grams!!, 0.1)
        assertNull(rice.gramsPerMl, "a cup of rice states no volume, so it gets none")
        assertEquals(240.0, volumeInLabel("1 cup (240 ml)"))
    }

    @Test
    fun aWeightPrintedInThePortionNameIsLeftOffTheChip() {
        val tbsp = AmountUnit.OfPortion(Portion("2 Tbsp (31g)", 31.0))
        assertEquals("2 Tbsp", tbsp.chipLabel)
        assertEquals("4 Tbsp", tbsp.amountText(2.0))
        assertEquals("31 g", tbsp.gramsLabel)
        assertEquals("1 container", withoutPrintedWeight("1 container (150 g)"))
    }

    @Test
    fun aCreamerInTablespoonsLogsInGramsOnceASpoonIsWeighed() {
        val creamer = CustomFood(id = "cr", name = "Creamer", servingAmount = 1.0, servingUnit = "tbsp", kcal = 35.0, fat = 2.5)
        assertEquals(listOf(AmountUnit.Serving("tbsp")), creamer.detailUnits())

        val weighed = creamer.copy(servingGrams = 15.0, sizes = listOf(FoodUnit("cup", 240.0)))
        assertEquals(listOf("tbsp", "cup", "grams", "oz", "lb"), weighed.detailUnits().map { it.chipLabel })

        val grams = weighed.draftFor(AmountUnit.Grams, 30.0)
        assertEquals(70.0, grams.kcal!!, 0.01)
        assertEquals(30.0, grams.grams)
        assertEquals("30 g", grams.amountText)
        val spoons = weighed.draftFor(AmountUnit.Serving("tbsp", 15.0), 2.0)
        assertEquals(70.0, spoons.kcal!!, 0.01)
        assertEquals(30.0, spoons.grams)
        assertEquals("2 tbsp", spoons.amountText)
        assertEquals(560.0, weighed.draftFor(AmountUnit.Saved(FoodUnit("cup", 240.0)), 1.0).kcal!!, 0.01)
        assertEquals(2.0, PortionAmount.switch(30.0, AmountUnit.Grams, AmountUnit.Serving("tbsp", 15.0)))
    }

    @Test
    fun aCustomFoodsMicrosScaleWithTheAmount() {
        val creamer = CustomFood(id = "cr", name = "Creamer", servingAmount = 1.0, servingUnit = "tbsp", servingGrams = 15.0, micros = mapOf("sugars_g" to 5.0))
        assertEquals(mapOf("sugars_g" to 10.0), creamer.draftFor(AmountUnit.Grams, 30.0).micros)
    }
}
