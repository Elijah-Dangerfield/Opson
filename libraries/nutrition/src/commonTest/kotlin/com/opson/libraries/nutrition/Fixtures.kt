package com.opson.libraries.nutrition

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.time.Instant

internal val TODAY = LocalDate(2026, 10, 25)
internal val YESTERDAY = LocalDate(2026, 10, 24)
internal val TOMORROW = LocalDate(2026, 10, 26)

/** 9:00 am on [TODAY], well before any delayed reveal. */
internal val MORNING = LocalDateTime(2026, 10, 25, 9, 0)

internal val SOME_INSTANT: Instant = Instant.fromEpochSeconds(1_790_000_000)

internal fun at(hour: Int, minute: Int = 0, date: LocalDate = TODAY) =
    LocalDateTime(date.year, date.month, date.day, hour, minute)

internal fun clockAt(dateTime: LocalDateTime, zone: TimeZone = TimeZone.UTC): Clock = object : Clock {
    override fun now(): Instant = dateTime.toInstant(zone)
}

internal fun policy(
    preset: PresetId = PresetId.Full,
    overrides: Map<Metric, Visibility> = emptyMap(),
    now: LocalDateTime = MORNING,
    zone: TimeZone = TimeZone.UTC,
    revealOn: RevealOn = RevealOn.DayClose,
) = VisibilityPolicy(NumbersSettings(preset = preset, overrides = overrides, revealOn = revealOn), clockAt(now, zone), zone)

/** Later with its totals opening once a week: what Coast became. */
internal fun weekly(now: LocalDateTime = MORNING, overrides: Map<Metric, Visibility> = emptyMap()) =
    policy(PresetId.Later, overrides = overrides, now = now, revealOn = RevealOn.Week)

/** Full numbers with every metric shown, micros included, for format tests. */
internal fun showAll(now: LocalDateTime = MORNING) =
    policy(PresetId.Full, overrides = mapOf(Metric.Micros to Visibility.Show), now = now)

internal fun VisibilityPolicy.text(metric: Metric, value: Double?, context: FormatContext = FormatContext()): String? =
    when (val display = resolve(metric, value, Surface.Today, context)) {
        Display.Hidden -> null
        is Display.Soft -> display.text
        is Display.Shown -> display.text
    }
