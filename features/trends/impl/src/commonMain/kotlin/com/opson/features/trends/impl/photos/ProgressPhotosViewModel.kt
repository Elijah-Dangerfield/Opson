package com.opson.features.trends.impl.photos

import androidx.lifecycle.viewModelScope
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.flowroutines.collectIn
import com.opson.libraries.progressphotos.PhotoBytes
import com.opson.libraries.progressphotos.ProgressPhoto
import com.opson.libraries.progressphotos.ProgressPhotoRepository
import com.opson.libraries.progressphotos.SavedComparison
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Inject
import kotlin.time.Clock

/**
 * The grid. Locks and asks for the phone's lock every time it is created,
 * which is every time the screen opens. Thumbnails are read only while
 * unlocked and dropped the moment it locks again. They are read beside the
 * page rather than inside an action, so the grid draws first and a tap
 * doesn't wait for the disk.
 */
@Inject
class ProgressPhotosViewModel(
    private val photos: ProgressPhotoRepository,
    private val lock: PhotoLock,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : SEAViewModel<ProgressPhotosState, ProgressPhotosEvent, ProgressPhotosAction>(initialStateArg = ProgressPhotosState()) {

    private var list: List<ProgressPhoto>? = null
    private var comparisons: List<SavedComparison> = emptyList()
    private val thumbnails = mutableMapOf<String, PhotoBytes>()
    private var ui = ProgressPhotosUi()
    private var reading: Job? = null

    private val today: LocalDate get() = clock.now().toLocalDateTime(timeZone).date

    init {
        lock.lock()
        lock.state.collectIn(viewModelScope) { takeAction(ProgressPhotosAction.LockChanged(it)) }
        combine(photos.observePhotos(), photos.observeComparisons()) { list, saved -> ProgressPhotosAction.PhotosArrived(list, saved) }
            .collectIn(viewModelScope) { takeAction(it) }
        viewModelScope.launch { lock.unlock() }
    }

    override suspend fun handleAction(action: ProgressPhotosAction) {
        when (action) {
            is ProgressPhotosAction.PhotosArrived -> {
                list = action.photos
                comparisons = action.saved
                thumbnails.keys.retainAll(action.photos.map { it.id }.toSet())
                rebuild(action)
                readThumbnails()
            }
            is ProgressPhotosAction.LockChanged -> {
                if (action.lock != PhotoLockState.Unlocked) {
                    reading?.cancel()
                    thumbnails.clear()
                    ui = ProgressPhotosUi()
                }
                rebuild(action)
                readThumbnails()
            }
            ProgressPhotosAction.UnlockTapped, ProgressPhotosAction.Foregrounded -> lock.unlock()
            is ProgressPhotosAction.ThumbnailsRead -> {
                // A read that finished after the lock came back down is dropped.
                if (lock.state.value != PhotoLockState.Unlocked) return
                val ids = list.orEmpty().map { it.id }.toSet()
                action.read.filterKeys { it in ids }.forEach { (id, bytes) -> thumbnails[id] = bytes }
                rebuild(action)
            }
            is ProgressPhotosAction.TileTapped -> sendEvent(ProgressPhotosEvent.OpenPhoto(action.id))
            ProgressPhotosAction.CompareTapped -> sendEvent(ProgressPhotosEvent.OpenCompare(null, null))
            is ProgressPhotosAction.SavedPairTapped -> comparisons.firstOrNull { it.id == action.id }?.let {
                sendEvent(ProgressPhotosEvent.OpenCompare(it.leftId, it.rightId))
            }
            ProgressPhotosAction.TakePhotosTapped -> sendEvent(ProgressPhotosEvent.OpenCapture)
            ProgressPhotosAction.MenuTapped -> changeUi(action) { copy(menuOpen = true) }
            ProgressPhotosAction.MenuDismissed -> changeUi(action) { copy(menuOpen = false) }
            ProgressPhotosAction.DeleteAllTapped -> changeUi(action) { copy(menuOpen = false, confirmDeleteAll = true) }
            ProgressPhotosAction.DeleteAllCancelled -> changeUi(action) { copy(confirmDeleteAll = false) }
            ProgressPhotosAction.DeleteAllConfirmed -> {
                changeUi(action) { copy(confirmDeleteAll = false) }
                photos.deleteAll()
            }
            ProgressPhotosAction.BackTapped -> sendEvent(ProgressPhotosEvent.Close)
        }
    }

    /** Reads the thumbnails not read yet, together, only while unlocked. */
    private fun readThumbnails() {
        if (lock.state.value != PhotoLockState.Unlocked) return
        val missing = list.orEmpty().map { it.id }.filter { it !in thumbnails }
        if (missing.isEmpty()) return
        reading?.cancel()
        reading = viewModelScope.launch {
            val read = coroutineScope {
                missing.map { id -> async { photos.thumbnail(id)?.let { id to PhotoBytes(it) } } }.awaitAll()
            }
            takeAction(ProgressPhotosAction.ThumbnailsRead(read.filterNotNull().toMap()))
        }
    }

    private suspend fun changeUi(action: ProgressPhotosAction, change: ProgressPhotosUi.() -> ProgressPhotosUi) {
        ui = ui.change()
        rebuild(action)
    }

    private suspend fun rebuild(action: ProgressPhotosAction) {
        action.updateState {
            ProgressPhotosStateBuilder.build(list, comparisons, thumbnails.toMap(), today, lock.state.value, ui)
        }
    }
}

sealed interface ProgressPhotosAction {
    data class PhotosArrived(val photos: List<ProgressPhoto>, val saved: List<SavedComparison>) : ProgressPhotosAction
    data class LockChanged(val lock: PhotoLockState) : ProgressPhotosAction
    data class ThumbnailsRead(val read: Map<String, PhotoBytes>) : ProgressPhotosAction
    data object UnlockTapped : ProgressPhotosAction
    data object Foregrounded : ProgressPhotosAction
    data class TileTapped(val id: String) : ProgressPhotosAction
    data object CompareTapped : ProgressPhotosAction
    data class SavedPairTapped(val id: String) : ProgressPhotosAction
    data object TakePhotosTapped : ProgressPhotosAction
    data object MenuTapped : ProgressPhotosAction
    data object MenuDismissed : ProgressPhotosAction
    data object DeleteAllTapped : ProgressPhotosAction
    data object DeleteAllConfirmed : ProgressPhotosAction
    data object DeleteAllCancelled : ProgressPhotosAction
    data object BackTapped : ProgressPhotosAction
}

sealed interface ProgressPhotosEvent {
    data class OpenPhoto(val id: String) : ProgressPhotosEvent
    data class OpenCompare(val leftId: String?, val rightId: String?) : ProgressPhotosEvent
    data object OpenCapture : ProgressPhotosEvent
    data object Close : ProgressPhotosEvent
}
