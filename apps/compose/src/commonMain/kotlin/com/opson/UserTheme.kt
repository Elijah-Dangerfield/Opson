package com.opson

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.opson.libraries.diary.PaletteChoice
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.TextSize
import com.opson.libraries.diary.ThemeMode
import com.opson.system.AppTheme
import com.opson.system.color.Palette
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The three settings `AppTheme` reads. Look and feel writes them. */
internal data class UserTheme(
    val palette: PaletteChoice = PaletteChoice.Pine,
    val theme: ThemeMode = ThemeMode.System,
    val textSize: TextSize = TextSize.Normal,
)

internal fun Settings.userTheme(): UserTheme = UserTheme(palette, theme, textSize)

/** Whether the app draws dark: the user's pick, or the system's when they left it on System. */
internal fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.System -> systemDark
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

/** The diary keeps the palette as a key so it need not depend on `:libraries:ui`; this is the bridge. */
internal fun PaletteChoice.toPalette(): Palette = when (this) {
    PaletteChoice.Pine -> Palette.Pine
    PaletteChoice.Espresso -> Palette.Espresso
    PaletteChoice.Clay -> Palette.Clay
    PaletteChoice.Plum -> Palette.Plum
}

/**
 * [AppTheme] with the user's palette, theme and text size. Its own composable, collecting only
 * those three fields, so logging a glass of water or changing a target does not re-theme or
 * recompose the app root; a theme change recomposes from here down.
 */
@Composable
internal fun UserAppTheme(
    settingsRepository: SettingsRepository,
    content: @Composable () -> Unit,
) {
    val themeFlow = remember(settingsRepository) {
        settingsRepository.settings.map { it.userTheme() }.distinctUntilChanged()
    }
    val user by themeFlow.collectAsState(initial = settingsRepository.settings.value.userTheme())
    AppTheme(
        palette = user.palette.toPalette(),
        darkTheme = user.theme.isDark(systemDark = isSystemInDarkTheme()),
        largeText = user.textSize == TextSize.Large,
        content = content,
    )
}
