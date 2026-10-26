package com.opson.features.settings.impl.integrations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.features.settings.impl.SettingsCard
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.SettingsHeader
import com.opson.features.settings.impl.SettingsPage
import com.opson.features.settings.impl.SettingsPreview
import com.opson.libraries.health.HealthSource
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.nutrition.ExerciseAdjustment
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.nutrition.ui.NutrientVisible
import com.opson.libraries.ui.components.DelayedLoadingIndicator
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.button.ButtonGhost
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.ButtonSize
import com.opson.libraries.ui.components.chip.StatusPill
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.radio.RadioRow
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.integrations_apple_health
import opson.libraries.resources.generated.resources.integrations_connect
import opson.libraries.resources.generated.resources.integrations_connected
import opson.libraries.resources.generated.resources.integrations_denied_apple_health
import opson.libraries.resources.generated.resources.integrations_denied_health_connect
import opson.libraries.resources.generated.resources.integrations_disconnect
import opson.libraries.resources.generated.resources.integrations_empty
import opson.libraries.resources.generated.resources.integrations_exercise_all
import opson.libraries.resources.generated.resources.integrations_exercise_all_line
import opson.libraries.resources.generated.resources.integrations_exercise_none
import opson.libraries.resources.generated.resources.integrations_exercise_none_line
import opson.libraries.resources.generated.resources.integrations_exercise_note
import opson.libraries.resources.generated.resources.integrations_exercise_some
import opson.libraries.resources.generated.resources.integrations_exercise_some_line
import opson.libraries.resources.generated.resources.integrations_exercise_title
import opson.libraries.resources.generated.resources.integrations_expended_title
import opson.libraries.resources.generated.resources.integrations_get_health_connect
import opson.libraries.resources.generated.resources.integrations_health_connect
import opson.libraries.resources.generated.resources.integrations_hidden
import opson.libraries.resources.generated.resources.integrations_left_out_add
import opson.libraries.resources.generated.resources.integrations_left_out_add_another
import opson.libraries.resources.generated.resources.integrations_left_out_empty
import opson.libraries.resources.generated.resources.integrations_left_out_title
import opson.libraries.resources.generated.resources.integrations_needs_install
import opson.libraries.resources.generated.resources.integrations_open_health_connect
import opson.libraries.resources.generated.resources.integrations_privacy
import opson.libraries.resources.generated.resources.integrations_reads
import opson.libraries.resources.generated.resources.integrations_remove_apple_health
import opson.libraries.resources.generated.resources.integrations_title
import opson.libraries.resources.generated.resources.integrations_total
import opson.libraries.resources.generated.resources.integrations_unsupported
import opson.libraries.resources.generated.resources.settings_back_more
import opson.libraries.resources.generated.resources.settings_loading_cd
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun IntegrationsScreen(
    state: IntegrationsState,
    onAction: (IntegrationsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(modifier = modifier) {
        SettingsHeader(
            title = stringResource(Res.string.integrations_title),
            backLabel = stringResource(Res.string.settings_back_more),
            onBack = { onAction(IntegrationsAction.BackTapped) },
        )
        if (state.loading) {
            // The store answers the connection check first, which can take a moment.
            DelayedLoadingIndicator(contentDescription = stringResource(Res.string.settings_loading_cd))
            return@SettingsPage
        }
        SourceCard(state, onAction)
        state.expended?.let { ExpendedCard(it, state.source) }
        state.exercise?.let { ExerciseCard(it, onAction) }
        LeftOutCard(state.leftOut, onAction)
        Note(stringResource(Res.string.integrations_privacy), Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
private fun SourceCard(state: IntegrationsState, onAction: (IntegrationsAction) -> Unit) {
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = AppTheme.spacing.section),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = stringResource(state.source.title), typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
                Text(
                    text = stringResource(
                        when (state.connection) {
                            Connection.Unsupported -> Res.string.integrations_unsupported
                            Connection.NeedsInstall -> Res.string.integrations_needs_install
                            else -> Res.string.integrations_reads
                        },
                    ),
                    typography = AppTheme.type.secondary,
                    color = AppTheme.colors.muted,
                )
            }
            when (state.connection) {
                Connection.Connected -> StatusPill(label = stringResource(Res.string.integrations_connected), icon = AppIcons.Check)
                Connection.NotConnected, Connection.Connecting -> ButtonPrimary(
                    onClick = { onAction(IntegrationsAction.ConnectTapped) },
                    size = ButtonSize.Small,
                    enabled = state.connection == Connection.NotConnected,
                ) {
                    Text(stringResource(Res.string.integrations_connect))
                }
                Connection.NeedsInstall -> ButtonPrimary(onClick = { onAction(IntegrationsAction.GetHealthConnectTapped) }, size = ButtonSize.Small) {
                    Text(stringResource(Res.string.integrations_get_health_connect))
                }
                Connection.Unsupported -> Unit
            }
        }
        if (state.denied) {
            HorizontalDivider(color = AppTheme.colors.line)
            Column(Modifier.padding(vertical = AppTheme.spacing.row), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                Text(
                    text = stringResource(
                        if (state.source == HealthSource.AppleHealth) Res.string.integrations_denied_apple_health else Res.string.integrations_denied_health_connect,
                    ),
                    typography = AppTheme.type.body,
                    color = AppTheme.colors.ink,
                )
                if (state.canOpenSource) {
                    ButtonGhost(onClick = { onAction(IntegrationsAction.OpenSourceTapped) }, size = ButtonSize.Small) {
                        Text(stringResource(Res.string.integrations_open_health_connect))
                    }
                }
            }
        }
        if (state.connection == Connection.Connected) {
            HorizontalDivider(color = AppTheme.colors.line)
            Column(Modifier.padding(vertical = AppTheme.spacing.chip)) {
                ButtonGhost(onClick = { onAction(IntegrationsAction.DisconnectTapped) }, size = ButtonSize.Small) {
                    Text(stringResource(Res.string.integrations_disconnect))
                }
                if (state.source == HealthSource.AppleHealth) {
                    Note(stringResource(Res.string.integrations_remove_apple_health), Modifier.padding(bottom = AppTheme.spacing.chip))
                }
            }
        }
    }
}

