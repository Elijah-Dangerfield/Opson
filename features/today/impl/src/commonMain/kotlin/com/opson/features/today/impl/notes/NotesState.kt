package com.opson.features.today.impl.notes

import com.opson.features.today.impl.MealNoteUi
import com.opson.features.today.impl.TodayStateBuilder
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.ui.label
import com.opson.libraries.ui.time.RelativeDay
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource

/**
 * The Notes screen: every meal's Note in the last [windowDays], newest first, one group per day.
 * Notes are what someone wrote, never a number, so every mode shows them in full.
 */
data class NotesState(
    val loading: Boolean = true,
    val fromTrends: Boolean = false,
    val days: List<NotesDay> = emptyList(),
    val windowDays: Int = NotesStateBuilder.PAGE_DAYS,
    /** Older notes can be loaded: the window has not reached [NotesStateBuilder.MAX_DAYS]. */
    val canShowEarlier: Boolean = true,
)

/** One day's notes, latest meal first. [year] is set only for a day outside this year. */
data class NotesDay(val date: LocalDate, val label: RelativeDay, val year: Int?, val notes: List<NoteRow>)

/** A meal's Note under the meal's name. */
data class NoteRow(val slot: MealSlot, val meal: StringResource, val note: MealNoteUi)

sealed interface NotesAction {
    data class DaysArrived(val days: List<Day>, val today: LocalDate, val windowDays: Int) : NotesAction
    data object ShowEarlierTapped : NotesAction

    /** Opens the meal's Note sheet to change it. */
    data class NoteTapped(val day: LocalDate, val slot: MealSlot) : NotesAction
    data object BackTapped : NotesAction
}

sealed interface NotesEvent {
    data class OpenNote(val day: LocalDate, val slot: MealSlot) : NotesEvent
    data object Close : NotesEvent
}

object NotesStateBuilder {
    /** How many days one page reads, and how far "Show earlier notes" reaches each time. */
    const val PAGE_DAYS = 90

    /** About three years; past that the list stops offering more. */
    const val MAX_DAYS = 1095

    /** Within a day the latest meal comes first, and Uncategorized, which has no time of its own, last. */
    private val slotOrder = MealSlot.meals.reversed() + MealSlot.Uncategorized

    fun build(days: List<Day>, today: LocalDate, windowDays: Int, fromTrends: Boolean): NotesState = NotesState(
        loading = false,
        fromTrends = fromTrends,
        days = days.sortedByDescending { it.date }.mapNotNull { day ->
            val notes = slotOrder.mapNotNull { slot -> TodayStateBuilder.noteOf(day, slot)?.let { NoteRow(slot, slot.label, it) } }
            if (notes.isEmpty()) {
                null
            } else {
                NotesDay(day.date, RelativeDay.of(day.date, today), day.date.year.takeIf { it != today.year }, notes)
            }
        },
        windowDays = windowDays,
        canShowEarlier = windowDays < MAX_DAYS,
    )
}
