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
    /** The matrix's last row, Show or Hide. `null` while `release.progressPhotos` is off. */
    val photos: PhotosRow? = null,
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

/** Progress photos: [shown] or hidden, and the preset's own choice when this one differs from it. */
data class PhotosRow(val shown: Boolean, val changedFrom: Boolean? = null) {
    val level: Visibility get() = if (shown) Visibility.Show else Visibility.Hide
}

sealed interface ChangedNote {
    /** "Set by you during setup." Onboarding writes the first preset without a stamp. */
    data object DuringSetup : ChangedNote

    /** "Last changed by you, Oct 25, 9:41 PM." */
    data class LastChanged(val timestamp: String) : ChangedNote
}

object ModesStateBuilder {

    /** The grid's columns, left to right. */
    val levels: List<Visibility> = listOf(Visibility.Show, Visibility.Soften, Visibility.Delay, Visibility.Hide)

    /** The two levels the photos row offers. Soft and Later don't apply to a photo. */
    val photoLevels: Set<Visibility> = setOf(Visibility.Show, Visibility.Hide)

    /** [photosReleased]: `release.progressPhotos`; off, the photos row and its change don't show. */
    fun build(
        numbers: NumbersSettings,
        timeZone: TimeZone,
        pickingRevealTime: Boolean = false,
        photosReleased: Boolean = false,
    ): ModesState {
        val changes = numbers.changes
        val photosChanged = photosReleased && numbers.progressPhotosChanged
        val preset = Presets[numbers.preset]
        return ModesState(
            presets = Presets.all.map {
                val current = it.id == numbers.preset
                val count = changes.size + if (photosChanged) 1 else 0
                PresetRow(it.id, selected = current, changes = if (current) count else 0)
            },
            matrix = Metric.entries.map { metric ->
                MatrixRow(metric, numbers.level(metric), changedFrom = preset.level(metric).takeIf { metric in changes })
            },
            photos = if (photosReleased) {
                PhotosRow(numbers.showsProgressPhotos, changedFrom = preset.progressPhotos.takeIf { photosChanged })
            } else {
                null
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
