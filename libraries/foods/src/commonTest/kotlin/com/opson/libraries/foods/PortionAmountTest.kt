package com.opson.libraries.foods

import com.opson.libraries.diary.CustomFood
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
}
