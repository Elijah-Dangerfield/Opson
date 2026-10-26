package com.opson.features.trends.impl.photos

import com.opson.libraries.progressphotos.PhotoBytes
import com.opson.libraries.progressphotos.Pose
import com.opson.libraries.progressphotos.ProgressPhoto
import com.opson.libraries.progressphotos.SavedComparison
import kotlinx.datetime.LocalDate

enum class CompareMode { SideBySide, Slider }

enum class CompareSide { Left, Right }

/** A photo in a date picker: `Oct 25 · Front`. */
data class PhotoChoice(val id: String, val date: LocalDate, val pose: Pose)

/** One side of the comparison. [image] is the full photo, `null` until read and while locked. */
data class ComparePhoto(val id: String, val date: LocalDate, val pose: Pose, val image: PhotoBytes?)

/**
 * Compare (design sync 2, 8.5): any two photos, side by side or on a slider.
 * Never a number on or near the photos, and nothing generated from them.
 */
data class CompareState(
    val lock: PhotoLockState = PhotoLockState.Locked,
    val loading: Boolean = true,
    /** Every photo, newest first, for both pickers. */
    val choices: List<PhotoChoice> = emptyList(),
    val left: ComparePhoto? = null,
    val right: ComparePhoto? = null,
    val mode: CompareMode = CompareMode.SideBySide,
    /** Where the slider's handle sits, `0..1` from the left. */
    val sliderAt: Float = 0.5f,
    /** The right-hand photo's note, as typed. */
    val note: String = "",
    /** This pair is saved: the ⋮ menu offers to remove it. */
    val savedId: String? = null,
    val picker: CompareSide? = null,
    val menuOpen: Boolean = false,
)

/** What the person has chosen on this page. */
data class CompareUi(
    val leftId: String? = null,
    val rightId: String? = null,
    val mode: CompareMode = CompareMode.SideBySide,
    val sliderAt: Float = 0.5f,
    val note: String? = null,
    val picker: CompareSide? = null,
    val menuOpen: Boolean = false,
)

object CompareStateBuilder {

    /**
     * The pair to show: what was chosen when it still exists, otherwise the
     * latest photo on the right and the earliest of the same pose on the left.
     */
    fun pair(photos: List<ProgressPhoto>, leftId: String?, rightId: String?): Pair<String?, String?> {
        val ids = photos.map { it.id }.toSet()
        val right = rightId?.takeIf { it in ids } ?: photos.firstOrNull()?.id
        val rightPose = photos.firstOrNull { it.id == right }?.pose
        val left = leftId?.takeIf { it in ids }
            ?: photos.lastOrNull { it.id != right && it.pose == rightPose }?.id
            ?: photos.lastOrNull { it.id != right }?.id
        return left to right
    }

    fun build(
        photos: List<ProgressPhoto>?,
        comparisons: List<SavedComparison>,
        images: Map<String, PhotoBytes>,
        lock: PhotoLockState,
        ui: CompareUi,
    ): CompareState {
        if (lock != PhotoLockState.Unlocked) return CompareState(lock = lock, loading = false)
        if (photos == null) return CompareState(lock = lock, loading = true)
        val (leftId, rightId) = pair(photos, ui.leftId, ui.rightId)
        val left = photos.firstOrNull { it.id == leftId }
        val right = photos.firstOrNull { it.id == rightId }
        return CompareState(
            lock = lock,
            loading = false,
            choices = photos.map { PhotoChoice(it.id, it.date, it.pose) },
            left = left?.let { ComparePhoto(it.id, it.date, it.pose, images[it.id]) },
            right = right?.let { ComparePhoto(it.id, it.date, it.pose, images[it.id]) },
            mode = ui.mode,
            sliderAt = ui.sliderAt,
            note = ui.note ?: right?.note.orEmpty(),
            savedId = comparisons.firstOrNull { it.leftId == leftId && it.rightId == rightId }?.id,
            picker = ui.picker,
            menuOpen = ui.menuOpen,
        )
    }
}
