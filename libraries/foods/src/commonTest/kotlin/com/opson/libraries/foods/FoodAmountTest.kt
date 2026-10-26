package com.opson.libraries.foods

import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.Precision
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Turning a catalog food and the amount picked on the amount sheet into the
 * nutrient values an item is logged with. Values are per 100 g in the
 * catalog; anything unknown there stays unknown on the item.
 */
class FoodAmountTest {
    private val rice = Food(
        id = "rice",
        source = FoodSource.UsdaSr,
        name = "Jasmine rice, cooked",
        per100g = NutrientProfile(kcal = 130.0, protein = 2.7, carbs = 28.0, fat = 0.3, fiber = null),
        micros = mapOf("calcium_mg" to 1.0),
        portions = listOf(Portion("1 cup", 158.0), Portion("1 bowl", 250.0)),
    )

    @Test
    fun gramsScaleFromPer100() {
        val n = rice.nutrientsFor(FoodAmount.Grams(150.0))

        assertEquals(150.0, n.grams)
        assertClose(195.0, n.kcal)
        assertClose(4.05, n.protein)
        assertClose(42.0, n.carbs)
        assertClose(1.5, n.micros["calcium_mg"])
    }

    @Test
    fun anUnknownNutrientStaysUnknownAtAnyAmount() {
        assertNull(rice.nutrientsFor(FoodAmount.Grams(500.0)).fiber)
    }

    @Test
    fun aPortionCountMultipliesThePortionsWeight() {
        val n = rice.nutrientsFor(FoodAmount.OfPortion(Portion("1 cup", 158.0), count = 2.0))

        assertEquals(316.0, n.grams)
        assertClose(410.8, n.kcal)
    }

    @Test
    fun aServingIsTheFirstPortionOrOneHundredGrams() {
        assertEquals(158.0, rice.gramsFor(FoodAmount.Servings()))
        assertEquals(79.0, rice.gramsFor(FoodAmount.Servings(0.5)))

        val noPortions = rice.copy(portions = emptyList())
        assertEquals(100.0, noPortions.gramsFor(FoodAmount.Servings()))
        assertClose(130.0, noPortions.nutrientsFor(FoodAmount.Servings()).kcal)
    }

    @Test
    fun aDraftCarriesTheAmountTheWayItReads() {
        val draft = rice.toLogDraft(FoodAmount.OfPortion(Portion("1 cup", 158.0), count = 1.5), Precision.Estimate)

        assertEquals("1½ cups", draft.amountText)
        assertEquals(1.5, draft.quantity)
        assertEquals("1 cup", draft.unit)
        assertEquals(237.0, draft.grams)
        assertEquals("rice", draft.foodId)
        assertEquals(Precision.Estimate, draft.precision)
        assertClose(308.1, draft.kcal)
        assertNull(draft.fiber)

        assertEquals("150 g", rice.toLogDraft(FoodAmount.Grams(150.0), Precision.Weighed).amountText)
        assertEquals("1 cup", rice.toLogDraft(FoodAmount.Servings()).amountText)
        assertEquals("½ cup", rice.toLogDraft(FoodAmount.Servings(0.5)).amountText)
    }

    @Test
    fun aDecimalPortionLabelReadsAsAFraction() {
        val oats = rice.copy(portions = listOf(Portion("0.25 cup", 20.0)))

        assertEquals("¼ cup", oats.amountTextFor(FoodAmount.Servings()))
        assertEquals("¾ cup", oats.amountTextFor(FoodAmount.Servings(3.0)))
        assertEquals("2 cups", oats.amountTextFor(FoodAmount.OfPortion(Portion("0.25 cup", 20.0), 8.0)))
    }

    @Test
    fun aDescribeResultIsAlwaysLoggedAsAnEstimate() {
        val parsed = ParsedItem(
            text = "2 eggs",
            name = "Egg, whole, raw",
            quantity = 2.0,
            amountText = "2 large",
            confidence = Confidence.High,
            foodId = "egg",
            portionLabel = "1 large",
            grams = 100.0,
            kcal = 143.0,
        )

        val draft = parsed.toLogDraft()

        assertEquals(Precision.Estimate, draft.precision)
        assertEquals(LogMethod.Describe, draft.method)
        assertEquals("2 large", draft.amountText)
        assertEquals(143.0, draft.kcal)
        assertNull(draft.protein)
    }

    private fun assertClose(expected: Double, actual: Double?) {
        assertTrue(actual != null && abs(expected - actual) < 1e-9, "expected $expected, was $actual")
    }
}
