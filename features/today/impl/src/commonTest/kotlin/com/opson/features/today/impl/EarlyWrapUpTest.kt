package com.opson.features.today.impl

import com.opson.features.today.impl.dayclose.EarlyWrapUp
import com.opson.libraries.diary.RecapKind
import com.opson.libraries.diary.RecapPeriod
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.PresetId
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Wrap up the day's "It's only 7:00 AM" note, and its way to the week's recap. */
class EarlyWrapUpTest {

    @Test
    fun threeHoursBeforeTheUsualWrapUp_isEarly() {
        val usual = LocalTime(20, 0)
        assertEquals(EarlyWrapUp(LocalTime(7, 0), usual), EarlyWrapUp.of(LocalTime(7, 0), usual))
        assertNotNull(EarlyWrapUp.of(LocalTime(16, 55), usual))
        assertNull(EarlyWrapUp.of(LocalTime(17, 0), usual))
        assertNull(EarlyWrapUp.of(LocalTime(21, 30), usual))
    }

    @Test
    fun beforeTheUsualTimeIsLearned_onlyTheMorningIsEarly() {
        assertEquals(EarlyWrapUp(LocalTime(9, 30), null), EarlyWrapUp.of(LocalTime(9, 30), null))
        assertNull(EarlyWrapUp.of(LocalTime(12, 0), null))
        assertNull(EarlyWrapUp.of(LocalTime(18, 0), null))
    }

    @Test
    fun aWrapUpAfterMidnight_isTheEndOfTheSameNight() {
        // Usually 12:30 AM: 10 pm is early, 11 pm is not.
        val usual = LocalTime(0, 30)
        assertNotNull(EarlyWrapUp.of(LocalTime(21, 0), usual))
        assertNull(EarlyWrapUp.of(LocalTime(22, 30), usual))
    }

    @Test
    fun someoneWhoWrapsUpInTheMorning_isHeldToTheirOwnTime_notToNoon() {
        // A night shift: usually wrapped up at 9 am.
        val usual = LocalTime(9, 0)
        assertNotNull(EarlyWrapUp.of(LocalTime(5, 30), usual))
        assertNull(EarlyWrapUp.of(LocalTime(8, 0), usual))
        assertNull(EarlyWrapUp.of(LocalTime(10, 0), usual))
    }

    @Test
    fun theNote_isOnlyForTodayNotYetWrappedUp() {
        val full = policy(PresetId.Full)
        val morning = LocalTime(7, 0)
        assertNotNull(TodayFixtures.dayCloseState(full, now = morning, usualWrapUp = LocalTime(20, 0)).early)
        assertNull(TodayFixtures.dayCloseState(full, now = morning, closed = true).early, "already wrapped up")
        assertNull(TodayFixtures.dayCloseState(full, now = morning, date = LocalDate(2026, 10, 24)).early, "a past day has no clock")
        assertNull(TodayFixtures.dayCloseState(full).early, "nothing knows the time")
    }

    @Test
    fun wrappingUpTheWeeksLastDay_offersItsRecap_withRecapsReleased() {
        val full = policy(PresetId.Full)
        // The fixtures' today is a Sunday: the last day of a week that starts on Monday.
        val week = RecapPeriod(RecapKind.Week, LocalDate(2026, 10, 19))
        assertEquals(week, TodayFixtures.dayCloseState(full, closed = true, recapsOn = true).weekRecap)
        assertNull(TodayFixtures.dayCloseState(full, closed = true).weekRecap, "recaps not released")
        assertNull(TodayFixtures.dayCloseState(full, recapsOn = true).weekRecap, "not wrapped up yet")
        val sundayStart = Settings(weekStart = DayOfWeek.SUNDAY)
        assertNull(TodayFixtures.dayCloseState(full, settings = sundayStart, closed = true, recapsOn = true).weekRecap, "a week starting today has just begun")
    }
}
