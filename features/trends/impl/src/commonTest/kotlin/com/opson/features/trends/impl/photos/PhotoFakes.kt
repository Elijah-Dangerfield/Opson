package com.opson.features.trends.impl.photos

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.opson.AppLifecycle
import com.opson.libraries.opson.AppLifecycleObserver
import com.opson.libraries.progressphotos.DeviceLock
import com.opson.libraries.progressphotos.DeviceLockResult
import com.opson.libraries.progressphotos.Pose
import com.opson.libraries.progressphotos.ProgressPhoto
import com.opson.libraries.progressphotos.ProgressPhotoRepository
import com.opson.libraries.progressphotos.ProgressPhotosEnabled
import com.opson.libraries.progressphotos.SavedComparison
import com.opson.libraries.scanner.CameraPermission
import com.opson.libraries.scanner.CameraPermissions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Photos and pairs in memory. Image reads are counted, so a test can prove nothing was read while locked. */
class FakeProgressPhotoRepository(photos: List<ProgressPhoto> = ProgressPhotosFixtures.photos) : ProgressPhotoRepository {
    val photos = MutableStateFlow(photos)
    val comparisons = MutableStateFlow<List<SavedComparison>>(emptyList())
    val imageReads = mutableListOf<String>()
    val notes = mutableMapOf<String, String>()
    var deletedAll = false

    override fun observePhotos(): Flow<List<ProgressPhoto>> = photos
    override fun observeComparisons(): Flow<List<SavedComparison>> = comparisons
    override fun observeLastTaken(): Flow<LocalDate?> = photos.map { list -> list.maxOfOrNull { it.date } }

    override suspend fun add(jpeg: ByteArray, thumbnail: ByteArray, pose: Pose, takenAt: Instant, date: LocalDate): ProgressPhoto {
        val photo = ProgressPhoto("added-${photos.value.size}", date, pose, takenAt)
        photos.value = listOf(photo) + photos.value
        return photo
    }

    override suspend fun image(id: String): ByteArray? {
        imageReads += id
        return byteArrayOf(1)
    }

    override suspend fun thumbnail(id: String): ByteArray? {
        imageReads += id
        return byteArrayOf(2)
    }

    override suspend fun setNote(id: String, note: String) {
        notes[id] = note
    }

    override suspend fun delete(id: String) {
        photos.value = photos.value.filterNot { it.id == id }
    }

    override suspend fun deleteAll() {
        deletedAll = true
        photos.value = emptyList()
        comparisons.value = emptyList()
    }

    override suspend fun saveComparison(leftId: String, rightId: String): SavedComparison {
        val saved = SavedComparison("pair-${comparisons.value.size}", leftId, rightId)
        comparisons.value = listOf(saved) + comparisons.value
        return saved
    }

    override suspend fun removeComparison(id: String) {
        comparisons.value = comparisons.value.filterNot { it.id == id }
    }
}

/**
 * The phone's lock. Answers [answer] at once, or, with [hold], waits until
 * [release] so a test can look while the prompt is up.
 */
class FakeDeviceLock(
    var set: Boolean = true,
    var answer: DeviceLockResult = DeviceLockResult.Unlocked,
    private val hold: Boolean = false,
) : DeviceLock {
    var prompts = 0
    private var pending: CompletableDeferred<DeviceLockResult>? = null

    override fun isSet(): Boolean = set

    override suspend fun authenticate(): DeviceLockResult {
        prompts++
        if (!hold) return answer
        val waiting = CompletableDeferred<DeviceLockResult>()
        pending = waiting
        return waiting.await()
    }

    fun release(result: DeviceLockResult = answer) {
        pending?.complete(result)
    }
}

/** Remembers its observers so a test can send the app to the background. */
class FakeAppLifecycle : AppLifecycle {
    private val observers = mutableListOf<AppLifecycleObserver>()

    override fun addObserver(observer: AppLifecycleObserver) {
        observers += observer
    }

    override fun removeObserver(observer: AppLifecycleObserver) {
        observers -= observer
    }

    fun background() = observers.toList().forEach { it.onEnterBackground() }
}

class FakeCameraPermissions(var permission: CameraPermission = CameraPermission.Granted) : CameraPermissions {
    var settingsOpened = 0

    override fun current(): CameraPermission = permission

    override fun openSettings() {
        settingsOpened++
    }
}

/** `release.progressPhotos` as the remote config would set it. */
fun photosFlag(on: Boolean): ProgressPhotosEnabled = ProgressPhotosEnabled(
    object : AppConfigMap() {
        override val map: Map<String, *> = mapOf("release" to mapOf("progressPhotos" to on))
    },
)
