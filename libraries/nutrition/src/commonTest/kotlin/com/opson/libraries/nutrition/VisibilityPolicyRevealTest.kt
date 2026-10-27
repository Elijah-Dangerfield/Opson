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
 * on the day itself. A weekly reveal (Coast's, now Later's) opens on the week view and nowhere else.
 *
 * Not covered here: what a revealed number looks like, which is the same as a
 * shown one ([VisibilityPolicyResolveTest]).
 */
class VisibilityPolicyRevealTest {

    /** Every surface that opens on the day's own terms. Widgets never open, see below. */
    private val closedSurfaces = Surface.entries - Surface.Week - Surface.DayClose - Surface.Widget

    private fun delayedCalories(
        surface: Surface,
        now: LocalDateTime = MORNING,
        context: FormatContext = FormatContext(),
        preset: PresetId = PresetId.Later,
        zone: TimeZone = TimeZone.UTC,
        revealOn: RevealOn = RevealOn.DayClose,
    ): Display = policy(preset, now = now, zone = zone, revealOn = revealOn).resolve(Metric.Calories, 1245.0, surface, context.copy(bare = true))

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
    fun delayed_neverOpensOnAWidget_whateverTheDayOrTime() {
        val contexts = listOf(FormatContext(), FormatContext(dayClosed = true), FormatContext(day = YESTERDAY))
        listOf(RevealOn.DayClose, RevealOn.Week).forEach { revealOn ->
            listOf(MORNING, at(21), at(23, 59)).forEach { now ->
                contexts.forEach { context ->
                    assertEquals(Display.Hidden, delayedCalories(Surface.Widget, now, context, revealOn = revealOn), "$revealOn $now $context")
                }
            }
        }
    }

    @Test
    fun aWidgetStillShowsWhatTheModeShows() {
        assertEquals(Display.Shown("1,245"), policy(PresetId.Full).resolve(Metric.Calories, 1245.0, Surface.Widget, FormatContext(bare = true)))
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
        val settings = NumbersSettings(preset = PresetId.Later, revealAt = LocalTime(19, 30))
        fun policyAt(hour: Int, minute: Int) = VisibilityPolicy(settings, clockAt(at(hour, minute)), TimeZone.UTC)
        assertFalse(policyAt(19, 29).isRevealed(Surface.Today))
        assertTrue(policyAt(19, 30).isRevealed(Surface.Today))
        assertEquals(Display.Shown("1,245"), policyAt(19, 30).resolve(Metric.Calories, 1245.0, Surface.Today, FormatContext(bare = true)))
    }

