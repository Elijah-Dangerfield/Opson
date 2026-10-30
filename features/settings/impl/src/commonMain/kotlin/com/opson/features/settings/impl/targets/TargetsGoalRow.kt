package com.opson.features.settings.impl.targets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.opson.features.settings.impl.SettingsCard
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.SettingsPreview
import com.opson.features.settings.impl.SettingsRow
import com.opson.libraries.diary.GoalBook
import com.opson.libraries.diary.Settings
import com.opson.libraries.goals.GoalFixtures
import com.opson.libraries.nutrition.BaselineChange
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.GoalKind
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.nutrition.ui.testTag
import com.opson.libraries.nutrition.ui.GoalDialog
import com.opson.libraries.nutrition.ui.GoalDialogButton
import com.opson.libraries.nutrition.ui.PaceInfoDialog
import com.opson.libraries.nutrition.ui.PaceNote
import com.opson.libraries.prompts.Tips
import com.opson.libraries.ui.components.CoachMarkAnchor
import com.opson.libraries.ui.components.CoachMarkScreen
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.goal_recalc_accept
import opson.libraries.resources.generated.resources.goal_recalc_activity
import opson.libraries.resources.generated.resources.goal_recalc_activity_level
import opson.libraries.resources.generated.resources.goal_recalc_activity_source
import opson.libraries.resources.generated.resources.goal_recalc_age
import opson.libraries.resources.generated.resources.goal_recalc_body
import opson.libraries.resources.generated.resources.goal_recalc_body_fat
import opson.libraries.resources.generated.resources.goal_recalc_details
import opson.libraries.resources.generated.resources.goal_recalc_digesting
import opson.libraries.resources.generated.resources.goal_recalc_height
import opson.libraries.resources.generated.resources.goal_recalc_keep
import opson.libraries.resources.generated.resources.goal_recalc_new
import opson.libraries.resources.generated.resources.goal_recalc_numbers
import opson.libraries.resources.generated.resources.goal_recalc_own_resting
import opson.libraries.resources.generated.resources.goal_recalc_resting
import opson.libraries.resources.generated.resources.goal_recalc_resting_source
import opson.libraries.resources.generated.resources.goal_recalc_sex
import opson.libraries.resources.generated.resources.goal_recalc_stage
import opson.libraries.resources.generated.resources.goal_recalc_title
import opson.libraries.resources.generated.resources.goal_recalc_unknown
import opson.libraries.resources.generated.resources.goal_recalc_weight
import opson.libraries.resources.generated.resources.targets_against_cancel
import opson.libraries.resources.generated.resources.targets_against_continue
import opson.libraries.resources.generated.resources.targets_against_gain_body
import opson.libraries.resources.generated.resources.targets_against_gain_title
import opson.libraries.resources.generated.resources.targets_against_keep_body
import opson.libraries.resources.generated.resources.targets_against_keep_title
import opson.libraries.resources.generated.resources.targets_against_lose_body
import opson.libraries.resources.generated.resources.targets_against_lose_title
import opson.libraries.resources.generated.resources.targets_change_body
import opson.libraries.resources.generated.resources.targets_change_cancel
import opson.libraries.resources.generated.resources.targets_change_edit_goal
import opson.libraries.resources.generated.resources.targets_change_own
import opson.libraries.resources.generated.resources.targets_change_title
import opson.libraries.resources.generated.resources.targets_goal_balance_over
import opson.libraries.resources.generated.resources.targets_goal_balance_under
import opson.libraries.resources.generated.resources.targets_goal_band
import opson.libraries.resources.generated.resources.targets_goal_change
import opson.libraries.resources.generated.resources.targets_goal_change_target
import opson.libraries.resources.generated.resources.targets_goal_driving
import opson.libraries.resources.generated.resources.targets_goal_driving_balance
import opson.libraries.resources.generated.resources.targets_goal_gain
import opson.libraries.resources.generated.resources.targets_goal_keep
import opson.libraries.resources.generated.resources.targets_goal_lose
import opson.libraries.resources.generated.resources.targets_goal_none
import opson.libraries.resources.generated.resources.targets_goal_none_body
import opson.libraries.resources.generated.resources.targets_goal_not_driving
import opson.libraries.resources.generated.resources.targets_goal_not_driving_body
import opson.libraries.resources.generated.resources.targets_goal_not_yet
import opson.libraries.resources.generated.resources.targets_goal_not_yet_body
import opson.libraries.resources.generated.resources.targets_goal_over
import opson.libraries.resources.generated.resources.targets_goal_paused
import opson.libraries.resources.generated.resources.targets_goal_paused_body
import opson.libraries.resources.generated.resources.targets_goal_target
import opson.libraries.resources.generated.resources.targets_goal_under
import opson.libraries.resources.generated.resources.targets_goal_use
import opson.libraries.resources.generated.resources.targets_goal_weekly
import opson.libraries.resources.generated.resources.tip_got_it
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

