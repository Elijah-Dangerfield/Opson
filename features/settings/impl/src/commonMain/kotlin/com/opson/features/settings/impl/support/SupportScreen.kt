package com.opson.features.settings.impl.support

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.features.settings.impl.SettingsDefaults
import com.opson.features.settings.impl.SettingsHeader
import com.opson.features.settings.impl.SettingsPage
import com.opson.features.settings.impl.SettingsPreview
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.dialog.Dialog
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.libraries.ui.system.minTouchTarget
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.system.thenIf
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_about_body
import opson.libraries.resources.generated.resources.settings_about_credits
import opson.libraries.resources.generated.resources.settings_about_privacy
import opson.libraries.resources.generated.resources.settings_about_terms
import opson.libraries.resources.generated.resources.settings_about_title
import opson.libraries.resources.generated.resources.settings_back_more
import opson.libraries.resources.generated.resources.settings_delete_failed
import opson.libraries.resources.generated.resources.settings_delete_offline
import opson.libraries.resources.generated.resources.settings_export_offline
import opson.libraries.resources.generated.resources.settings_find_could_not_open
import opson.libraries.resources.generated.resources.settings_send_feedback
import opson.libraries.resources.generated.resources.settings_support_crisis_call
import opson.libraries.resources.generated.resources.settings_support_crisis_call_or_text
import opson.libraries.resources.generated.resources.settings_support_crisis_unknown
import opson.libraries.resources.generated.resources.settings_support_crisis_unknown_sub
import opson.libraries.resources.generated.resources.settings_support_crisis_where
import opson.libraries.resources.generated.resources.settings_support_data_body
import opson.libraries.resources.generated.resources.settings_support_data_title
import opson.libraries.resources.generated.resources.settings_support_delete
import opson.libraries.resources.generated.resources.settings_support_delete_button
import opson.libraries.resources.generated.resources.settings_support_delete_confirm
import opson.libraries.resources.generated.resources.settings_support_delete_title
import opson.libraries.resources.generated.resources.settings_export_numbers_body
import opson.libraries.resources.generated.resources.settings_export_numbers_go
import opson.libraries.resources.generated.resources.settings_export_numbers_not_now
import opson.libraries.resources.generated.resources.settings_export_numbers_title
import opson.libraries.resources.generated.resources.settings_support_export
import opson.libraries.resources.generated.resources.settings_support_export_sub
import opson.libraries.resources.generated.resources.settings_support_summary
import opson.libraries.resources.generated.resources.settings_support_summary_card_body
import opson.libraries.resources.generated.resources.settings_support_summary_card_title
import opson.libraries.resources.generated.resources.settings_support_summary_sub
import opson.libraries.resources.generated.resources.settings_support_find
import opson.libraries.resources.generated.resources.settings_support_hard_body
import opson.libraries.resources.generated.resources.settings_support_hard_title
import opson.libraries.resources.generated.resources.settings_support_helpline
import opson.libraries.resources.generated.resources.settings_support_keep
import opson.libraries.resources.generated.resources.settings_support_opens_browser
import opson.libraries.resources.generated.resources.settings_support_provider
import opson.libraries.resources.generated.resources.settings_support_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun SupportScreen(
    state: SupportState,
    onAction: (SupportAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(modifier = modifier) {
        SettingsHeader(
            title = stringResource(Res.string.settings_support_title),
            backLabel = stringResource(Res.string.settings_back_more),
            onBack = { onAction(SupportAction.BackTapped) },
        )
        if (state.providerSummary == ProviderSummaryOffer.First) ProviderSummaryCard(onAction)
        EdgeCard(
            edgeToEdge = false,
            contentPadding = SettingsDefaults.CardInset,
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.settings_support_hard_title),
                    typography = AppTheme.type.cardTitle,
                    color = AppTheme.colors.ink,
                )
                Text(
                    text = stringResource(Res.string.settings_support_hard_body),
                    typography = AppTheme.type.body,
                    color = AppTheme.colors.muted,
                )
            }
            if (state.findSupport) {
                FindSupportButtons(state, onAction)
            } else {
                val opens = stringResource(Res.string.settings_support_opens_browser)
                CardButton(
                    text = stringResource(Res.string.settings_support_helpline),
                    trailing = AppIcons.OpenInNew.described(opens),
                    onClick = { onAction(SupportAction.HelplineTapped) },
                )
                CardButton(
                    text = stringResource(Res.string.settings_support_provider),
                    trailing = AppIcons.OpenInNew.described(opens),
                    onClick = { onAction(SupportAction.FindProviderTapped) },
                )
            }
        }
        // A trusted person returns when it works; 0.1 shows no "Coming later" rows, which App
        // Review can read as placeholder content.
        DataCard(state, onAction)
        AboutCard(onAction)
    }
    if (state.delete == DeleteStep.Confirming || state.delete == DeleteStep.Deleting) {
        DeleteDialog(working = state.delete == DeleteStep.Deleting, onAction = onAction)
    }
    if (state.export == ExportStatus.ConfirmingNumbers) ExportNumbersDialog(onAction)
}

