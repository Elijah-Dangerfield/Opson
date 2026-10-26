package com.opson.libraries.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals

/** The prototype's `MICROS` table, and how logged amounts become percents of each reference. */
class MicroPercentsTest {

    @Test
    fun table_thirteenInPrototypeOrder() {
        assertEquals(13, Micronutrient.entries.size)
        assertEquals(7, Micronutrient.inGroup(MicroGroup.Vitamins).size)
        assertEquals(
            listOf("Calcium", "Iron", "Magnesium", "Potassium", "Sodium", "Zinc"),
            Micronutrient.inGroup(MicroGroup.Minerals).map { it.name },
        )
    }

    @Test
    fun references_spelledAsThePrototype() {
        val f = EnglishNutritionFormatter
        assertEquals("700 µg", f.microReference(Micronutrient.VitaminA))
        assertEquals("2.4 µg", f.microReference(Micronutrient.VitaminB12))
        assertEquals("90 mg", f.microReference(Micronutrient.VitaminC))
        assertEquals("1,000 mg", f.microReference(Micronutrient.Calcium))
        assertEquals("4,700 mg", f.microReference(Micronutrient.Potassium))
        assertEquals("2,300 mg limit", f.microReference(Micronutrient.Sodium))
    }

    @Test
    fun percents_sumEntriesAndDivideByTheReference() {
        val percents = MicroPercents.of(
            listOf(
                mapOf(Micronutrient.Calcium to 300.0, Micronutrient.VitaminC to 45.0),
                mapOf(Micronutrient.Calcium to 160.0),
                emptyMap(),
            ),
        )
        assertEquals(46.0, percents.getValue(Micronutrient.Calcium), 1e-9)
        assertEquals(50.0, percents.getValue(Micronutrient.VitaminC), 1e-9)
        assertEquals(0.0, percents.getValue(Micronutrient.Zinc), 1e-9)
        assertEquals(13, percents.size)
    }
}
