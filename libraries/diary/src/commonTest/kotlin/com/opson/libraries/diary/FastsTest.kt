package com.opson.libraries.diary

import com.opson.libraries.nutrition.FastingPlan
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The fasting rules as code: who sees fasting ([FastingAccess]), when a fast starts and what the
 * status card says ([FastingSchedule]), and the 16 hour cap ([Fast.endsBy]). Times are UTC
 * unless a test says otherwise; the 14:10 plan opens at 9:00 and closes at 19:00.
 */
class FastsTest {
    private val utc = TimeZone.UTC
    private val plan = FastingPlan.FourteenTen
    private val oct25 = LocalDate(2026, 10, 25)

    private fun at(text: String) = Instant.parse(text)

    // ---------- who sees it ----------

    @Test
    fun access_isOfferedOnlyInFullAndSprint() {
        PresetId.entries.forEach { preset ->
            val expected = if (preset == PresetId.Full || preset == PresetId.Full) FastingAccess.Offered else FastingAccess.NotInThisMode
            assertEquals(expected, FastingAccess.of(settings(preset), released = true), preset.name)
        }
    }

    @Test
    fun access_isHiddenInEveryModeForSomeoneWhoAgreedNumbersHaveBeenHard() {
        PresetId.entries.forEach { preset ->
            assertEquals(FastingAccess.Hidden, FastingAccess.of(settings(preset, hard = true), released = true), preset.name)
        }
    }

    @Test
    fun access_isHiddenInEveryModeWhileTheReleaseFlagIsOff() {
        PresetId.entries.forEach { preset ->
            assertEquals(FastingAccess.Hidden, FastingAccess.of(settings(preset), released = false), preset.name)
        }
    }

    @Test
    fun timerOn_needsTheSwitchAndAccess() {
        assertFalse(Settings().fastingTimerOn(released = true), "off by default")
        assertTrue(settings(PresetId.Full).copy(fasting = true).fastingTimerOn(released = true))
        assertFalse(settings(PresetId.NoNumbers).copy(fasting = true).fastingTimerOn(released = true))
        assertFalse(settings(PresetId.Full, hard = true).copy(fasting = true).fastingTimerOn(released = true))
        assertFalse(settings(PresetId.Full).copy(fasting = true).fastingTimerOn(released = false))
    }

    // ---------- the cap ----------

    @Test
    fun aFastNeverRunsPast16Hours() {
        val start = at("2026-10-25T19:00:00Z")
        assertEquals(start + 14.hours, Fast(oct25, start, goalHours = 14).endsBy)
        assertEquals(start + 16.hours, Fast(oct25, start, goalHours = 20).endsBy, "a goal over the cap is cut to it")
        assertEquals(start + 16.hours, Fast(oct25, start, goalHours = null).endsBy)
        assertFalse(Fast(oct25, start, goalHours = 16).isRunningAt(start + 16.hours))
    }

    @Test
    fun endedAt_isTheRecordedEndOrTheGoalOnceItPasses() {
        val start = at("2026-10-25T19:00:00Z")
        val fast = Fast(oct25, start, goalHours = 14)
        assertNull(fast.endedAt(start + 3.hours))
        assertEquals(start + 14.hours, fast.endedAt(start + 15.hours))
        assertEquals(start + 2.hours, fast.copy(endedAt = start + 2.hours).endedAt(start + 3.hours))
    }

    // ---------- the window ----------

    @Test
    fun theWindowIsOpenFromOpeningUntilClosing() {
        assertFalse(FastingSchedule.isWindowOpen(plan, at("2026-10-25T08:59:00Z"), utc))
        assertTrue(FastingSchedule.isWindowOpen(plan, at("2026-10-25T09:00:00Z"), utc))
        assertTrue(FastingSchedule.isWindowOpen(plan, at("2026-10-25T18:59:00Z"), utc))
        assertFalse(FastingSchedule.isWindowOpen(plan, at("2026-10-25T19:00:00Z"), utc))
    }

    @Test
    fun lastClose_isTonightAfterClosingAndLastNightBeforeIt() {
        assertEquals(oct25 to at("2026-10-25T19:00:00Z"), FastingSchedule.lastClose(plan, at("2026-10-25T22:00:00Z"), utc))
        assertEquals(oct25 to at("2026-10-25T19:00:00Z"), FastingSchedule.lastClose(plan, at("2026-10-26T06:00:00Z"), utc))
    }

    @Test
    fun lastClose_followsTheDevicesZone() {
        val tokyo = TimeZone.of("Asia/Tokyo")
        // 11:00 UTC is 20:00 in Tokyo, after that evening's 19:00 close (10:00 UTC).
        assertEquals(oct25 to at("2026-10-25T10:00:00Z"), FastingSchedule.lastClose(plan, at("2026-10-25T11:00:00Z"), tokyo))
    }

