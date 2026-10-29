package com.opson.features.onboarding.impl

import com.opson.libraries.diary.Settings
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Today's birthday line: on the day, on today only, never with celebrations off, and gone once put away. */
class BirthdayCardTest {

    private val today = LocalDate(2026, 10, 25)
    private val born = Settings().withBirthDate(LocalDate(1990, 10, 25))

    @Test
    fun itShows_onTheirBirthday_onToday() {
        assertTrue(BirthdayCardViewModel.shows(born, today, day = today, thankedOn = null))
    }

    @Test
    fun itDoesNotShow_onAnotherDay_aDayLookedBackOn_withCelebrationsOff_orOncePutAway() {
        assertFalse(BirthdayCardViewModel.shows(born, LocalDate(2026, 10, 26), day = LocalDate(2026, 10, 26), thankedOn = null))
        assertFalse(BirthdayCardViewModel.shows(born, today, day = LocalDate(2026, 10, 24), thankedOn = null), "Today showing yesterday")
        assertFalse(BirthdayCardViewModel.shows(born.copy(celebrationsEnabled = false), today, day = today, thankedOn = null))
        assertFalse(BirthdayCardViewModel.shows(born, today, day = today, thankedOn = "2026-10-25"))
        assertTrue(BirthdayCardViewModel.shows(born, today, day = today, thankedOn = "2025-10-25"), "last year's thanks")
        assertFalse(BirthdayCardViewModel.shows(Settings(birthYear = 1990), today, day = today, thankedOn = null), "only a year")
    }
}
