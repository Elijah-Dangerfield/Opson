package com.opson.features.settings.impl.modes

import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Presets
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.ui.time.DateCopy
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Levels, never values: this page shows which numbers are on, not what they are. */
data class ModesState(
    val presets: List<PresetRow> = emptyList(),
    val matrix: List<MatrixRow> = emptyList(),
    val pauseOn: Boolean = false,
    val changedNote: ChangedNote = ChangedNote.DuringSetup,
)

/** [selected] only when this is the preset and nothing has been changed on top of it. */
data class PresetRow(val id: PresetId, val selected: Boolean)

/** The configured level, Delay included: the grid says what was chosen, not what is open right now. */
data class MatrixRow(val metric: Metric, val level: Visibility)

sealed interface ChangedNote {
    /** "Set by you during setup." Onboarding writes the first preset without a stamp. */
    data object DuringSetup : ChangedNote

    /** "Last changed by you, Oct 25, 9:41 PM." */
    data class LastChanged(val timestamp: String) : ChangedNote
}

object ModesStateBuilder {

    /** The grid's columns, left to right. */
    val levels: List<Visibility> = listOf(Visibility.Show, Visibility.Soften, Visibility.Delay, Visibility.Hide)

    fun build(numbers: NumbersSettings, timeZone: TimeZone): ModesState = ModesState(
        presets = Presets.all.map { PresetRow(it.id, selected = it.id == numbers.preset && !numbers.isAdjusted) },
        matrix = Metric.entries.map { MatrixRow(it, numbers.level(it)) },
        pauseOn = numbers.pauseBeforeMore,
        changedNote = numbers.changedAt
            ?.let { ChangedNote.LastChanged(DateCopy.timestamp(it.toLocalDateTime(timeZone))) }
            ?: ChangedNote.DuringSetup,
    )
}
