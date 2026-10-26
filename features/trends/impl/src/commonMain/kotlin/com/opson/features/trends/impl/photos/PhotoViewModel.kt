package com.opson.features.trends.impl.photos

import androidx.lifecycle.viewModelScope
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.flowroutines.collectIn
import com.opson.libraries.progressphotos.PhotoBytes
import com.opson.libraries.progressphotos.ProgressPhoto
import com.opson.libraries.progressphotos.ProgressPhotoRepository
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject

/** One photo, large, with delete in ⋮. */
data class PhotoState(
    val lock: PhotoLockState = PhotoLockState.Locked,
    val photo: PhotoTile? = null,
    val note: String = "",
    val menuOpen: Boolean = false,
    val confirmDelete: Boolean = false,
)

@Inject
class PhotoViewModel(
    @Assisted private val id: String,
    private val photos: ProgressPhotoRepository,
    private val lock: PhotoLock,
) : SEAViewModel<PhotoState, PhotoEvent, PhotoAction>(initialStateArg = PhotoState()) {

    private var photo: ProgressPhoto? = null
    private var image: PhotoBytes? = null

    init {
        lock.state.collectIn(viewModelScope) { takeAction(PhotoAction.LockChanged(it)) }
        photos.observePhotos().collectIn(viewModelScope) { list -> takeAction(PhotoAction.PhotosArrived(list)) }
    }

    override suspend fun handleAction(action: PhotoAction) {
        when (action) {
            is PhotoAction.PhotosArrived -> {
                photo = action.photos.firstOrNull { it.id == id }
                // Deleted here or elsewhere: nothing left to show.
                if (photo == null) sendEvent(PhotoEvent.Close) else settle(action)
            }
            is PhotoAction.LockChanged -> settle(action)
            PhotoAction.UnlockTapped, PhotoAction.Foregrounded -> lock.unlock()
            PhotoAction.MenuTapped -> action.updateState { it.copy(menuOpen = it.lock == PhotoLockState.Unlocked) }
            PhotoAction.MenuDismissed -> action.updateState { it.copy(menuOpen = false) }
            PhotoAction.DeleteTapped -> action.updateState { it.copy(menuOpen = false, confirmDelete = true) }
            PhotoAction.DeleteCancelled -> action.updateState { it.copy(confirmDelete = false) }
            PhotoAction.DeleteConfirmed -> {
                action.updateState { it.copy(confirmDelete = false) }
                photos.delete(id)
            }
            PhotoAction.BackTapped -> sendEvent(PhotoEvent.Close)
        }
    }

    private suspend fun settle(action: PhotoAction) {
        val unlocked = lock.state.value == PhotoLockState.Unlocked
        if (!unlocked) {
            image = null
        } else if (image == null) {
            image = photos.image(id)?.let(::PhotoBytes)
        }
        val current = photo
        action.updateState { state ->
            if (!unlocked) {
                PhotoState(lock = lock.state.value)
            } else {
                state.copy(
                    lock = lock.state.value,
                    photo = current?.let { PhotoTile(it.id, it.date, it.pose, image) },
                    note = current?.note.orEmpty(),
                )
            }
        }
    }
}

sealed interface PhotoAction {
    data class PhotosArrived(val photos: List<ProgressPhoto>) : PhotoAction
    data class LockChanged(val lock: PhotoLockState) : PhotoAction
    data object UnlockTapped : PhotoAction
    data object Foregrounded : PhotoAction
    data object MenuTapped : PhotoAction
    data object MenuDismissed : PhotoAction
    data object DeleteTapped : PhotoAction
    data object DeleteConfirmed : PhotoAction
    data object DeleteCancelled : PhotoAction
    data object BackTapped : PhotoAction
}

sealed interface PhotoEvent {
    data object Close : PhotoEvent
}
