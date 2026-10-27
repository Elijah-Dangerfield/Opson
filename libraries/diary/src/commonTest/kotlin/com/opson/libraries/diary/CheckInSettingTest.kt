package com.opson.libraries.diary

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The check-in switch in Reminders: on unless turned off, and a top-level key, since settings sync merges shallowly. */
class CheckInSettingTest {

    @Test
    fun onUntilTurnedOff_soSettingsFromBeforeItKeepTheCard() {
        assertTrue(SettingsJson.decodeFromString(Settings.serializer(), "{}").checkIn)
    }

    @Test
    fun itTravelsAsATopLevelKey() {
        val off = Settings(checkIn = false)
        val json = SettingsJson.encodeToJsonElement(Settings.serializer(), off).jsonObject
        assertEquals(false, json.getValue("checkIn").jsonPrimitive.boolean)
        assertEquals(off, SettingsJson.decodeFromJsonElement(Settings.serializer(), json))
    }
}
