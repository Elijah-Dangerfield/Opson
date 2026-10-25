package com.opson.libraries.navigation.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.button.Button
import com.opson.libraries.ui.components.button.ButtonSize
import com.opson.libraries.ui.components.button.ButtonType
import com.opson.libraries.ui.components.dialog.BasicDialog
import com.opson.libraries.ui.components.dialog.DialogState
import com.opson.libraries.ui.components.dialog.rememberDialogState
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.Dimension
import com.opson.system.VerticalSpacerD1000
import com.opson.system.VerticalSpacerD500
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.common_error_code
import opson.libraries.resources.generated.resources.common_report_to_developers
import opson.libraries.resources.generated.resources.common_report_to_developers_aside
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
internal fun ErrorDialog(
    state: DialogState,
    title: String,
    subtitle: String,
    actionTitle: String,
    errorCode: Int?,
    onDismissRequest: () -> Unit,
    onAction: () -> Unit,
    onReportToDeveloper: (() -> Unit)? = null,
    reportActionTitle: String = stringResource(Res.string.common_report_to_developers),
) {
    val showReportButton = onReportToDeveloper != null

    BasicDialog(
        state = state,
        onDismissRequest = onDismissRequest,
        topContent = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimension.D200)) {
                Text(text = title)
            }
        },
        content = {
            Column {
                Text(text = subtitle)

                errorCode?.let {
                    VerticalSpacerD1000()
                    Text(
                        text = stringResource(Res.string.common_error_code, it),
                        typography = AppTheme.typography.Body.B500.Bold,
                        color = AppTheme.colors.textSecondary,
                    )
                }

            }
        },
        bottomContent = {
            Column {
                Button(
                    size = ButtonSize.Medium,
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = actionTitle)
                }
                if (showReportButton) {
                    Spacer(modifier = Modifier.height(Dimension.D600))
                    Button(
                        size = ButtonSize.Medium,
                        type = ButtonType.Secondary,
                        onClick = { onReportToDeveloper() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = reportActionTitle)
                    }

                    VerticalSpacerD500()

                    Text(stringResource(Res.string.common_report_to_developers_aside))
                }
            }
        }
    )
}

@Preview
@Composable
private fun ErrorDialogContentPreview() {
    PreviewContent {
        ErrorDialog(
            state = rememberDialogState(),
            title = "Yikes.",
            subtitle = "We hit a snag while loading. Please try again in a sec.",
            actionTitle = "Okay",
            errorCode = 1003,
            onDismissRequest = {},
            onAction = {},
            onReportToDeveloper = {}
        )
    }
}
