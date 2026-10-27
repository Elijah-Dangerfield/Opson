package com.opson.features.settings.impl.more

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.features.settings.impl.SectionLabel
import com.opson.features.settings.impl.SettingsCard
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.SettingsPage
import com.opson.features.settings.impl.SettingsPreview
import com.opson.features.settings.impl.SettingsRow
import com.opson.features.settings.impl.label
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.dialog.Dialog
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.fasting_more_off
import opson.libraries.resources.generated.resources.fasting_more_on
import opson.libraries.resources.generated.resources.fasting_title
import opson.libraries.resources.generated.resources.integrations_more_connected
import opson.libraries.resources.generated.resources.integrations_more_not_connected
import opson.libraries.resources.generated.resources.integrations_more_title
import opson.libraries.resources.generated.resources.reminders_more_quiet_off
import opson.libraries.resources.generated.resources.reminders_more_quiet_on
import opson.libraries.resources.generated.resources.reminders_more_row
import opson.libraries.resources.generated.resources.settings_about_credits
import opson.libraries.resources.generated.resources.settings_about_privacy
import opson.libraries.resources.generated.resources.settings_about_terms
import opson.libraries.resources.generated.resources.settings_log_out_body
import opson.libraries.resources.generated.resources.settings_log_out_body_guest
import opson.libraries.resources.generated.resources.settings_log_out_cancel
import opson.libraries.resources.generated.resources.settings_log_out_title
import opson.libraries.resources.generated.resources.settings_modes_changes
import opson.libraries.resources.generated.resources.settings_more_about
import opson.libraries.resources.generated.resources.settings_more_account
import opson.libraries.resources.generated.resources.settings_more_guest
import opson.libraries.resources.generated.resources.settings_more_log_out
import opson.libraries.resources.generated.resources.settings_more_look
import opson.libraries.resources.generated.resources.settings_more_look_sub
import opson.libraries.resources.generated.resources.settings_more_modes
import opson.libraries.resources.generated.resources.settings_more_modes_changed
import opson.libraries.resources.generated.resources.settings_more_plan
import opson.libraries.resources.generated.resources.settings_more_plan_free
import opson.libraries.resources.generated.resources.settings_more_qa
import opson.libraries.resources.generated.resources.settings_more_qa_sub
import opson.libraries.resources.generated.resources.settings_more_signed_in
import opson.libraries.resources.generated.resources.settings_more_signed_in_as
import opson.libraries.resources.generated.resources.settings_more_support
import opson.libraries.resources.generated.resources.settings_more_support_sub
import opson.libraries.resources.generated.resources.settings_more_targets
import opson.libraries.resources.generated.resources.settings_more_targets_sub
import opson.libraries.resources.generated.resources.settings_more_testing
import opson.libraries.resources.generated.resources.settings_more_title
import opson.libraries.resources.generated.resources.settings_more_today
import opson.libraries.resources.generated.resources.settings_more_today_sub
import opson.libraries.resources.generated.resources.settings_more_version
import opson.libraries.resources.generated.resources.settings_more_water
import opson.libraries.resources.generated.resources.settings_more_water_glasses
import opson.libraries.resources.generated.resources.widgets_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * [version] is the line under the cards ([MoreVersion]); the entry point reads it from the build,
 * so previews pass a fixed one.
 */