    // ---------- when a fast starts ----------

    @Test
    fun due_startsAtTheCloseWithThePlansHours() {
        val due = FastingSchedule.due(plan, at("2026-10-25T21:30:00Z"), utc, latest = null, lastLoggedAt = null)
        assertEquals(Fast(oct25, at("2026-10-25T19:00:00Z"), goalHours = 14), due)
    }

    @Test
    fun due_afterMidnightIsLastNightsFast() {
        val due = FastingSchedule.due(plan, at("2026-10-26T05:00:00Z"), utc, latest = null, lastLoggedAt = null)
        assertEquals(oct25, due?.day)
    }

    @Test
    fun due_nothingWhileTheWindowIsOpen() {
        assertNull(FastingSchedule.due(plan, at("2026-10-25T12:00:00Z"), utc, latest = null, lastLoggedAt = null))
    }

    @Test
    fun due_nothingWhenTheEveningAlreadyHasAFast() {
        val tonight = Fast(oct25, at("2026-10-25T19:00:00Z"), endedAt = at("2026-10-25T20:00:00Z"), goalHours = 14)
        assertNull(FastingSchedule.due(plan, at("2026-10-25T22:00:00Z"), utc, latest = tonight, lastLoggedAt = null))
    }

    @Test
    fun due_nothingWhenFoodWasLoggedAfterTheClose() {
        assertNull(FastingSchedule.due(plan, at("2026-10-25T22:00:00Z"), utc, latest = null, lastLoggedAt = at("2026-10-25T20:10:00Z")))
        val beforeClose = FastingSchedule.due(plan, at("2026-10-25T22:00:00Z"), utc, latest = null, lastLoggedAt = at("2026-10-25T18:30:00Z"))
        assertEquals(oct25, beforeClose?.day, "dinner before the close does not stop the fast")
    }

    @Test
    fun due_yesterdaysFastDoesNotStopTonights() {
        val lastNight = Fast(LocalDate(2026, 10, 24), at("2026-10-24T19:00:00Z"), goalHours = 14)
        assertEquals(oct25, FastingSchedule.due(plan, at("2026-10-25T20:00:00Z"), utc, lastNight, lastLoggedAt = null)?.day)
    }

    // ---------- the status card ----------

    @Test
    fun status_runningCountsFromTheStart() {
        val fast = Fast(oct25, at("2026-10-25T19:00:00Z"), goalHours = 14)
        assertEquals(
            FastingStatus.Running(since = LocalTime(19, 0), elapsed = 13.hours + 40.minutes),
            FastingSchedule.status(plan, at("2026-10-26T08:40:00Z"), utc, fast),
        )
    }

    @Test
    fun status_theWindowOpensWhenTheGoalIsReached() {
        val fast = Fast(oct25, at("2026-10-25T19:00:00Z"), goalHours = 14)
        assertEquals(FastingStatus.WindowOpen(until = LocalTime(19, 0)), FastingSchedule.status(plan, at("2026-10-26T09:00:00Z"), utc, fast))
    }

    @Test
    fun status_endedEarlySaysWhenAndWhenTheNextStarts() {
        val fast = Fast(oct25, at("2026-10-25T19:00:00Z"), endedAt = at("2026-10-25T20:10:00Z"), goalHours = 14)
        assertEquals(
            FastingStatus.Ended(at = LocalTime(20, 10), nextStart = LocalTime(19, 0)),
            FastingSchedule.status(plan, at("2026-10-25T23:00:00Z"), utc, fast),
        )
    }

    @Test
    fun status_restingWhenNoFastStartedThisEvening() {
        val lastNight = Fast(LocalDate(2026, 10, 24), at("2026-10-24T19:00:00Z"), goalHours = 14)
        assertEquals(FastingStatus.Resting(nextStart = LocalTime(19, 0)), FastingSchedule.status(plan, at("2026-10-25T21:00:00Z"), utc, lastNight))
        assertEquals(FastingStatus.Resting(nextStart = LocalTime(19, 0)), FastingSchedule.status(plan, at("2026-10-25T21:00:00Z"), utc, null))
    }

    @Test
    fun status_neverReadsPastTheCapEvenWithALongerGoal() {
        val fast = Fast(oct25, at("2026-10-25T19:00:00Z"), goalHours = 20)
        val status = FastingSchedule.status(FastingPlan.SixteenEight, at("2026-10-26T10:59:00Z"), utc, fast)
        assertTrue(status is FastingStatus.Running && status.elapsed <= 16.hours)
        assertTrue(FastingSchedule.status(FastingPlan.SixteenEight, at("2026-10-26T11:00:00Z"), utc, fast) !is FastingStatus.Running)
    }

    private fun settings(preset: PresetId, hard: Boolean = false) =
        Settings(numbers = NumbersSettings(preset = preset), onboarding = OnboardingAnswers(hard = hard))
}
