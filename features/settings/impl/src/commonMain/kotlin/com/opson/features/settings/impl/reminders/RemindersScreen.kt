package com.opson.features.settings.impl.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.features.settings.impl.SectionLabel
import com.opson.features.settings.impl.SettingsCard
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.SettingsHeader
import com.opson.features.settings.impl.SettingsPage
import com.opson.features.settings.impl.SettingsPreview
import com.opson.features.settings.impl.SettingsRow
import com.opson.libraries.diary.EventLevel
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.ui.label
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.reminders.NotificationPermission
import com.opson.libraries.ui.components.AppSwitch
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.dialog.TimePickerDialog
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.reminders_big_day_body
import opson.libraries.resources.generated.resources.reminders_big_day_title
import opson.libraries.resources.generated.resources.reminders_celebration_counts_body
import opson.libraries.resources.generated.resources.reminders_celebration_counts_title
import opson.libraries.resources.generated.resources.reminders_celebrations_body
import opson.libraries.resources.generated.resources.reminders_celebrations_title
import opson.libraries.resources.generated.resources.reminders_level_full
import opson.libraries.resources.generated.resources.reminders_level_off
import opson.libraries.resources.generated.resources.reminders_level_subtle
import opson.libraries.resources.generated.resources.reminders_meals_body
import opson.libraries.resources.generated.resources.reminders_meals_title
import opson.libraries.resources.generated.resources.reminders_no_evening
import opson.libraries.resources.generated.resources.reminders_permission_off
import opson.libraries.resources.generated.resources.reminders_permission_open
import opson.libraries.resources.generated.resources.reminders_quiet_body
import opson.libraries.resources.generated.resources.reminders_quiet_title
import opson.libraries.resources.generated.resources.reminders_time_cancel
import opson.libraries.resources.generated.resources.reminders_time_off
import opson.libraries.resources.generated.resources.reminders_time_ok
import opson.libraries.resources.generated.resources.reminders_time_title
import opson.libraries.resources.generated.resources.reminders_times_label
import opson.libraries.resources.generated.resources.reminders_title
import opson.libraries.resources.generated.resources.settings_back_more
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Reminders and celebrations (v3 `reminders.png`, `reminders--full.png`), with Celebrations on top
 * (`release.celebrations`). New food tried and
 * Weekly recap are left out until something in the app acts on them
 * (docs/plans/screens/reminders.md).
 */
@Composable
fun RemindersScreen(
    state: RemindersState,
    onAction: (RemindersAction) -> Unit,
    modifier: Modifier = Modifier,
    /** With `release.reminders` off: the Celebrations rows alone. */
    celebrationsOnly: Boolean = false,
) {
    SettingsPage(modifier = modifier) {
        SettingsHeader(
            title = stringResource(Res.string.reminders_title),
            backLabel = stringResource(Res.string.settings_back_more),
            onBack = { onAction(RemindersAction.BackTapped) },
        )
        state.celebrations?.let { CelebrationsCard(it, onAction) }
        if (!celebrationsOnly) Reminders(state, onAction)
    }
    state.pickingRow?.let { row ->
        TimePickerDialog(
            title = stringResource(Res.string.reminders_time_title, stringResource(row.slot.label)),
            initial = row.time,
            okLabel = stringResource(Res.string.reminders_time_ok),
            cancelLabel = stringResource(Res.string.reminders_time_cancel),
            onPicked = { onAction(RemindersAction.MealTimePicked(it)) },
            onDismiss = { onAction(RemindersAction.MealTimeDismissed) },
        )
    }
}

/** Quiet mode, After a big day, meal reminders and their times (`release.reminders`). */
@Composable
private fun Reminders(state: RemindersState, onAction: (RemindersAction) -> Unit) {
        SwitchCard(
            title = Res.string.reminders_quiet_title,
            body = Res.string.reminders_quiet_body,
            checked = state.quiet,
            onToggle = { onAction(RemindersAction.QuietToggled) },
        )
        EdgeCard(edgeToEdge = false, verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
            LevelChoice(
                title = Res.string.reminders_big_day_title,
                body = Res.string.reminders_big_day_body,
                selected = state.bigDay,
                onSelect = { onAction(RemindersAction.BigDayPicked(it)) },
            )
            HorizontalDivider(color = AppTheme.colors.line)
            LevelChoice(
                title = Res.string.reminders_meals_title,
                body = Res.string.reminders_meals_body,
                selected = state.mealLevel,
                onSelect = { onAction(RemindersAction.MealLevelPicked(it)) },
            )
        }
        if (state.showPermissionNote) PermissionNote(onOpen = { onAction(RemindersAction.OpenSettingsTapped) })
        SectionLabel(stringResource(Res.string.reminders_times_label), modifier = Modifier.padding(top = 4.dp))
        SettingsCard {
            state.meals.forEachIndexed { index, row ->
                SettingsRow(
                    title = stringResource(row.slot.label),
                    icon = null,
                    subtitle = if (row.on) row.timeLabel else stringResource(Res.string.reminders_time_off),
                    showDivider = index < state.meals.lastIndex,
                    onClick = { onAction(RemindersAction.MealTimeTapped(row.slot)) },
                ) {
                    AppSwitch(checked = row.on, onCheckedChange = { onAction(RemindersAction.MealToggled(row.slot)) })
                }
            }
        }
        Text(
            text = stringResource(Res.string.reminders_no_evening),
            typography = AppTheme.type.secondarySmall,
            color = AppTheme.colors.muted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
}

/** Celebrations on or off, and, while on, whether they say how many days. */
@Composable
private fun CelebrationsCard(choice: CelebrationsChoice, onAction: (RemindersAction) -> Unit) {
    EdgeCard(edgeToEdge = false, verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
        SwitchRow(
            title = Res.string.reminders_celebrations_title,
            body = Res.string.reminders_celebrations_body,
            checked = choice.on,
            onToggle = { onAction(RemindersAction.CelebrationsToggled) },
        )
        if (choice.on) {
            HorizontalDivider(color = AppTheme.colors.line)
            SwitchRow(
                title = Res.string.reminders_celebration_counts_title,
                body = Res.string.reminders_celebration_counts_body,
                checked = choice.counts,
                onToggle = { onAction(RemindersAction.CelebrationCountsToggled) },
            )
        }
    }
}

@Composable
private fun SwitchRow(title: StringResource, body: StringResource, checked: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(title), typography = AppTheme.type.bodyStrong, color = AppTheme.colors.ink)
            Text(text = stringResource(body), typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted)
        }
        AppSwitch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun SwitchCard(title: StringResource, body: StringResource, checked: Boolean, onToggle: () -> Unit) {
    EdgeCard(edgeToEdge = false) { SwitchRow(title, body, checked, onToggle) }
}

