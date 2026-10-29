package com.opson.features.today.impl.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewModelScope
import com.opson.features.today.NotesOrigin
import com.opson.features.today.impl.MealNoteUi
import com.opson.features.today.impl.text
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.ui.SectionLabel
import com.opson.libraries.diary.ui.TodayCard
import com.opson.libraries.diary.ui.icon
import com.opson.libraries.diary.ui.tileColors
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.DelayedLoadingIndicator
import com.opson.libraries.ui.components.EmptyCard
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.PageHeader
import com.opson.libraries.ui.components.PromptAction
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.RowIcon
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_more_title
import opson.libraries.resources.generated.resources.today_dayclose_back
import opson.libraries.resources.generated.resources.today_label_today
import opson.libraries.resources.generated.resources.today_note_hunger_line
import opson.libraries.resources.generated.resources.today_notes_add_today
import opson.libraries.resources.generated.resources.today_notes_day_year
import opson.libraries.resources.generated.resources.today_notes_earlier
import opson.libraries.resources.generated.resources.today_notes_edit_cd
import opson.libraries.resources.generated.resources.today_notes_edit_day_cd
import opson.libraries.resources.generated.resources.today_notes_empty
import opson.libraries.resources.generated.resources.today_notes_empty_window
import opson.libraries.resources.generated.resources.today_notes_loading_cd
import opson.libraries.resources.generated.resources.today_notes_title
import opson.libraries.resources.generated.resources.trends_title
import org.jetbrains.compose.resources.stringResource

/**
 * Reads the diary over a window that "Show earlier notes" widens, 90 days at a time. The Note
 * sheets write the same records, so an edit made from here shows as soon as the sheet closes, and
 * a note saved from Close the day is the day's note, listed like any other.
 * Nothing here goes to telemetry: notes are health data.
 */
@Inject
class NotesViewModel(
    @Assisted private val from: NotesOrigin,
    private val diary: DiaryRepository,
    private val calendar: LocalCalendar,
) : SEAViewModel<NotesState, NotesEvent, NotesAction>(initialStateArg = NotesState(from = from)) {

    private val window = MutableStateFlow(NotesStateBuilder.PAGE_DAYS)

    init {
        viewModelScope.launch {
            val today = calendar.today()
            @OptIn(ExperimentalCoroutinesApi::class)
            window.flatMapLatest { days ->
                diary.observeDays(today.minus(DatePeriod(days = days - 1)), today).map { NotesAction.DaysArrived(it, today, days) }
            }.collect(::takeAction)
        }
    }

    override suspend fun handleAction(action: NotesAction) {
        when (action) {
            is NotesAction.DaysArrived -> action.updateState { NotesStateBuilder.build(action.days, action.today, action.windowDays, from) }
            NotesAction.ShowEarlierTapped -> window.update { (it + NotesStateBuilder.PAGE_DAYS).coerceAtMost(NotesStateBuilder.MAX_DAYS) }
            is NotesAction.NoteTapped -> sendEvent(action.slot?.let { NotesEvent.OpenNote(action.day, it) } ?: NotesEvent.OpenDayNote(action.day))
            NotesAction.AddTodayTapped -> sendEvent(NotesEvent.OpenDayNote(calendar.today()))
            NotesAction.BackTapped -> sendEvent(NotesEvent.Close)
        }
    }
}

/**
 * The Notes screen: a group per day, newest first, the day's own note and each meal's Note in a
 * card, each opening its sheet to change it. Write a note for today sits above them until today has one.
 */
@Composable
fun NotesScreen(state: NotesState, onAction: (NotesAction) -> Unit, modifier: Modifier = Modifier) {
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding = PaddingValues(horizontal = AppTheme.spacing.page, vertical = AppTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
        ) {
            item(key = "header") {
                PageHeader(
                    title = stringResource(Res.string.today_notes_title),
                    backLabel = stringResource(
                        Res.string.today_dayclose_back,
                        stringResource(
                            when (state.from) {
                                NotesOrigin.Today -> Res.string.today_label_today
                                NotesOrigin.Trends -> Res.string.trends_title
                                NotesOrigin.More -> Res.string.settings_more_title
                            },
                        ),
                    ),
                    onBack = { onAction(NotesAction.BackTapped) },
                )
            }
            if (!state.loading && state.canAddToday) {
                item(key = "add-today") {
                    PromptAction(
                        label = stringResource(Res.string.today_notes_add_today),
                        onClick = { onAction(NotesAction.AddTodayTapped) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            when {
                state.loading -> item(key = "loading") {
                    DelayedLoadingIndicator(contentDescription = stringResource(Res.string.today_notes_loading_cd))
                }
                state.days.isEmpty() -> item(key = "empty") {
                    EmptyCard(
                        message = stringResource(
                            if (state.canShowEarlier && state.windowDays > NotesStateBuilder.PAGE_DAYS) Res.string.today_notes_empty_window else Res.string.today_notes_empty,
                        ),
                        icon = AppIcons.EditNote,
                    )
                }
                else -> items(state.days, key = { it.date.toString() }) { day -> NotesDayGroup(day, onAction) }
            }
            if (!state.loading && state.canShowEarlier) {
                item(key = "earlier") {
                    PromptAction(
                        label = stringResource(Res.string.today_notes_earlier),
                        onClick = { onAction(NotesAction.ShowEarlierTapped) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesDayGroup(day: NotesDay, onAction: (NotesAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        val label = day.label.text()
        SectionLabel(if (day.year != null) stringResource(Res.string.today_notes_day_year, label, day.year) else label)
        TodayCard(verticalArrangement = Arrangement.Top, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
            day.notes.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = AppTheme.colors.line)
                NoteRowView(row, onOpen = { onAction(NotesAction.NoteTapped(day.date, row.slot)) })
            }
        }
    }
}

@Composable
private fun NoteRowView(row: NoteRow, onOpen: () -> Unit) {
    val title = stringResource(row.title)
    val edit = if (row.slot == null) stringResource(Res.string.today_notes_edit_day_cd) else stringResource(Res.string.today_notes_edit_cd, title)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { onClick(label = edit) { onOpen(); true } }
            .bounceClick(scaleDown = 0.99f, onClick = onOpen)
            .padding(vertical = AppTheme.spacing.row),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        val slot = row.slot
        if (slot != null) RowIcon(icon = slot.icon, color = slot.tileColors().content) else RowIcon(icon = AppIcons.EditNote, color = AppTheme.colors.muted)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
            NoteBody(row.note)
        }
    }
}

@Composable
private fun NoteBody(note: MealNoteUi) {
    note.hunger?.let { Text(text = stringResource(Res.string.today_note_hunger_line, stringResource(it)), typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.inkSoft) }
    note.text?.let { Text(text = it, typography = AppTheme.type.body, color = AppTheme.colors.ink) }
}
