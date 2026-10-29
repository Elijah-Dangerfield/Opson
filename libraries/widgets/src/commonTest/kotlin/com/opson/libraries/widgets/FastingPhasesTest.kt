package com.opson.libraries.widgets

import com.opson.libraries.diary.Fast
import com.opson.libraries.nutrition.FastingPlan
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** The fasting widget's phases on 14:10 (window 9 am to 7 pm), in UTC. */
class FastingPhasesTest {

    private val zone = TimeZone.UTC
    private val day = LocalDate(2026, 10, 25)
    private val plan = FastingPlan.FourteenTen
    private val copy = WidgetCopy.FastingCopy()

    private fun at(hour: Int, minute: Int = 0, date: LocalDate = day): Instant = LocalDateTime(date, LocalTime(hour, minute)).toInstant(zone)

    private fun phases(now: Instant, latest: Fast? = null, lastLoggedAt: Instant? = null): List<WidgetFastingPhase> =
        FastingPhases.build(FastingInputs(plan, latest, lastLoggedAt, now, zone), copy)

    @Test
    fun inTheWindow_itRunsToTheClose_thenAFastOfThePlansHours() {
        val phases = phases(now = at(12))

        assertEquals(WidgetFastingPhase.WINDOW, phases[0].kind)
        assertEquals(0L, phases[0].fromMs, "the phase showing now has no start to wait for")
        assertEquals(at(19).toEpochMilliseconds(), phases[0].untilMs)

        assertEquals(WidgetFastingPhase.FASTING, phases[1].kind)
        assertEquals(at(19).toEpochMilliseconds(), phases[1].fromMs)
        assertEquals(at(19).toEpochMilliseconds(), phases[1].sinceMs)
        assertEquals((at(19) + 14.hours).toEpochMilliseconds(), phases[1].untilMs)

        assertEquals(WidgetFastingPhase.WINDOW, phases[2].kind, "the fast ends as the window opens")
    }

    @Test
    fun aRunningFast_isTheTimer_fromWhenItStarted() {
        val fast = Fast(day = day, startedAt = at(19), goalHours = 14)
        val phases = phases(now = at(22), latest = fast)

        assertEquals(WidgetFastingPhase.FASTING, phases[0].kind)
        assertEquals(at(19).toEpochMilliseconds(), phases[0].sinceMs)
        assertEquals("Fasting", phases[0].title)
        assertEquals("Ends at", phases[0].caption)
    }

    @Test
    fun afterTheCloseWithNoFastWrittenYet_theDueFastShowsAsRunning() {
        val phases = phases(now = at(21))

        assertEquals(WidgetFastingPhase.FASTING, phases[0].kind)
        assertEquals(at(19).toEpochMilliseconds(), phases[0].sinceMs, "the same start the app writes")
    }

    @Test
    fun foodAfterTheClose_meansNoFastTonight() {
        val phases = phases(now = at(21), lastLoggedAt = at(20))

        assertEquals(WidgetFastingPhase.DONE, phases[0].kind)
        assertEquals("Next fast at", phases[0].caption)
        assertEquals(at(19, date = LocalDate(2026, 10, 26)).toEpochMilliseconds(), phases[0].untilMs)
        assertEquals(WidgetFastingPhase.WINDOW, phases[1].kind)
        assertEquals(at(9, date = LocalDate(2026, 10, 26)).toEpochMilliseconds(), phases[1].fromMs)
    }

    @Test
    fun aFastEndedEarly_isDone_untilTheWindowOpens() {
        val fast = Fast(day = LocalDate(2026, 10, 24), startedAt = at(19, date = LocalDate(2026, 10, 24)), endedAt = at(6), goalHours = 14)
        val phases = phases(now = at(7), latest = fast)

        assertEquals(WidgetFastingPhase.DONE, phases[0].kind)
        assertEquals(at(9).toEpochMilliseconds(), phases[1].fromMs)
        assertEquals(WidgetFastingPhase.WINDOW, phases[1].kind)
    }

    @Test
    fun thePhasesRunADayAndAHalfAhead_andStop() {
        val phases = phases(now = at(12))
        val last = phases.last()
        assertTrue(phases.size in 3..8)
        assertTrue(last.fromMs <= (at(12) + 36.hours).toEpochMilliseconds())
    }
}
