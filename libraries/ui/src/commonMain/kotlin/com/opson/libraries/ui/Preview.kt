package com.opson.libraries.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.components.AppBottomBar
import com.opson.libraries.ui.components.BottomBarItem
import com.opson.libraries.ui.components.dialog.DialogHost
import com.opson.libraries.ui.components.dialog.LocalDialogHostState
import com.opson.libraries.ui.components.dialog.rememberDialogHostState
import com.opson.libraries.ui.system.LocalAppState
import com.opson.libraries.ui.system.LocalBuildInfo
import com.opson.libraries.ui.system.LocalClock
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.libraries.core.AppState
import com.opson.libraries.core.BuildInfo
import com.opson.libraries.core.fixed
import com.opson.system.AppThemeProvider
import com.opson.system.background
import com.opson.system.color.defaultColors
import com.opson.system.thenIf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Clock
import kotlin.time.Instant

sealed class PreviewBottomBar(val render: @Composable () -> Unit) {

    object None: PreviewBottomBar({})

    object Home : PreviewBottomBar({
        AppBottomBar(
            items = listOf(
                BottomBarItem.Home(isSelected = true),
                BottomBarItem.Activity(isSelected = false),
                BottomBarItem.Profile(isSelected = false),
            ),
            onItemClick = {},
        )
    })

    object Activity : PreviewBottomBar({
        AppBottomBar(
            items = listOf(
                BottomBarItem.Home(isSelected = false),
                BottomBarItem.Activity(isSelected = true),
                BottomBarItem.Profile(isSelected = false),
            ),
            onItemClick = {},
        )
    })

    object Profile : PreviewBottomBar({
        AppBottomBar(
            items = listOf(
                BottomBarItem.Home(isSelected = false),
                BottomBarItem.Activity(isSelected = false),
                BottomBarItem.Profile(isSelected = true),
            ),
            onItemClick = {},
        )
    })
}

/**
 * A composable that is suitable as the root for any composable preview
 *
 * It will set up the theme and some suitable defaults like a background color.
 */
@Composable
fun PreviewContent(
    modifier: Modifier = Modifier,
    appState: AppState = PreviewAppState,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    backgroundColor: ColorResource? = defaultColors.background,
    bottomBar: PreviewBottomBar = PreviewBottomBar.None,
    content: @Composable () -> Unit,
) {
    val dialogHostState = rememberDialogHostState()
    CompositionLocalProvider(
        LocalAppState provides appState,
        LocalClock provides Clock.fixed(Instant.parse("2023-01-01T00:00:00Z")),
        LocalBuildInfo provides BuildInfo,
        LocalDialogHostState provides dialogHostState
    ) {
        AppThemeProvider {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .thenIf(backgroundColor != null) { background(backgroundColor!!) }
                    .padding(contentPadding),
            ) {
                content()
                Box(Modifier.align(Alignment.BottomCenter)) {
                    bottomBar.render()
                }

                DialogHost(
                    modifier = Modifier.matchParentSize(),
                    hostState = dialogHostState
                )
            }
        }
    }
}


val PreviewAppState = object : AppState {
    override val isOffline: StateFlow<Boolean> = MutableStateFlow(false)

    override val isBlockActive: StateFlow<Boolean> = MutableStateFlow(false)
}