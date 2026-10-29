package com.opson.features.today.impl.notes

import com.opson.features.today.NotesOrigin
import com.opson.features.today.impl.MealNoteUi
import com.opson.features.today.impl.TodayStateBuilder
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.ui.label
import com.opson.libraries.ui.time.RelativeDay
import kotlinx.datetime.LocalDate
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_notes_day_note
import org.jetbrains.compose.resources.StringResource

/**
 * The Notes screen: every note in the last [windowDays], newest first, one group per day. A day's
 * group holds its own note first, then each meal's Note. Notes are what someone wrote, never a
 * number, so every mode shows them in full.
 */
data class NotesState(
    val loading: Boolean = true,
    val from: NotesOrigin = NotesOrigin.Today,
    val days: List<NotesDay> = emptyList(),
    val windowDays: Int = NotesStateBuilder.PAGE_DAYS,
    /** Older notes can be loaded: the window has not reached [NotesStateBuilder.MAX_DAYS]. */
    val canShowEarlier: Boolean = true,
    /** Today has no note of its own yet, so the list offers to write one. */
    val canAddToday: Boolean = false,
)

/** One day's notes, the day's own first, then latest meal first. [year] is set only for a day outside this year. */
data class NotesDay(val date: LocalDate, val label: RelativeDay, val year: Int?, val notes: List<NoteRow>)

/**
 * One note under where it was written: [slot] the meal, or null for the day's own note, which
 * Close the day and the Note sheet both write. [title] is the meal's name or "Day note".
 */
data class NoteRow(val slot: MealSlot?, val title: StringResource, val note: MealNoteUi)

sealed interface NotesAction {
    data class DaysArrived(val days: List<Day>, val today: LocalDate, val windowDays: Int) : NotesAction
    data object ShowEarlierTapped : NotesAction

    /** Opens the note to change it: the meal's Note sheet, or the day's Note sheet when [slot] is null. */
    data class NoteTapped(val day: LocalDate, val slot: MealSlot?) : NotesAction

    /** "Write a note for today". */
    data object AddTodayTapped : NotesAction
    data object BackTapped : NotesAction
}

sealed interface NotesEvent {
    data class OpenNote(val day: LocalDate, val slot: MealSlot) : NotesEvent
    data class OpenDayNote(val day: LocalDate) : NotesEvent
    data object Close : NotesEvent
}

object NotesStateBuilder {
    /** How many days one page reads, and how far "Show earlier notes" reaches each time. */
    const val PAGE_DAYS = 90

    /** About three years; past that the list stops offering more. */
    const val MAX_DAYS = 1095

    /** Within a day the latest meal comes first, and Uncategorized, which has no time of its own, last. */
    private val slotOrder = MealSlot.meals.reversed() + MealSlot.Uncategorized

    fun build(days: List<Day>, today: LocalDate, windowDays: Int, from: NotesOrigin): NotesState = NotesState(
        loading = false,
        from = from,
        days = days.sortedByDescending { it.date }.mapNotNull { day ->
            val dayNote = dayNoteOf(day)?.let { NoteRow(slot = null, title = Res.string.today_notes_day_note, note = MealNoteUi(hunger = null, text = it)) }
            val notes = listOfNotNull(dayNote) +
                slotOrder.mapNotNull { slot -> TodayStateBuilder.noteOf(day, slot)?.let { NoteRow(slot, slot.label, it) } }
            if (notes.isEmpty()) {
                null
            } else {
                NotesDay(day.date, RelativeDay.of(day.date, today), day.date.year.takeIf { it != today.year }, notes)
            }
        },
        windowDays = windowDays,
        canShowEarlier = windowDays < MAX_DAYS,
        canAddToday = days.none { it.date == today && dayNoteOf(it) != null },
    )

    private fun dayNoteOf(day: Day): String? = day.note?.trim()?.takeIf { it.isNotEmpty() }
}
