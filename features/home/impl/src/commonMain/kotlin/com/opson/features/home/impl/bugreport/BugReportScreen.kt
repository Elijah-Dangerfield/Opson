package com.opson.features.home.impl.bugreport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import com.opson.system.AppTheme
import com.opson.system.Dimension
import com.opson.system.VerticalSpacerD1000
import com.opson.system.VerticalSpacerD500
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.SectionCard
import com.opson.libraries.ui.components.SummaryRow
import com.opson.libraries.ui.components.button.Button
import com.opson.libraries.ui.components.button.ButtonSize
import com.opson.libraries.ui.components.header.TopBar
import com.opson.libraries.ui.components.text.OutlinedTextField
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.screenContentPadding
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.bug_report_captured_details
import opson.libraries.resources.generated.resources.bug_report_error_code_label
import opson.libraries.resources.generated.resources.bug_report_id_label
import opson.libraries.resources.generated.resources.bug_report_prompt
import opson.libraries.resources.generated.resources.bug_report_title
import opson.libraries.resources.generated.resources.common_message_label
import opson.libraries.resources.generated.resources.common_message_placeholder
import opson.libraries.resources.generated.resources.common_send
import opson.libraries.resources.generated.resources.common_sending
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

private const val BUG_REPORT_CHAR_LIMIT = 180

@Composable
fun BugReportScreen(
    state: BugReportState,
    onAction: (BugReportAction) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    Screen(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopBar(
                title = stringResource(Res.string.bug_report_title),
                onNavigateBack = { onAction(BugReportAction.Back) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .screenContentPadding(
                    paddingValues = paddingValues,
                    includeImePadding = true
                ),
            verticalArrangement = Arrangement.Top
        ) {
            VerticalSpacerD1000()

            if (state.hasContext) {
                SectionCard(title = stringResource(Res.string.bug_report_captured_details)) {
                    state.contextMessage?.let {
                        Text(
                            text = it,
                            typography = AppTheme.typography.Body.B500,
                            color = AppTheme.colors.ink
                        )
                    }

                    state.errorCode?.let {
                        SummaryRow(
                            label = stringResource(Res.string.bug_report_error_code_label),
                            value = "$it"
                        )
                    }

                    state.logId?.let {
                        SummaryRow(
                            label = stringResource(Res.string.bug_report_id_label),
                            value = it
                        )
                    }
                }

                VerticalSpacerD1000()
            }

            Text(
                text = stringResource(Res.string.bug_report_prompt),
                typography = AppTheme.typography.Body.B700,
                color = AppTheme.colors.textSecondary
            )

            VerticalSpacerD1000()

            OutlinedTextField(
                value = state.message,
                onValueChange = { newValue ->
                    val limited = newValue.take(BUG_REPORT_CHAR_LIMIT)
                    onAction(BugReportAction.MessageChanged(limited))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimension.D1900),
                label = { Text(stringResource(Res.string.common_message_label)) },
                placeholder = { Text(stringResource(Res.string.common_message_placeholder)) },
                singleLine = false,
                minLines = 6,
                maxLines = 10,
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (state.canSubmit) {
                            focusManager.clearFocus(force = true)
                            onAction(BugReportAction.Submit)
                        }
                    }
                )
            )

            VerticalSpacerD500()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val messageLength = state.message.length
                val counterColor = if (messageLength >= BUG_REPORT_CHAR_LIMIT) {
                    AppTheme.colors.ink
                } else {
                    AppTheme.colors.textSecondary
                }

                Text(
                    text = "$messageLength/$BUG_REPORT_CHAR_LIMIT",
                    color = counterColor,
                    typography = AppTheme.typography.Body.B500
                )
            }

            state.errorMessage?.let {
                VerticalSpacerD500()
                Text(
                    text = stringResource(it),
                    // Arrives after a failed send, away from where focus is; say it out loud.
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color = AppTheme.colors.ink,
                    textAlign = TextAlign.Start
                )
            }

            VerticalSpacerD1000()

            Button(
                modifier = Modifier.fillMaxWidth(),
                size = ButtonSize.Large,
                enabled = state.canSubmit,
                onClick = {
                    focusManager.clearFocus(force = true)
                    if (state.canSubmit) {
                        onAction(BugReportAction.Submit)
                    }
                }
            ) {
                Text(
                    if (state.isSubmitting) {
                        stringResource(Res.string.common_sending)
                    } else {
                        stringResource(Res.string.common_send)
                    },
                )
            }

            VerticalSpacerD500()
        }
    }
}

@Preview
@Composable
private fun BugReportScreenPreview() {
    PreviewContent {
        BugReportScreen(
            state = BugReportState(
                message = "The shortcut sheet would not open.",
                logId = "12356j32345k1",
                errorCode = 1200,
                contextMessage = "Something went wrong while loading."
            ),
            onAction = {}
        )
    }
}
