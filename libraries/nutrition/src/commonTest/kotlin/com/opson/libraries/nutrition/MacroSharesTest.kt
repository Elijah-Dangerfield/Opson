package com.opson.libraries.nutrition

import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MacroSharesTest {

    private val all: (Metric) -> Boolean = { true }

    @Test
    fun energyIsFourFourAndNineKcalAGram() {
        assertEquals(
            mapOf(Metric.Protein to 40.0, Metric.Carbs to 80.0, Metric.Fat to 90.0),
            macroEnergy(protein = 10.0, carbs = 20.0, fat = 10.0),
        )
    }

    @Test
    fun aMacroWithNoGramsHasNoEnergy() {
        assertEquals(mapOf(Metric.Protein to 40.0), macroEnergy(protein = 10.0, carbs = null, fat = null))
    }

    /** Smoked salmon, 18 g protein and 9 g fat: 72 and 81 kcal, whatever its label says. */
    @Test
    fun sharesAreOfTheMacrosOwnEnergySoTheyMakeAHundred() {
        val shares = macroEnergyShares(protein = 18.0, carbs = 0.0, fat = 9.0, shown = all)

        assertEquals(listOf(47, 0, 53), shares.values.map { it.roundToInt() })
        assertEquals(100.0, shares.values.sum(), 1e-9)
    }

    @Test
    fun sharesComeInProteinCarbsFatOrder() {
        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat), macroEnergyShares(10.0, 20.0, 10.0, all).keys.toList())
    }

    /** With fat hidden, protein and carbs make the whole, so the gap they leave says nothing about fat. */
    @Test
    fun aHiddenMacroIsLeftOutOfTheWhole() {
        val shares = macroEnergyShares(protein = 10.0, carbs = 30.0, fat = 20.0) { it != Metric.Fat }

        assertEquals(setOf(Metric.Protein, Metric.Carbs), shares.keys)
        assertEquals(25.0, shares.getValue(Metric.Protein), 1e-9)
        assertEquals(75.0, shares.getValue(Metric.Carbs), 1e-9)
    }

    @Test
    fun aMacroWithUnknownGramsIsLeftOut() {
        val shares = macroEnergyShares(protein = 10.0, carbs = null, fat = 0.0, shown = all)

        assertEquals(mapOf(Metric.Protein to 100.0, Metric.Fat to 0.0), shares)
    }

    @Test
    fun noEnergyOnShowMeansNoShares() {
        assertTrue(macroEnergyShares(0.0, 0.0, 0.0, all).isEmpty())
        assertTrue(macroEnergyShares(10.0, 20.0, 10.0) { false }.isEmpty())
        assertTrue(macroEnergyShares(null, null, null, all).isEmpty())
    }
}