@Composable
fun MoreScreen(
    state: MoreState,
    onAction: (MoreAction) -> Unit,
    modifier: Modifier = Modifier,
    version: String = PREVIEW_VERSION,
    scrollState: ScrollState = rememberScrollState(),
) {
    SettingsPage(modifier = modifier, scrollState = scrollState) {
        Text(
            text = stringResource(Res.string.settings_more_title),
            typography = AppTheme.type.h1,
            color = AppTheme.colors.ink,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp),
        )
        state.groups.forEach { group ->
            group.section()?.let { SectionLabel(stringResource(it.label)) }
            SettingsCard {
                group.forEachIndexed { index, row ->
                    MoreRowItem(
                        row = row,
                        showDivider = index < group.lastIndex,
                        onClick = { onAction(MoreAction.RowTapped(row)) },
                    )
                }
            }
        }
        Text(
            text = stringResource(Res.string.settings_more_version, version),
            typography = AppTheme.type.secondarySmall,
            color = AppTheme.colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
    when (val step = state.logOut) {
        is LogOutStep.Confirming -> LogOutDialog(guest = step.guest, working = false, onAction = onAction)
        is LogOutStep.Working -> LogOutDialog(guest = step.guest, working = true, onAction = onAction)
        LogOutStep.Idle -> Unit
    }
}

private val MoreSection.label: StringResource
    get() = when (this) {
        MoreSection.About -> Res.string.settings_more_about
        MoreSection.Account -> Res.string.settings_more_account
        MoreSection.Testing -> Res.string.settings_more_testing
    }

@Composable
private fun MoreRowItem(row: MoreRow, showDivider: Boolean, onClick: () -> Unit) {
    when (row) {
        is MoreRow.Modes -> {
            val preset = stringResource(row.preset.label)
            SettingsRow(
                title = stringResource(Res.string.settings_more_modes),
                icon = AppIcons.Tune,
                subtitle = if (row.changes > 0) {
                    stringResource(
                        Res.string.settings_more_modes_changed,
                        preset,
                        pluralStringResource(Res.plurals.settings_modes_changes, row.changes, row.changes),
                    )
                } else {
                    preset
                },
                chevron = true,
                showDivider = showDivider,
                onClick = onClick,
            )
        }
        MoreRow.Targets -> SettingsRow(
            title = stringResource(Res.string.settings_more_targets),
            icon = AppIcons.TrackChanges,
            subtitle = stringResource(Res.string.settings_more_targets_sub),
            chevron = true,
            showDivider = showDivider,
            onClick = onClick,
        )
        MoreRow.TodayScreen -> SettingsRow(
            title = stringResource(Res.string.settings_more_today),
            icon = AppIcons.DashboardCustomize,
            subtitle = stringResource(Res.string.settings_more_today_sub),
            chevron = true,
            showDivider = showDivider,
            onClick = onClick,
        )
        MoreRow.LookAndFeel -> SettingsRow(
            title = stringResource(Res.string.settings_more_look),
            icon = AppIcons.Palette,
            subtitle = stringResource(Res.string.settings_more_look_sub),
            chevron = true,
            showDivider = showDivider,
            onClick = onClick,
        )
        is MoreRow.Reminders -> SettingsRow(
            title = stringResource(Res.string.reminders_more_row),
            icon = AppIcons.Notifications,
            subtitle = stringResource(if (row.quiet) Res.string.reminders_more_quiet_on else Res.string.reminders_more_quiet_off),
            chevron = true,
            showDivider = showDivider,
            onClick = onClick,
        )
        is MoreRow.Health -> SettingsRow(
            title = stringResource(Res.string.integrations_more_title),
            icon = AppIcons.Watch,
            subtitle = stringResource(if (row.connected) Res.string.integrations_more_connected else Res.string.integrations_more_not_connected),
            chevron = true,
            showDivider = showDivider,
            onClick = onClick,
        )
        is MoreRow.Water -> SettingsRow(
            title = stringResource(Res.string.settings_more_water),
            icon = AppIcons.WaterDropOutline,
            quietIcon = true,
            trailingText = pluralStringResource(Res.plurals.settings_more_water_glasses, row.glasses, row.glasses),
            showDivider = showDivider,
            onClick = onClick,
        )
        is MoreRow.Fasting -> SettingsRow(
            title = stringResource(Res.string.fasting_title),
            icon = AppIcons.Schedule,
            quietIcon = true,
            trailingText = if (row.on) stringResource(Res.string.fasting_more_on, row.plan.key) else stringResource(Res.string.fasting_more_off),
            showDivider = showDivider,
            onClick = onClick,
        )
        MoreRow.Widgets -> SettingsRow(
            title = stringResource(Res.string.widgets_title),
            icon = AppIcons.Widgets,
            quietIcon = true,
            showDivider = showDivider,
            onClick = onClick,
        )
        MoreRow.Support -> SettingsRow(
            title = stringResource(Res.string.settings_more_support),
            icon = AppIcons.Support,
            quietIcon = true,
            subtitle = stringResource(Res.string.settings_more_support_sub),
            showDivider = showDivider,
            onClick = onClick,
        )
        MoreRow.Plan -> SettingsRow(
            title = stringResource(Res.string.settings_more_plan),
            icon = AppIcons.WorkspacePremium,
            quietIcon = true,
            trailingText = stringResource(Res.string.settings_more_plan_free),
            showDivider = showDivider,
        )
        MoreRow.Privacy -> LinkRow(Res.string.settings_about_privacy, AppIcons.LockOutlined, showDivider, onClick)
        MoreRow.Terms -> LinkRow(Res.string.settings_about_terms, AppIcons.EventNote, showDivider, onClick)
        MoreRow.Credits -> SettingsRow(
            title = stringResource(Res.string.settings_about_credits),
            icon = AppIcons.Info,
            quietIcon = true,
            chevron = true,
            showDivider = showDivider,
            onClick = onClick,
        )
        is MoreRow.LogOut -> SettingsRow(
            title = stringResource(Res.string.settings_more_log_out),
            icon = null,
            subtitle = when {
                row.account.guest -> stringResource(Res.string.settings_more_guest)
                row.account.email != null -> stringResource(Res.string.settings_more_signed_in_as, row.account.email)
                else -> stringResource(Res.string.settings_more_signed_in)
            },
            showDivider = showDivider,
            onClick = onClick,
        )
        MoreRow.Qa -> SettingsRow(
            title = stringResource(Res.string.settings_more_qa),
            icon = AppIcons.Science,
            quietIcon = true,
            subtitle = stringResource(Res.string.settings_more_qa_sub),
            chevron = true,
            showDivider = showDivider,
            onClick = onClick,
        )
    }
}

/** Opens a web page, so it carries the external-link mark rather than a chevron. */
@Composable
private fun LinkRow(title: StringResource, icon: IconResource, showDivider: Boolean, onClick: () -> Unit) {
    SettingsRow(
        title = stringResource(title),
        icon = icon,
        quietIcon = true,
        showDivider = showDivider,
        onClick = onClick,
        trailing = { Icon(icon = AppIcons.OpenInNew, size = 18.dp, color = AppTheme.colors.muted) },
    )
}

/** Like Support's delete: a modal, the safe choice in the accent, and nothing closes it mid-way. */
@Composable
private fun LogOutDialog(guest: Boolean, working: Boolean, onAction: (MoreAction) -> Unit) {
    Dialog(onDismissRequest = { onAction(MoreAction.LogOutCancelled) }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.bg, AppTheme.shapes.dialog)
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Text(
                text = stringResource(Res.string.settings_log_out_title),
                typography = AppTheme.type.dayLabel,
                color = AppTheme.colors.ink,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(if (guest) Res.string.settings_log_out_body_guest else Res.string.settings_log_out_body),
                typography = AppTheme.type.body,
                color = AppTheme.colors.inkSoft,
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            ) {
                FlatButton(
                    text = stringResource(Res.string.settings_log_out_cancel),
                    onClick = { onAction(MoreAction.LogOutCancelled) },
                    tone = FlatButtonTone.AccentText,
                    enabled = !working,
                    typography = AppTheme.type.button,
                )
                FlatButton(
                    text = stringResource(Res.string.settings_more_log_out),
                    onClick = { onAction(MoreAction.LogOutConfirmed) },
                    tone = FlatButtonTone.Text,
                    enabled = !working,
                    typography = AppTheme.type.button,
                )
            }
        }
    }
}

