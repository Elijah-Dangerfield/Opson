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

    @Test
    fun untrackedKeys_areDropped() {
        assertNull(Micronutrient.fromWireKey("sugars_g"))
        assertNull(Micronutrient.fromWireKey("sodium"))
        val amounts = mapOf("sodium_mg" to 400.0, "choline_mg" to 90.0, "vitamin_a_rae_ug" to 70.0)
        assertEquals(
            mapOf(Micronutrient.Sodium to 400.0, Micronutrient.VitaminA to 70.0),
            amounts.toMicronutrients(),
        )
    }

    @Test
    fun microReference_usesThePolicyFormatter() {
        assertEquals("2,300 mg limit", policy().microReference(Micronutrient.Sodium))
        assertEquals("700 µg", policy(PresetId.Blind).microReference(Micronutrient.VitaminA))
    }
}
