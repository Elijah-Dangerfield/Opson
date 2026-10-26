package com.opson.libraries.progressphotos.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.progressphotos.Pose
import com.opson.libraries.progressphotos.ProgressPhoto
import com.opson.libraries.progressphotos.ProgressPhotoFileStore
import com.opson.libraries.progressphotos.ProgressPhotoRepository
import com.opson.libraries.progressphotos.SavedComparison
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Progress photos as files in one private folder: `index.json` listing them,
 * then `<id>.jpg` and `<id>.thumb.jpg` per photo. The index is read once, on
 * first use, and every change writes it whole. Nothing here touches the network.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class ProgressPhotoRepositoryImpl @Inject constructor(
    private val files: ProgressPhotoFileStore,
) : ProgressPhotoRepository {

    private val mutex = Mutex()

    /** `null` until the index has been read. */
    private val index = MutableStateFlow<IndexFile?>(null)

    override fun observePhotos(): Flow<List<ProgressPhoto>> = loaded().map { file ->
        file.photos.map { it.toPhoto() }.sortedWith(compareByDescending<ProgressPhoto> { it.takenAt }.thenBy { it.pose.ordinal })
    }.distinctUntilChanged()

    override fun observeComparisons(): Flow<List<SavedComparison>> = loaded().map { file ->
        file.comparisons.reversed().map { SavedComparison(it.id, it.leftId, it.rightId) }
    }.distinctUntilChanged()

    override fun observeLastTaken(): Flow<LocalDate?> = loaded().map { file ->
        file.photos.maxOfOrNull { LocalDate.parse(it.date) }
    }.distinctUntilChanged()

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun add(jpeg: ByteArray, thumbnail: ByteArray, pose: Pose, takenAt: Instant, date: LocalDate): ProgressPhoto =
        change { file ->
            val entry = PhotoEntry(
                id = Uuid.random().toString(),
                date = date.toString(),
                pose = pose.wire,
                takenAtMs = takenAt.toEpochMilliseconds(),
            )
            // The images first: an index entry never points at a file that isn't there.
            files.write(imageName(entry.id), jpeg)
            files.write(thumbnailName(entry.id), thumbnail)
            file.copy(photos = file.photos + entry) to entry.toPhoto()
        }

    override suspend fun image(id: String): ByteArray? = readOrNull(imageName(id))

    override suspend fun thumbnail(id: String): ByteArray? = readOrNull(thumbnailName(id))

    override suspend fun setNote(id: String, note: String) {
        change { file ->
            file.copy(photos = file.photos.map { if (it.id == id) it.copy(note = note) else it }) to Unit
        }
    }

    override suspend fun delete(id: String) {
        // The index first: a crash between the two leaves an orphan file, never a dangling entry.
        change { file ->
            file.copy(
                photos = file.photos.filterNot { it.id == id },
                comparisons = file.comparisons.filterNot { it.leftId == id || it.rightId == id },
            ) to Unit
        }
        files.delete(imageName(id))
        files.delete(thumbnailName(id))
    }

    override suspend fun deleteAll() {
        mutex.withLock {
            files.deleteAll()
            index.value = IndexFile()
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun saveComparison(leftId: String, rightId: String): SavedComparison = change { file ->
        val existing = file.comparisons.firstOrNull { it.leftId == leftId && it.rightId == rightId }
        if (existing != null) {
            file to SavedComparison(existing.id, existing.leftId, existing.rightId)
        } else {
            val entry = ComparisonEntry(Uuid.random().toString(), leftId, rightId)
            file.copy(comparisons = file.comparisons + entry) to SavedComparison(entry.id, leftId, rightId)
        }
    }

    override suspend fun removeComparison(id: String) {
        change { file -> file.copy(comparisons = file.comparisons.filterNot { it.id == id }) to Unit }
    }

    private fun loaded(): Flow<IndexFile> = flow {
        mutex.withLock { ensureLoaded() }
        emitAll(index.filterNotNull())
    }

    /** Applies [block] to the index and writes the result, one change at a time. */
    private suspend fun <T> change(block: suspend (IndexFile) -> Pair<IndexFile, T>): T = mutex.withLock {
        val (next, result) = block(ensureLoaded())
        writeIndex(next)
        index.value = next
        result
    }

    private suspend fun ensureLoaded(): IndexFile = index.value ?: readIndex().also { index.value = it }

    /** A missing index is no photos; an unreadable one is logged and read as none rather than crashing the page. */
    private suspend fun readIndex(): IndexFile {
        val bytes = readOrNull(INDEX) ?: return IndexFile()
        return Catching { json.decodeFromString(IndexFile.serializer(), bytes.decodeToString()) }
            .logOnFailure("Couldn't read the progress photo index")
            .getOrNull() ?: IndexFile()
    }

    private suspend fun writeIndex(file: IndexFile) {
        files.write(INDEX, json.encodeToString(IndexFile.serializer(), file).encodeToByteArray())
    }

    private suspend fun readOrNull(name: String): ByteArray? =
        Catching { files.read(name) }.logOnFailure("Couldn't read a progress photo file").getOrNull()

    private companion object {
        const val INDEX = "index.json"
        val json = Json { ignoreUnknownKeys = true }

        fun imageName(id: String) = "$id.jpg"

        fun thumbnailName(id: String) = "$id.thumb.jpg"
    }
}

@Serializable
internal data class IndexFile(
    val photos: List<PhotoEntry> = emptyList(),
    val comparisons: List<ComparisonEntry> = emptyList(),
)

@Serializable
internal data class PhotoEntry(
    val id: String,
    val date: String,
    val pose: String,
    val takenAtMs: Long,
    val note: String = "",
) {
    fun toPhoto() = ProgressPhoto(
        id = id,
        date = LocalDate.parse(date),
        pose = Pose.fromWire(pose),
        takenAt = Instant.fromEpochMilliseconds(takenAtMs),
        note = note,
    )
}

@Serializable
internal data class ComparisonEntry(val id: String, val leftId: String, val rightId: String)
