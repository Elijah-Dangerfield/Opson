package com.opson.libraries.nutrition

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Delayed reveal, ported from `revealed` in the prototype. For Delayed (and
 * any preset without a reveal setting) a delayed number opens on the week view,
 * at day close, once the day is closed, for any past day, and from 9:00 pm local
 * on the day itself. Coast opens on the week view and nowhere else.
 *
 * Not covered here: what a revealed number looks like, which is the same as a
 * shown one ([VisibilityPolicyResolveTest]).
 */
class VisibilityPolicyRevealTest {

    private val closedSurfaces = Surface.entries - Surface.Week - Surface.DayClose

    private fun delayedCalories(
        surface: Surface,
        now: LocalDateTime = MORNING,
        context: FormatContext = FormatContext(),
        preset: PresetId = PresetId.Delayed,
        zone: TimeZone = TimeZone.UTC,
    ): Display = policy(preset, now = now, zone = zone).resolve(Metric.Calories, 1245.0, surface, context.copy(bare = true))

    @Test
    fun delayed_staysHiddenInTheMorningOnEveryOrdinarySurface() {
        closedSurfaces.forEach { surface ->
            assertEquals(Display.Hidden, delayedCalories(surface), surface.name)
        }
    }

    @Test
    fun delayed_opensOnTheWeekView() {
        assertEquals(Display.Shown("1,245"), delayedCalories(Surface.Week))
    }

    @Test
    fun delayed_opensAtDayClose() {
        assertEquals(Display.Shown("1,245"), delayedCalories(Surface.DayClose))
    }

    @Test
    fun delayed_opensOnceTheDayIsClosed() {
        closedSurfaces.forEach { surface ->
            assertEquals(Display.Shown("1,245"), delayedCalories(surface, context = FormatContext(dayClosed = true)), surface.name)
        }
    }

    @Test
    fun delayed_opensForPastDays() {
        closedSurfaces.forEach { surface ->
            assertEquals(Display.Shown("1,245"), delayedCalories(surface, context = FormatContext(day = YESTERDAY)), surface.name)
        }
    }

    @Test
    fun delayed_staysHiddenOneMinuteBeforeNine() {
        assertEquals(Display.Hidden, delayedCalories(Surface.Today, now = at(20, 59)))
    }

    @Test
    fun delayed_opensAtNineSharp() {
        assertEquals(Display.Shown("1,245"), delayedCalories(Surface.Today, now = at(21, 0)))
    }

    @Test
    fun delayed_opensAtTheUsersOwnRevealTime() {
        val settings = NumbersSettings(preset = PresetId.Delayed, revealAt = LocalTime(19, 30))
        fun policyAt(hour: Int, minute: Int) = VisibilityPolicy(settings, clockAt(at(hour, minute)), TimeZone.UTC)
        assertFalse(policyAt(19, 29).isRevealed(Surface.Today))
        assertTrue(policyAt(19, 30).isRevealed(Surface.Today))
        assertEquals(Display.Shown("1,245"), policyAt(19, 30).resolve(Metric.Calories, 1245.0, Surface.Today, FormatContext(bare = true)))
    }

    @Test
    fun delayed_aLaterRevealTimeKeepsNineOClockShut() {
        val settings = NumbersSettings(preset = PresetId.Delayed, revealAt = LocalTime(22, 15))
        assertFalse(VisibilityPolicy(settings, clockAt(at(21, 0)), TimeZone.UTC).isRevealed(Surface.Today))
        assertTrue(VisibilityPolicy(settings, clockAt(at(22, 15)), TimeZone.UTC).isRevealed(Surface.Today))
    }

    @Test
    fun revealTime_defaultsToNine() {
        assertEquals(LocalTime(21, 0), NumbersSettings().revealAt)
    }

    @Test
    fun delayed_staysOpenUntilMidnight() {
        assertEquals(Display.Shown("1,245"), delayedCalories(Surface.Today, now = at(23, 59)))
    }

