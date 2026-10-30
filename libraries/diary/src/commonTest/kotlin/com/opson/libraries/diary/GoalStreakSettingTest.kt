package com.opson.libraries.diary

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `goalStreakEnabled`: off until chosen, a top-level key so the server's one-level merge keeps it. */
class GoalStreakSettingTest {

    @Test
    fun offByDefault_andForSettingsFromBeforeIt() {
        assertFalse(Settings().goalStreakEnabled)
        val old = SettingsJson.decodeFromJsonElement(Settings.serializer(), JsonObject(mapOf("mealGroups" to JsonPrimitive(true))))
        assertFalse(old.goalStreakEnabled)
    }

    @Test
    fun travelsAtTheTop_andRoundTrips() {
        val settings = Settings(goalStreakEnabled = true)
        val json = SettingsJson.encodeToJsonElement(Settings.serializer(), settings).jsonObject
        assertTrue(json.getValue("goalStreakEnabled").jsonPrimitive.boolean)
        assertEquals(settings, SettingsJson.decodeFromJsonElement(Settings.serializer(), json))
    }
}
