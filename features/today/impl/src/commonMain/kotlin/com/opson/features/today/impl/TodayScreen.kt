package com.opson.features.today.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_placeholder
import opson.libraries.resources.generated.resources.today_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Suppress("UnusedParameter") // The pattern is in place for the screen that replaces this.
@Composable
fun TodayScreen(
    state: TodayState,
    onAction: (TodayAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AppTheme.spacing.page, vertical = AppTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
        ) {
            Text(text = stringResource(Res.string.today_title), typography = AppTheme.type.h1, color = AppTheme.colors.ink)
            Text(text = stringResource(Res.string.today_placeholder), typography = AppTheme.type.body, color = AppTheme.colors.muted)
        }
    }
}

@Preview
@Composable
private fun TodayScreenPreview() {
    PreviewLightDark {
        TodayScreen(state = TodayState, onAction = {})
    }
}
