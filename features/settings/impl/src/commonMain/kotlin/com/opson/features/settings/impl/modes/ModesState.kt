package com.opson.features.settings.impl.modes

import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Presets
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.ui.time.DateCopy
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Levels, never values: this page shows which numbers are on, not what they are. */
data class ModesState(
    val presets: List<PresetRow> = emptyList(),
    val matrix: List<MatrixRow> = emptyList(),
    val pauseOn: Boolean = false,
    val revealAt: LocalTime = NumbersSettings.DEFAULT_REVEAL_AT,
    /** "9:00 PM", the trailing text of the Reveal time row. */
    val revealAtText: String = DateCopy.time(revealAt.hour, revealAt.minute),
    /** The time picker is open. */
    val pickingRevealTime: Boolean = false,
    val changedNote: ChangedNote = ChangedNote.DuringSetup,
)

/**
 * [selected] on the current preset, changed or not (v3). [changes] counts the levels that differ
 * from the preset's own, the "1 change" badge; it is only ever non-zero on the selected row.
 */
data class PresetRow(val id: PresetId, val selected: Boolean, val changes: Int = 0)

/**
 * The configured level, Delay included: the grid says what was chosen, not what is open right
 * now. [changedFrom] is the preset's own level when this one was changed ("Changed from Hide").
 */
data class MatrixRow(val metric: Metric, val level: Visibility, val changedFrom: Visibility? = null)

sealed interface ChangedNote {
    /** "Set by you during setup." Onboarding writes the first preset without a stamp. */
    data object DuringSetup : ChangedNote

    /** "Last changed by you, Oct 25, 9:41 PM." */
    data class LastChanged(val timestamp: String) : ChangedNote
}

object ModesStateBuilder {

    /** The grid's columns, left to right. */
    val levels: List<Visibility> = listOf(Visibility.Show, Visibility.Soften, Visibility.Delay, Visibility.Hide)

    fun build(numbers: NumbersSettings, timeZone: TimeZone, pickingRevealTime: Boolean = false): ModesState {
        val changes = numbers.changes
        val preset = Presets[numbers.preset]
        return ModesState(
            presets = Presets.all.map {
                val current = it.id == numbers.preset
                PresetRow(it.id, selected = current, changes = if (current) changes.size else 0)
            },
            matrix = Metric.entries.map { metric ->
                MatrixRow(metric, numbers.level(metric), changedFrom = preset.level(metric).takeIf { metric in changes })
            },
            pauseOn = numbers.pauseBeforeMore,
            revealAt = numbers.revealAt,
            revealAtText = DateCopy.time(numbers.revealAt.hour, numbers.revealAt.minute),
            pickingRevealTime = pickingRevealTime,
            changedNote = numbers.changedAt
                ?.let { ChangedNote.LastChanged(DateCopy.timestamp(it.toLocalDateTime(timeZone))) }
                ?: ChangedNote.DuringSetup,
        )
    }
}
