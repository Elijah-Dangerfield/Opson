package com.opson.features.trends.impl.photos

import androidx.lifecycle.viewModelScope
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.flowroutines.collectIn
import com.opson.libraries.progressphotos.PhotoBytes
import com.opson.libraries.progressphotos.ProgressPhoto
import com.opson.libraries.progressphotos.ProgressPhotoRepository
import com.opson.libraries.progressphotos.SavedComparison
import kotlinx.coroutines.flow.combine
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject

/**
 * Compare. Reads the two full photos only while unlocked and lets go of them
 * when it locks. Saving keeps the pair's ids, nothing rendered.
 */
@Inject
class CompareViewModel(
    @Assisted leftId: String?,
    @Assisted rightId: String?,
    private val photos: ProgressPhotoRepository,
    private val lock: PhotoLock,
) : SEAViewModel<CompareState, CompareEvent, CompareAction>(initialStateArg = CompareState()) {

    private var list: List<ProgressPhoto>? = null
    private var comparisons: List<SavedComparison> = emptyList()
    private val images = mutableMapOf<String, PhotoBytes>()
    private var ui = CompareUi(leftId = leftId, rightId = rightId)

    init {
        lock.state.collectIn(viewModelScope) { takeAction(CompareAction.LockChanged(it)) }
        combine(photos.observePhotos(), photos.observeComparisons()) { list, saved -> CompareAction.PhotosArrived(list, saved) }
            .collectIn(viewModelScope) { takeAction(it) }
    }

    override suspend fun handleAction(action: CompareAction) {
        when (action) {
            is CompareAction.PhotosArrived -> {
                list = action.photos
                comparisons = action.saved
                settle(action)
            }
            is CompareAction.LockChanged -> {
                if (action.lock != PhotoLockState.Unlocked) {
                    images.clear()
                    ui = ui.copy(picker = null, menuOpen = false)
                }
                settle(action)
            }
            CompareAction.UnlockTapped, CompareAction.Foregrounded -> lock.unlock()
            is CompareAction.PickerTapped -> change(action) { copy(picker = action.side) }
            CompareAction.PickerDismissed -> change(action) { copy(picker = null) }
            is CompareAction.PhotoPicked -> change(action) {
                when (action.side) {
                    CompareSide.Left -> copy(leftId = action.id, picker = null)
                    CompareSide.Right -> copy(rightId = action.id, note = null, picker = null)
                }
            }
            is CompareAction.ModePicked -> change(action) { copy(mode = action.mode) }
            is CompareAction.SliderMoved -> {
                // Every frame of a drag: move the handle, without settling the pair and rebuilding the pickers.
                ui = ui.copy(sliderAt = action.at.coerceIn(0f, 1f))
                action.updateState { it.copy(sliderAt = ui.sliderAt) }
            }
            is CompareAction.NoteTyped -> {
                val rightId = state.right?.id ?: return
                // The field holds only what is kept, so nothing typed past the limit looks saved.
                val note = action.text.take(NOTE_LIMIT)
                change(action) { copy(note = note) }
                write("Couldn't save a progress photo note") { photos.setNote(rightId, note) }
            }
            CompareAction.MenuTapped -> change(action) { copy(menuOpen = true) }
            CompareAction.MenuDismissed -> change(action) { copy(menuOpen = false) }
            CompareAction.SaveComparisonTapped -> {
                change(action) { copy(menuOpen = false) }
                val left = state.left ?: return
                val right = state.right ?: return
                // It shows under the grid; the menu now offers to remove it.
                write("Couldn't save a photo comparison") { photos.saveComparison(left.id, right.id) }
            }
            CompareAction.RemoveComparisonTapped -> {
                change(action) { copy(menuOpen = false) }
                val savedId = state.savedId ?: return
                write("Couldn't remove a photo comparison") { photos.removeComparison(savedId) }
            }
            CompareAction.BackTapped -> sendEvent(CompareEvent.Close)
        }
    }

    /** Fixes the pair against the photos that exist, reads its images if unlocked, and rebuilds. */
    private suspend fun settle(action: CompareAction) {
        val current = list
        if (current != null) {
            val (left, right) = CompareStateBuilder.pair(current, ui.leftId, ui.rightId)
            ui = ui.copy(leftId = left, rightId = right)
            images.keys.retainAll(setOfNotNull(left, right))
            if (lock.state.value == PhotoLockState.Unlocked) {
                listOfNotNull(left, right).filter { it !in images }.forEach { id ->
                    photos.image(id)?.let { images[id] = PhotoBytes(it) }
                }
            }
        }
        action.updateState { CompareStateBuilder.build(list, comparisons, images.toMap(), lock.state.value, ui) }
    }

    /**
     * A write to the photo index. It throws on a full disk, or on iOS when protected data is
     * unavailable; uncaught, that ends the action loop and takes the app down with it. The page
     * already shows what it holds, so a failure is logged and the next change tries again.
     */
    private suspend fun write(failure: String, block: suspend () -> Unit) {
        Catching { block() }.logOnFailure(failure)
    }

    private suspend fun change(action: CompareAction, block: CompareUi.() -> CompareUi) {
        ui = ui.block()
        settle(action)
    }

    private companion object {
        const val NOTE_LIMIT = 280
    }
}

sealed interface CompareAction {
    data class PhotosArrived(val photos: List<ProgressPhoto>, val saved: List<SavedComparison>) : CompareAction
    data class LockChanged(val lock: PhotoLockState) : CompareAction
    data object UnlockTapped : CompareAction
    data object Foregrounded : CompareAction
    data class PickerTapped(val side: CompareSide) : CompareAction
    data object PickerDismissed : CompareAction
    data class PhotoPicked(val side: CompareSide, val id: String) : CompareAction
    data class ModePicked(val mode: CompareMode) : CompareAction
    data class SliderMoved(val at: Float) : CompareAction
    data class NoteTyped(val text: String) : CompareAction
    data object MenuTapped : CompareAction
    data object MenuDismissed : CompareAction
    data object SaveComparisonTapped : CompareAction
    data object RemoveComparisonTapped : CompareAction
    data object BackTapped : CompareAction
}

sealed interface CompareEvent {
    data object Close : CompareEvent
}
