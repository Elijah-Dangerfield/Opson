package com.opson.features.today.impl.meal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.opson.features.today.impl.Toast
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonDefaults
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.text.TextArea
import com.opson.system.AppTheme
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_note_placeholder
import opson.libraries.resources.generated.resources.today_note_save
import opson.libraries.resources.generated.resources.today_note_title
import opson.libraries.resources.generated.resources.today_toast_note_saved
import org.jetbrains.compose.resources.stringResource

/**
 * A meal's note: where, who with, anything worth remembering. It is the
 * note on the meal's "how it felt" record, so saving it keeps the hunger,
 * fullness and moods already there. Voice log is 0.2.
 */
data class MealNoteState(val slot: MealSlot, val draft: String = "", val loaded: Boolean = false)

sealed interface MealNoteAction {
    data class FeelingLoaded(val feeling: MealFeeling?) : MealNoteAction
    data class NoteTyped(val text: String) : MealNoteAction
    data object SaveTapped : MealNoteAction
    data object CloseTapped : MealNoteAction
}

sealed interface MealNoteEvent {
    data class ShowToast(val toast: Toast) : MealNoteEvent
    data object Dismiss : MealNoteEvent
}

@Inject
class MealNoteViewModel(
    @Assisted private val day: LocalDate,
    @Assisted private val slot: MealSlot,
    private val diary: DiaryRepository,
) : SEAViewModel<MealNoteState, MealNoteEvent, MealNoteAction>(initialStateArg = MealNoteState(slot)) {

    /** The feeling as stored, so a save changes the note and nothing else. */
    private var feeling: MealFeeling? = null

    private var prefilled = false

    init {
        viewModelScope.launch {
            diary.observeDay(day).collect { takeAction(MealNoteAction.FeelingLoaded(it.feelings[slot])) }
        }
    }

    override suspend fun handleAction(action: MealNoteAction) {
        when (action) {
            is MealNoteAction.FeelingLoaded -> {
                feeling = action.feeling
                // Prefill once: a sync landing while the user types must not wipe the draft.
                if (!prefilled) action.updateState { it.copy(draft = action.feeling?.note.orEmpty(), loaded = true) }.also { prefilled = true }
            }
            is MealNoteAction.NoteTyped -> action.updateState { it.copy(draft = action.text) }
            MealNoteAction.SaveTapped -> {
                val note = state.draft.trim()
                diary.setFeeling(day, slot, (feeling ?: MealFeeling()).copy(note = note.ifEmpty { null }))
                if (note.isNotEmpty()) sendEvent(MealNoteEvent.ShowToast(Toast(Res.string.today_toast_note_saved, listOf(slot.sentenceName))))
                sendEvent(MealNoteEvent.Dismiss)
            }
            MealNoteAction.CloseTapped -> sendEvent(MealNoteEvent.Dismiss)
        }
    }
}

@Composable
fun MealNoteSheetContent(
    state: MealNoteState,
    onAction: (MealNoteAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        SheetHeader(
            title = stringResource(Res.string.today_note_title, stringResource(state.slot.sentenceName)),
            onClose = { onAction(MealNoteAction.CloseTapped) },
        )
        TextArea(
            value = state.draft,
            onValueChange = { onAction(MealNoteAction.NoteTyped(it)) },
            placeholder = stringResource(Res.string.today_note_placeholder),
            minLines = NOTE_LINES,
        )
        FlatButton(
            text = stringResource(Res.string.today_note_save),
            onClick = { onAction(MealNoteAction.SaveTapped) },
            tone = FlatButtonTone.Accent,
            centered = true,
            minHeight = FlatButtonDefaults.TallMinHeight,
            typography = AppTheme.type.button,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val NOTE_LINES = 4
