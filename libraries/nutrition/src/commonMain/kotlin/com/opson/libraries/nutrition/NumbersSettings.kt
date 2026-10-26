package com.opson.libraries.nutrition

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
 */
data class NumbersSettings(
    val preset: PresetId = PresetId.Full,
    val overrides: Map<Metric, Visibility> = emptyMap(),
    val pauseBeforeMore: Boolean = false,
    val changedAt: Instant? = null,
    val revealAt: LocalTime = DEFAULT_REVEAL_AT,
) {
    /** The raw level, `Delay` included. Use [VisibilityPolicy.resolve] to render. */
    fun level(metric: Metric): Visibility = overrides[metric] ?: Presets[preset].level(metric)

    fun score(): Int = Metric.entries.sumOf { level(it).score }

    /** "Full numbers, adjusted" in the prototype: the preset no longer describes what is on screen. */
    val isAdjusted: Boolean get() = overrides.isNotEmpty()

    /** Picking a preset clears every override, so the preset means what its card says. */
    fun pickPreset(id: PresetId, at: Instant): NumbersSettings =
        copy(preset = id, overrides = emptyMap(), changedAt = at)

    fun override(metric: Metric, level: Visibility, at: Instant): NumbersSettings =
        copy(overrides = overrides + (metric to level), changedAt = at)

    fun isRaise(next: NumbersSettings): Boolean = next.score() > score()

    /**
     * The levels that differ from the preset's own, the "1 change" on Modes. An override equal
     * to the preset's level changes nothing on screen, so it does not count.
     */
    val changes: Map<Metric, Visibility>
        get() = overrides.filter { (metric, level) -> Presets[preset].level(metric) != level }

    /** Whether moving to [next] has to go through the friction sheet first. Lowering never does. */
    fun needsPause(next: NumbersSettings): Boolean = pauseBeforeMore && isRaise(next)

    companion object {
        /** 9:00 pm, the prototype's fixed reveal and the default for everyone who has not picked one. */
        val DEFAULT_REVEAL_AT: LocalTime = LocalTime(21, 0)
    }
}