/**
 * `release.providerSummary`, for someone who said in setup they work with a provider: the summary
 * gets its own card at the top, in the accent, rather than a row under Your data.
 */
@Composable
private fun ProviderSummaryCard(onAction: (SupportAction) -> Unit) {
    EdgeCard(
        edgeToEdge = false,
        contentPadding = SettingsDefaults.CardInset,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(Res.string.settings_support_summary_card_title),
                typography = AppTheme.type.cardTitle,
                color = AppTheme.colors.ink,
            )
            Text(
                text = stringResource(Res.string.settings_support_summary_card_body),
                typography = AppTheme.type.body,
                color = AppTheme.colors.muted,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .clip(AppTheme.shapes.inputLarge)
                .background(AppTheme.colors.accent)
                .semantics { role = Role.Button }
                .bounceClick(scaleDown = 0.98f) { onAction(SupportAction.ProviderSummaryTapped) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Icon(icon = AppIcons.EventNote, size = 24.dp, color = AppTheme.colors.onAccent)
            Text(
                text = stringResource(Res.string.settings_support_summary),
                typography = AppTheme.type.cardTitle,
                color = AppTheme.colors.onAccent,
                modifier = Modifier.weight(1f),
            )
            Icon(icon = AppIcons.ChevronRight, size = 24.dp, color = AppTheme.colors.onAccent)
        }
    }
}

/** Said before the file opens, for a mode that keeps any of its numbers out of sight. */
@Composable
private fun ExportNumbersDialog(onAction: (SupportAction) -> Unit) {
    Dialog(onDismissRequest = { onAction(SupportAction.ExportNotNow) }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.bg, AppTheme.shapes.dialog)
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Icon(icon = AppIcons.Download, size = 28.dp, color = AppTheme.colors.muted)
            Text(
                text = stringResource(Res.string.settings_export_numbers_title),
                typography = AppTheme.type.dayLabel,
                color = AppTheme.colors.ink,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.settings_export_numbers_body),
                typography = AppTheme.type.body,
                color = AppTheme.colors.inkSoft,
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            ) {
                FlatButton(
                    text = stringResource(Res.string.settings_export_numbers_not_now),
                    onClick = { onAction(SupportAction.ExportNotNow) },
                    tone = FlatButtonTone.Text,
                    typography = AppTheme.type.button,
                )
                FlatButton(
                    text = stringResource(Res.string.settings_export_numbers_go),
                    onClick = { onAction(SupportAction.ExportConfirmed) },
                    tone = FlatButtonTone.AccentText,
                    typography = AppTheme.type.button,
                )
            }
        }
    }
}

/**
 * Design sync 2 (`release.findSupport`): "Find support near you" in the accent, then the crisis
 * line for the phone's country, which dials. With no list for the region the row opens Find
 * support instead.
 */
