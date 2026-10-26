package com.opson.libraries.progressphotos

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Which way the person faced. A set is a front and, if they like, a side. */
enum class Pose(val wire: String) {
    Front("front"),
    Side("side"),
    ;

    companion object {
        fun fromWire(value: String?): Pose = entries.firstOrNull { it.wire == value } ?: Front
    }
}

/**
 * One progress photo. The image itself never rides along: it is read on demand
 * with [ProgressPhotoRepository.image] or [ProgressPhotoRepository.thumbnail],
 * so a list of photos costs nothing to hold.
 */
data class ProgressPhoto(
    val id: String,
    /** The day it was taken, on the phone's calendar. */
    val date: LocalDate,
    val pose: Pose,
    val takenAt: Instant,
    /** The person's own words, optional. Never a number the app wrote. */
    val note: String = "",
)

/** A pair the person chose to keep from Compare. Just the two ids: nothing is rendered or exported. */
data class SavedComparison(val id: String, val leftId: String, val rightId: String)

/**
 * JPEG bytes, compared by identity: two images are the same only if they are
 * the same object, so a state holding them stays cheap to compare.
 */
class PhotoBytes(val jpeg: ByteArray)

/**
 * Progress photos, kept only on this phone in app-private storage: never the
 * photo gallery, never synced, never uploaded, left out of backups. Deleting
 * the account (or signing out) deletes them. The screens ask for the phone's
 * lock before they read anything here; this repository does not, so it stays
 * testable and the Weight row can say when the last one was taken.
 */
interface ProgressPhotoRepository {
    /** Every photo, newest first. */
    fun observePhotos(): Flow<List<ProgressPhoto>>

    /** Saved pairs, newest first. A pair whose photo was deleted is gone too. */
    fun observeComparisons(): Flow<List<SavedComparison>>

    /** The day of the newest photo, or `null` before the first. */
    fun observeLastTaken(): Flow<LocalDate?>

    suspend fun add(jpeg: ByteArray, thumbnail: ByteArray, pose: Pose, takenAt: Instant, date: LocalDate): ProgressPhoto

    /** The full photo, or `null` if it is gone or unreadable. */
    suspend fun image(id: String): ByteArray?

    /** A small copy for the grid, or `null` if it is gone or unreadable. */
    suspend fun thumbnail(id: String): ByteArray?

    suspend fun setNote(id: String, note: String)

    suspend fun delete(id: String)

    /** Every photo, every saved pair, and the index. For good. */
    suspend fun deleteAll()

    suspend fun saveComparison(leftId: String, rightId: String): SavedComparison

    suspend fun removeComparison(id: String)
}

/**
 * Where the bytes live. Bound per platform: a folder in Android's
 * `noBackupFilesDir`, or `Application Support` on iOS with backup turned off
 * and complete file protection. Names are flat (`index.json`, `<id>.jpg`).
 */
interface ProgressPhotoFileStore {
    /** `null` when there is no such file. Throws when there is one that can't be read. */
    suspend fun read(name: String): ByteArray?

    /** Replaces the whole file, so a crash mid-write never leaves half of one. */
    suspend fun write(name: String, bytes: ByteArray)

    suspend fun delete(name: String)

    /** The folder and everything in it. */
    suspend fun deleteAll()
}
