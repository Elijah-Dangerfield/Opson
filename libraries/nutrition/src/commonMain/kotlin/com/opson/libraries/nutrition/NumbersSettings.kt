package com.opson.libraries.nutrition

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

/**
 * The part of the user's settings the [VisibilityPolicy] reads.
 *
 * [changedAt] is when the user last changed the mode or an override, `null`
 * until they ever do. Settings only change when the user changes them, and the
 * More screen shows when that was.
 *
 * [revealAt] is when anything set to Later opens on the day itself (design v3
 * made it a setting; the prototype fixed it at 9:00 pm).
 *
 * [progressPhotos] is the Progress photos row of Modes and numbers, Show (`true`) or Hide
 * (`false`) and nothing between; `null` follows the preset. Photos are not a [Metric]: they carry
 * no amount and have no Soft or Later, so they stay out of [score]. Showing them is still
 * showing more, which [isRaise] knows.
 *
 * [revealOn] is Later's "a time each day or once a week" (design sync 4: Coast became Later,
 * weekly). [endAfter] is "End after a set time", on any preset.
 */
data class NumbersSettings(
    val preset: PresetId = PresetId.Full,
    val overrides: Map<Metric, Visibility> = emptyMap(),
    val pauseBeforeMore: Boolean = false,
    val changedAt: Instant? = null,
    val revealAt: LocalTime = DEFAULT_REVEAL_AT,
    val progressPhotos: Boolean? = null,
    val revealOn: RevealOn = RevealOn.DayClose,
    val endAfter: ModeTiming? = null,
) {
    /** The raw level, `Delay` included. Use [VisibilityPolicy.resolve] to render. */
    fun level(metric: Metric): Visibility = overrides[metric] ?: Presets[preset].level(metric)

    fun score(): Int = Metric.entries.sumOf { level(it).score }

    /** "Full numbers, adjusted" in the prototype: the preset no longer describes what is on screen. */
    val isAdjusted: Boolean get() = overrides.isNotEmpty() || progressPhotos != null

    /** Whether the Progress photos row shows on Weight: the person's choice, else the preset's. */
    val showsProgressPhotos: Boolean get() = progressPhotos ?: Presets[preset].progressPhotos

    /** Photos set apart from the preset's own choice: one of the "1 change" on Modes. */
    val progressPhotosChanged: Boolean get() = progressPhotos != null && progressPhotos != Presets[preset].progressPhotos

    /** Delayed numbers open once a week, on the week view, rather than each day. */
    val revealsWeekly: Boolean get() = revealOn == RevealOn.Week

    /**
     * Picking a preset clears every override and the photos choice, so the preset means what its
     * card says. A timer that would only switch to the preset just picked has nothing left to do.
     */
    fun pickPreset(id: PresetId, at: Instant): NumbersSettings = copy(
        preset = id,
        overrides = emptyMap(),
        progressPhotos = null,
        endAfter = endAfter?.takeIf { it.then != id },
        changedAt = at,
    )

    fun override(metric: Metric, level: Visibility, at: Instant): NumbersSettings =
        copy(overrides = overrides + (metric to level), changedAt = at)

    /** More numbers, photos that were hidden coming back, or Later numbers opening each day instead of weekly. */
    fun isRaise(next: NumbersSettings): Boolean =
        next.score() > score() ||
            (next.showsProgressPhotos && !showsProgressPhotos) ||
            (revealsWeekly && !next.revealsWeekly && Metric.entries.any { next.level(it) == Visibility.Delay })

    /** Fewer things on screen and nothing more: "Saved. Fewer numbers from now on." */
    fun isLower(next: NumbersSettings): Boolean =
        !isRaise(next) && (next.score() < score() || (showsProgressPhotos && !next.showsProgressPhotos))

    /**
     * The levels that differ from the preset's own, the "1 change" on Modes. An override equal
     * to the preset's level changes nothing on screen, so it does not count.
     */
    val changes: Map<Metric, Visibility>
        get() = overrides.filter { (metric, level) -> Presets[preset].level(metric) != level }

    /** Whether moving to [next] has to go through the friction sheet first. Lowering never does. */
    fun needsPause(next: NumbersSettings): Boolean = pauseBeforeMore && isRaise(next)

    /**
     * The settings once [endAfter] has run out on [today]: its preset, stamped [at]. `null` while
     * there is no timer or it has time left, and when the switch shows more with the pause on,
     * because that switch goes through the pause like any other ([timerWaiting]).
     */
    fun timedOut(today: LocalDate, at: Instant): NumbersSettings? {
        val timing = endAfter?.takeIf { it.isDue(today) } ?: return null
        val next = pickPreset(timing.then, at).copy(endAfter = null)
        return next.takeUnless { needsPause(it) }
    }

    /** The timer ran out but its switch shows more with the pause on: Today offers it instead. */
    fun timerWaiting(today: LocalDate): ModeTiming? =
        endAfter?.takeIf { it.isDue(today) && timedOut(today, Instant.DISTANT_PAST) == null }

    companion object {
        /** 9:00 pm, the prototype's fixed reveal and the default for everyone who has not picked one. */
        val DEFAULT_REVEAL_AT: LocalTime = LocalTime(21, 0)
    }
}
