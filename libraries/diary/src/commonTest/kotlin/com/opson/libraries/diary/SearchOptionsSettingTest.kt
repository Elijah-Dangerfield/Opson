package com.opson.libraries.diary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Logger's search options: three top-level keys (settings sync merges shallowly), defaults
 * that leave search as it was, and a value from a newer build read as the default.
 */
class SearchOptionsSettingTest {

    @Test
    fun defaultsLeaveSearchAsItWas() {
        val settings = SettingsJson.decodeFromString(Settings.serializer(), "{}")
        assertEquals(SearchSort.BestMatch, settings.searchSort)
        assertEquals(SearchDatabase.All, settings.searchDatabase)
        assertEquals(true, settings.searchTabs)
    }

    @Test
    fun theyTravelAsTopLevelKeys() {
        val settings = Settings(searchSort = SearchSort.AToZ, searchDatabase = SearchDatabase.MyFoods, searchTabs = false)
        val json = SettingsJson.encodeToString(Settings.serializer(), settings)
        listOf("\"searchSort\":\"a_to_z\"", "\"searchDatabase\":\"my_foods\"", "\"searchTabs\":false")
            .forEach { assertTrue(it in json, "missing $it in $json") }
        assertEquals(settings, SettingsJson.decodeFromString(Settings.serializer(), json))
    }

    @Test
    fun aValueThisBuildDoesNotKnowIsTheDefault() {
        val decoded = SettingsJson.decodeFromString(
            Settings.serializer(),
            """{"searchSort":"most_logged","searchDatabase":"nccdb","searchTabs":false}""",
        )
        assertEquals(SearchSort.BestMatch, decoded.searchSort)
        assertEquals(SearchDatabase.All, decoded.searchDatabase)
        assertEquals(false, decoded.searchTabs)
    }
}
