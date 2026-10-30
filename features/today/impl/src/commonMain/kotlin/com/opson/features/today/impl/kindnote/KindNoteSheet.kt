package com.opson.features.today.impl.kindnote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.features.today.impl.Toast
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonDefaults
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.ToneIcon
import com.opson.libraries.ui.components.icon.ToneIconSize
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.color.ToneColors
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.kind_note_body
import opson.libraries.resources.generated.resources.kind_note_fewer
import opson.libraries.resources.generated.resources.kind_note_not_now
import opson.libraries.resources.generated.resources.kind_note_private
import opson.libraries.resources.generated.resources.kind_note_stop
import opson.libraries.resources.generated.resources.kind_note_stopped
import opson.libraries.resources.generated.resources.kind_note_support
import opson.libraries.resources.generated.resources.kind_note_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The kind note's sheet ([KindNotePrompt]): a line, Find support, Show fewer numbers where there
 * are numbers to show fewer of, and Not now. Under them, that it was worked out on the phone,
 * and a way to stop the notes. No number and no reason, ever: naming what the log showed would
 * put a number back in front of someone it may be hurting.
 *
 * TODO(clinician): review the wording before 1.0.
 */
@Composable
fun KindNoteSheetContent(state: KindNoteState, onAction: (KindNoteAction) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        ToneIcon(icon = AppIcons.Favorite, tone = ToneColors(AppTheme.colors.accent, AppTheme.colors.accentTint), size = ToneIconSize.Large)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(Res.string.kind_note_title), typography = AppTheme.type.sheetTitle, color = AppTheme.colors.ink)
            Text(text = stringResource(Res.string.kind_note_body), typography = AppTheme.type.lead, color = AppTheme.colors.muted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SheetButton(Res.string.kind_note_support, FlatButtonTone.Accent) { onAction(KindNoteAction.FindSupportTapped) }
            if (state.offersFewerNumbers) {
                SheetButton(Res.string.kind_note_fewer, FlatButtonTone.Soft) { onAction(KindNoteAction.FewerNumbersTapped) }
            }
            SheetButton(Res.string.kind_note_not_now, FlatButtonTone.Text) { onAction(KindNoteAction.NotNowTapped) }
        }
        Text(
            text = stringResource(Res.string.kind_note_private),
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        FlatButton(
            text = stringResource(Res.string.kind_note_stop),
            onClick = { onAction(KindNoteAction.StopTapped) },
            tone = FlatButtonTone.Text,
            centered = true,
            typography = AppTheme.type.secondaryStrong,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SheetButton(text: StringResource, tone: FlatButtonTone, onClick: () -> Unit) {
    FlatButton(
        text = stringResource(text),
        onClick = onClick,
        tone = tone,
        centered = true,
        minHeight = FlatButtonDefaults.TallMinHeight,
        typography = AppTheme.type.button,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** [offersFewerNumbers]: anything but No numbers, which has none left to hide. */
data class KindNoteState(val offersFewerNumbers: Boolean = true)

sealed interface KindNoteAction {
    data object FindSupportTapped : KindNoteAction
    data object FewerNumbersTapped : KindNoteAction
    data object NotNowTapped : KindNoteAction
    data object StopTapped : KindNoteAction
}

sealed interface KindNoteEvent {
    data object OpenFindSupport : KindNoteEvent
    data object OpenModes : KindNoteEvent
    data class ShowToast(val toast: Toast) : KindNoteEvent
    data object Dismiss : KindNoteEvent
}

@Inject
class KindNoteViewModel(
    private val settings: SettingsRepository,
) : SEAViewModel<KindNoteState, KindNoteEvent, KindNoteAction>(
    initialStateArg = KindNoteState(offersFewerNumbers = settings.settings.value.numbers.preset != PresetId.NoNumbers),
) {
    override suspend fun handleAction(action: KindNoteAction) {
        when (action) {
            KindNoteAction.FindSupportTapped -> sendEvent(KindNoteEvent.OpenFindSupport)
            KindNoteAction.FewerNumbersTapped -> if (state.offersFewerNumbers) sendEvent(KindNoteEvent.OpenModes)
            KindNoteAction.NotNowTapped -> sendEvent(KindNoteEvent.Dismiss)
            KindNoteAction.StopTapped -> {
                settings.update { it.copy(hardStretchNotes = false) }
                sendEvent(KindNoteEvent.ShowToast(Toast(Res.string.kind_note_stopped)))
                sendEvent(KindNoteEvent.Dismiss)
            }
        }
    }
}

@Preview
@Composable
private fun TodayKindNotePreview() = PreviewContent(backgroundColor = AppTheme.colors.bg) {
    KindNoteSheetContent(KindNoteState(), onAction = {})
}

@Preview
@Composable
private fun TodayKindNoteNoNumbersDarkPreview() = PreviewContent(darkTheme = true, backgroundColor = AppTheme.colors.bg) {
    KindNoteSheetContent(KindNoteState(offersFewerNumbers = false), onAction = {})
}
