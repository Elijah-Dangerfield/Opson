package com.opson.libraries.diary

import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Targets
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** More › Today screen's settings (design sync 4, 13.6): top-level keys, safe defaults, and the toggle remembered per goal style. */
class TodayCardSettingsTest {

    @Test
    fun theCardsSettings_travelAsTopLevelKeys_andRoundTrip() {
        val s = Settings(
            todayCard = SummaryCardPreset.Equation,
            todayMainNumber = MainNumberChoice.Eaten,
            todayMainNumberByStyle = mapOf("range" to "balance"),
            todayBurnedBreakdown = false,
            todayMealsCount = true,
            todayNutrientsLink = false,
            todayWater = false,
            todayWeight = false,
            todayCloseDay = false,
        )
        val json = SettingsJson.encodeToJsonElement(Settings.serializer(), s).jsonObject
        assertEquals("equation", json.getValue("todayCard").jsonPrimitive.content)
        assertEquals("eaten", json.getValue("todayMainNumber").jsonPrimitive.content)
        assertEquals(s, SettingsJson.decodeFromJsonElement(Settings.serializer(), json))
    }

    @Test
    fun anUnknownPresetOrMainNumber_readsAsTheDefault() {
        val future = SettingsJson.decodeFromString(Settings.serializer(), """{"todayCard":"hologram","todayMainNumber":"vibes"}""")
        assertEquals(SummaryCardPreset.InOut, future.todayCard)
        assertEquals(MainNumberChoice.Follow, future.todayMainNumber)
    }

    @Test
    fun theToggle_isRememberedPerGoalStyle_andForgotten() {
        val range = Settings()
        val remembered = range.rememberMainNumber(MainNumberChoice.Balance)
        assertEquals(MainNumberChoice.Balance, remembered.rememberedMainNumber)

        val surplus = remembered.copy(targets = Targets.Default.copy(style = TargetStyle.Surplus(300, 300)))
        assertNull(surplus.rememberedMainNumber, "each goal style has its own")
        assertEquals(MainNumberChoice.Balance, surplus.copy(targets = Targets.Default).rememberedMainNumber)

        assertNull(remembered.forgetMainNumber().rememberedMainNumber)
        assertNull(Settings(todayMainNumberByStyle = mapOf("range" to "nonsense")).rememberedMainNumber)
    }
}
