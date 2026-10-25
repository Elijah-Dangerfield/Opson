package com.opson.libraries.ui.components.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.opson.system.AppTheme
import com.opson.system.Dimension
import com.opson.system.VerticalSpacerD500
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.button.Button
import com.opson.libraries.ui.components.button.ButtonSize
import com.opson.libraries.ui.components.button.ButtonStyle
import com.opson.libraries.ui.components.button.ButtonType
import com.opson.libraries.ui.components.text.Text
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.shake_dismiss
import opson.libraries.resources.generated.resources.shake_report_a_bug
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

// Debug-only CTA label — dev-facing, so a constant rather than a string
// resource (the button never renders in release builds).
private const val NetworkInspectorCta = "Network inspector"

@Composable
fun ShakeDialog(
    headline: String,
    subtext: String?,
    onDismiss: () -> Unit,
    onReportBug: () -> Unit,
    modifier: Modifier = Modifier,
    state: DialogState = rememberDialogState(),
    // Debug-only: when non-null, an extra action opens the on-device network
    // inspector. Release callers leave this null so the button never shows.
    onOpenNetworkInspector: (() -> Unit)? = null,
) {
    BasicDialog(
        state = state,
        onDismissRequest = onDismiss,
        modifier = modifier,
        topContent = {
            Text(
                text = headline,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (subtext != null) {
                    Spacer(modifier = Modifier.height(Dimension.D300))
                    Text(
                        text = subtext,
                        typography = AppTheme.typography.Body.B600,
                        color = AppTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    VerticalSpacerD500()
                }
            }
        },
        bottomContent = {
            Column{
                Button(
                    onClick = {
                        state.dismiss()
                        onReportBug()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    size = ButtonSize.Medium,
                    type = ButtonType.Danger,
                ) {
                    Text(stringResource(Res.string.shake_report_a_bug))
                }

                if (onOpenNetworkInspector != null) {
                    Spacer(modifier = Modifier.height(Dimension.D500))
                    Button(
                        onClick = {
                            state.dismiss()
                            onOpenNetworkInspector()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        size = ButtonSize.Medium,
                        type = ButtonType.Secondary,
                    ) {
                        Text(NetworkInspectorCta)
                    }
                }

                Spacer(modifier = Modifier.height(Dimension.D500))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    size = ButtonSize.Medium,
                    style = ButtonStyle.Text
                ) {
                    Text(stringResource(Res.string.shake_dismiss))
                }
            }
        }
    )
}

@Preview
@Composable
private fun ShakeDialogPreview_WithSubtext() {
    PreviewContent {
        ShakeDialog(
            headline = "I felt that.",
            subtext = "Testing the waters?",
            onDismiss = {},
            onReportBug = {},
        )
    }
}

@Preview
@Composable
private fun ShakeDialogPreview_NoSubtext() {
    PreviewContent {
        ShakeDialog(
            headline = "Whoa.",
            subtext = null,
            onDismiss = {},
            onReportBug = {},
        )
    }
}

@Preview
@Composable
private fun ShakeDialogPreview_WithInspector() {
    PreviewContent {
        ShakeDialog(
            headline = "I felt that.",
            subtext = "Testing the waters?",
            onDismiss = {},
            onReportBug = {},
            onOpenNetworkInspector = {},
        )
    }
}

@Preview
@Composable
private fun ShakeDialogPreview_LongMessage() {
    PreviewContent {
        ShakeDialog(
            headline = "You really like shaking me.",
            subtext = "I've lost count.",
            onDismiss = {},
            onReportBug = {},
        )
    }
}