@Composable
private fun LevelChoice(title: StringResource, body: StringResource, selected: EventLevel, onSelect: (EventLevel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        Column {
            Text(text = stringResource(title), typography = AppTheme.type.bodyStrong, color = AppTheme.colors.ink)
            Text(text = stringResource(body), typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted)
        }
        SegmentedControl(
            options = RemindersStateBuilder.levels,
            selected = selected,
            onSelect = onSelect,
            label = { stringResource(it.label) },
        )
    }
}

/** No warning colour: a plain fact and the way to change it. */
@Composable
private fun PermissionNote(onOpen: () -> Unit) {
    EdgeCard(edgeToEdge = false, color = AppTheme.colors.soft) {
        Text(
            text = stringResource(Res.string.reminders_permission_off),
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.ink,
        )
        FlatButton(
            text = stringResource(Res.string.reminders_permission_open),
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth(),
            tone = FlatButtonTone.Outlined,
            centered = true,
        )
    }
}

private val EventLevel.label: StringResource
    get() = when (this) {
        EventLevel.Off -> Res.string.reminders_level_off
        EventLevel.Subtle -> Res.string.reminders_level_subtle
        EventLevel.Full -> Res.string.reminders_level_full
    }

/** The design's page, without the voice and check-in switches it once ended with. */
@Preview
@Composable
private fun RemindersPreview() {
    SettingsPreview { RemindersScreen(RemindersStateBuilder.build(SettingsFixtures.remindersSet), onAction = {}) }
}

/** Never opened: the synced defaults are not acted on, so meal reminders read Off. */
@Preview
@Composable
private fun RemindersEmptyPreview() {
    SettingsPreview { RemindersScreen(RemindersStateBuilder.build(SettingsFixtures.settings()), onAction = {}) }
}

@Preview
@Composable
private fun RemindersPermissionDeniedPreview() {
    SettingsPreview {
        RemindersScreen(
            RemindersStateBuilder.build(SettingsFixtures.remindersSet, permission = NotificationPermission.Denied),
            onAction = {},
        )
    }
}

/** The page is the same in every mode: nothing on it is a number. */
@Preview
@Composable
private fun RemindersBlindPreview() {
    val settings = SettingsFixtures.remindersSet.copy(numbers = NumbersSettings(preset = PresetId.NoNumbers))
    SettingsPreview { RemindersScreen(RemindersStateBuilder.build(settings), onAction = {}) }
}

@Preview
@Composable
private fun RemindersDarkPreview() {
    SettingsPreview(dark = true) { RemindersScreen(RemindersStateBuilder.build(SettingsFixtures.remindersSet), onAction = {}) }
}

/** With `release.celebrations` on: Celebrations and their day counts above the reminders. */
@Preview
@Composable
private fun RemindersCelebrationsPreview() {
    SettingsPreview { RemindersScreen(RemindersStateBuilder.build(SettingsFixtures.remindersSet, celebrationsOn = true), onAction = {}) }
}

/** With `release.reminders` off: the Celebrations rows alone, from More. */
@Preview
@Composable
private fun RemindersCelebrationsOnlyPreview() {
    SettingsPreview {
        RemindersScreen(RemindersStateBuilder.build(SettingsFixtures.settings(), celebrationsOn = true), onAction = {}, celebrationsOnly = true)
    }
}

@Preview
@Composable
private fun RemindersTimePreview() {
    SettingsPreview {
        RemindersScreen(RemindersStateBuilder.build(SettingsFixtures.remindersSet, pickingTime = MealSlot.Dinner), onAction = {})
    }
}
