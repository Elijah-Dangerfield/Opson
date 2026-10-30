package com.opson.features.onboarding.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.opson.libraries.diary.AgeGate
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.TapField
import com.opson.libraries.ui.components.dialog.DatePickerDialog
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import com.opson.system.Dimension
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.age_body
import opson.libraries.resources.generated.resources.age_dialog
import opson.libraries.resources.generated.resources.age_pick
import opson.libraries.resources.generated.resources.age_title
import opson.libraries.resources.generated.resources.age_too_young_body
import opson.libraries.resources.generated.resources.age_too_young_later
import opson.libraries.resources.generated.resources.age_too_young_support
import opson.libraries.resources.generated.resources.age_too_young_title
import opson.libraries.resources.generated.resources.onboarding_continue
import opson.libraries.resources.generated.resources.plan_date_cancel
import opson.libraries.resources.generated.resources.plan_date_ok
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/*
 * The age gate's two steps ([OnboardingStep.Age], [OnboardingStep.TooYoung]). Self-contained so a
 * reworked onboarding can place them wherever it likes, as long as it is before any account is
 * made. The rules are [AgeGate]'s; the view model's `handleAgeContinue` and `isAgeGateClosed` apply
 * them, and `withAgeAnswer` saves the answer on the new account. These only draw.
 */

/**
 * "When's your birthday?" A neutral question: nothing on it says where the line is, so nobody is
 * told which answer gets them in. Continue waits for a date.
 */
@Composable
internal fun ColumnScope.AgeStep(state: OnboardingState, onAction: (OnboardingAction) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    OnboardingTopBar(canGoBack = state.canGoBack, onBack = { onAction(OnboardingAction.Back) })
    ScrollingBody {
        StepTitle(title = stringResource(Res.string.age_title), body = stringResource(Res.string.age_body))
        TapField(
            text = state.ageAnswer?.let(DateCopy::monthDayYear) ?: stringResource(Res.string.age_pick),
            muted = state.ageAnswer == null,
            onClick = { picking = true },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    PinnedPrimary(
        label = stringResource(Res.string.onboarding_continue),
        enabled = state.ageAnswer != null,
        onClick = { onAction(OnboardingAction.ContinueTapped) },
    )
    if (picking) {
        DatePickerDialog(
            title = stringResource(Res.string.age_dialog),
            initial = state.ageAnswer,
            opensOn = state.today.minus(DatePeriod(years = OPENS_AT_AGE)),
            range = state.today.minus(DatePeriod(years = OLDEST))..state.today,
            okLabel = stringResource(Res.string.plan_date_ok),
            cancelLabel = stringResource(Res.string.plan_date_cancel),
            onPicked = {
                onAction(OnboardingAction.AgeAnswered(it))
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/**
 * Under [AgeGate.MINIMUM_AGE]: no account, no way back to the question, and Find support for
 * anyone who wants help with eating now. Kind, not a telling-off.
 */
@Composable
internal fun ColumnScope.TooYoungStep(onAction: (OnboardingAction) -> Unit) {
    OnboardingTopBar(canGoBack = false, onBack = {})
    ScrollingBody {
        StepTitle(title = stringResource(Res.string.age_too_young_title))
        Column(verticalArrangement = Arrangement.spacedBy(Dimension.D500)) {
            Text(text = stringResource(Res.string.age_too_young_body), typography = AppTheme.type.lead, color = AppTheme.colors.inkSoft)
            Text(text = stringResource(Res.string.age_too_young_later), typography = AppTheme.type.lead, color = AppTheme.colors.inkSoft)
        }
        Spacer(Modifier.height(Dimension.D700))
    }
    PinnedPrimary(
        label = stringResource(Res.string.age_too_young_support),
        enabled = true,
        onClick = { onAction(OnboardingAction.TalkToSomeoneTapped) },
    )
}

/** Where the picker opens with nothing picked: thirty years back, as the profile step's does. */
private const val OPENS_AT_AGE = 30

private const val OLDEST = 120

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingAgePreview() = PreviewContent {
    OnboardingScreen(state = OnboardingFixtures.age(), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingAgePickedDarkPreview() = PreviewContent(darkTheme = true) {
    OnboardingScreen(state = OnboardingFixtures.age(LocalDate(1994, 3, 12)), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingTooYoungPreview() = PreviewContent {
    OnboardingScreen(state = OnboardingFixtures.tooYoung(), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingTooYoungLargeTextDarkPreview() = PreviewContent(darkTheme = true, largeText = true) {
    OnboardingScreen(state = OnboardingFixtures.tooYoung(), onAction = {})
}
