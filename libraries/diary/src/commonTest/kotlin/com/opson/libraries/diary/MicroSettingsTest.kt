package com.opson.libraries.diary

import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.ReferenceBasis
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Vitamins and minerals: references from Resting energy's details, the person's own numbers, and Today's four. */
class MicroSettingsTest {

    private val today = LocalDate(2026, 10, 27)

    @Test
    fun defaults_areEmpty_andTodayShowsTheFourMinerals() {
        val settings = SettingsJson.decodeFromString(Settings.serializer(), "{}")
        assertEquals(emptyMap(), settings.microTargets)
        assertEquals(emptyList(), settings.todayNutrients)
        assertEquals(
            listOf(Micronutrient.Sodium, Micronutrient.Potassium, Micronutrient.Calcium, Micronutrient.Iron),
            settings.todayMicronutrients,
        )
    }

    @Test
    fun references_followBirthYearAndSex() {
        val settings = Settings(birthYear = 1976, bodySex = BodySex.Female)
        val references = settings.microReferences(today)
        assertEquals(ReferenceBasis.AgeAndSex, references.basis)
        // 50 this year: still the 31 to 50 band.
        assertEquals(18.0, references.of(Micronutrient.Iron))
        assertEquals(8.0, Settings(birthYear = 1975, bodySex = BodySex.Female).microReferences(today).of(Micronutrient.Iron))
    }

    @Test
    fun references_withNothingKnown_areTheDailyValues() {
        assertEquals(ReferenceBasis.DailyValue, Settings().microReferences(today).basis)
        assertEquals(ReferenceBasis.DailyValue, Settings(bodySex = BodySex.Unsaid).microReferences(today).basis)
    }

    @Test
    fun ownTarget_setsAndClears_andUnknownKeysAreIgnored() {
        val set = Settings(microTargets = mapOf("not_a_nutrient" to 3.0)).withMicroTarget(Micronutrient.VitaminD, 25.0)
        assertEquals(mapOf(Micronutrient.VitaminD to 25.0), set.ownMicroTargets)
        assertEquals(25.0, set.microReferences(today).of(Micronutrient.VitaminD))
        val cleared = set.withMicroTarget(Micronutrient.VitaminD, null)
        assertEquals(20.0, cleared.microReferences(today).of(Micronutrient.VitaminD))
    }

    @Test
    fun todayNutrients_addAtTheEnd_stopAtFour_andNeverEmpty() {
        val swapped = Settings().withTodayNutrient(Micronutrient.Sodium, false).withTodayNutrient(Micronutrient.VitaminD, true)
        assertEquals(listOf("potassium", "calcium", "iron", "vitamin_d"), swapped.todayNutrients)
        assertEquals(swapped, swapped.withTodayNutrient(Micronutrient.Zinc, true))

        var one = Settings()
        listOf(Micronutrient.Sodium, Micronutrient.Potassium, Micronutrient.Calcium).forEach { one = one.withTodayNutrient(it, false) }
        assertEquals(listOf(Micronutrient.Iron), one.todayMicronutrients)
        assertEquals(one, one.withTodayNutrient(Micronutrient.Iron, false))
    }

    @Test
    fun todayNutrients_readsPastJunk() {
        val settings = Settings(todayNutrients = listOf("zinc", "bogus", "zinc", "iron", "calcium", "sodium", "potassium"))
        assertEquals(listOf(Micronutrient.Zinc, Micronutrient.Iron, Micronutrient.Calcium, Micronutrient.Sodium), settings.todayMicronutrients)
        assertEquals(Settings.DEFAULT_TODAY_NUTRIENTS, Settings(todayNutrients = listOf("bogus")).todayMicronutrients)
    }

    /**
     * The server merges one level deep, and a key left out of a write keeps its old value. So
     * both keys are top level, and the empty defaults are written out: clearing an own number
     * or going back to the default four has to reach the server.
     */
    @Test
    fun theyTravelAsTopLevelKeys_emptyIncluded() {
        val empty = SettingsJson.encodeToString(Settings.serializer(), Settings())
        assertTrue("\"microTargets\":{}" in empty, empty)
        assertTrue("\"todayNutrients\":[]" in empty, empty)

        val settings = Settings().withMicroTarget(Micronutrient.Iron, 12.5).withTodayNutrient(Micronutrient.Sodium, false)
        val json = SettingsJson.encodeToString(Settings.serializer(), settings)
        assertTrue("\"microTargets\":{\"iron\":12.5}" in json, json)
        assertTrue("\"todayNutrients\":[\"potassium\",\"calcium\",\"iron\"]" in json, json)
        assertEquals(settings, SettingsJson.decodeFromString(Settings.serializer(), json))
    }
}