@Composable
private fun FindSupportButtons(state: SupportState, onAction: (SupportAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clip(AppTheme.shapes.inputLarge)
            .background(AppTheme.colors.accent)
            .semantics { role = Role.Button }
            .bounceClick(scaleDown = 0.98f) { onAction(SupportAction.FindSupportTapped) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Icon(icon = AppIcons.SupportAgent, size = 24.dp, color = AppTheme.colors.onAccent)
        Text(
            text = stringResource(Res.string.settings_support_find),
            typography = AppTheme.type.cardTitle,
            color = AppTheme.colors.onAccent,
            modifier = Modifier.weight(1f),
        )
        Icon(icon = AppIcons.ChevronRight, size = 24.dp, color = AppTheme.colors.onAccent)
    }
    val crisis = state.crisis
    CardButton(
        text = when {
            crisis == null -> stringResource(Res.string.settings_support_crisis_unknown)
            crisis.callAndTextShareANumber -> stringResource(Res.string.settings_support_crisis_call_or_text, crisis.call.shown)
            else -> stringResource(Res.string.settings_support_crisis_call, crisis.call.shown)
        },
        subtitle = crisis?.let { stringResource(Res.string.settings_support_crisis_where, stringResource(it.country.label)) }
            ?: stringResource(Res.string.settings_support_crisis_unknown_sub),
        leading = AppIcons.Call,
        onClick = { onAction(SupportAction.CrisisTapped) },
    )
    state.couldNotDial?.let { Note(stringResource(Res.string.settings_find_could_not_open, it)) }
}

@Composable
private fun DataCard(state: SupportState, onAction: (SupportAction) -> Unit) {
    EdgeCard(
        edgeToEdge = false,
        contentPadding = SettingsDefaults.CardInset,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(Res.string.settings_support_data_title),
                typography = AppTheme.type.cardTitle,
                color = AppTheme.colors.ink,
            )
            Text(
                text = stringResource(Res.string.settings_support_data_body),
                typography = AppTheme.type.body,
                color = AppTheme.colors.muted,
            )
        }
        if (state.providerSummary == ProviderSummaryOffer.InYourData) {
            CardButton(
                text = stringResource(Res.string.settings_support_summary),
                subtitle = stringResource(Res.string.settings_support_summary_sub),
                leading = AppIcons.EventNote,
                onClick = { onAction(SupportAction.ProviderSummaryTapped) },
            )
        }
        CardButton(
            text = stringResource(Res.string.settings_support_export),
            subtitle = stringResource(Res.string.settings_support_export_sub),
            leading = AppIcons.Download,
            enabled = state.export != ExportStatus.Working,
            onClick = { onAction(SupportAction.ExportTapped) },
        )
        if (state.export == ExportStatus.Failed) Note(stringResource(Res.string.settings_export_offline))
        CardButton(
            text = stringResource(Res.string.settings_support_delete),
            leading = AppIcons.DeleteOutline,
            leadingColor = AppTheme.colors.muted,
            outlined = true,
            onClick = { onAction(SupportAction.DeleteTapped) },
        )
        (state.delete as? DeleteStep.Failed)?.let { failed ->
            Note(stringResource(if (failed.offline) Res.string.settings_delete_offline else Res.string.settings_delete_failed))
        }
        CardButton(
            text = stringResource(Res.string.settings_send_feedback),
            onClick = { onAction(SupportAction.SendFeedbackTapped) },
        )
    }
}

/** What Opson is and isn't, in a quiet card at the end, then the published legal pages. */
@Composable
private fun AboutCard(onAction: (SupportAction) -> Unit) {
    EdgeCard(
        edgeToEdge = false,
        contentPadding = SettingsDefaults.CardInset,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(Res.string.settings_about_title),
                typography = AppTheme.type.cardTitle,
                color = AppTheme.colors.ink,
            )
            Text(
                text = stringResource(Res.string.settings_about_body),
                typography = AppTheme.type.body,
                color = AppTheme.colors.muted,
            )
        }
        val opens = AppIcons.OpenInNew.described(stringResource(Res.string.settings_support_opens_browser))
        CardButton(
            text = stringResource(Res.string.settings_about_privacy),
            trailing = opens,
            onClick = { onAction(SupportAction.PrivacyTapped) },
        )
        CardButton(
            text = stringResource(Res.string.settings_about_terms),
            trailing = opens,
            onClick = { onAction(SupportAction.TermsTapped) },
        )
        CardButton(
            text = stringResource(Res.string.settings_about_credits),
            trailing = opens,
            onClick = { onAction(SupportAction.CreditsTapped) },
        )
    }
}

/** A rounded button inside a card: soft fill (or a line border), an optional icon at either end. */
@Composable
private fun CardButton(
    text: String,
    onClick: () -> Unit,
    leading: IconResource? = null,
    leadingColor: ColorResource = AppTheme.colors.accent,
    trailing: IconResource? = null,
    outlined: Boolean = false,
    enabled: Boolean = true,
    subtitle: String? = null,
) {
    val shape = AppTheme.shapes.inputLarge
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minTouchTarget()
            .defaultMinSize(minHeight = 52.dp)
            .clip(shape)
            .thenIf(!outlined) { background(AppTheme.colors.soft) }
            .thenIf(outlined) { border(1.dp, AppTheme.colors.line.color, shape) }
            .semantics { role = Role.Button }
            .bounceClick(enabled = enabled, scaleDown = 0.98f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        if (leading != null) Icon(icon = leading, size = 24.dp, color = leadingColor)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = text,
                typography = AppTheme.type.cardTitle,
                color = if (enabled) AppTheme.colors.ink else AppTheme.colors.muted,
            )
            if (subtitle != null) Text(text = subtitle, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
        if (trailing != null) Icon(icon = trailing, size = 24.dp, color = AppTheme.colors.accent)
    }
}

