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
 *
 * [progressPhotos] is the Progress photos row of Modes and numbers, Show (`true`) or Hide
 * (`false`) and nothing between; `null` follows the preset. Photos are not a [Metric]: they carry
 * no amount and have no Soft or Later, so they stay out of [score]. Showing them is still
 * showing more, which [isRaise] knows.
 */
data class NumbersSettings(
    val preset: PresetId = PresetId.Full,
    val overrides: Map<Metric, Visibility> = emptyMap(),
    val pauseBeforeMore: Boolean = false,
    val changedAt: Instant? = null,
    val revealAt: LocalTime = DEFAULT_REVEAL_AT,
    val progressPhotos: Boolean? = null,
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

    /** Picking a preset clears every override and the photos choice, so the preset means what its card says. */
    fun pickPreset(id: PresetId, at: Instant): NumbersSettings =
        copy(preset = id, overrides = emptyMap(), progressPhotos = null, changedAt = at)

    fun override(metric: Metric, level: Visibility, at: Instant): NumbersSettings =
        copy(overrides = overrides + (metric to level), changedAt = at)

    /** More numbers, or photos that were hidden coming back. */
    fun isRaise(next: NumbersSettings): Boolean =
        next.score() > score() || (next.showsProgressPhotos && !showsProgressPhotos)

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

    companion object {
        /** 9:00 pm, the prototype's fixed reveal and the default for everyone who has not picked one. */
        val DEFAULT_REVEAL_AT: LocalTime = LocalTime(21, 0)
    }
}