    @Test
    fun delayed_aLaterRevealTimeKeepsNineOClockShut() {
        val settings = NumbersSettings(preset = PresetId.Later, revealAt = LocalTime(22, 15))
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
        val inNewYork = VisibilityPolicy(NumbersSettings(preset = PresetId.Later), clock, newYork)
        val inUtc = VisibilityPolicy(NumbersSettings(preset = PresetId.Later), clock, TimeZone.UTC)

        assertFalse(inNewYork.isRevealed(Surface.Today))
        assertTrue(inUtc.isRevealed(Surface.Today))
        assertTrue(policy(PresetId.Later, now = at(21), zone = newYork).isRevealed(Surface.Today))
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
    fun weekly_opensOnTheWeekView() {
        assertEquals(Display.Shown("1,245"), delayedCalories(Surface.Week, revealOn = RevealOn.Week))
    }

    @Test
    fun weekly_theWeekEndingTodayOpensAtTheRevealTime() {
        val thisWeek = FormatContext(day = TODAY)
        assertEquals(Display.Hidden, delayedCalories(Surface.Week, now = MORNING, context = thisWeek, revealOn = RevealOn.Week))
        assertEquals(Display.Hidden, delayedCalories(Surface.Week, now = at(20, 59), context = thisWeek, revealOn = RevealOn.Week))
        assertEquals(Display.Shown("1,245"), delayedCalories(Surface.Week, now = at(21), context = thisWeek, revealOn = RevealOn.Week))
    }

    @Test
    fun weekly_theWeekOpensAtTheUsersOwnRevealTime() {
        fun coastAt(now: LocalDateTime) =
            VisibilityPolicy(NumbersSettings(preset = PresetId.Later, revealAt = LocalTime(19, 0), revealOn = RevealOn.Week), clockAt(now), TimeZone.UTC)
        val thisWeek = FormatContext(day = TODAY)
        assertEquals(LocalDateTime(TODAY, LocalTime(19, 0)), coastAt(MORNING).weekOpensAt(TODAY))
        assertEquals(Display.Hidden, coastAt(at(18, 59)).resolve(Metric.Calories, 1245.0, Surface.Week, thisWeek))
        assertEquals(Display.Shown("1,245 kcal"), coastAt(at(19, 0)).resolve(Metric.Calories, 1245.0, Surface.Week, thisWeek))
        assertFalse(coastAt(at(18, 59)).isWeekOpen(TODAY))
        assertTrue(coastAt(at(19, 0)).isWeekOpen(TODAY))
    }

    @Test
    fun weekly_lastWeekStaysOpen() {
        val lastWeek = FormatContext(day = YESTERDAY)
        assertEquals(Display.Shown("1,245"), delayedCalories(Surface.Week, now = MORNING, context = lastWeek, revealOn = RevealOn.Week))
        assertTrue(weekly(now = MORNING).isWeekOpen(YESTERDAY))
        assertFalse(weekly(now = MORNING).isWeekOpen(TODAY))
    }

    @Test
    fun revealsWeekly_isTheSettingsOwnRule() {
        assertTrue(weekly().revealsWeekly)
        assertFalse(policy(PresetId.Later).revealsWeekly)
        assertTrue(policy(PresetId.Full, overrides = mapOf(Metric.Calories to Visibility.Delay), revealOn = RevealOn.Week).revealsWeekly)
    }

    @Test
    fun delayed_ignoresTheWeekEndOnTheWeekView() {
        assertEquals(Display.Shown("1,245"), delayedCalories(Surface.Week, context = FormatContext(day = TODAY)))
    }

    @Test
    fun softWeight_saysWhichWayTheLineGoes() {
        val soft = policy(PresetId.Later)
        fun word(kgPerWeek: Double?) = soft.resolve(Metric.Weight, 72.6, Surface.Weight, FormatContext(trendKgPerWeek = kgPerWeek))
        assertEquals(Display.Soft("Trend only", null), word(null))
        assertEquals(Display.Soft("Steady", null), word(0.1))
        assertEquals(Display.Soft("Steady", null), word(-0.15))
        assertEquals(Display.Soft("Rising slowly", null), word(0.4))
        assertEquals(Display.Soft("Falling slowly", null), word(-0.6))
        // A shown weight is the amount, whatever the slope; no change readout exists.
        assertEquals(Display.Shown("72.6 kg"), policy(PresetId.Full).resolve(Metric.Weight, 72.6, Surface.Weight, FormatContext(trendKgPerWeek = -0.6)))
    }

    @Test
    fun weekly_staysHiddenOffTheWeekView_whateverTheDayOrTime() {
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
                        delayedCalories(surface, now = now, context = context, revealOn = RevealOn.Week),
                        "$surface $context $now",
                    )
                }
            }
        }
    }

    @Test
    fun revealing_doesNotTouchSoftenedOrHiddenMetrics() {
        val evening = policy(PresetId.Later, now = at(22))
        assertEquals(Display.Soft("Trend only", null), evening.resolve(Metric.Weight, 72.6, Surface.Week))
        assertEquals(Display.Hidden, evening.resolve(Metric.Micros, 62.0, Surface.Week))
    }

    @Test
    fun effectiveLevel_neverReturnsDelay() {
        val delayed = policy(PresetId.Later)
        assertEquals(Visibility.Delay, delayed.level(Metric.Calories))
        assertEquals(Visibility.Hide, delayed.effectiveLevel(Metric.Calories, Surface.Today))
        assertEquals(Visibility.Show, delayed.effectiveLevel(Metric.Calories, Surface.Week))
        assertEquals(Visibility.Soften, delayed.effectiveLevel(Metric.Weight, Surface.Today))
    }
}
