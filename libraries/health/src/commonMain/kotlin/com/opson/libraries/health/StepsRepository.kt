package com.opson.libraries.health

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Steps from the health store: Apple Health's daily step count, which it already merges across
 * iPhone and Apple Watch the way the Health app does, or Health Connect's daily total across the
 * apps that write steps. Read on the device and kept on it. Nothing here goes to the server, logs
 * or telemetry.
 *
 * Steps never add to Burned. The store's active energy already counts walking, so adding steps on
 * top would count it twice.
 *
 * Its own interface so the many [HealthRepository] fakes stay as they are. Callers check
 * `release.health` before collecting, as they do for energy: nothing touches the store with it off.
 */
interface StepsRepository {
    /**
     * Connected, with something Opson reads not allowed yet: on a phone connected before steps
     * were read, that is steps. Asking again is [HealthRepository.connect]. The same as
     * [HealthRecords.canAskForMore].
     */
    val canAskForMore: StateFlow<Boolean>

    /**
     * Each day's steps from [from] to [to], both included, midnight to midnight in [timeZone].
     * Past days are read once, today (when it is in the span) again whenever energy is, so the
     * count moves while the screen is open. A day with no steps recorded is missing. Null while
     * not connected or when the store could not be read, never an empty span standing in for one.
     *
     * The last values are kept on the phone, so a cold start shows them before the store answers.
     */
    fun stepsBetween(from: LocalDate, to: LocalDate, timeZone: TimeZone): Flow<Map<LocalDate, Int>?>

    /** [day]'s steps, live when it is today; null when not connected, unread or none recorded. */
    fun steps(day: LocalDate, timeZone: TimeZone): Flow<Int?> = stepsBetween(day, day, timeZone).map { it?.get(day) }

    /** Reads nothing: for previews, tests and anything built without a health store. */
    object None : StepsRepository {
        override val canAskForMore: StateFlow<Boolean> = MutableStateFlow(false)

        override fun stepsBetween(from: LocalDate, to: LocalDate, timeZone: TimeZone): Flow<Map<LocalDate, Int>?> = flowOf(null)
    }
}
