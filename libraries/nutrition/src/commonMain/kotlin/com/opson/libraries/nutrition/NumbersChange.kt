package com.opson.libraries.nutrition

import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * One change the user asked for to what numbers they see. Every screen that
 * changes visibility (modes, the water toggle, weight view, the scores page)
 * describes it as one of these, so the pause sheet can hold it, show what it is,
 * and apply it later unchanged.
 */
@Serializable
sealed interface NumbersChange {

    /** Picking a mode clears every override, so the mode means what its card says. */
    @Serializable
    data class PickPreset(val id: PresetId) : NumbersChange

    @Serializable
    data class Override(val metric: Metric, val level: Visibility) : NumbersChange

    /** Back to the mode's own level for [metric]. */
    @Serializable
    data class ClearOverride(val metric: Metric) : NumbersChange

    @Serializable
    data object TurnOffPause : NumbersChange

    /** When anything set to Later opens on the day. Earlier shows numbers sooner, so it is a raise. */
    @Serializable
    data class SetRevealAt(val time: LocalTime) : NumbersChange

    /** The settings after this change, stamped [at] so More can say when they last changed. */
    fun applyTo(settings: NumbersSettings, at: Instant): NumbersSettings = when (this) {
        is PickPreset -> settings.pickPreset(id, at)
        is Override -> settings.override(metric, level, at)
        is ClearOverride -> settings.copy(overrides = settings.overrides - metric, changedAt = at)
        TurnOffPause -> settings.copy(pauseBeforeMore = false, changedAt = at)
        is SetRevealAt -> settings.copy(revealAt = time, changedAt = at)
    }

    /**
     * Whether this has to go through "Pause before showing more" first.
     *
     * Only with the pause on. Then any change that raises the score pauses, and
     * so does turning the pause off: otherwise it could be skipped in two taps
     * (pause off, then raise), which defeats a setting the user turned on to
     * protect themselves. Lowering and equal-score changes never pause.
     *
     * The reveal time changes no level, so the score cannot see it. Moving it
     * earlier opens Later numbers sooner, which is showing more, so that pauses
     * too; moving it later never does.
     */
    fun needsPause(current: NumbersSettings): Boolean {
        if (!current.pauseBeforeMore) return false
        if (this == TurnOffPause) return true
        if (this is SetRevealAt) return time < current.revealAt
        // The stamp does not affect the score, so any instant will do.
        return current.isRaise(applyTo(current, Instant.DISTANT_PAST))
    }
}
