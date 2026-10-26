package com.opson.libraries.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NutrientAmountsTest {

    private val oats = NutrientAmounts(kcal = 300.0, protein = 10.0, carbs = 50.0, fat = 6.0, fiber = 8.0)
    private val banana = NutrientAmounts(kcal = 105.0, protein = 1.0, carbs = 27.0, fat = 0.0, fiber = 3.0)
    private val nameOnly = NutrientAmounts(kcal = null)

    @Test
    fun plus_addsEverything() {
        assertEquals(NutrientAmounts(405.0, 11.0, 77.0, 6.0, 11.0), oats + banana)
    }

    @Test
    fun plus_aNameOnlyItemAddsNothing() {
        assertEquals(oats, oats + nameOnly)
        assertEquals(oats, nameOnly + oats)
    }

    @Test
    fun total_ofOnlyNameOnlyItemsHasNoEnergy() {
        // So the meal resolves to Hidden instead of claiming 0 kcal.
        assertNull(listOf(nameOnly, nameOnly).total().kcal)
        assertEquals(Display.Hidden, showAll().resolve(Metric.Calories, listOf(nameOnly).total().kcal, Surface.Today))
    }

    @Test
    fun total_ofNothingIsNone() {
        assertEquals(NutrientAmounts.None, emptyList<NutrientAmounts>().total())
    }

    @Test
    fun total_mixed() {
        assertEquals(NutrientAmounts(405.0, 11.0, 77.0, 6.0, 11.0), listOf(oats, nameOnly, banana).total())
    }

    @Test
    fun times_scalesAPortionFromPer100g() {
        // Jasmine rice per 100 g, one 158 g cup.
        val per100 = NutrientAmounts(kcal = 130.0, protein = 2.7, carbs = 28.0, fat = 0.3, fiber = 0.4)
        val cup = per100 * (158.0 / 100.0)
        assertEquals(205.4, cup.kcal!!, 1e-9)
        assertEquals(44.24, cup.carbs, 1e-9)
        assertNull((nameOnly * 2.0).kcal)
    }

    @Test
    fun of_readsEachMetric() {
        assertEquals(300.0, oats.of(Metric.Calories))
        assertEquals(8.0, oats.of(Metric.Fiber))
        assertNull(oats.of(Metric.Water))
    }
}
