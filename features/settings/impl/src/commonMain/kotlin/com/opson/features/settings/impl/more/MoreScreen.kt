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
import com.opson.features.settings.MorePlacement
import com.opson.features.settings.MoreRowContribution
import com.opson.features.settings.impl.SectionLabel
import com.opson.features.settings.impl.SettingsCard
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.SettingsPage
import com.opson.features.settings.impl.SettingsPreview
import com.opson.features.settings.impl.SettingsRow
import com.opson.features.settings.impl.label
import com.opson.libraries.navigation.Route
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
import opson.libraries.resources.generated.resources.reminders_more_celebrations_off
import opson.libraries.resources.generated.resources.reminders_more_celebrations_on
import opson.libraries.resources.generated.resources.reminders_more_quiet_off
import opson.libraries.resources.generated.resources.reminders_more_quiet_on
import opson.libraries.resources.generated.resources.reminders_more_row
import opson.libraries.resources.generated.resources.settings_about_credits
import opson.libraries.resources.generated.resources.settings_about_privacy
import opson.libraries.resources.generated.resources.settings_about_terms
import opson.libraries.resources.generated.resources.settings_log_out_anyway
import opson.libraries.resources.generated.resources.settings_log_out_body
import opson.libraries.resources.generated.resources.settings_log_out_body_guest
import opson.libraries.resources.generated.resources.settings_log_out_cancel
import opson.libraries.resources.generated.resources.settings_log_out_stay
import opson.libraries.resources.generated.resources.settings_log_out_title
import opson.libraries.resources.generated.resources.settings_log_out_unsynced_body
import opson.libraries.resources.generated.resources.settings_log_out_unsynced_title
import opson.libraries.resources.generated.resources.settings_modes_changes
import opson.libraries.resources.generated.resources.settings_more_about
import opson.libraries.resources.generated.resources.settings_more_account
import opson.libraries.resources.generated.resources.settings_more_guest
import opson.libraries.resources.generated.resources.settings_more_log_out
import opson.libraries.resources.generated.resources.settings_more_display
import opson.libraries.resources.generated.resources.settings_more_modes_changed
import opson.libraries.resources.generated.resources.settings_more_plan
import opson.libraries.resources.generated.resources.settings_more_plan_free
import opson.libraries.resources.generated.resources.settings_more_profile
import opson.libraries.resources.generated.resources.settings_more_profile_sub
import opson.libraries.resources.generated.resources.settings_more_signed_in
import opson.libraries.resources.generated.resources.settings_more_signed_in_as
import opson.libraries.resources.generated.resources.settings_more_report_bug
import opson.libraries.resources.generated.resources.settings_more_report_bug_sub
import opson.libraries.resources.generated.resources.settings_more_send_feedback_sub
import opson.libraries.resources.generated.resources.settings_more_support
import opson.libraries.resources.generated.resources.settings_send_feedback
import opson.libraries.resources.generated.resources.today_more_checkin
import opson.libraries.resources.generated.resources.today_more_checkin_sub
import opson.libraries.resources.generated.resources.today_more_journal
import opson.libraries.resources.generated.resources.today_more_notes
import opson.libraries.resources.generated.resources.today_more_notes_sub
import opson.libraries.resources.generated.resources.settings_more_support_sub
import opson.libraries.resources.generated.resources.settings_more_targets
import opson.libraries.resources.generated.resources.settings_more_targets_sub
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
            group.label()?.let { SectionLabel(stringResource(it)) }
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
    LogOutCopy.of(state.logOut)?.let { LogOutDialog(it, working = state.logOut is LogOutStep.Working, onAction = onAction) }
}

private fun List<MoreRow>.label(): StringResource? = when (val first = firstOrNull()) {
    is MoreRow.Contributed -> first.row.section
    else -> when (section()) {
        MoreSection.About -> Res.string.settings_more_about
        MoreSection.Account -> Res.string.settings_more_account
        null -> null
    }
}

