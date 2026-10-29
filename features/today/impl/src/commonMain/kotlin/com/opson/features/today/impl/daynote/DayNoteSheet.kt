package com.opson.features.today.impl.daynote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.viewModelScope
import com.opson.features.today.impl.Toast
import com.opson.libraries.diary.DayWrite
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.ui.SectionLabel
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonDefaults
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.text.FocusOnOpen
import com.opson.libraries.ui.components.text.TextArea
import com.opson.system.AppTheme
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_dayclose_note_label
import opson.libraries.resources.generated.resources.today_dayclose_note_label_other_day
import opson.libraries.resources.generated.resources.today_dayclose_note_placeholder
import opson.libraries.resources.generated.resources.today_daynote_save
import opson.libraries.resources.generated.resources.today_daynote_title
import opson.libraries.resources.generated.resources.today_toast_daynote_saved
import org.jetbrains.compose.resources.stringResource

/**
 * The day's note, the same field Day close has. Saving it never closes the day:
 * adding a note must not change anything else.
 */
data class DayNoteState(val draft: String = "", val isToday: Boolean = true, val loaded: Boolean = false)

sealed interface DayNoteAction {
    data class NoteLoaded(val text: String?, val isToday: Boolean) : DayNoteAction
    data class NoteTyped(val text: String) : DayNoteAction
    data object SaveTapped : DayNoteAction
    data object CloseTapped : DayNoteAction
}

sealed interface DayNoteEvent {
    data class ShowToast(val toast: Toast) : DayNoteEvent

    /** The day is closed (Close the day, maybe on another phone): the note was not saved. */
    data class DayClosed(val day: LocalDate) : DayNoteEvent
    data object Dismiss : DayNoteEvent
}

@Inject
class DayNoteViewModel(
    @Assisted private val day: LocalDate,
    private val diary: DiaryRepository,
    calendar: LocalCalendar,
) : SEAViewModel<DayNoteState, DayNoteEvent, DayNoteAction>(initialStateArg = DayNoteState(isToday = day == calendar.today())) {

    private var prefilled = false

    init {
        viewModelScope.launch {
            diary.observeDay(day).collect { takeAction(DayNoteAction.NoteLoaded(it.note, day == calendar.today())) }
        }
    }

    override suspend fun handleAction(action: DayNoteAction) {
        when (action) {
            is DayNoteAction.NoteLoaded -> if (!prefilled) {
                prefilled = true
                action.updateState { it.copy(draft = action.text.orEmpty(), isToday = action.isToday, loaded = true) }
            }
            is DayNoteAction.NoteTyped -> action.updateState { it.copy(draft = action.text) }
            DayNoteAction.SaveTapped -> {
                // Before the day is read the draft is empty, and saving it would wipe the note.
                if (!state.loaded) return
                val note = state.draft.trim()
                if (diary.setDayNote(day, note.ifEmpty { null }) is DayWrite.DayClosed) {
                    sendEvent(DayNoteEvent.DayClosed(day))
                    return
                }
                if (note.isNotEmpty()) sendEvent(DayNoteEvent.ShowToast(Toast(Res.string.today_toast_daynote_saved)))
                sendEvent(DayNoteEvent.Dismiss)
            }
            DayNoteAction.CloseTapped -> sendEvent(DayNoteEvent.Dismiss)
        }
    }
}

@Composable
fun DayNoteSheetContent(
    state: DayNoteState,
    onAction: (DayNoteAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        SheetHeader(title = stringResource(Res.string.today_daynote_title), onClose = { onAction(DayNoteAction.CloseTapped) })
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
            SectionLabel(
                stringResource(if (state.isToday) Res.string.today_dayclose_note_label else Res.string.today_dayclose_note_label_other_day),
            )
            val noteField = remember { FocusRequester() }
            TextArea(
                value = state.draft,
                onValueChange = { onAction(DayNoteAction.NoteTyped(it)) },
                placeholder = stringResource(Res.string.today_dayclose_note_placeholder),
                modifier = Modifier.focusRequester(noteField),
            )
            // Opened to write the note, so the field is ready once what is there has been read.
            FocusOnOpen(noteField, enabled = state.loaded)
        }
        FlatButton(
            text = stringResource(Res.string.today_daynote_save),
            onClick = { onAction(DayNoteAction.SaveTapped) },
            tone = FlatButtonTone.Accent,
            centered = true,
            minHeight = FlatButtonDefaults.TallMinHeight,
            typography = AppTheme.type.button,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
