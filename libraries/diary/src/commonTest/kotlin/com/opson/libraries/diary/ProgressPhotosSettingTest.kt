package com.opson.libraries.diary

import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Modes' Progress photos row: held in [NumbersSettings], but on the wire a top-level key, since
 * the server merges settings one level deep and a 0.1 build rewrites `numbers` without it.
 */
class ProgressPhotosSettingTest {

    private fun encode(settings: Settings) = SettingsJson.encodeToJsonElement(Settings.serializer(), settings).jsonObject

    private fun decode(json: String) = SettingsJson.decodeFromString(Settings.serializer(), json)

    @Test
    fun aChoiceTravelsAsATopLevelKey_notInsideNumbers() {
        val shown = Settings(numbers = NumbersSettings(preset = PresetId.MacrosOnly, progressPhotos = true))
        val json = encode(shown)

        assertEquals(true, json.getValue("progressPhotos").jsonPrimitive.boolean)
        assertFalse("progressPhotos" in json.getValue("numbers").jsonObject)
        assertEquals(shown, SettingsJson.decodeFromJsonElement(Settings.serializer(), json))
    }

    @Test
    fun noChoiceIsWrittenAsNull_soAPresetPickClearsItInTheMerge() {
        assertEquals(JsonNull, encode(Settings()).getValue("progressPhotos"))
    }

    @Test
    fun settingsFromBeforeIt_followThePreset() {
        assertNull(decode("""{"numbers":{"preset":"blind"}}""").numbers.progressPhotos)
        assertEquals(Settings(), decode("{}"))
    }

    @Test
    fun aMergedRow_aZeroOneNumbersBesideTheTopLevelKey_keepsTheChoice() {
        val merged = decode("""{"numbers":{"preset":"rhythm","pauseBeforeMore":true},"progressPhotos":true}""")
        assertEquals(NumbersSettings(preset = PresetId.NoNumbers, pauseBeforeMore = true, progressPhotos = true), merged.numbers)
    }

    @Test
    fun onlyTheTopLevelKeyCounts() {
        assertNull(decode("""{"numbers":{"preset":"full","progressPhotos":false}}""").numbers.progressPhotos)
        assertEquals(false, decode("""{"progressPhotos":false}""").numbers.progressPhotos)
    }
}
