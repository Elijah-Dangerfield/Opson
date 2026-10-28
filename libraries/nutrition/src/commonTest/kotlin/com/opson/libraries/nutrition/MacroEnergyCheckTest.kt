package com.opson.libraries.nutrition

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A typed energy against its typed grams: when they disagree, and the two ways to make them agree. */
class MacroEnergyCheckTest {

    private fun grams(protein: Double? = null, carbs: Double? = null, fat: Double? = null, fiber: Double? = null): Map<Metric, Double> = buildMap {
        protein?.let { put(Metric.Protein, it) }
        carbs?.let { put(Metric.Carbs, it) }
        fat?.let { put(Metric.Fat, it) }
        fiber?.let { put(Metric.Fiber, it) }
    }

    @Test
    fun theMacrosEnergy_isFourFourNine_andFiberAddsNothing() {
        assertEquals(30.0 * 4 + 50.0 * 4 + 20.0 * 9, MacroEnergyCheck.macroKcal(grams(30.0, 50.0, 20.0, fiber = 8.0)))
        assertEquals(120.0, MacroEnergyCheck.macroKcal(grams(protein = 30.0)))
        assertNull(MacroEnergyCheck.macroKcal(grams(fiber = 8.0)))
        assertNull(MacroEnergyCheck.macroKcal(emptyMap()))
    }

    @Test
    fun aLabelsRounding_isNotAMismatch() {
        // 500 at 4/4/9; a label printing 510 or 490 is rounding.
        val label = grams(30.0, 50.0, 20.0)
        assertNull(MacroEnergyCheck.mismatch(510.0, label))
        assertNull(MacroEnergyCheck.mismatch(490.0, label))
        // Small foods get the 15 kcal floor rather than 12% of almost nothing.
        assertNull(MacroEnergyCheck.mismatch(25.0, grams(1.0, 8.0, 0.0)))
    }

    @Test
    fun beyondTheTolerance_eitherWay_isAMismatch() {
        val label = grams(30.0, 50.0, 20.0)
        val under = assertNotNull(MacroEnergyCheck.mismatch(400.0, label))
        assertEquals(EnergyMismatch(kcal = 400.0, macroKcal = 500.0), under)
        assertTrue(under.over, "the macros come to more than was typed")
        val over = assertNotNull(MacroEnergyCheck.mismatch(620.0, label))
        assertEquals(500.0, over.macroKcal)
        assertFalse(over.over)
    }

    @Test
    fun fiberCountedAtTwo_insideOrOutsideCarbs_agrees() {
        // 4/4/9 says 500. A US label counts 10 g of fiber at 2 inside carbs (480), an EU one outside them (520).
        val label = grams(30.0, 50.0, 20.0, fiber = 10.0)
        assertNull(MacroEnergyCheck.mismatch(470.0, label))
        assertNull(MacroEnergyCheck.mismatch(530.0, label))
        // Without the fiber the same numbers are out.
        assertNotNull(MacroEnergyCheck.mismatch(440.0, grams(30.0, 50.0, 20.0)))
        assertNull(MacroEnergyCheck.mismatch(440.0, label))
    }

    @Test
    fun withAMacroMissing_onlyMacrosThatAlreadyComeToMoreDisagree() {
        // 30 g protein is 120 kcal; the carbs and fat nobody typed can make up the rest of 500.
        assertNull(MacroEnergyCheck.mismatch(500.0, grams(protein = 30.0)))
        assertNull(MacroEnergyCheck.mismatch(500.0, grams(protein = 30.0, fat = 20.0)))
        // But 60 g of fat alone is 540, which 300 can't hold.
        assertEquals(EnergyMismatch(300.0, 540.0), MacroEnergyCheck.mismatch(300.0, grams(fat = 60.0)))
    }

    @Test
    fun noMacros_isNothingToCompare() {
        assertNull(MacroEnergyCheck.mismatch(500.0, emptyMap()))
        assertNull(MacroEnergyCheck.mismatch(500.0, grams(fiber = 30.0)))
    }

    @Test
    fun fitting_scalesProteinCarbsAndFatTogether_untilTheyAgree() {
        val label = grams(30.0, 50.0, 20.0, fiber = 6.0)
        val fitted = MacroEnergyCheck.fitted(400.0, label)
        assertEquals(400.0, assertNotNull(MacroEnergyCheck.macroKcal(fitted)), absoluteTolerance = 1e-9)
        assertEquals(6.0, fitted[Metric.Fiber], "fiber has no energy to scale")
        // The split is kept: protein is still 30 of every 100 grams of the three.
        assertTrue(abs(fitted.getValue(Metric.Protein) / fitted.getValue(Metric.Carbs) - 30.0 / 50.0) < 1e-9)
        assertNull(MacroEnergyCheck.mismatch(400.0, fitted))
    }

    @Test
    fun fitting_withNoMacroEnergy_changesNothing() {
        val zero = grams(protein = 0.0, fiber = 4.0)
        assertEquals(zero, MacroEnergyCheck.fitted(300.0, zero))
    }
}
