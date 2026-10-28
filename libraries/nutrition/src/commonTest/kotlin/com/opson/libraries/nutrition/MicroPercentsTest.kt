package com.opson.libraries.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The tracked vitamins and minerals, their Daily Values, and how logged amounts become percents
 * of a reference and a count of the foods that list each one.
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
    fun keys_roundTrip() {
        Micronutrient.entries.forEach { assertEquals(it, Micronutrient.fromKey(it.key)) }
        assertNull(Micronutrient.fromKey("vitamin_d_ug"))
    }

    /** The label's numbers (21 CFR 101.9), so a percent here matches "% Daily Value" on a pack. */
    @Test
    fun dailyValues_areTheFdasAdultDailyValues() {
        val f = EnglishNutritionFormatter
        fun dv(micro: Micronutrient) = f.microReference(micro, micro.dailyValue)
        assertEquals("900 µg", dv(Micronutrient.VitaminA))
        assertEquals("2.4 µg", dv(Micronutrient.VitaminB12))
        assertEquals("90 mg", dv(Micronutrient.VitaminC))
        assertEquals("20 µg", dv(Micronutrient.VitaminD))
        assertEquals("1,300 mg", dv(Micronutrient.Calcium))
        assertEquals("4,700 mg", dv(Micronutrient.Potassium))
        assertEquals("2,300 mg limit", dv(Micronutrient.Sodium))
        assertEquals("1.2 mg", dv(Micronutrient.Thiamin))
        assertEquals("1.3 mg", dv(Micronutrient.Riboflavin))
        assertEquals("16 mg", dv(Micronutrient.Niacin))
        assertEquals("1.7 mg", dv(Micronutrient.VitaminB6))
        assertEquals("550 mg", dv(Micronutrient.Choline))
        assertEquals("0.9 mg", dv(Micronutrient.Copper))
        assertEquals("2.3 mg", dv(Micronutrient.Manganese))
        assertEquals("1,250 mg", dv(Micronutrient.Phosphorus))
        assertEquals("55 µg", dv(Micronutrient.Selenium))
    }

    @Test
    fun amounts_keepTwoPlaces_andATraceIsNotZero() {
        val f = EnglishNutritionFormatter
        assertEquals("0.89 mg", f.microAmount(Micronutrient.Copper, 0.89))
        assertEquals("2.1 mg", f.microAmount(Micronutrient.Iron, 2.104))
        assertEquals("1,200 mg", f.microAmount(Micronutrient.Calcium, 1200.0))
        assertEquals("<0.01 mg", f.microAmount(Micronutrient.Copper, 0.001))
        assertEquals("0 µg", f.microAmount(Micronutrient.VitaminD, 0.0))
    }

    @Test
    fun percents_sumEntriesAndDivideByTheReference() {
        val percents = MicroPercents.of(
            listOf(
                mapOf(Micronutrient.Calcium to 300.0, Micronutrient.VitaminC to 45.0),
                mapOf(Micronutrient.Calcium to 160.0),
                emptyMap(),
            ),
            MicroReferences.forPerson(BodySex.Male, ageYears = 30),
        )
        assertEquals(46.0, percents.getValue(Micronutrient.Calcium), 1e-9)
        assertEquals(50.0, percents.getValue(Micronutrient.VitaminC), 1e-9)
    }

    /** Nothing listed zinc, so there is no zinc percent: "Not listed", never 0%. */
    @Test
    fun percents_leaveOutWhatNothingLists_butKeepAListedZero() {
        val percents = MicroPercents.of(listOf(mapOf(Micronutrient.VitaminD to 0.0), emptyMap()))
        assertNull(percents[Micronutrient.Zinc])
        assertEquals(0.0, percents.getValue(Micronutrient.VitaminD), 1e-9)
    }

    @Test
    fun percents_useTheirOwnNumberOverTheTable() {
        val references = MicroReferences.forPerson(BodySex.Female, 40, own = mapOf(Micronutrient.Iron to 10.0))
        assertEquals(50.0, MicroPercents.of(listOf(mapOf(Micronutrient.Iron to 5.0)), references).getValue(Micronutrient.Iron), 1e-9)
    }

    @Test
    fun coverage_countsFoodsThatListIt_aZeroIncluded() {
        val coverage = MicroPercents.coverage(
            listOf(
                mapOf(Micronutrient.VitaminD to 2.0),
                mapOf(Micronutrient.VitaminD to 0.0, Micronutrient.Iron to 1.0),
                emptyMap(),
            ),
        )
        assertEquals(MicroCoverage(listing = 2, of = 3), coverage.getValue(Micronutrient.VitaminD))
        assertTrue(coverage.getValue(Micronutrient.VitaminD).isPartial)
        assertEquals(MicroCoverage(listing = 1, of = 3), coverage.getValue(Micronutrient.Iron))
        assertTrue(coverage.getValue(Micronutrient.Zinc).isNone)
        assertFalse(MicroCoverage(3, 3).isPartial)
    }

    @Test
    fun coverage_ofNothing_isNoneOfNone() {
        assertEquals(MicroCoverage(0, 0), MicroPercents.coverage(emptyList()).getValue(Micronutrient.Calcium))
    }
}
