package com.opson.features.trends.impl.photos

import androidx.lifecycle.viewModelScope
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.flowroutines.collectIn
import com.opson.libraries.progressphotos.PhotoBytes
import com.opson.libraries.progressphotos.Pose
import com.opson.libraries.progressphotos.ProgressPhoto
import com.opson.libraries.progressphotos.ProgressPhotoRepository
import com.opson.libraries.scanner.CameraFacing
import com.opson.libraries.scanner.CameraPermission
import com.opson.libraries.scanner.CameraPermissions
import com.opson.libraries.scanner.StillResult
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Inject
import kotlin.time.Clock

/**
 * Taking a set: the front, then the side if they like. The last photo of the
 * same pose sits faintly over the camera so poses line up.
 */
data class CaptureState(
    val lock: PhotoLockState = PhotoLockState.Locked,
    /** `null` until checked. */
    val permission: CameraPermission? = null,
    val cameraUnavailable: Boolean = false,
    val facing: CameraFacing = CameraFacing.Front,
    val pose: Pose = Pose.Front,
    /** Goes up by one per press; the camera takes a still on each rise. */
    val shutter: Int = 0,
    /** A press is being taken and saved. */
    val busy: Boolean = false,
    /** The last photo of [pose], drawn faintly over the camera. */
    val ghost: PhotoBytes? = null,
    /** Poses saved since the camera opened. */
    val saved: List<Pose> = emptyList(),
    val failed: Boolean = false,
) {
    val cameraOn: Boolean
        get() = lock == PhotoLockState.Unlocked && permission == CameraPermission.Granted && !cameraUnavailable
}

@Inject
class CaptureViewModel(
    private val photos: ProgressPhotoRepository,
    private val lock: PhotoLock,
    private val permissions: CameraPermissions,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : SEAViewModel<CaptureState, CaptureEvent, CaptureAction>(initialStateArg = CaptureState()) {

    private var list: List<ProgressPhoto> = emptyList()
    private var ghostFor: String? = null

    /** Photos taken since the camera opened: the set being taken never guides itself. */
    private val takenNow = mutableSetOf<String>()

    init {
        lock.state.collectIn(viewModelScope) { takeAction(CaptureAction.LockChanged(it)) }
        photos.observePhotos().collectIn(viewModelScope) { takeAction(CaptureAction.PhotosArrived(it)) }
        viewModelScope.launch { takeAction(CaptureAction.PermissionChecked(permissions.current())) }
    }

    override suspend fun handleAction(action: CaptureAction) {
        when (action) {
            is CaptureAction.LockChanged -> {
                action.updateState { it.copy(lock = action.lock) }
                refreshGhost(action, state.pose)
            }
            is CaptureAction.PhotosArrived -> {
                list = action.photos
                refreshGhost(action, state.pose)
            }
            is CaptureAction.PermissionChecked -> {
                action.updateState { it.copy(permission = action.permission) }
                if (action.permission == CameraPermission.NotAsked) sendEvent(CaptureEvent.RequestPermission)
            }
            is CaptureAction.PermissionAnswered -> action.updateState { it.copy(permission = action.permission) }
            CaptureAction.AllowCameraTapped -> sendEvent(CaptureEvent.RequestPermission)
            CaptureAction.OpenSettingsTapped -> permissions.openSettings()
            CaptureAction.Resumed -> {
                val now = permissions.current()
                if (state.permission is CameraPermission.Denied && now == CameraPermission.Granted) {
                    action.updateState { it.copy(permission = now) }
                }
            }
            CaptureAction.ShutterTapped -> if (state.cameraOn && !state.busy) {
                action.updateState { it.copy(shutter = it.shutter + 1, busy = true, failed = false) }
            }
            is CaptureAction.Captured -> captured(action)
            CaptureAction.FlipTapped -> action.updateState {
                it.copy(facing = if (it.facing == CameraFacing.Front) CameraFacing.Back else CameraFacing.Front, busy = false)
            }
            CaptureAction.CameraUnavailable -> action.updateState { it.copy(cameraUnavailable = true, busy = false) }
            CaptureAction.UnlockTapped, CaptureAction.Foregrounded -> lock.unlock()
            CaptureAction.DoneTapped, CaptureAction.BackTapped -> sendEvent(CaptureEvent.Close)
        }
    }

    private suspend fun captured(action: CaptureAction.Captured) {
        val shot = (action.result as? StillResult.Taken)?.shot
        if (shot == null || lock.state.value != PhotoLockState.Unlocked) {
            action.updateState { it.copy(busy = false, failed = shot == null) }
            return
        }
        val now = clock.now()
        val pose = state.pose
        val added = Catching {
            photos.add(shot.jpeg, shot.thumbnail, pose, now, now.toLocalDateTime(timeZone).date)
        }.logOnFailure("Couldn't save a progress photo").getOrNull()
        if (added == null) {
            action.updateState { it.copy(busy = false, failed = true) }
            return
        }
        takenNow += added.id
        val saved = state.saved + pose
        if (Pose.entries.all { it in saved }) {
            action.updateState { it.copy(busy = false, saved = saved) }
            sendEvent(CaptureEvent.Close)
            return
        }
        val next = Pose.entries.first { it !in saved }
        action.updateState { it.copy(busy = false, saved = saved, pose = next) }
        refreshGhost(action, next)
    }

    /** The newest photo of [pose], read only while unlocked. The lock itself is the truth, not the last state. */
    private suspend fun refreshGhost(action: CaptureAction, pose: Pose) {
        if (lock.state.value != PhotoLockState.Unlocked) {
            ghostFor = null
            action.updateState { it.copy(ghost = null) }
            return
        }
        val last = list.firstOrNull { it.pose == pose && it.id !in takenNow }
        if (last?.id == ghostFor) return
        ghostFor = last?.id
        val image = last?.let { photos.thumbnail(it.id) }?.let(::PhotoBytes)
        action.updateState { it.copy(ghost = image) }
    }
}

sealed interface CaptureAction {
    data class LockChanged(val lock: PhotoLockState) : CaptureAction
    data class PhotosArrived(val photos: List<ProgressPhoto>) : CaptureAction
    data class PermissionChecked(val permission: CameraPermission) : CaptureAction
    data class PermissionAnswered(val permission: CameraPermission) : CaptureAction
    data object AllowCameraTapped : CaptureAction
    data object OpenSettingsTapped : CaptureAction
    data object Resumed : CaptureAction
    data object ShutterTapped : CaptureAction
    data class Captured(val result: StillResult) : CaptureAction
    data object FlipTapped : CaptureAction
    data object CameraUnavailable : CaptureAction
    data object UnlockTapped : CaptureAction
    data object Foregrounded : CaptureAction
    data object DoneTapped : CaptureAction
    data object BackTapped : CaptureAction
}

sealed interface CaptureEvent {
    data object RequestPermission : CaptureEvent
    data object Close : CaptureEvent
}
