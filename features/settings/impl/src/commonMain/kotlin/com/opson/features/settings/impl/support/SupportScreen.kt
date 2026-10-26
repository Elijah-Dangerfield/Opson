package com.opson.features.settings.impl.support

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.opson.features.settings.impl.LightAndDark
import com.opson.features.settings.impl.SettingsPage
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.EdgeCardDefaults
import com.opson.libraries.ui.components.ListRow
import com.opson.libraries.ui.components.PageHeader
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonDefaults
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_back_more
import opson.libraries.resources.generated.resources.settings_delete_failed
import opson.libraries.resources.generated.resources.settings_delete_offline
import opson.libraries.resources.generated.resources.settings_export_offline
import opson.libraries.resources.generated.resources.settings_send_feedback
import opson.libraries.resources.generated.resources.settings_support_clinician
import opson.libraries.resources.generated.resources.settings_support_clinician_sub
import opson.libraries.resources.generated.resources.settings_support_coming_later
import opson.libraries.resources.generated.resources.settings_support_data_body
import opson.libraries.resources.generated.resources.settings_support_data_title
import opson.libraries.resources.generated.resources.settings_support_delete
import opson.libraries.resources.generated.resources.settings_support_delete_button
import opson.libraries.resources.generated.resources.settings_support_delete_confirm
import opson.libraries.resources.generated.resources.settings_support_export
import opson.libraries.resources.generated.resources.settings_support_hard_body
import opson.libraries.resources.generated.resources.settings_support_hard_title
import opson.libraries.resources.generated.resources.settings_support_helpline
import opson.libraries.resources.generated.resources.settings_support_keep
import opson.libraries.resources.generated.resources.settings_support_provider
import opson.libraries.resources.generated.resources.settings_support_title
import opson.libraries.resources.generated.resources.settings_support_trusted
import opson.libraries.resources.generated.resources.settings_support_trusted_sub
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun SupportScreen(
    state: SupportState,
    onAction: (SupportAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(modifier = modifier) {
        PageHeader(
            title = stringResource(Res.string.settings_support_title),
            backLabel = stringResource(Res.string.settings_back_more),
            onBack = { onAction(SupportAction.BackTapped) },
        )
        EdgeCard(
            edgeToEdge = false,
            contentPadding = EdgeCardDefaults.InsetPadding,
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Text(
                text = stringResource(Res.string.settings_support_hard_title),
                typography = AppTheme.type.bodyStrong,
                color = AppTheme.colors.ink,
            )
            Text(
                text = stringResource(Res.string.settings_support_hard_body),
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.muted,
            )
            FlatButton(
                text = stringResource(Res.string.settings_support_helpline),
                onClick = { onAction(SupportAction.HelplineTapped) },
                modifier = Modifier.fillMaxWidth(),
            )
            FlatButton(
                text = stringResource(Res.string.settings_support_provider),
                onClick = { onAction(SupportAction.FindProviderTapped) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        EdgeCard(edgeToEdge = false, contentPadding = EdgeCardDefaults.ListPadding, verticalArrangement = Arrangement.Top) {
            val later = stringResource(Res.string.settings_support_coming_later)
            ListRow(
                title = stringResource(Res.string.settings_support_trusted),
                subtitle = stringResource(Res.string.settings_support_trusted_sub),
                trailingText = later,
                showDivider = true,
            )
            ListRow(
                title = stringResource(Res.string.settings_support_clinician),
                subtitle = stringResource(Res.string.settings_support_clinician_sub),
                trailingText = later,
            )
        }
        DataCard(state, onAction)
    }
}

@Composable
private fun DataCard(state: SupportState, onAction: (SupportAction) -> Unit) {
    EdgeCard(edgeToEdge = false, contentPadding = EdgeCardDefaults.InsetPadding) {
        Text(
            text = stringResource(Res.string.settings_support_data_title),
            typography = AppTheme.type.bodyStrong,
            color = AppTheme.colors.ink,
        )
        Text(
            text = stringResource(Res.string.settings_support_data_body),
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.muted,
        )
        FlatButton(
            text = stringResource(Res.string.settings_support_export),
            onClick = { onAction(SupportAction.ExportTapped) },
            enabled = state.export != ExportStatus.Working,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.export == ExportStatus.Failed) Note(stringResource(Res.string.settings_export_offline))
        when (val delete = state.delete) {
            DeleteStep.Idle, is DeleteStep.Failed -> {
                FlatButton(
                    text = stringResource(Res.string.settings_support_delete),
                    onClick = { onAction(SupportAction.DeleteTapped) },
                    tone = FlatButtonTone.Outlined,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (delete is DeleteStep.Failed) {
                    Note(
                        stringResource(
                            if (delete.offline) Res.string.settings_delete_offline else Res.string.settings_delete_failed,
                        ),
                    )
                }
            }
            DeleteStep.Confirming, DeleteStep.Deleting -> DeleteConfirm(working = delete == DeleteStep.Deleting, onAction)
        }
        FlatButton(
            text = stringResource(Res.string.settings_send_feedback),
            onClick = { onAction(SupportAction.SendFeedbackTapped) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DeleteConfirm(working: Boolean, onAction: (SupportAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(Dp.Hairline, AppTheme.colors.line.color, AppTheme.shapes.inputLarge)
            .padding(AppTheme.spacing.section),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
    ) {
        Text(
            text = stringResource(Res.string.settings_support_delete_confirm),
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.ink,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
            FlatButton(
                text = stringResource(Res.string.settings_support_keep),
                onClick = { onAction(SupportAction.KeepMyDataTapped) },
                centered = true,
                enabled = !working,
                shape = AppTheme.shapes.input,
                minHeight = FlatButtonDefaults.CompactMinHeight,
                modifier = Modifier.weight(1f),
            )
            FlatButton(
                text = stringResource(Res.string.settings_support_delete_button),
                onClick = { onAction(SupportAction.DeleteConfirmed) },
                tone = FlatButtonTone.Accent,
                centered = true,
                enabled = !working,
                shape = AppTheme.shapes.input,
                minHeight = FlatButtonDefaults.CompactMinHeight,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text = text, typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted)
}

@Preview(widthDp = 800, heightDp = 1000)
@Composable
private fun SupportPreview() {
    LightAndDark { SupportScreen(SupportState(), onAction = {}) }
}

@Preview(widthDp = 800, heightDp = 1000)
@Composable
private fun SupportConfirmingPreview() {
    LightAndDark { SupportScreen(SupportState(delete = DeleteStep.Confirming), onAction = {}) }
}

@Preview(widthDp = 800, heightDp = 1000)
@Composable
private fun SupportOfflinePreview() {
    LightAndDark {
        SupportScreen(SupportState(export = ExportStatus.Failed, delete = DeleteStep.Failed(offline = true)), onAction = {})
    }
}