/** What previews show under the cards: a TestFlight build. */
internal const val PREVIEW_VERSION = "0.2.0 (702) · beta · 4a8b463"

private val previewAccount = MoreAccount(email = "sam@example.com", guest = false)

/** Every 0.2 row on, as design sync 2 draws More. */
@Preview
@Composable
private fun MorePreview() {
    val settings = SettingsFixtures.settings().copy(quiet = true)
    val state = MoreStateBuilder.build(settings, healthConnected = true, remindersOn = true, fastingReleased = true, widgetsOn = true)
    SettingsPreview { MoreScreen(state, onAction = {}) }
}

/** A beta build, signed in: About, Account and Testing under the design's cards. */
@Preview
@Composable
private fun MoreSignedInBetaPreview() {
    val state = MoreStateBuilder.build(SettingsFixtures.settings(), remindersOn = true, account = previewAccount, qa = true)
    SettingsPreview { MoreScreen(state, onAction = {}) }
}

/** An App Store build with a guest: no Testing card, and the version line without the commit. */
@Preview
@Composable
private fun MoreGuestStorePreview() {
    val state = MoreStateBuilder.build(SettingsFixtures.settings(), account = MoreAccount(email = null, guest = true))
    SettingsPreview { MoreScreen(state, onAction = {}, version = MoreVersion.line("0.2.0", 702, "store", "4a8b4639")) }
}

