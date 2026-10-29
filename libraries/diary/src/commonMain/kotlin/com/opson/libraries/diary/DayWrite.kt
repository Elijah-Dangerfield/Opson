package com.opson.libraries.diary

import kotlinx.datetime.LocalDate

/**
 * What a write to one day's log came to. A day closed with Close the day takes nothing new and
 * keeps what it has until it is reopened (docs/decisions.md, 2026-10-27), so a write that would
 * change its food, water or notes answers [DayClosed] and writes nothing.
 */
sealed interface DayWrite<out T> {
    data class Written<out T>(val value: T) : DayWrite<T>

    /** [day] is closed. Nothing was written. */
    data class DayClosed(val day: LocalDate) : DayWrite<Nothing>
}

/** The written value, or null when the day was closed. */
fun <T> DayWrite<T>.valueOrNull(): T? = (this as? DayWrite.Written)?.value

/**
 * What Close the day saves with the close itself. Each null clears what was stored. The check-in
 * is not here: it has its own write ([DayCheckIns]), and closing leaves it as it is.
 */
data class DayReflection(
    val note: String? = null,
    val feeling: DayFeeling? = null,
)