    @Test
    fun delayed_nineOClockAppliesToAnExplicitToday() {
        assertEquals(Display.Shown("1,245"), delayedCalories(Surface.Logger, now = at(21, 0), context = FormatContext(day = TODAY)))
    }

    @Test
    fun delayed_tomorrowIsNotRevealedByTonightsNineOClock() {
        assertEquals(Display.Hidden, delayedCalories(Surface.Today, now = at(22), context = FormatContext(day = TOMORROW)))
    }

    @Test
    fun delayed_nineOClockIsLocalTime() {
        val newYork = TimeZone.of("America/New_York")
        // 21:30 UTC on Oct 25 is 5:30 pm in New York (EDT).
        val utcEvening = LocalDateTime(2026, 10, 25, 21, 30)
        val clock = clockAt(utcEvening, TimeZone.UTC)
        val inNewYork = VisibilityPolicy(NumbersSettings(preset = PresetId.Delayed), clock, newYork)
        val inUtc = VisibilityPolicy(NumbersSettings(preset = PresetId.Delayed), clock, TimeZone.UTC)

        assertFalse(inNewYork.isRevealed(Surface.Today))
        assertTrue(inUtc.isRevealed(Surface.Today))
        assertTrue(policy(PresetId.Delayed, now = at(21), zone = newYork).isRevealed(Surface.Today))
    }

    @Test
    fun delayedOverride_withoutARevealSetting_behavesLikeDayClose() {
        val overrides = mapOf(Metric.Calories to Visibility.Delay)
        val morning = policy(PresetId.Full, overrides = overrides)
        val evening = policy(PresetId.Full, overrides = overrides, now = at(21))

        assertEquals(Display.Hidden, morning.resolve(Metric.Calories, 1245.0, Surface.Today))
        assertEquals(Display.Shown("1,245 kcal"), morning.resolve(Metric.Calories, 1245.0, Surface.DayClose))
        assertEquals(Display.Shown("1,245 kcal"), evening.resolve(Metric.Calories, 1245.0, Surface.Today))
    }

    @Test
    fun coast_opensOnTheWeekView() {
        assertEquals(Display.Shown("1,245"), delayedCalories(Surface.Week, preset = PresetId.Coast))
    }

    @Test
    fun coast_staysHiddenOffTheWeekView_whateverTheDayOrTime() {
        val contexts = listOf(
            FormatContext(),
            FormatContext(dayClosed = true),
            FormatContext(day = YESTERDAY),
            FormatContext(day = TODAY),
        )
        val times = listOf(MORNING, at(21), at(23, 59))
        (Surface.entries - Surface.Week).forEach { surface ->
            contexts.forEach { context ->
                times.forEach { now ->
                    assertEquals(
                        Display.Hidden,
                        delayedCalories(surface, now = now, context = context, preset = PresetId.Coast),
                        "$surface $context $now",
                    )
                }
            }
        }
    }

    @Test
    fun revealing_doesNotTouchSoftenedOrHiddenMetrics() {
        val evening = policy(PresetId.Delayed, now = at(22))
        assertEquals(Display.Soft("Trend only", null), evening.resolve(Metric.Weight, 72.6, Surface.Week))
        assertEquals(Display.Hidden, evening.resolve(Metric.Micros, 62.0, Surface.Week))
    }

    @Test
    fun effectiveLevel_neverReturnsDelay() {
        val delayed = policy(PresetId.Delayed)
        assertEquals(Visibility.Delay, delayed.level(Metric.Calories))
        assertEquals(Visibility.Hide, delayed.effectiveLevel(Metric.Calories, Surface.Today))
        assertEquals(Visibility.Show, delayed.effectiveLevel(Metric.Calories, Surface.Week))
        assertEquals(Visibility.Soften, delayed.effectiveLevel(Metric.Weight, Surface.Today))
    }
}
