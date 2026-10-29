package com.opson.libraries.diary

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The Celebrations settings: top-level keys, on by default, and gentler after numbers have been hard. */
class CelebrationSettingsTest {

    @Test
    fun onWithCounts_byDefault() {
        assertEquals(CelebrationStyle.Counts, Settings().celebrationStyle)
    }

    @Test
    fun afterNumbersHaveBeenHard_theyAreGentle_untilThePersonAsksForCounts() {
        val hard = Settings(onboarding = OnboardingAnswers(hard = true))
        assertEquals(CelebrationStyle.Gentle, hard.celebrationStyle)
        assertEquals(CelebrationStyle.Counts, hard.copy(celebrationCounts = true).celebrationStyle)
        assertEquals(CelebrationStyle.Gentle, Settings(celebrationCounts = false).celebrationStyle)
    }

    @Test
    fun off_isOff_whateverTheCounts() {
        assertEquals(CelebrationStyle.Off, Settings(celebrationsEnabled = false, celebrationCounts = true).celebrationStyle)
    }

    @Test
    fun theKeys_travelAtTheTop_andRoundTrip() {
        val settings = Settings(celebrationsEnabled = false, celebrationCounts = true)
        val json = SettingsJson.encodeToJsonElement(Settings.serializer(), settings).jsonObject
        assertFalse(json.getValue("celebrationsEnabled").jsonPrimitive.boolean)
        assertEquals(true, json.getValue("celebrationCounts").jsonPrimitive.boolean)
        assertEquals(settings, SettingsJson.decodeFromJsonElement(Settings.serializer(), json))
    }

    @Test
    fun olderJson_withoutTheKeys_readsAsOn() {
        val old = SettingsJson.decodeFromJsonElement(Settings.serializer(), JsonObject(mapOf("mealGroups" to JsonPrimitive(true))))
        assertEquals(CelebrationStyle.Counts, old.celebrationStyle)
    }
}
