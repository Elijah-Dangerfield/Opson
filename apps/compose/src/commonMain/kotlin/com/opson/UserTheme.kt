package com.opson

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.TextSize
import com.opson.libraries.diary.ThemeMode
import com.opson.system.AppTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The two settings `AppTheme` reads. Look and feel writes them. */
internal data class UserTheme(
    val theme: ThemeMode = ThemeMode.System,
    val textSize: TextSize = TextSize.Normal,
)

internal fun Settings.userTheme(): UserTheme = UserTheme(theme, textSize)

/** Whether the app draws dark: the user's pick, or the system's when they left it on System. */
internal fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.System -> systemDark
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

/**
 * [AppTheme] with the user's appearance and text size. Its own composable, collecting only
 * those two fields, so logging a glass of water or changing a target does not re-theme or
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
        darkTheme = user.theme.isDark(systemDark = isSystemInDarkTheme()),
        largeText = user.textSize == TextSize.Large,
        content = content,
    )
}
