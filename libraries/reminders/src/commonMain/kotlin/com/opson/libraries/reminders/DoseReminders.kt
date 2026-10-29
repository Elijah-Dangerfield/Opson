package com.opson.libraries.reminders

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

/**
 * A dose day the medication plan wants a nudge for. [covered] is a dose already logged for it,
 * which drops the nudge and takes a delivered one off the screen. [quickLog] is whether "Already
 * did" can log it without the sheet: there is a current dose to log.
 */
data class DoseNudge(
    val medicationId: String,
    val date: LocalDate,
    val time: LocalTime,
    val covered: Boolean = false,
    val quickLog: Boolean = true,
)

/**
 * Dose days for the reminder plan, from `:libraries:medication:impl`. Kept as this small seam so
 * the planner stays the one place that decides what is pending: the platform's set is replaced
 * whole on every plan, so a second scheduler would have its notifications cancelled.
 */
interface DoseReminders {
    /**
     * The dose days from [from] for [days] days that want a nudge, while `release.medication`,
     * tracking and the medication's own reminder switch are all on. Empty otherwise.
     */
    fun upcoming(from: LocalDate, days: Int): Flow<List<DoseNudge>>

    /** "Already did": a dose of the current amount at [at], on the suggested site. */
    suspend fun alreadyDid(medicationId: String, date: LocalDate, at: Instant)

    /** For a build with no medication module, and tests that are not about doses. */
    object None : DoseReminders {
        override fun upcoming(from: LocalDate, days: Int): Flow<List<DoseNudge>> = flowOf(emptyList())

        override suspend fun alreadyDid(medicationId: String, date: LocalDate, at: Instant) = Unit
    }
}
