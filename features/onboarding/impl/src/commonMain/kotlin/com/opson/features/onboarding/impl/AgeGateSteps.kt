package com.opson.features.onboarding.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.opson.libraries.diary.AgeGate
import com.opson.libraries.diary.ui.BirthdayField
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.Dimension
import kotlinx.datetime.LocalDate
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.age_body
import opson.libraries.resources.generated.resources.age_title
import opson.libraries.resources.generated.resources.age_too_young_body
import opson.libraries.resources.generated.resources.age_too_young_later
import opson.libraries.resources.generated.resources.age_too_young_support
import opson.libraries.resources.generated.resources.age_too_young_title
import opson.libraries.resources.generated.resources.onboarding_continue
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
 * told which answer gets them in. Continue waits for a date. The one place onboarding asks it:
 * the goal flow's profile reuses it.
 */
@Composable
internal fun ColumnScope.AgeStep(state: OnboardingState, onAction: (OnboardingAction) -> Unit) {
    OnboardingTopBar(canGoBack = state.canGoBack, onBack = { onAction(OnboardingAction.Back) })
    ScrollingBody {
        StepTitle(title = stringResource(Res.string.age_title), body = stringResource(Res.string.age_body))
        // Typed, and the phone can fill it from the person's own contact card: quicker than a
        // picker. Any real past date is taken here; the gate's rule is applied on Continue. The
        // keyboard is up when the step opens, and its Done is Continue (ignored until there is a date).
        BirthdayField(
            parts = state.ageParts,
            onChange = { onAction(OnboardingAction.AgeTyped(it)) },
            today = state.today,
            order = state.dateOrder,
            label = null,
            enabled = !state.isFinishing,
            onDone = { onAction(OnboardingAction.ContinueTapped) },
            focusOnOpen = true,
        )
    }
    PinnedPrimary(
        label = stringResource(Res.string.onboarding_continue),
        enabled = state.ageAnswer != null,
        onClick = { onAction(OnboardingAction.ContinueTapped) },
    )
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
