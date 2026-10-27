package com.opson.libraries.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** `resolveRange` and `share`: amounts that only exist at Show, since neither has a soft word. */
class RangeAndShareTest {

    @Test
    fun range_bareAndWithUnit() {
        val full = showAll()
        assertEquals(Display.Shown("1,900 to 2,300"), full.resolveRange(Metric.Calories, 1900.0, 2300.0, Surface.DayClose, FormatContext(bare = true)))
        assertEquals(Display.Shown("1,900 to 2,300 kcal"), full.resolveRange(Metric.Calories, 1900.0, 2300.0, Surface.Settings))
    }

    @Test
    fun range_whoseEndsMeet_isOneNumber_neverAFakeRange() {
        val full = showAll()
        assertEquals(Display.Shown("2,100"), full.resolveRange(Metric.Calories, 2100.0, 2100.0, Surface.Today, FormatContext(bare = true)))
        assertEquals(Display.Shown("2,100 kcal"), full.resolveRange(Metric.Calories, 2100.0, 2100.0, Surface.Settings))
    }

    @Test
    fun range_hiddenAtEveryLevelButShow() {
        listOf(Visibility.Soften, Visibility.Hide).forEach { level ->
            val p = policy(PresetId.Full, overrides = mapOf(Metric.Calories to level))
            assertEquals(Display.Hidden, p.resolveRange(Metric.Calories, 1900.0, 2300.0, Surface.Settings), "$level")
        }
    }

    @Test
    fun range_delayOpensOnDayCloseOnly() {
        val delayed = later()
        assertEquals(Display.Hidden, delayed.resolveRange(Metric.Calories, 1900.0, 2300.0, Surface.Settings))
        assertEquals(
            Display.Shown("1,900 to 2,300"),
            delayed.resolveRange(Metric.Calories, 1900.0, 2300.0, Surface.DayClose, FormatContext(bare = true)),
        )
    }

    @Test
    fun range_blindHidesItEverywhere() {
        Surface.entries.forEach { surface ->
            assertEquals(Display.Hidden, policy(PresetId.NoNumbers).resolveRange(Metric.Calories, 1900.0, 2300.0, surface))
        }
    }

    @Test
    fun range_isForEnergyOnly() {
        assertFailsWith<IllegalArgumentException> { showAll().resolveRange(Metric.Protein, 100.0, 140.0, Surface.Settings) }
    }

    @Test
    fun share_percentWhenTheMacroIsShown() {
        assertEquals(Display.Shown("30%"), showAll().share(Metric.Fat, 30.0, Surface.Settings))
        assertEquals(Display.Shown("23%"), showAll().share(Metric.Protein, 22.857, Surface.Settings))
    }

    @Test
    fun share_hiddenWhenTheMacroIsSoftOrHidden() {
        val proteinShown = policy(PresetId.MacrosOnly, overrides = mapOf(Metric.Protein to Visibility.Show))
        assertEquals(Display.Hidden, proteinShown.share(Metric.Carbs, 48.0, Surface.Settings))
        val softProtein = policy(PresetId.MacrosOnly)
        assertEquals(Display.Hidden, softProtein.share(Metric.Protein, 23.0, Surface.Settings))
        assertEquals(Display.Shown("23%"), proteinShown.share(Metric.Protein, 23.0, Surface.Settings))
    }

    @Test
    fun share_isForMacrosOnly() {
        assertFailsWith<IllegalArgumentException> { showAll().share(Metric.Calories, 100.0, Surface.Settings) }
    }

    @Test
    fun energyShare_onlyWhenCaloriesShow() {
        assertEquals(Display.Shown("21%"), policy(PresetId.Full).energyShare(21.2, Surface.Week))
        assertEquals(Display.Hidden, policy(PresetId.NoNumbers).energyShare(21.2, Surface.Week))
        assertEquals(Display.Hidden, policy(PresetId.MacrosOnly).energyShare(21.2, Surface.Week))
        assertEquals(Display.Hidden, policy(PresetId.MacrosWithGrams).energyShare(21.2, Surface.Week))
        assertEquals(Display.Hidden, policy(PresetId.Full).energyShare(Double.NaN, Surface.Week))
    }
}