/**
 * Expended today. Hidden is one line with no heading, because a hidden number takes its label
 * with it; Empty keeps the heading, since there is nothing to hide.
 */
@Composable
private fun ExpendedCard(card: ExpendedCard, source: HealthSource) {
    SettingsCard {
        when (card) {
            ExpendedCard.Hidden -> Note(stringResource(Res.string.integrations_hidden), Modifier.padding(vertical = AppTheme.spacing.section))
            ExpendedCard.Empty -> {
                Heading()
                Note(stringResource(Res.string.integrations_empty, stringResource(source.title)), Modifier.padding(bottom = AppTheme.spacing.section))
            }
            is ExpendedCard.Shown -> {
                Heading()
                card.rows.forEach { row ->
                    HorizontalDivider(color = AppTheme.colors.line)
                    AmountRow(stringResource(row.label), row.display, strong = false)
                }
                NutrientVisible(card.total) {
                    HorizontalDivider(color = AppTheme.colors.ink)
                    AmountRow(stringResource(Res.string.integrations_total), card.total, strong = true)
                }
            }
        }
    }
}

/** "Should exercise change your range?" with v3's three answers and its promise underneath. */
@Composable
private fun ExerciseCard(question: ExerciseQuestion, onAction: (IntegrationsAction) -> Unit) {
    SettingsCard {
        Text(
            text = stringResource(Res.string.integrations_exercise_title),
            typography = AppTheme.type.cardTitle,
            color = AppTheme.colors.ink,
            modifier = Modifier.padding(top = AppTheme.spacing.section, bottom = AppTheme.spacing.row),
        )
        HorizontalDivider(color = AppTheme.colors.line)
        Column(Modifier.selectableGroup()) {
            question.options.forEachIndexed { index, choice ->
                RadioRow(
                    title = stringResource(choice.title),
                    subtitle = stringResource(choice.line),
                    selected = choice == question.selected,
                    onClick = { onAction(IntegrationsAction.ExercisePicked(choice)) },
                    showDivider = index < question.options.lastIndex,
                )
            }
        }
        Note(stringResource(Res.string.integrations_exercise_note), Modifier.padding(top = AppTheme.spacing.chip, bottom = AppTheme.spacing.section))
    }
}

/** Days left out of the math (v3 `integrations--full.png`): each day and its note, then a way to add one. */
@Composable
private fun LeftOutCard(card: LeftOutCard, onAction: (IntegrationsAction) -> Unit) {
    SettingsCard {
        Text(
            text = stringResource(Res.string.integrations_left_out_title),
            typography = AppTheme.type.cardTitle,
            color = AppTheme.colors.ink,
            modifier = Modifier.padding(top = AppTheme.spacing.section, bottom = AppTheme.spacing.chip),
        )
        if (card.days.isEmpty()) {
            Note(stringResource(Res.string.integrations_left_out_empty), Modifier.padding(vertical = AppTheme.spacing.chip))
        }
        card.days.forEach { day ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = AppTheme.spacing.chip),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
            ) {
                Text(text = day.label, typography = AppTheme.type.body, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
                day.note?.let { Text(text = it, typography = AppTheme.type.body, color = AppTheme.colors.muted, maxLines = 1) }
            }
        }
        Column(Modifier.padding(bottom = AppTheme.spacing.chip)) {
            ButtonGhost(onClick = { onAction(IntegrationsAction.LeaveOutDayTapped) }, size = ButtonSize.Small) {
                Text(
                    stringResource(
                        if (card.days.isEmpty()) Res.string.integrations_left_out_add else Res.string.integrations_left_out_add_another,
                    ),
                )
            }
        }
    }
}

