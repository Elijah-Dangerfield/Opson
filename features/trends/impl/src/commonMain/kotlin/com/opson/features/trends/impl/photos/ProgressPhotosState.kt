package com.opson.features.trends.impl.photos

import com.opson.libraries.progressphotos.PhotoBytes
import com.opson.libraries.progressphotos.Pose
import com.opson.libraries.progressphotos.ProgressPhoto
import com.opson.libraries.progressphotos.SavedComparison
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/** One photo in a grid or a picker. [image] is `null` until it is read, and always while locked. */
data class PhotoTile(val id: String, val date: LocalDate, val pose: Pose, val image: PhotoBytes?)

/** A month of photos, newest first. [showYear] once it is not this year. */
data class PhotoMonth(val month: Month, val year: Int, val showYear: Boolean, val tiles: List<PhotoTile>)

/** A pair saved from Compare, earlier on the left. */
data class SavedPair(val id: String, val left: PhotoTile, val right: PhotoTile)

/**
 * The grid (design sync 2, 8.4). Nothing about the photos, not even how
 * many there are, is in state until the lock is [PhotoLockState.Unlocked].
 */
data class ProgressPhotosState(
    val lock: PhotoLockState = PhotoLockState.Locked,
    val loading: Boolean = true,
    val months: List<PhotoMonth> = emptyList(),
    val saved: List<SavedPair> = emptyList(),
    val canCompare: Boolean = false,
    val menuOpen: Boolean = false,
    val confirmDeleteAll: Boolean = false,
) {
    val empty: Boolean get() = !loading && months.isEmpty()
}

/** The grid's own choices, which no repository holds. */
data class ProgressPhotosUi(val menuOpen: Boolean = false, val confirmDeleteAll: Boolean = false)

object ProgressPhotosStateBuilder {

    fun build(
        photos: List<ProgressPhoto>?,
        comparisons: List<SavedComparison>,
        thumbnails: Map<String, PhotoBytes>,
        today: LocalDate,
        lock: PhotoLockState,
        ui: ProgressPhotosUi,
    ): ProgressPhotosState {
        if (lock != PhotoLockState.Unlocked) return ProgressPhotosState(lock = lock, loading = false)
        if (photos == null) return ProgressPhotosState(lock = lock, loading = true)
        val tiles = photos.associate { it.id to it.tile(thumbnails[it.id]) }
        return ProgressPhotosState(
            lock = lock,
            loading = false,
            months = photos
                .groupBy { it.date.year to it.date.month }
                .map { (key, list) ->
                    PhotoMonth(
                        month = key.second,
                        year = key.first,
                        showYear = key.first != today.year,
                        tiles = list.map { tiles.getValue(it.id) },
                    )
                },
            saved = comparisons.mapNotNull { pair ->
                val left = tiles[pair.leftId] ?: return@mapNotNull null
                val right = tiles[pair.rightId] ?: return@mapNotNull null
                SavedPair(pair.id, left, right)
            },
            canCompare = photos.size >= 2,
            menuOpen = ui.menuOpen,
            confirmDeleteAll = ui.confirmDeleteAll,
        )
    }

    private fun ProgressPhoto.tile(image: PhotoBytes?) = PhotoTile(id, date, pose, image)
}
