package com.opson.libraries.nutrition

import kotlinx.datetime.LocalDate

/**
 * "End after a set time" (design sync 4, 13.5a): [weeks] from [startedOn], then the mode switches
 * to [then]. Works on any preset. Sprint became this.
 *
 * When the time is up the app switches and says so once on Today, unless the switch shows more
 * numbers while "Pause before showing more" is on: showing more still goes through the pause, so
 * then Today offers the switch instead of making it ([NumbersSettings.timedOut]).
 */
data class ModeTiming(val startedOn: LocalDate, val weeks: Int, val then: PresetId) {

    /** The first day on [then]. */
    val endsOn: LocalDate get() = startedOn.plusDays(weeks * DAYS_PER_WEEK)

    fun isDue(today: LocalDate): Boolean = today >= endsOn

    companion object {
        const val DAYS_PER_WEEK = 7

        /** The lengths on offer, in weeks. */
        val LENGTHS: List<Int> = listOf(1, 2, 4)

        const val DEFAULT_WEEKS = 2

        /**
         * What the switch offers first when turned on: No numbers, the design's "2 weeks, then No
         * numbers", or Macros only for someone already on No numbers, the next gentlest.
         */
        fun defaultThen(current: PresetId): PresetId = if (current == PresetId.NoNumbers) PresetId.MacrosOnly else PresetId.NoNumbers
    }
}
