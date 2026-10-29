package com.opson.libraries.diary

import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The card's other readings as Settings keeps them: one top-level map, round-tripped, an unknown
 * value read as no choice, and how a figure walks its readings. Which readings a mode allows is
 * Today's (`TodayFlipsTest`), not this module's.
 */
class TodayReadingsTest {

    @Test
    fun aReading_travelsAsOneTopLevelKey_andRoundTrips() {
        val s = Settings().withReading(FlipFigure.Macros, Reading.Percent).withReading(FlipFigure.Water, Reading.Left)
        val json = SettingsJson.encodeToJsonElement(Settings.serializer(), s).jsonObject
        assertEquals(mapOf("macros" to "percent", "water" to "left"), s.todayReadings)
        assertEquals(setOf("macros", "water"), json.getValue("todayReadings").jsonObject.keys)
        val back = SettingsJson.decodeFromJsonElement(Settings.serializer(), json)
        assertEquals(Reading.Percent, back.reading(FlipFigure.Macros))
        assertEquals(Reading.Left, back.reading(FlipFigure.Water))
        assertNull(back.reading(FlipFigure.DeficitSoFar))
    }

    @Test
    fun aReadingNoBuildKnows_isNoChoice() {
        val future = SettingsJson.decodeFromString(Settings.serializer(), """{"todayReadings":{"macros":"vibes","sparkles":"left"}}""")
        assertNull(future.reading(FlipFigure.Macros))
        assertEquals(Settings().todayMainNumberByStyle, future.todayMainNumberByStyle)
    }

    @Test
    fun aSaveWithoutTheKey_readsAsNothingChosen() {
        val old = SettingsJson.decodeFromString(Settings.serializer(), """{"todayCard":"in_out"}""")
        assertEquals(emptyMap(), old.todayReadings)
    }

    @Test
    fun flips_walkTheReadings_andFallBackWhenAChoiceIsNoLongerAllowed() {
        val readings = listOf(Reading.Eaten, Reading.Left, Reading.Percent)
        assertEquals(Reading.Left, Flips.next(readings, Reading.Eaten))
        assertEquals(Reading.Eaten, Flips.next(readings, Reading.Percent), "round again at the end")
        assertNull(Flips.next(listOf(Reading.Eaten), Reading.Eaten), "one reading is not a flip")
        assertEquals(Reading.Percent, Flips.pick(readings, Reading.Percent))
        assertEquals(Reading.Eaten, Flips.pick(listOf(Reading.Eaten, Reading.Percent), Reading.Left), "the mode took Left away")
        assertEquals(Reading.Eaten, Flips.pick(readings, null))
        assertNull(Flips.pick(emptyList<Reading>(), Reading.Left))
    }
}
