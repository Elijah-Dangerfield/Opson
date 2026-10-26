package com.opson.features.trends.impl.photos

import com.opson.libraries.progressphotos.Pose
import com.opson.libraries.progressphotos.ProgressPhoto
import com.opson.libraries.progressphotos.SavedComparison
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * The photos the design draws (Oct 25 front and side, Oct 18, Sep 27 front
 * and side, Sep 13), newest first, for previews and the leak test. No image
 * bytes: frames draw their stripes, as the design does.
 */
object ProgressPhotosFixtures {

    val today = LocalDate(2026, 10, 25)

    val photos: List<ProgressPhoto> = listOf(
        photo("oct25-front", LocalDate(2026, 10, 25), Pose.Front, minute = 1, note = "Lifting three times a week since September"),
        photo("oct25-side", LocalDate(2026, 10, 25), Pose.Side, minute = 0),
        photo("oct18-front", LocalDate(2026, 10, 18), Pose.Front),
        photo("sep27-front", LocalDate(2026, 9, 27), Pose.Front, minute = 1),
        photo("sep27-side", LocalDate(2026, 9, 27), Pose.Side, minute = 0),
        photo("sep13-front", LocalDate(2026, 9, 13), Pose.Front),
    )

    val comparisons: List<SavedComparison> = emptyList()

    fun grid(lock: PhotoLockState = PhotoLockState.Unlocked, photos: List<ProgressPhoto> = this.photos): ProgressPhotosState =
        ProgressPhotosStateBuilder.build(photos, comparisons, emptyMap(), today, lock, ProgressPhotosUi())

    /** Sep 13 against Oct 25, the design's pair and the default. */
    fun compare(mode: CompareMode = CompareMode.SideBySide, lock: PhotoLockState = PhotoLockState.Unlocked): CompareState =
        CompareStateBuilder.build(photos, comparisons, emptyMap(), lock, CompareUi(mode = mode))

    private fun photo(id: String, date: LocalDate, pose: Pose, minute: Int = 0, note: String = "") = ProgressPhoto(
        id = id,
        date = date,
        pose = pose,
        takenAt = Instant.fromEpochMilliseconds(date.toEpochDays() * DAY_MS + minute * MINUTE_MS),
        note = note,
    )

    private const val DAY_MS = 86_400_000L
    private const val MINUTE_MS = 60_000L
}