@Preview
@Composable
private fun MoreLogOutPreview() {
    val state = MoreStateBuilder.build(SettingsFixtures.settings(), account = previewAccount, qa = true)
        .copy(logOut = LogOutStep.Confirming(guest = false))
    SettingsPreview { MoreScreen(state, onAction = {}) }
}

/** A guest is told plainly that their log stays behind. */
@Preview
@Composable
private fun MoreLogOutGuestDarkPreview() {
    val state = MoreStateBuilder.build(SettingsFixtures.settings(), account = MoreAccount(email = null, guest = true))
        .copy(logOut = LogOutStep.Confirming(guest = true))
    SettingsPreview(dark = true) { MoreScreen(state, onAction = {}) }
}

/** Every 0.2 flag off: what 0.1 shows. */
@Preview
@Composable
private fun MoreFlagsOffPreview() {
    SettingsPreview { MoreScreen(MoreStateBuilder.build(SettingsFixtures.settings()), onAction = {}) }
}

@Preview
@Composable
private fun MoreFuelWithAChangeDarkPreview() {
    val settings = SettingsFixtures.settings(SettingsFixtures.nutrientsFirstWithACarbsChange)
    SettingsPreview(dark = true) { MoreScreen(MoreStateBuilder.build(settings), onAction = {}) }
}

/** With `release.reminders` on: Reminders and celebrations in the first card. */
@Preview
@Composable
private fun MoreRemindersPreview() {
    SettingsPreview { MoreScreen(MoreStateBuilder.build(SettingsFixtures.settings(), remindersOn = true), onAction = {}) }
}

@Preview
@Composable
private fun MoreWithFastingPreview() {
    val settings = SettingsFixtures.settings().copy(fasting = true)
    SettingsPreview { MoreScreen(MoreStateBuilder.build(settings, fastingReleased = true), onAction = {}) }
}

/** With `release.widgets` on: Widgets after Fasting in the extras card, as v3. */
@Preview
@Composable
private fun MoreWithWidgetsPreview() {
    val settings = SettingsFixtures.settings().copy(fasting = true)
    SettingsPreview { MoreScreen(MoreStateBuilder.build(settings, fastingReleased = true, widgetsOn = true), onAction = {}) }
}

@Preview
@Composable
private fun MoreWithHealthPreview() {
    SettingsPreview { MoreScreen(MoreStateBuilder.build(SettingsFixtures.settings(), healthConnected = true), onAction = {}) }
}

@Preview
@Composable
private fun MoreBlindPreview() {
    val settings = SettingsFixtures.settings(NumbersSettings(preset = PresetId.NoNumbers))
    SettingsPreview { MoreScreen(MoreStateBuilder.build(settings), onAction = {}) }
}
