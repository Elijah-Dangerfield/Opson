package com.opson.features.settings.impl.targets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.features.settings.impl.SettingsCard
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.SettingsPreview
import com.opson.features.settings.impl.SettingsRow
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.Units
import com.opson.libraries.nutrition.WeightGoal
import com.opson.libraries.nutrition.WeightGoals
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.prompts.Tips
import com.opson.libraries.ui.components.CoachMarkAnchor
import com.opson.libraries.ui.components.CoachMarkScreen
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.targets_goal_change
import opson.libraries.resources.generated.resources.tip_got_it
import opson.libraries.resources.generated.resources.targets_goal_driving
import opson.libraries.resources.generated.resources.targets_goal_gain
import opson.libraries.resources.generated.resources.targets_goal_keep
import opson.libraries.resources.generated.resources.targets_goal_lose
import opson.libraries.resources.generated.resources.targets_goal_none
import opson.libraries.resources.generated.resources.targets_goal_none_body
import opson.libraries.resources.generated.resources.targets_goal_not_driving
import opson.libraries.resources.generated.resources.targets_goal_not_driving_body
import opson.libraries.resources.generated.resources.targets_goal_not_yet
import opson.libraries.resources.generated.resources.targets_goal_not_yet_body
import opson.libraries.resources.generated.resources.targets_goal_use
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

// The weight goal's row at the top of Targets (behind release.onboardingSetup). Its own file, as
// TargetsExerciseRow is, so its previews pair with their own designs.

/**
 * Whether the weight goal sets the day's target, said plainly, with a way to change either: the
 * goal through the goal flow, the target on this page. A target moved by hand says the goal no
 * longer sets it, and offers to let it again.
 */
@Composable
internal fun WeightGoalCard(row: WeightGoalRow, onAction: (TargetsAction) -> Unit) {
    when (row) {
        // Targets' one tip, so the visit is this card's: it stays put for as long as the page is open.
        is WeightGoalRow.Driving -> CoachMarkScreen {
            CoachMarkAnchor(
                id = Tips.TargetsGoal.id,
                text = stringResource(Tips.TargetsGoal.text),
                gotIt = stringResource(Res.string.tip_got_it),
            ) {
                SettingsCard {
                    SettingsRow(
                        title = stringResource(Res.string.targets_goal_driving),
                        icon = AppIcons.Favorite,
                        subtitle = when (row.direction) {
                            GoalDirection.Lose -> row.pace?.let { stringResource(Res.string.targets_goal_lose, it) }
                            GoalDirection.Gain -> row.pace?.let { stringResource(Res.string.targets_goal_gain, it) }
                            else -> null
                        } ?: stringResource(Res.string.targets_goal_keep),
                        trailingText = stringResource(Res.string.targets_goal_change),
                        onClick = { onAction(TargetsAction.ChangeGoalTapped) },
                    )
                }
            }
        }
        is WeightGoalRow.NotDriving -> SettingsCard {
            Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(if (row.byHand) Res.string.targets_goal_not_driving else Res.string.targets_goal_not_yet),
                    typography = AppTheme.type.bodyBold,
                    color = AppTheme.colors.ink,
                )
                Text(
                    text = stringResource(if (row.byHand) Res.string.targets_goal_not_driving_body else Res.string.targets_goal_not_yet_body),
                    typography = AppTheme.type.secondary,
                    color = AppTheme.colors.muted,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (row.canUseGoal) {
                        FlatButton(text = stringResource(Res.string.targets_goal_use), onClick = { onAction(TargetsAction.UseGoalTapped) })
                    }
                    FlatButton(text = stringResource(Res.string.targets_goal_change), onClick = { onAction(TargetsAction.ChangeGoalTapped) })
                }
            }
        }
        WeightGoalRow.None -> SettingsCard {
            SettingsRow(
                title = stringResource(Res.string.targets_goal_none),
                icon = AppIcons.Favorite,
                subtitle = stringResource(Res.string.targets_goal_none_body),
                chevron = true,
                onClick = { onAction(TargetsAction.ChangeGoalTapped) },
            )
        }
    }
}

private val previewGoal = WeightGoal(
    direction = GoalDirection.Lose,
    targetKg = Units.lbToKg(170.0),
    kgPerWeek = Units.lbToKg(0.5),
    startKg = Units.lbToKg(180.0),
)

@Composable
private fun GoalRowPreview(goal: WeightGoal, byHand: Boolean, burnKnown: Boolean = true) {
    val base = SettingsFixtures.settings().copy(weightUnit = WeightUnit.Lb)
    val (stamped, targets) = WeightGoals.apply(goal, base.targets, aimKcal = 2200, scaleMacros = true)
    val settings = base.copy(weightGoal = if (goal.isSet) stamped else goal, targets = if (byHand) targets.raise() else targets)
    val state = TargetsStateBuilder.build(
        settings,
        SettingsFixtures.policy(settings.numbers),
        burned = SettingsFixtures.burned.takeIf { burnKnown },
        goalsAndEnergy = true,
        weightGoals = true,
    )
    SettingsPreview { TargetsScreen(state, onAction = {}) }
}

/** The goal sets the target, and says so. */
@Preview
@Composable
private fun TargetsWeightGoalPreview() = GoalRowPreview(previewGoal, byHand = false)

/** The target was moved by hand: the goal says it no longer sets it, and offers to again. */
@Preview
@Composable
private fun TargetsWeightGoalByHandPreview() = GoalRowPreview(previewGoal, byHand = true)

/** No goal yet: an offer to set one. */
@Preview
@Composable
private fun TargetsWeightGoalNonePreview() = GoalRowPreview(WeightGoal.None, byHand = false)
