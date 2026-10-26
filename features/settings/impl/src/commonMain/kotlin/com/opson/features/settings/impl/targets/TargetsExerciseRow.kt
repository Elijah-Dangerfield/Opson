package com.opson.features.settings.impl.targets

import androidx.compose.runtime.Composable
import com.opson.features.settings.impl.SettingsCard
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.SettingsPreview
import com.opson.features.settings.impl.SettingsRow
import com.opson.features.settings.impl.integrations.title
import com.opson.libraries.nutrition.ExerciseAdjustment
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.integrations_targets_exercise
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

// Targets' Exercise adjustment row (v3 targets.png, 0.2 behind release.health). Kept apart from
// TargetsScreen so the 0.2 work merges cleanly.

/** "Exercise adjustment", the answer under it; opens Watch and health apps, where it is asked. */
@Composable
internal fun ExerciseAdjustmentCard(answer: ExerciseAdjustment, onAction: (TargetsAction) -> Unit) {
    SettingsCard {
        SettingsRow(
            title = stringResource(Res.string.integrations_targets_exercise),
            icon = null,
            subtitle = stringResource(answer.title),
            chevron = true,
            onClick = { onAction(TargetsAction.ExerciseAdjustmentTapped) },
        )
    }
}

@Composable
private fun TargetsWithExercise(dark: Boolean) {
    val settings = SettingsFixtures.settings().copy(exerciseRange = ExerciseAdjustment.Some)
    SettingsPreview(dark = dark) {
        TargetsScreen(TargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers), healthConnected = true), onAction = {})
    }
}

@Preview
@Composable
private fun TargetsExerciseAdjustmentPreview() = TargetsWithExercise(dark = false)

@Preview
@Composable
private fun TargetsExerciseAdjustmentDarkPreview() = TargetsWithExercise(dark = true)
