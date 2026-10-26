package com.opson.libraries.progressphotos.impl

import com.opson.libraries.progressphotos.Pose
import com.opson.libraries.progressphotos.ProgressPhotoFileStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ProgressPhotoRepositoryImplTest {

    private val files = FakeFileStore()
    private val repository = ProgressPhotoRepositoryImpl(files)

    private val sep13 = LocalDate(2026, 9, 13)
    private val oct25 = LocalDate(2026, 10, 25)

    private suspend fun add(date: LocalDate, pose: Pose = Pose.Front, at: Long = date.toEpochDays() * DAY_MS) =
        repository.add(byteArrayOf(1, 2, 3), byteArrayOf(9), pose, Instant.fromEpochMilliseconds(at), date)

    @Test
    fun addWritesTheImageTheThumbnailAndTheIndex() = runTest {
        val photo = add(oct25)

        assertContentEquals(byteArrayOf(1, 2, 3), files.files["${photo.id}.jpg"])
        assertContentEquals(byteArrayOf(9), files.files["${photo.id}.thumb.jpg"])
        assertTrue(files.files.getValue("index.json").decodeToString().contains(photo.id))
        assertContentEquals(byteArrayOf(1, 2, 3), repository.image(photo.id))
        assertContentEquals(byteArrayOf(9), repository.thumbnail(photo.id))
    }

    @Test
    fun photosComeNewestFirstAndSurviveARestart() = runTest {
        add(sep13)
        add(oct25, Pose.Front)
        add(oct25, Pose.Side)

        val again = ProgressPhotoRepositoryImpl(files).observePhotos().first()

        assertEquals(listOf(oct25, oct25, sep13), again.map { it.date })
        assertEquals(listOf(Pose.Front, Pose.Side, Pose.Front), again.map { it.pose })
        assertEquals(oct25, repository.observeLastTaken().first())
    }

    @Test
    fun noIndexIsNoPhotos() = runTest {
        assertEquals(emptyList(), repository.observePhotos().first())
        assertNull(repository.observeLastTaken().first())
    }

    @Test
    fun aBrokenIndexReadsAsNoneInsteadOfCrashing() = runTest {
        files.files["index.json"] = "not json".encodeToByteArray()

        assertEquals(emptyList(), repository.observePhotos().first())
    }

    @Test
    fun aNoteIsKept() = runTest {
        val photo = add(oct25)

        repository.setNote(photo.id, "Lifting three times a week")

        assertEquals("Lifting three times a week", repository.observePhotos().first().single().note)
    }

    @Test
    fun deleteRemovesTheFilesTheEntryAndAnyPairWithIt() = runTest {
        val first = add(sep13)
        val second = add(oct25)
        repository.saveComparison(first.id, second.id)

        repository.delete(first.id)

        assertEquals(listOf(second.id), repository.observePhotos().first().map { it.id })
        assertEquals(emptyList(), repository.observeComparisons().first())
        assertNull(files.files["${first.id}.jpg"])
        assertNull(files.files["${first.id}.thumb.jpg"])
    }

    @Test
    fun deleteAllRemovesEveryFile() = runTest {
        val first = add(sep13)
        add(oct25)
        repository.saveComparison(first.id, first.id)

        repository.deleteAll()

        assertTrue(files.files.isEmpty())
        assertEquals(emptyList(), repository.observePhotos().first())
        assertEquals(emptyList(), repository.observeComparisons().first())
        assertNull(repository.image(first.id))
    }

    @Test
    fun savingTheSamePairTwiceKeepsOne() = runTest {
        val first = add(sep13)
        val second = add(oct25)

        val saved = repository.saveComparison(first.id, second.id)
        val again = repository.saveComparison(first.id, second.id)

        assertEquals(saved, again)
        assertEquals(listOf(saved), repository.observeComparisons().first())
        repository.removeComparison(saved.id)
        assertEquals(emptyList(), repository.observeComparisons().first())
    }

    @Test
    fun anUnreadableFileIsNull() = runTest {
        files.failReads = true

        assertNull(repository.image("anything"))
    }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}

/** Files in a map. [failReads] makes every read throw, as a corrupt disk would. */
class FakeFileStore : ProgressPhotoFileStore {
    val files = mutableMapOf<String, ByteArray>()
    var failReads = false

    override suspend fun read(name: String): ByteArray? {
        if (failReads) error("disk")
        return files[name]
    }

    override suspend fun write(name: String, bytes: ByteArray) {
        files[name] = bytes
    }

    override suspend fun delete(name: String) {
        files.remove(name)
    }

    override suspend fun deleteAll() {
        files.clear()
    }
}
