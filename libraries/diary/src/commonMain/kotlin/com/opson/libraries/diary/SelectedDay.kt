package com.opson.libraries.diary

import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate

/**
 * The day Today is showing. Today writes it; the center + reads it when the
 * logger is opened without a day, so food lands on the day the user is
 * looking at.
 *
 * At local midnight it moves to the new date if it was on the old today, so a
 * phone left open overnight does not keep logging into yesterday. A day the
 * user picked on purpose stays picked.
 */
interface SelectedDay {
    val date: StateFlow<LocalDate>

    fun select(day: LocalDate)
}