@Composable
private fun MoreRowItem(row: MoreRow, showDivider: Boolean, onClick: () -> Unit) {
    when (row) {
        is MoreRow.Display -> {
            val preset = stringResource(row.preset.label)
            SettingsRow(
                title = stringResource(Res.string.settings_more_display),
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
        MoreRow.Profile -> SettingsRow(
            title = stringResource(Res.string.settings_more_profile),
            icon = AppIcons.Person,
            subtitle = stringResource(Res.string.settings_more_profile_sub),
            chevron = true,
            showDivider = showDivider,
            onClick = onClick,
        )
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
        is MoreRow.Reminders -> SettingsRow(
            title = stringResource(Res.string.reminders_more_row),
            icon = AppIcons.Notifications,
            subtitle = stringResource(
                when (row.celebrations) {
                    null -> if (row.quiet) Res.string.reminders_more_quiet_on else Res.string.reminders_more_quiet_off
                    true -> Res.string.reminders_more_celebrations_on
                    false -> Res.string.reminders_more_celebrations_off
                },
            ),
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
        MoreRow.ReportBug -> SettingsRow(
            title = stringResource(Res.string.settings_more_report_bug),
            icon = AppIcons.BugReport,
            quietIcon = true,
            subtitle = stringResource(Res.string.settings_more_report_bug_sub),
            showDivider = showDivider,
            onClick = onClick,
        )
        MoreRow.SendFeedback -> SettingsRow(
            title = stringResource(Res.string.settings_send_feedback),
            icon = AppIcons.Feedback,
            quietIcon = true,
            subtitle = stringResource(Res.string.settings_more_send_feedback_sub),
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
            destructive = true,
            subtitle = when {
                row.account.guest -> stringResource(Res.string.settings_more_guest)
                row.account.email != null -> stringResource(Res.string.settings_more_signed_in_as, row.account.email)
                else -> stringResource(Res.string.settings_more_signed_in)
            },
            showDivider = showDivider,
            onClick = onClick,
        )
        is MoreRow.Contributed -> SettingsRow(
            title = stringResource(row.row.title),
            icon = row.row.icon,
            quietIcon = true,
            subtitle = row.row.subtitle?.let { stringResource(it) },
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

/** The log out dialog's words: the first ask, a guest's, or the one after the account couldn't be reached. */
internal enum class LogOutCopy(val title: StringResource, val body: StringResource, val cancel: StringResource, val confirm: StringResource) {
    SignedIn(Res.string.settings_log_out_title, Res.string.settings_log_out_body, Res.string.settings_log_out_cancel, Res.string.settings_more_log_out),
    Guest(Res.string.settings_log_out_title, Res.string.settings_log_out_body_guest, Res.string.settings_log_out_cancel, Res.string.settings_more_log_out),
    Unsynced(
        Res.string.settings_log_out_unsynced_title,
        Res.string.settings_log_out_unsynced_body,
        Res.string.settings_log_out_stay,
        Res.string.settings_log_out_anyway,
    ),
    ;

    companion object {
        /** Null while no dialog is up. */
        fun of(step: LogOutStep): LogOutCopy? = when (step) {
            LogOutStep.Idle -> null
            is LogOutStep.Confirming -> of(step.guest)
            is LogOutStep.Working -> if (step.unsynced) Unsynced else of(step.guest)
            LogOutStep.Unsynced -> Unsynced
        }

        private fun of(guest: Boolean): LogOutCopy = if (guest) Guest else SignedIn
    }
}

/** Like Support's delete: a modal, the safe choice in the accent, and nothing closes it mid-way. */
@Composable
private fun LogOutDialog(copy: LogOutCopy, working: Boolean, onAction: (MoreAction) -> Unit) {
    Dialog(onDismissRequest = { if (!working) onAction(MoreAction.LogOutCancelled) }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.bg, AppTheme.shapes.dialog)
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Text(
                text = stringResource(copy.title),
                typography = AppTheme.type.dayLabel,
                color = AppTheme.colors.ink,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(copy.body),
                typography = AppTheme.type.body,
                color = AppTheme.colors.inkSoft,
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            ) {
                FlatButton(
                    text = stringResource(copy.cancel),
                    onClick = { onAction(MoreAction.LogOutCancelled) },
                    tone = FlatButtonTone.AccentText,
                    enabled = !working,
                    typography = AppTheme.type.button,
                )
                FlatButton(
                    text = stringResource(copy.confirm),
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

/** Today's Journal rows as More draws them; the real ones live in Today, which settings cannot see. */
private class PreviewJournalRow(
    override val title: StringResource,
    override val subtitle: StringResource,
    override val icon: IconResource,
) : MoreRowContribution {
    override val section = Res.string.today_more_journal
    override val placement = MorePlacement.AfterExtras
    override fun route(): Route = Route()
}

private val previewJournal = listOf(
    PreviewJournalRow(Res.string.today_more_checkin, Res.string.today_more_checkin_sub, AppIcons.Mood),
    PreviewJournalRow(Res.string.today_more_notes, Res.string.today_more_notes_sub, AppIcons.EditNote),
)

/** Every 0.2 row on, as design sync 2 draws More, with Today's Journal card under the extras. */
@Preview
@Composable
private fun MorePreview() {
    val settings = SettingsFixtures.settings().copy(quiet = true)
    val state = MoreStateBuilder.build(
        settings,
        healthConnected = true,
        remindersOn = true,
        fastingReleased = true,
        widgetsOn = true,
        goalsAndEnergyOn = true,
        contributed = previewJournal,
    )
    SettingsPreview { MoreScreen(state, onAction = {}) }
}

/** A beta build, signed in: About and Account under the design's cards. */
@Preview
@Composable
private fun MoreSignedInBetaPreview() {
    val state = MoreStateBuilder.build(SettingsFixtures.settings(), remindersOn = true, account = previewAccount)
    SettingsPreview { MoreScreen(state, onAction = {}) }
}

/** An App Store build with a guest: the version line without the commit. */
@Preview
@Composable
private fun MoreGuestStorePreview() {
    val state = MoreStateBuilder.build(SettingsFixtures.settings(), account = MoreAccount(email = null, guest = true))
    SettingsPreview { MoreScreen(state, onAction = {}, version = MoreVersion.line("0.2.0", 702, "store", "4a8b4639")) }
}

@Preview
@Composable
private fun MoreLogOutPreview() {
    val state = MoreStateBuilder.build(SettingsFixtures.settings(), account = previewAccount)
        .copy(logOut = LogOutStep.Confirming(guest = false))
    SettingsPreview { MoreScreen(state, onAction = {}) }
}

/** The sync before logging out didn't reach the account: Stay, or log out and lose what never left the phone. */
@Preview
@Composable
private fun MoreLogOutUnsyncedPreview() {
    val state = MoreStateBuilder.build(SettingsFixtures.settings(), account = previewAccount).copy(logOut = LogOutStep.Unsynced)
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
