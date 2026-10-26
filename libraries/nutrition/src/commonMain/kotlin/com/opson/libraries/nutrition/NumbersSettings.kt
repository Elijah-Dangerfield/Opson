package com.opson.libraries.nutrition

import kotlin.time.Instant

/**
 * The part of the user's settings the [VisibilityPolicy] reads.
 *
 * [changedAt] is when the user last changed the mode or an override, `null`
 * until they ever do. Settings only change when the user changes them, and the
 * More screen shows when that was.
 */
data class NumbersSettings(
    val preset: PresetId = PresetId.Full,
    val overrides: Map<Metric, Visibility> = emptyMap(),
    val pauseBeforeMore: Boolean = false,
    val changedAt: Instant? = null,
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

    /** Whether moving to [next] has to go through the friction sheet first. Lowering never does. */
    fun needsPause(next: NumbersSettings): Boolean = pauseBeforeMore && isRaise(next)
}
