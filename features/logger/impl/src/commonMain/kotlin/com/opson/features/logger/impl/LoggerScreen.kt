package com.opson.features.logger.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.logger_placeholder
import opson.libraries.resources.generated.resources.logger_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/** The logger's content. The entry point puts it in the sheet, so this previews on its own. */
@Suppress("UnusedParameter") // `state` is in place for the screen that replaces this.
@Composable
fun LoggerScreen(
    state: LoggerState,
    onAction: (LoggerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.spacing.page, vertical = AppTheme.spacing.section),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        SheetHeader(
            title = stringResource(Res.string.logger_title),
            onClose = { onAction(LoggerAction.CloseTapped) },
        )
        Text(text = stringResource(Res.string.logger_placeholder), typography = AppTheme.type.body, color = AppTheme.colors.muted)
    }
}

@Preview
@Composable
private fun LoggerScreenPreview() {
    PreviewLightDark {
        LoggerScreen(state = LoggerState(), onAction = {})
    }
}
