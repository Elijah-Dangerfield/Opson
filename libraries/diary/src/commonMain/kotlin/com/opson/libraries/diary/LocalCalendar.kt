package com.opson.libraries.diary

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * The device's calendar: today, in the zone the user is in right now. Screens
 * ask this rather than reading the system clock and zone themselves, so a test
 * can pin the date.
 */
interface LocalCalendar {
    fun timeZone(): TimeZone

    fun today(): LocalDate
}