/** "No", "Some of it", "All of it": the answer, here and on Targets' Exercise adjustment row. */
internal val ExerciseAdjustment.title: StringResource
    get() = when (this) {
        ExerciseAdjustment.None -> Res.string.integrations_exercise_none
        ExerciseAdjustment.Some -> Res.string.integrations_exercise_some
        ExerciseAdjustment.All -> Res.string.integrations_exercise_all
    }

private val ExerciseAdjustment.line: StringResource
    get() = when (this) {
        ExerciseAdjustment.None -> Res.string.integrations_exercise_none_line
        ExerciseAdjustment.Some -> Res.string.integrations_exercise_some_line
        ExerciseAdjustment.All -> Res.string.integrations_exercise_all_line
    }

@Composable
private fun Heading() {
    Text(
        text = stringResource(Res.string.integrations_expended_title),
        typography = AppTheme.type.secondaryStrong.Bold,
        color = AppTheme.colors.muted,
        modifier = Modifier.padding(top = AppTheme.spacing.section, bottom = AppTheme.spacing.row),
    )
}

/** A label and its amount; the label goes with the amount when the amount is hidden. */
@Composable
private fun AmountRow(label: String, display: Display, strong: Boolean) {
    NutrientVisible(display) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = AppTheme.spacing.section),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                typography = if (strong) AppTheme.type.bodyBold else AppTheme.type.body,
                color = AppTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            NutrientText(
                metric = Metric.EnergyOut,
                display = display,
                typography = AppTheme.type.bodyBold.Tabular,
                color = AppTheme.colors.ink,
            )
        }
    }
}

@Composable
private fun Note(text: String, modifier: Modifier = Modifier) {
    Text(text = text, typography = AppTheme.type.secondary, color = AppTheme.colors.muted, modifier = modifier)
}

private val HealthSource.title: StringResource
    get() = when (this) {
        HealthSource.AppleHealth -> Res.string.integrations_apple_health
        HealthSource.HealthConnect -> Res.string.integrations_health_connect
    }

@Preview
@Composable
private fun IntegrationsNotConnectedPreview() {
    SettingsPreview {
        IntegrationsScreen(IntegrationsFixtures.state(SettingsFixtures.policy(PresetId.Full), status = HealthStatus.NotConnected), onAction = {})
    }
}

@Preview
@Composable
private fun IntegrationsConnectedPreview() {
    SettingsPreview {
        IntegrationsScreen(IntegrationsFixtures.state(SettingsFixtures.policy(PresetId.Full)), onAction = {})
    }
}

@Preview
@Composable
private fun IntegrationsPermissionDeniedPreview() {
    SettingsPreview {
        IntegrationsScreen(
            IntegrationsFixtures.state(SettingsFixtures.policy(PresetId.Full), status = HealthStatus.NotConnected, denied = true),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun IntegrationsBlindPreview() {
    SettingsPreview { IntegrationsScreen(IntegrationsFixtures.state(SettingsFixtures.policy(PresetId.Blind)), onAction = {}) }
}

@Preview
@Composable
private fun IntegrationsAppleHealthEmptyPreview() {
    SettingsPreview {
        IntegrationsScreen(
            IntegrationsFixtures.state(SettingsFixtures.policy(PresetId.Full), source = HealthSource.AppleHealth, energy = null),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun IntegrationsConnectedDarkPreview() {
    SettingsPreview(dark = true) {
        IntegrationsScreen(IntegrationsFixtures.state(SettingsFixtures.policy(PresetId.Full)), onAction = {})
    }
}

@Preview
@Composable
private fun IntegrationsExerciseNoPreview() {
    SettingsPreview {
        IntegrationsScreen(
            IntegrationsFixtures.state(SettingsFixtures.policy(PresetId.Full), settings = IntegrationsFixtures.settings.copy(exerciseRange = null)),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun IntegrationsExerciseAllPreview() {
    SettingsPreview {
        IntegrationsScreen(
            IntegrationsFixtures.state(SettingsFixtures.policy(PresetId.Full), settings = IntegrationsFixtures.settings.copy(exerciseRange = ExerciseAdjustment.All)),
            onAction = {},
        )
    }
}

/** Numbers have been hard: no exercise question at all, and no days left out yet. */
@Preview
@Composable
private fun IntegrationsNumbersHardPreview() {
    SettingsPreview {
        IntegrationsScreen(
            IntegrationsFixtures.state(
                SettingsFixtures.policy(PresetId.Fuel),
                settings = IntegrationsFixtures.settings.copy(onboarding = OnboardingAnswers(hard = true)),
                days = emptyList(),
            ),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun IntegrationsBlindDarkPreview() {
    SettingsPreview(dark = true) { IntegrationsScreen(IntegrationsFixtures.state(SettingsFixtures.policy(PresetId.Blind)), onAction = {}) }
}
