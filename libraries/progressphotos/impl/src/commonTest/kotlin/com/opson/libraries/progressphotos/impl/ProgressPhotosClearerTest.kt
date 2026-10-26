package com.opson.libraries.progressphotos.impl

import com.opson.libraries.progressphotos.Pose
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class ProgressPhotosClearerTest {

    @Test
    fun aUserChangeDeletesEveryPhoto() = runTest {
        val files = FakeFileStore()
        val repository = ProgressPhotoRepositoryImpl(files)
        repository.add(byteArrayOf(1), byteArrayOf(2), Pose.Front, Instant.fromEpochMilliseconds(0), LocalDate(2026, 10, 25))

        ProgressPhotosClearer(repository).clear(previousUserId = "user")

        assertTrue(files.files.isEmpty())
        assertEquals(emptyList(), repository.observePhotos().first())
    }
}
