package com.opson

import com.opson.libraries.diary.PaletteChoice
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.TextSize
import com.opson.libraries.diary.ThemeMode
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The bridge from the diary's palette key to the theme's palette, and the slice of settings
 * the root theme watches. Not covered: that `UserAppTheme` recomposes on a change (a
 * composition claim, and the theme itself is the gallery's to show).
 */
class UserThemeTest {

    @Test
    fun everyPaletteChoiceMapsToThePaletteOfTheSameName() {
        PaletteChoice.entries.forEach { assertEquals(it.name, it.toPalette().name) }
    }

    @Test
    fun theThemeSliceIgnoresEverythingButPaletteThemeAndTextSize() {
        val settings = Settings(palette = PaletteChoice.Plum, theme = ThemeMode.Dark, textSize = TextSize.Large)

        val unrelatedChange = settings.copy(waterGlasses = 12, numbers = NumbersSettings(PresetId.Blind))

        assertEquals(UserTheme(PaletteChoice.Plum, ThemeMode.Dark, TextSize.Large), settings.userTheme())
        assertEquals(settings.userTheme(), unrelatedChange.userTheme())
    }
}
