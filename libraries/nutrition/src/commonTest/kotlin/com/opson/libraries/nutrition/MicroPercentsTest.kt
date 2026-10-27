package com.opson.libraries.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The prototype's `MICROS` table and the catalog's other vitamins and minerals, and how logged
 * amounts become percents of each reference.
 */
class MicroPercentsTest {

    @Test
    fun table_twelveVitaminsAndTenMinerals() {
        assertEquals(22, Micronutrient.entries.size)
        assertEquals(12, Micronutrient.inGroup(MicroGroup.Vitamins).size)
        assertEquals(
            listOf("Calcium", "Copper", "Iron", "Magnesium", "Manganese", "Phosphorus", "Potassium", "Selenium", "Sodium", "Zinc"),
            Micronutrient.inGroup(MicroGroup.Minerals).map { it.name },
        )
    }

    @Test
    fun onlySodiumIsALimit() {
        assertEquals(listOf(Micronutrient.Sodium), Micronutrient.entries.filter { it.isLimit })
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
    fun references_forTheCatalogsOtherMicros_areTheFdaDailyValues() {
        val f = EnglishNutritionFormatter
        assertEquals("1.2 mg", f.microReference(Micronutrient.Thiamin))
        assertEquals("1.3 mg", f.microReference(Micronutrient.Riboflavin))
        assertEquals("16 mg", f.microReference(Micronutrient.Niacin))
        assertEquals("1.7 mg", f.microReference(Micronutrient.VitaminB6))
        assertEquals("550 mg", f.microReference(Micronutrient.Choline))
        assertEquals("0.9 mg", f.microReference(Micronutrient.Copper))
        assertEquals("2.3 mg", f.microReference(Micronutrient.Manganese))
        assertEquals("1,250 mg", f.microReference(Micronutrient.Phosphorus))
        assertEquals("55 µg", f.microReference(Micronutrient.Selenium))
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
        assertEquals(Micronutrient.entries.size, percents.size)
    }
}
