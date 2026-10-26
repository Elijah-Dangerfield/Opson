package com.opson.libraries.diary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The amount sheet's star and "Custom size…": two top-level keys, so the shallow settings merge keeps them. */
class FoodPrefsSettingTest {

    @Test
    fun defaultsAreEmpty() {
        val settings = SettingsJson.decodeFromString(Settings.serializer(), "{}")
        assertEquals(emptyList(), settings.favoriteFoods)
        assertEquals(emptyMap(), settings.foodUnits)
    }

    @Test
    fun theStarToggles() {
        val starred = Settings().toggleFavorite("f:rice").toggleFavorite("c:granola")
        assertEquals(listOf("c:granola", "f:rice"), starred.favoriteFoods)
        assertTrue(starred.isFavorite("f:rice"))
        assertFalse(starred.toggleFavorite("f:rice").isFavorite("f:rice"))
    }

    @Test
    fun aSizeWithTheSameNameReplacesTheOldOne() {
        val settings = Settings()
            .withUnit("f:rice", FoodUnit("my bowl", 300.0))
            .withUnit("f:rice", FoodUnit("scoop", 90.0))
            .withUnit("f:rice", FoodUnit("My bowl", 420.0))
        assertEquals(listOf(FoodUnit("scoop", 90.0), FoodUnit("My bowl", 420.0)), settings.unitsFor("f:rice"))
        assertEquals(emptyList(), settings.unitsFor("f:other"))
    }

    @Test
    fun theyTravelAsTopLevelKeys() {
        val settings = Settings().toggleFavorite("f:rice").withUnit("f:rice", FoodUnit("my bowl", 420.0))
        val json = SettingsJson.encodeToString(Settings.serializer(), settings)
        assertTrue("\"favoriteFoods\":[\"f:rice\"]" in json, json)
        assertTrue("\"foodUnits\":{\"f:rice\":[{\"label\":\"my bowl\",\"grams\":420.0}]}" in json, json)
        assertEquals(settings, SettingsJson.decodeFromString(Settings.serializer(), json))
    }
}
