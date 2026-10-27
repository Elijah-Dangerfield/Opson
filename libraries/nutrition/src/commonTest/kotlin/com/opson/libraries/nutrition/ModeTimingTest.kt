package com.opson.libraries.nutrition

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "End after a set time" (13.5a) and Later's daily or weekly reveal. */
class ModeTimingTest {

    private val start = LocalDate(2026, 10, 20)
    private val twoWeeks = ModeTiming(startedOn = start, weeks = 2, then = PresetId.NoNumbers)
    private val full = NumbersSettings(preset = PresetId.Full, endAfter = twoWeeks)

    @Test
    fun endsOn_isTheFirstDayOnTheNextPreset() {
        assertEquals(LocalDate(2026, 11, 3), twoWeeks.endsOn)
        assertFalse(twoWeeks.isDue(LocalDate(2026, 11, 2)))
        assertTrue(twoWeeks.isDue(LocalDate(2026, 11, 3)))
    }

    @Test
    fun timedOut_switchesOnTheDay_andClearsTheTimer() {
        assertNull(full.timedOut(LocalDate(2026, 11, 2), SOME_INSTANT))
        val after = assertNotNull(full.timedOut(LocalDate(2026, 11, 3), SOME_INSTANT))
        assertEquals(PresetId.NoNumbers, after.preset)
        assertNull(after.endAfter)
        assertEquals(SOME_INSTANT, after.changedAt)
    }

    @Test
    fun timedOut_clearsOverrides_likeAnyPick() {
        val adjusted = full.copy(overrides = mapOf(Metric.Micros to Visibility.Show))
        assertEquals(emptyMap(), adjusted.timedOut(LocalDate(2026, 11, 3), SOME_INSTANT)?.overrides)
    }

    @Test
    fun aSwitchToMore_withThePauseOn_waitsForThePerson() {
        val noNumbers = NumbersSettings(
            preset = PresetId.NoNumbers,
            pauseBeforeMore = true,
            endAfter = ModeTiming(startedOn = start, weeks = 1, then = PresetId.Full),
        )
        val due = LocalDate(2026, 10, 27)
        assertNull(noNumbers.timedOut(due, SOME_INSTANT))
        assertEquals(noNumbers.endAfter, noNumbers.timerWaiting(due))
        assertNull(noNumbers.timerWaiting(LocalDate(2026, 10, 26)), "not due yet")
        // Without the pause the switch is the person's own earlier choice, and it happens.
        assertEquals(PresetId.Full, noNumbers.copy(pauseBeforeMore = false).timedOut(due, SOME_INSTANT)?.preset)
    }

    @Test
    fun aSwitchToFewer_neverWaits_evenWithThePauseOn() {
        val paused = full.copy(pauseBeforeMore = true)
        assertEquals(PresetId.NoNumbers, paused.timedOut(LocalDate(2026, 11, 3), SOME_INSTANT)?.preset)
        assertNull(paused.timerWaiting(LocalDate(2026, 11, 3)))
    }

    @Test
    fun pickingAPreset_keepsTheTimer_unlessItPicksWhereTheTimerWasGoing() {
        assertEquals(twoWeeks, full.pickPreset(PresetId.Later, SOME_INSTANT).endAfter)
        assertNull(full.pickPreset(PresetId.NoNumbers, SOME_INSTANT).endAfter)
    }

    @Test
    fun revealingEachDayInsteadOfWeekly_isARaise_whenSomethingIsLater() {
        val weekly = NumbersSettings(preset = PresetId.Later, revealOn = RevealOn.Week)
        val daily = weekly.copy(revealOn = RevealOn.DayClose)
        assertTrue(weekly.isRaise(daily))
        assertFalse(daily.isRaise(weekly))
        assertTrue(NumbersChange.SetRevealWeekly(weekly = false).needsPause(weekly.copy(pauseBeforeMore = true)))
        assertFalse(NumbersChange.SetRevealWeekly(weekly = true).needsPause(daily.copy(pauseBeforeMore = true)))
        val nothingLater = NumbersSettings(preset = PresetId.Full, revealOn = RevealOn.Week)
        assertFalse(nothingLater.isRaise(nothingLater.copy(revealOn = RevealOn.DayClose)), "nothing waits, so nothing opens sooner")
    }

    @Test
    fun defaultThen_isNoNumbers_orLaterFromNoNumbers() {
        assertEquals(PresetId.NoNumbers, ModeTiming.defaultThen(PresetId.Full))
        assertEquals(PresetId.Later, ModeTiming.defaultThen(PresetId.NoNumbers))
    }
}
