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
import com.opson.features.settings.impl.TimeDialog
import com.opson.libraries.diary.EventLevel
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Tone
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
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.reminders_big_day_body
import opson.libraries.resources.generated.resources.reminders_big_day_title
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
import opson.libraries.resources.generated.resources.reminders_tags_body
import opson.libraries.resources.generated.resources.reminders_tags_title
import opson.libraries.resources.generated.resources.reminders_time_cancel
import opson.libraries.resources.generated.resources.reminders_time_off
import opson.libraries.resources.generated.resources.reminders_time_ok
import opson.libraries.resources.generated.resources.reminders_time_title
import opson.libraries.resources.generated.resources.reminders_times_label
import opson.libraries.resources.generated.resources.reminders_title
import opson.libraries.resources.generated.resources.reminders_tone_example
import opson.libraries.resources.generated.resources.reminders_tone_plain
import opson.libraries.resources.generated.resources.reminders_tone_plain_sample
import opson.libraries.resources.generated.resources.reminders_tone_title
import opson.libraries.resources.generated.resources.reminders_tone_warm
import opson.libraries.resources.generated.resources.reminders_tone_warm_sample
import opson.libraries.resources.generated.resources.settings_back_more
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Reminders and celebrations (v3 `reminders.png`, `reminders--full.png`). New food tried and
 * Weekly recap are left out until something in the app acts on them
 * (docs/plans/screens/reminders.md).
 */
@Composable
fun RemindersScreen(
    state: RemindersState,
    onAction: (RemindersAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(modifier = modifier) {
        SettingsHeader(
            title = stringResource(Res.string.reminders_title),
            backLabel = stringResource(Res.string.settings_back_more),
            onBack = { onAction(RemindersAction.BackTapped) },
        )
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
        EdgeCard(edgeToEdge = false) {
            Text(text = stringResource(Res.string.reminders_tone_title), typography = AppTheme.type.bodyStrong, color = AppTheme.colors.ink)
            SegmentedControl(
                options = Tone.entries,
                selected = state.tone,
                onSelect = { onAction(RemindersAction.TonePicked(it)) },
                label = { stringResource(if (it == Tone.Warm) Res.string.reminders_tone_warm else Res.string.reminders_tone_plain) },
            )
            val sample = if (state.tone == Tone.Warm) Res.string.reminders_tone_warm_sample else Res.string.reminders_tone_plain_sample
            Text(
                text = stringResource(Res.string.reminders_tone_example, stringResource(sample)),
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.muted,
            )
        }
        SwitchCard(
            title = Res.string.reminders_tags_title,
            body = Res.string.reminders_tags_body,
            checked = state.askTags,
            onToggle = { onAction(RemindersAction.HungerQuestionsToggled) },
        )
        Text(
            text = stringResource(Res.string.reminders_no_evening),
            typography = AppTheme.type.secondarySmall,
            color = AppTheme.colors.muted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
    state.pickingRow?.let { row ->
        TimeDialog(
            title = stringResource(Res.string.reminders_time_title, stringResource(row.slot.label)),
            initial = row.time,
            okLabel = stringResource(Res.string.reminders_time_ok),
            cancelLabel = stringResource(Res.string.reminders_time_cancel),
            onPicked = { onAction(RemindersAction.MealTimePicked(it)) },
            onDismiss = { onAction(RemindersAction.MealTimeDismissed) },
        )
    }
}

@Composable
private fun SwitchCard(title: StringResource, body: StringResource, checked: Boolean, onToggle: () -> Unit) {
    EdgeCard(edgeToEdge = false) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(title), typography = AppTheme.type.bodyStrong, color = AppTheme.colors.ink)
                Text(text = stringResource(body), typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted)
            }
            AppSwitch(checked = checked, onCheckedChange = { onToggle() })
        }
    }
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
    val settings = SettingsFixtures.remindersSet.copy(numbers = NumbersSettings(preset = PresetId.Blind), tone = Tone.Warm)
    SettingsPreview { RemindersScreen(RemindersStateBuilder.build(settings), onAction = {}) }
}

@Preview
@Composable
private fun RemindersDarkPreview() {
    SettingsPreview(dark = true) { RemindersScreen(RemindersStateBuilder.build(SettingsFixtures.remindersSet), onAction = {}) }
}

@Preview
@Composable
private fun RemindersTimePreview() {
    SettingsPreview {
        RemindersScreen(RemindersStateBuilder.build(SettingsFixtures.remindersSet, pickingTime = MealSlot.Dinner), onAction = {})
    }
}
