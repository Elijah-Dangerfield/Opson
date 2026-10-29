package com.opson.features.onboarding.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.Screen
import com.opson.system.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The goal flow from Today or Targets, in onboarding's layout so the two read as one flow. Back on
 * the first step closes it; nothing is saved until the last step.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GoalSetupScreen(state: GoalSetupState, onAction: (GoalSetupAction) -> Unit, modifier: Modifier = Modifier) {
    BackHandler(enabled = !state.saving) { onAction(GoalSetupAction.BackTapped) }
    Screen(
        modifier = modifier,
        containerColor = AppTheme.colors.bg.color,
        contentWindowInsets = WindowInsets.systemBars,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val view = state.view ?: return@Column
            PlanStepContent(
                step = state.step,
                view = view,
                busy = state.saving,
                canGoBack = !state.saving,
                onBack = { onAction(GoalSetupAction.BackTapped) },
                onSkip = { onAction(GoalSetupAction.SkipTapped) },
                onContinue = { onAction(GoalSetupAction.ContinueTapped) },
                onCommit = { onAction(GoalSetupAction.CommitTapped(it)) },
                onPlan = { onAction(GoalSetupAction.Plan(it)) },
            )
        }
    }
}

/** A goal setup state as the view model builds it, for previews: [answers] in [preset], an existing account. */
internal fun goalSetupFixture(step: PlanStep, answers: PlanAnswers, preset: PresetId = PresetId.Full, hard: Boolean = false): GoalSetupState {
    val policy = OnboardingFixtures.policy(preset)
    val settings = Settings()
    val context = PlanContext(today = DEFAULT_TODAY, hard = hard, policy = policy, fresh = false)
    return GoalSetupState(
        loaded = true,
        step = step,
        answers = answers,
        settings = settings,
        policy = policy,
        context = context,
        view = PlanViews.all(answers, context, settings),
    )
}

@Composable
private fun GoalSetupPreview(state: GoalSetupState, darkTheme: Boolean = false) {
    PreviewContent(darkTheme = darkTheme) { GoalSetupScreen(state = state, onAction = {}) }
}

/** Opened from Today's card: the profile as the account has it, so most of it is already filled in. */
@Preview(widthDp = 390, heightDp = 1400)
@Composable
private fun GoalSetupProfilePreview() =
    GoalSetupPreview(goalSetupFixture(PlanStep.Profile, PlanAnswers(OnboardingFixtures.ImperialBody)))

@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun GoalSetupGoalPreview() = GoalSetupPreview(goalSetupFixture(PlanStep.Goal, OnboardingFixtures.LoseTen))

/** "Just track" from Today: the targets are the person's own and stay as they are. */
@Preview(widthDp = 390, heightDp = 1000)
@Composable
private fun GoalSetupOverviewJustTrackPreview() = GoalSetupPreview(
    goalSetupFixture(
        PlanStep.Overview,
        OnboardingFixtures.LoseTen.copy(goal = GoalAnswer(com.opson.libraries.nutrition.GoalDirection.JustTrack)),
    ),
)

@Preview(widthDp = 390, heightDp = 1000)
@Composable
private fun GoalSetupOverviewDarkPreview() =
    GoalSetupPreview(goalSetupFixture(PlanStep.Overview, OnboardingFixtures.LoseTen), darkTheme = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun GoalSetupCommitmentPreview() = GoalSetupPreview(goalSetupFixture(PlanStep.Commit, OnboardingFixtures.LoseTen))
