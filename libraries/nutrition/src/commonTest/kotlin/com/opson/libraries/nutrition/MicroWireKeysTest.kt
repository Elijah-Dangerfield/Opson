package com.opson.libraries.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The wire keys entries and catalog foods carry, mapped onto the tracked
 * micronutrients. What a percent is made of from there is `MicroPercentsTest`.
 */
class MicroWireKeysTest {

    @Test
    fun everyMicronutrient_roundTripsThroughItsWireKey() {
        Micronutrient.entries.forEach { micro ->
            assertEquals(micro, Micronutrient.fromWireKey(micro.wireKey))
        }
    }

    @Test
    fun wireKeys_carryTheReferenceUnit() {
        Micronutrient.entries.forEach { micro ->
            val suffix = when (micro.unit) {
                MicroUnit.Mg -> "_mg"
                MicroUnit.Ug -> "_ug"
            }
            assertEquals(true, micro.wireKey.endsWith(suffix), micro.wireKey)
        }
    }

    /**
     * The keys the catalog importers write (`scripts/import_usda_foods.py` and
     * `import_off_foods.py`, whose tests pin the other direction). A typo here or there would
     * drop a nutrient silently: the food would just never show it.
     */
    @Test
    fun wireKeys_areTheCatalogsKeys() {
        assertEquals(
            setOf(
                "vitamin_a_rae_ug", "thiamin_mg", "riboflavin_mg", "niacin_mg", "vitamin_b6_mg", "folate_dfe_ug",
                "vitamin_b12_ug", "vitamin_c_mg", "vitamin_d_ug", "vitamin_e_mg", "vitamin_k_ug", "choline_mg",
                "calcium_mg", "copper_mg", "iron_mg", "magnesium_mg", "manganese_mg", "phosphorus_mg", "potassium_mg",
                "selenium_ug", "sodium_mg", "zinc_mg",
            ),
            Micronutrient.entries.map { it.wireKey }.toSet(),
        )
    }

    @Test
    fun untrackedKeys_areDropped() {
        assertNull(Micronutrient.fromWireKey("sugars_g"))
        assertNull(Micronutrient.fromWireKey("sodium"))
        val amounts = mapOf("sodium_mg" to 400.0, "cholesterol_mg" to 90.0, "vitamin_a_rae_ug" to 70.0)
        assertEquals(
            mapOf(Micronutrient.Sodium to 400.0, Micronutrient.VitaminA to 70.0),
            amounts.toMicronutrients(),
        )
    }

    @Test
    fun theBVitaminsListTogether_mineralsAlphabetically() {
        assertEquals(
            listOf(
                Micronutrient.VitaminA, Micronutrient.Thiamin, Micronutrient.Riboflavin, Micronutrient.Niacin,
                Micronutrient.VitaminB6, Micronutrient.Folate, Micronutrient.VitaminB12, Micronutrient.VitaminC,
                Micronutrient.VitaminD, Micronutrient.VitaminE, Micronutrient.VitaminK, Micronutrient.Choline,
            ),
            Micronutrient.inGroup(MicroGroup.Vitamins),
        )
        val minerals = Micronutrient.inGroup(MicroGroup.Minerals)
        assertEquals(minerals.sortedBy { it.key }, minerals)
    }

    @Test
    fun microReference_usesThePolicyFormatter() {
        assertEquals("2,300 mg limit", policy().microReference(Micronutrient.Sodium))
        assertEquals("700 µg", policy(PresetId.Blind).microReference(Micronutrient.VitaminA))
    }
}
