package com.opson.libraries.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** A nutrient's route key, and what one food's part of it shows in each mode. */
class NutrientTest {

    private val iron = Nutrient.Micro(Micronutrient.Iron)
    private val protein = Nutrient.Macro(Metric.Protein)

    @Test
    fun keys_roundTrip_andEnergyIsNotANutrientHere() {
        assertEquals(iron, Nutrient.fromKey("iron"))
        assertEquals(protein, Nutrient.fromKey("protein"))
        assertNull(Nutrient.fromKey("calories"))
        assertNull(Nutrient.fromKey("nope"))
        assertFailsWith<IllegalArgumentException> { Nutrient.Macro(Metric.Calories) }
    }

    @Test
    fun allNumbers_showsAmountAndShare() {
        val policy = policy(PresetId.Full)
        assertEquals(Display.Shown("2.1 mg"), policy.resolveContribution(iron, 2.1, Surface.Nutrients))
        assertEquals("12 g", (policy.resolveContribution(protein, 12.0, Surface.Nutrients) as Display.Shown).text)
        assertEquals(Display.Shown("40%", 0.4f), policy.resolveShare(iron, 40.0, Surface.Nutrients))
    }

    @Test
    fun macrosWithGrams_showsMacroAndMicroAmounts() {
        val policy = policy(PresetId.MacrosWithGrams)
        assertEquals("12 g", (policy.resolveContribution(protein, 12.0, Surface.Nutrients) as Display.Shown).text)
        assertEquals(Display.Shown("2.1 mg"), policy.resolveContribution(iron, 2.1, Surface.Nutrients))
    }

    /** Macros only softens macros: foods are ranked, and neither an amount nor a share shows. */
    @Test
    fun macrosOnly_hidesAmountsAndShares() {
        val policy = policy(PresetId.MacrosOnly)
        assertEquals(Display.Hidden, policy.resolveContribution(protein, 12.0, Surface.Nutrients))
        assertEquals(Display.Hidden, policy.resolveShare(protein, 40.0, Surface.Nutrients))
        assertEquals(Display.Hidden, policy.resolveContribution(iron, 2.1, Surface.Nutrients))
    }

    @Test
    fun noNumbers_hidesEverything() {
        val policy = policy(PresetId.NoNumbers)
        assertEquals(Display.Hidden, policy.resolveContribution(protein, 12.0, Surface.Nutrients))
        assertEquals(Display.Hidden, policy.resolveShare(iron, 40.0, Surface.Nutrients))
    }
}
