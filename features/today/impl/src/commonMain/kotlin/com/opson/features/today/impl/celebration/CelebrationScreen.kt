package com.opson.features.today.impl.celebration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.CelebrationStyle
import com.opson.libraries.diary.Milestone
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.prompts.PromptEvent
import com.opson.libraries.prompts.PromptEvents
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.CelebrationBurst
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonDefaults
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.confirm
import com.opson.system.AppTheme
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.celebrate_can_do_this
import opson.libraries.resources.generated.resources.celebrate_in_a_row
import opson.libraries.resources.generated.resources.celebrate_rather_not
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/** [offerTurnOff]: the very first celebration, which offers "I'd rather not see these". */
data class CelebrationState(
    val milestone: Milestone? = null,
    val style: CelebrationStyle = CelebrationStyle.Counts,
    val offerTurnOff: Boolean = false,
)

sealed interface CelebrationAction {
    /** "I can do this". */
    data object CanDoThisTapped : CelebrationAction

    /** "I'd rather not see these": celebrations off, reversible in More. */
    data object TurnOffTapped : CelebrationAction
    data object BackTapped : CelebrationAction
}

sealed interface CelebrationEvent {
    data object Close : CelebrationEvent

    /** Closed after turning celebrations off; the toast says where to turn them back on. */
    data object TurnedOff : CelebrationEvent
}

/**
 * A milestone's celebration. The prompt host opens it with the milestone already written down as
 * shown, so this only says it and takes one answer. Turning celebrations off is a plain settings
 * write, synced like every other.
 */
@Inject
class CelebrationViewModel(
    @Assisted milestoneWire: String,
    @Assisted offerTurnOff: Boolean,
    private val settingsRepository: SettingsRepository,
    private val telemetry: CelebrationTelemetry = CelebrationTelemetry.None,
    private val promptEvents: PromptEvents = PromptEvents.None,
) : SEAViewModel<CelebrationState, CelebrationEvent, CelebrationAction>(
    initialStateArg = CelebrationState(
        milestone = Milestone.fromWire(milestoneWire),
        // Off only if they turned them off since the host picked this one; it still says it kindly.
        style = settingsRepository.settings.value.celebrationStyle.takeUnless { it == CelebrationStyle.Off } ?: CelebrationStyle.Gentle,
        offerTurnOff = offerTurnOff,
    ),
) {
    private var answered = false

    init {
        state.milestone?.let { telemetry.shown(it, state.style, state.offerTurnOff) } ?: sendEvent(CelebrationEvent.Close)
    }

    override suspend fun handleAction(action: CelebrationAction) {
        val milestone = state.milestone ?: return sendEvent(CelebrationEvent.Close)
        if (answered) return
        answered = true
        when (action) {
            CelebrationAction.CanDoThisTapped -> {
                telemetry.answered(milestone, CelebrationAnswer.CanDoThis)
                // Their own glad answer: the one moment a prompt may follow in the same session.
                promptEvents.emit(PromptEvent.CelebrationCheered)
                sendEvent(CelebrationEvent.Close)
            }
            CelebrationAction.TurnOffTapped -> {
                settingsRepository.update { it.copy(celebrationsEnabled = false) }
                telemetry.answered(milestone, CelebrationAnswer.TurnedOff)
                sendEvent(CelebrationEvent.TurnedOff)
            }
            CelebrationAction.BackTapped -> {
                telemetry.answered(milestone, CelebrationAnswer.Back)
                sendEvent(CelebrationEvent.Close)
            }
        }
    }
}

/**
 * The celebration, full screen: the milestone in big type over a burst of confetti (still under
 * reduce motion and in previews), one honest line about what they did, and "I can do this". One
 * success haptic as it arrives. Nothing on it is a nutrition number, in any mode.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CelebrationScreen(state: CelebrationState, onAction: (CelebrationAction) -> Unit, modifier: Modifier = Modifier) {
    val milestone = state.milestone ?: return
    val copy = celebrationCopy(milestone, state.style)
    val haptics = LocalHapticFeedback.current
    val inspecting = LocalInspectionMode.current
    LaunchedEffect(milestone) { if (!inspecting) haptics.confirm() }
    BackHandler { onAction(CelebrationAction.BackTapped) }
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = AppTheme.spacing.page, vertical = AppTheme.spacing.section),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Spacer(Modifier.height(AppTheme.spacing.section))
                CelebrationBurst(modifier = Modifier.size(BurstSize)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
                        Icon(icon = AppIcons.AutoAwesome, size = 32.dp, color = AppTheme.colors.accent)
                        Spacer(Modifier.height(AppTheme.spacing.row))
                        Text(
                            text = stringResource(copy.title),
                            typography = if (state.style == CelebrationStyle.Gentle || !copy.inARow) AppTheme.type.subH1 else AppTheme.type.display,
                            color = AppTheme.colors.ink,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { heading() },
                        )
                        if (copy.inARow) {
                            Text(
                                text = stringResource(Res.string.celebrate_in_a_row),
                                typography = AppTheme.type.bodyStrong,
                                color = AppTheme.colors.muted,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(AppTheme.spacing.section))
                Text(
                    text = stringResource(copy.line),
                    typography = AppTheme.type.lead,
                    color = AppTheme.colors.inkSoft,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = AppTheme.spacing.section),
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = AppTheme.spacing.section),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
            ) {
                FlatButton(
                    text = stringResource(Res.string.celebrate_can_do_this),
                    onClick = { onAction(CelebrationAction.CanDoThisTapped) },
                    tone = FlatButtonTone.Accent,
                    centered = true,
                    minHeight = FlatButtonDefaults.TallMinHeight,
                    typography = AppTheme.type.button,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.offerTurnOff) {
                    FlatButton(
                        text = stringResource(Res.string.celebrate_rather_not),
                        onClick = { onAction(CelebrationAction.TurnOffTapped) },
                        tone = FlatButtonTone.Text,
                        centered = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private val BurstSize = 300.dp

@Composable
private fun CelebrationPreviewFrame(state: CelebrationState, dark: Boolean = false, largeText: Boolean = false) {
    PreviewContent(darkTheme = dark, largeText = largeText) { CelebrationScreen(state, onAction = {}) }
}

/** The first celebration: a week, with the way to turn them off under the button. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CelebrationWeekFirstPreview() =
    CelebrationPreviewFrame(CelebrationState(Milestone.Week1, CelebrationStyle.Counts, offerTurnOff = true))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CelebrationWeekDarkPreview() = CelebrationPreviewFrame(CelebrationState(Milestone.Week1), dark = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CelebrationFirstWrapUpPreview() = CelebrationPreviewFrame(CelebrationState(Milestone.FirstWrapUp, offerTurnOff = true))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CelebrationCheckInsPreview() = CelebrationPreviewFrame(CelebrationState(Milestone.CheckIns7))

/** After numbers have been hard: a kind title and line, and no count. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CelebrationGentlePreview() = CelebrationPreviewFrame(CelebrationState(Milestone.Days30, CelebrationStyle.Gentle))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CelebrationYearLargeTextPreview() = CelebrationPreviewFrame(CelebrationState(Milestone.Year1), largeText = true)