// The goal's card at the top of Targets (behind release.onboardingSetup), and the dialogs a goal
// asks its questions in. Its own file, as TargetsExerciseRow is, so its previews pair with their
// own designs.

/**
 * What the goal does to the target, said plainly. While it sets the target, the target is shown
 * read-only with Change, which asks first; once the person sets their own, the card says so and
 * offers the goal back.
 */
@Composable
internal fun GoalCardView(card: GoalCard, onAction: (TargetsAction) -> Unit) {
    when (card) {
        // Targets' one tip, so the visit is this card's: it stays put for as long as the page is open.
        is GoalCard.Setting -> CoachMarkScreen {
            CoachMarkAnchor(
                id = Tips.TargetsGoal.id,
                text = stringResource(Tips.TargetsGoal.text),
                gotIt = stringResource(Res.string.tip_got_it),
            ) {
                SettingCard(card, onAction)
            }
        }
        is GoalCard.Own -> ExplainedCard(
            title = Res.string.targets_goal_not_driving,
            body = Res.string.targets_goal_not_driving_body,
            useGoal = true,
            onAction = onAction,
        )
        is GoalCard.Paused -> ExplainedCard(Res.string.targets_goal_paused, Res.string.targets_goal_paused_body, useGoal = true, onAction = onAction)
        is GoalCard.Waiting -> ExplainedCard(Res.string.targets_goal_not_yet, Res.string.targets_goal_not_yet_body, useGoal = false, onAction = onAction)
        GoalCard.None -> SettingsCard {
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

@Composable
private fun SettingCard(card: GoalCard.Setting, onAction: (TargetsAction) -> Unit) {
    SettingsCard {
        SettingsRow(
            title = stringResource(if (card.kind == GoalKind.EnergyBalance) Res.string.targets_goal_driving_balance else Res.string.targets_goal_driving),
            icon = AppIcons.Favorite,
            subtitle = subtitle(card),
            trailingText = stringResource(Res.string.targets_goal_change),
            onClick = { onAction(TargetsAction.ChangeGoalTapped) },
        )
        Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (card.target as? Display.Shown)?.let {
                Text(
                    text = stringResource(Res.string.targets_goal_target, it.text),
                    typography = AppTheme.type.cardTitle,
                    color = AppTheme.colors.ink,
                    modifier = Modifier.testTag(Metric.Calories.testTag()),
                )
            }
            val low = card.low as? Display.Shown
            val high = card.high as? Display.Shown
            if (low != null && high != null) {
                Text(text = stringResource(Res.string.targets_goal_band, low.text, high.text), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
            }
            (card.difference as? Display.Shown)?.let {
                Text(
                    text = stringResource(if (card.differenceSign < 0) Res.string.targets_goal_under else Res.string.targets_goal_over, it.text),
                    typography = AppTheme.type.secondary,
                    color = AppTheme.colors.muted,
                    modifier = Modifier.testTag(Metric.EnergyOut.testTag()),
                )
            }
            if (card.weekly) Text(text = stringResource(Res.string.targets_goal_weekly), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
            card.pace?.let { pace ->
                PaceNote(
                    assessment = pace,
                    onInfo = { onAction(TargetsAction.PaceInfoTapped) },
                    onFindSupport = { onAction(TargetsAction.FindSupportTapped) }.takeIf { pace.needsCare },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            // Only while the target shows: in a mode that hides it, there is nothing here to change.
            if (card.target is Display.Shown) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlatButton(text = stringResource(Res.string.targets_goal_change_target), onClick = { onAction(TargetsAction.ChangeTargetTapped) })
                }
            }
        }
    }
}

@Composable
private fun subtitle(card: GoalCard.Setting): String {
    val balance = card.balance as? Display.Shown
    return when {
        balance != null -> stringResource(if (card.balanceSign < 0) Res.string.targets_goal_balance_under else Res.string.targets_goal_balance_over, balance.text)
        card.direction == GoalDirection.Lose && card.paceText != null -> stringResource(Res.string.targets_goal_lose, card.paceText)
        card.direction == GoalDirection.Gain && card.paceText != null -> stringResource(Res.string.targets_goal_gain, card.paceText)
        else -> stringResource(Res.string.targets_goal_keep)
    }
}

@Composable
private fun ExplainedCard(title: StringResource, body: StringResource, useGoal: Boolean, onAction: (TargetsAction) -> Unit) {
    SettingsCard {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(title), typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink)
            Text(text = stringResource(body), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (useGoal) FlatButton(text = stringResource(Res.string.targets_goal_use), onClick = { onAction(TargetsAction.UseGoalTapped) })
                FlatButton(text = stringResource(Res.string.targets_goal_change), onClick = { onAction(TargetsAction.ChangeGoalTapped) })
            }
        }
    }
}

/** The dialog over Targets: Change on a goal's target, an edit against the goal, a recalculation, or how fast is right. */
@Composable
internal fun TargetsDialogView(dialog: TargetsDialog, onAction: (TargetsAction) -> Unit) {
    val dismiss = { onAction(TargetsAction.DialogDismissed) }
    when (dialog) {
        TargetsDialog.ChangeTarget -> GoalDialog(
            title = stringResource(Res.string.targets_change_title),
            lines = listOf(stringResource(Res.string.targets_change_body)),
            buttons = listOf(
                GoalDialogButton(stringResource(Res.string.targets_change_edit_goal)) { onAction(TargetsAction.EditGoalChosen) },
                GoalDialogButton(stringResource(Res.string.targets_change_own)) { onAction(TargetsAction.OwnTargetChosen) },
                GoalDialogButton(stringResource(Res.string.targets_change_cancel), dismiss),
            ),
            onDismiss = dismiss,
        )
        is TargetsDialog.AgainstGoal -> {
            val (title, body) = when (dialog.direction) {
                GoalDirection.Gain -> Res.string.targets_against_gain_title to Res.string.targets_against_gain_body
                GoalDirection.Maintain -> Res.string.targets_against_keep_title to Res.string.targets_against_keep_body
                else -> Res.string.targets_against_lose_title to Res.string.targets_against_lose_body
            }
            GoalDialog(
                title = stringResource(title),
                lines = listOf(stringResource(body)),
                // Keeping the goal is the safe choice, so it comes first, in the accent.
                buttons = listOf(
                    GoalDialogButton(stringResource(Res.string.targets_against_cancel), dismiss),
                    GoalDialogButton(stringResource(Res.string.targets_against_continue)) { onAction(TargetsAction.PauseGoalChosen) },
                ),
                onDismiss = dismiss,
            )
        }
        is TargetsDialog.Recalculated -> {
            val old = dialog.old as? Display.Shown
            val new = dialog.new as? Display.Shown
            GoalDialog(
                title = stringResource(Res.string.goal_recalc_title),
                lines = buildList {
                    add(stringResource(Res.string.goal_recalc_body))
                    dialog.reasons.filter { it != BaselineChange.BodyFat || dialog.reasons.size == 1 }.forEach { add(stringResource(it.reason)) }
                    if (dialog.reasons.isEmpty()) add(stringResource(Res.string.goal_recalc_details))
                    when {
                        old != null && new != null -> add(stringResource(Res.string.goal_recalc_numbers, old.text, new.text))
                        new != null -> add(stringResource(Res.string.goal_recalc_new, new.text))
                    }
                },
                buttons = listOf(
                    GoalDialogButton(stringResource(Res.string.goal_recalc_accept)) { onAction(TargetsAction.RecalculationAccepted) },
                    GoalDialogButton(stringResource(Res.string.goal_recalc_keep)) { onAction(TargetsAction.RecalculationKept) },
                ),
                onDismiss = dismiss,
            )
        }
        is TargetsDialog.PaceInfo -> PaceInfoDialog(
            limitText = (dialog.limit as? Display.Shown)?.text,
            estimated = dialog.estimated,
            canWorkOut = dialog.canWorkOut,
            onDismiss = dismiss,
        )
    }
}

private val BaselineChange.reason: StringResource
    get() = when (this) {
        BaselineChange.RestingSource -> Res.string.goal_recalc_resting_source
        BaselineChange.OwnResting -> Res.string.goal_recalc_own_resting
        BaselineChange.Resting -> Res.string.goal_recalc_resting
        BaselineChange.ActivitySource -> Res.string.goal_recalc_activity_source
        BaselineChange.ActivityLevel -> Res.string.goal_recalc_activity_level
        BaselineChange.Activity -> Res.string.goal_recalc_activity
        BaselineChange.DigestingFood -> Res.string.goal_recalc_digesting
        BaselineChange.Weight -> Res.string.goal_recalc_weight
        BaselineChange.Height -> Res.string.goal_recalc_height
        BaselineChange.Age -> Res.string.goal_recalc_age
        BaselineChange.Sex -> Res.string.goal_recalc_sex
        BaselineChange.Stage -> Res.string.goal_recalc_stage
        BaselineChange.BodyFat -> Res.string.goal_recalc_body_fat
        BaselineChange.Unknown -> Res.string.goal_recalc_unknown
    }

@Composable
private fun GoalCardPreview(settings: Settings, preset: PresetId = PresetId.Full, dialog: TargetsDialogKind? = null) {
    val state = TargetsStateBuilder.build(
        settings,
        SettingsFixtures.policy(preset),
        burned = SettingsFixtures.burned,
        goalsAndEnergy = true,
        weightGoals = true,
        today = GoalFixtures.today,
        weightKg = GoalFixtures.WEIGHT_KG,
        dialog = dialog,
    )
    SettingsPreview { TargetsScreen(state, onAction = {}) }
}

private fun goalSettings(goal: com.opson.libraries.nutrition.Goal = GoalFixtures.lose): Settings =
    GoalFixtures.settings(goal, base = SettingsFixtures.settings().copy(weightUnit = WeightUnit.Lb, heightCm = 180.0, birthYear = 1996))

/** The goal sets the target: read-only, with the band, the deficit and Change. */
@Preview
@Composable
private fun TargetsWeightGoalPreview() = GoalCardPreview(goalSettings())

/** Averaged over the week, at a pace past a lean body's fat-loss limit. */
@Preview
@Composable
private fun TargetsGoalFastWeeklyPreview() = GoalCardPreview(goalSettings(GoalFixtures.loseFast.copy(averaging = com.opson.libraries.nutrition.GoalAveraging.Weekly)))

/** The person set their own target: the goal is kept, and offered back. */
@Preview
@Composable
private fun TargetsWeightGoalByHandPreview() = GoalCardPreview(GoalBook.override(goalSettings()))

/** No goal yet: an offer to set one. */
@Preview
@Composable
private fun TargetsWeightGoalNonePreview() = GoalCardPreview(SettingsFixtures.settings())

/** Macros with grams: the goal says what it does in words. */
@Preview
@Composable
private fun TargetsGoalWordsPreview() = GoalCardPreview(goalSettings(), preset = PresetId.MacrosWithGrams)

/** Change on the goal's target asks first. */
@Preview
@Composable
private fun TargetsGoalChangeDialogPreview() = GoalCardPreview(goalSettings(), dialog = TargetsDialogKind.ChangeTarget)

/** An own target that points against the goal. */
@Preview
@Composable
private fun TargetsGoalAgainstDialogPreview() = GoalCardPreview(GoalBook.override(goalSettings()), dialog = TargetsDialogKind.AgainstGoal(GoalDirection.Lose))

/** Apple Health was connected: the new target, and a choice. */
@Preview
@Composable
private fun TargetsGoalRecalculatedPreview() = GoalCardPreview(goalSettings(), dialog = TargetsDialogKind.Recalculate(GoalFixtures.connectedHealth))

/** The pace note's (i). */
@Preview
@Composable
private fun TargetsGoalPaceInfoPreview() = GoalCardPreview(goalSettings(GoalFixtures.loseFast), dialog = TargetsDialogKind.PaceInfo)