/**
 * Design v3: a modal dialog, so it can't be confirmed by accident while scrolling. The safe
 * choice is the accent; Delete stays neutral, because the app has no red. Tapping outside or
 * back keeps the data, except while the delete is already running.
 */
@Composable
private fun DeleteDialog(working: Boolean, onAction: (SupportAction) -> Unit) {
    Dialog(onDismissRequest = { if (!working) onAction(SupportAction.KeepMyDataTapped) }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.bg, AppTheme.shapes.dialog)
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Icon(icon = AppIcons.DeleteOutline, size = 28.dp, color = AppTheme.colors.muted)
            Text(
                text = stringResource(Res.string.settings_support_delete_title),
                typography = AppTheme.type.dayLabel,
                color = AppTheme.colors.ink,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.settings_support_delete_confirm),
                typography = AppTheme.type.body,
                color = AppTheme.colors.inkSoft,
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            ) {
                FlatButton(
                    text = stringResource(Res.string.settings_support_keep),
                    onClick = { onAction(SupportAction.KeepMyDataTapped) },
                    tone = FlatButtonTone.AccentText,
                    enabled = !working,
                    typography = AppTheme.type.button,
                )
                FlatButton(
                    text = stringResource(Res.string.settings_support_delete_button),
                    onClick = { onAction(SupportAction.DeleteConfirmed) },
                    tone = FlatButtonTone.Text,
                    enabled = !working,
                    typography = AppTheme.type.button,
                )
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text = text, typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted)
}

/** Design sync 2, `release.findSupport` on, the phone set to the US. */
@Preview
@Composable
private fun SupportPreview() {
    SettingsPreview { SupportScreen(SupportStateBuilder.build(findSupport = true, SupportCountry.UnitedStates), onAction = {}) }
}

/** `release.findSupport` off: the 0.1 helpline links. */
@Preview
@Composable
private fun SupportLinksPreview() {
    SettingsPreview { SupportScreen(SupportState(), onAction = {}) }
}

/** A region with no list: the crisis row opens Find support. */
@Preview
@Composable
private fun SupportNoCountryPreview() {
    SettingsPreview { SupportScreen(SupportStateBuilder.build(findSupport = true, country = null), onAction = {}) }
}

/** Find support (10.3), paired with the design's `support-screen__support-find`. */
@Preview
@Composable
private fun SupportFindPreview() {
    SettingsPreview { FindSupportScreen(FindSupportStateBuilder.build(SupportCountry.UnitedStates), onAction = {}) }
}

@Preview
@Composable
private fun SupportDeletePreview() {
    val state = SupportStateBuilder.build(findSupport = true, SupportCountry.UnitedStates).copy(delete = DeleteStep.Confirming)
    SettingsPreview { SupportScreen(state, onAction = {}) }
}

@Preview
@Composable
private fun SupportOfflineDarkPreview() {
    SettingsPreview(dark = true) {
        SupportScreen(SupportState(export = ExportStatus.Failed, delete = DeleteStep.Failed(offline = true)), onAction = {})
    }
}

/** Before the file opens, in a mode that keeps some of its numbers out of sight. */
@Preview
@Composable
private fun SupportExportNumbersPreview() {
    val state = SupportStateBuilder.build(findSupport = true, SupportCountry.UnitedStates).copy(export = ExportStatus.ConfirmingNumbers)
    SettingsPreview { SupportScreen(state, onAction = {}) }
}

/** `release.providerSummary`, for someone who said in setup they work with a provider. */
@Preview
@Composable
private fun SupportProviderFirstPreview() {
    val state = SupportStateBuilder.build(findSupport = true, SupportCountry.UnitedStates, providerSummary = true, workingWithProvider = true)
    SettingsPreview { SupportScreen(state, onAction = {}) }
}

/** `release.providerSummary`, for everyone else: a row beside export. */
@Preview
@Composable
private fun SupportProviderRowPreview() {
    val state = SupportStateBuilder.build(findSupport = true, SupportCountry.UnitedStates, providerSummary = true)
    SettingsPreview { SupportScreen(state, onAction = {}) }
}
