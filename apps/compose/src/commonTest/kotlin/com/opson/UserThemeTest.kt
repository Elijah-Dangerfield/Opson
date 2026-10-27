package com.opson

import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.TextSize
import com.opson.libraries.diary.ThemeMode
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The slice of settings the root theme watches, and how Appearance resolves to light or dark. Not covered: that `UserAppTheme` recomposes on a change (a
 * composition claim, and the theme itself is the gallery's to show).
 */
class UserThemeTest {

    @Test
    fun theThemeSliceIgnoresEverythingButAppearanceAndTextSize() {
        val settings = Settings(theme = ThemeMode.Dark, textSize = TextSize.Large)

        val unrelatedChange = settings.copy(waterGlasses = 12, numbers = NumbersSettings(PresetId.Blind))

        assertEquals(UserTheme(ThemeMode.Dark, TextSize.Large), settings.userTheme())
        assertEquals(settings.userTheme(), unrelatedChange.userTheme())
    }

    /** The app and the Android system bars both read this, so a forced theme gets readable bar icons. */
    @Test
    fun aPickedThemeOverridesTheSystem_andSystemFollowsIt() {
        assertEquals(true, ThemeMode.Dark.isDark(systemDark = false))
        assertEquals(false, ThemeMode.Light.isDark(systemDark = true))
        assertEquals(true, ThemeMode.System.isDark(systemDark = true))
        assertEquals(false, ThemeMode.System.isDark(systemDark = false))
    }
}
