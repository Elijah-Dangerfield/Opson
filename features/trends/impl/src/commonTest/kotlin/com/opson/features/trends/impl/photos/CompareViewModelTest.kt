package com.opson.features.trends.impl.photos

import com.opson.libraries.flowroutines.testing.CoroutineTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompareViewModelTest : CoroutineTest() {

    private val photos = FakeProgressPhotoRepository()
    private val lifecycle = FakeAppLifecycle()
    private val lock = PhotoLock(FakeDeviceLock(), lifecycle)

    private suspend fun vm(leftId: String? = null, rightId: String? = null, unlocked: Boolean = true): CompareViewModel {
        if (unlocked) lock.unlock()
        return CompareViewModel(leftId, rightId, photos, lock)
    }

    private fun CoroutineScope.events(vm: CompareViewModel) = mutableListOf<CompareEvent>().also { list ->
        launch { vm.eventFlow.collect { list += it } }
    }

    @Test
    fun withNoPair_theLatestAgainstTheEarliestOfItsPose() = runUnitTest {
        val vm = vm()

        assertEquals("sep13-front", vm.state.left?.id)
        assertEquals("oct25-front", vm.state.right?.id)
        assertNotNull(vm.state.left?.image)
        assertEquals(6, vm.state.choices.size)
        assertEquals("Lifting three times a week since September", vm.state.note)
    }

    @Test
    fun aChosenPairIsKept() = runUnitTest {
        val vm = vm("sep27-side", "oct25-side")

        assertEquals("sep27-side", vm.state.left?.id)
        assertEquals("oct25-side", vm.state.right?.id)
    }

    @Test
    fun aDeletedPhotoFallsBackToTheDefault() = runUnitTest {
        val vm = vm("sep27-side", "oct25-side")

        photos.delete("sep27-side")

        assertEquals("oct25-side", vm.state.right?.id)
        assertEquals("sep13-front", vm.state.left?.id, "no other side photo, so the earliest other one")
    }

    @Test
    fun locked_readsNoPhoto() = runUnitTest {
        val vm = vm(unlocked = false)

        assertEquals(PhotoLockState.Locked, vm.state.lock)
        assertNull(vm.state.left)
        assertTrue(photos.imageReads.isEmpty())
    }

    @Test
    fun goingToTheBackground_letsGoOfThePhotos() = runUnitTest {
        val vm = vm()

        lifecycle.background()

        assertEquals(PhotoLockState.Locked, vm.state.lock)
        assertNull(vm.state.right)
        vm.takeAction(CompareAction.Foregrounded)
        assertEquals(PhotoLockState.Unlocked, vm.state.lock)
    }

    @Test
    fun pickingADate() = runUnitTest {
        val vm = vm()

        vm.takeAction(CompareAction.PickerTapped(CompareSide.Left))
        assertEquals(CompareSide.Left, vm.state.picker)
        vm.takeAction(CompareAction.PhotoPicked(CompareSide.Left, "oct18-front"))

        assertEquals("oct18-front", vm.state.left?.id)
        assertNull(vm.state.picker)
        vm.takeAction(CompareAction.PickerTapped(CompareSide.Right))
        vm.takeAction(CompareAction.PickerDismissed)
        assertNull(vm.state.picker)
    }

    @Test
    fun pickingTheRightPhoto_showsItsNote() = runUnitTest {
        val vm = vm()
        vm.takeAction(CompareAction.NoteTyped("Typed"))

        vm.takeAction(CompareAction.PhotoPicked(CompareSide.Right, "oct18-front"))

        assertEquals("", vm.state.note)
    }

    @Test
    fun modeAndSlider() = runUnitTest {
        val vm = vm()

        vm.takeAction(CompareAction.ModePicked(CompareMode.Slider))
        vm.takeAction(CompareAction.SliderMoved(0.3f))
        assertEquals(CompareMode.Slider, vm.state.mode)
        assertEquals(0.3f, vm.state.sliderAt)

        vm.takeAction(CompareAction.SliderMoved(1.7f))
        assertEquals(1f, vm.state.sliderAt)
        vm.takeAction(CompareAction.SliderMoved(-1f))
        assertEquals(0f, vm.state.sliderAt)
    }

    @Test
    fun noteTyped_isSavedToTheLaterPhoto() = runUnitTest {
        val vm = vm()

        vm.takeAction(CompareAction.NoteTyped("Back to running"))

        assertEquals("Back to running", vm.state.note)
        assertEquals("Back to running", photos.notes["oct25-front"])
    }

    @Test
    fun saveAndRemoveTheComparison_fromTheMenu() = runUnitTest {
        val vm = vm()

        vm.takeAction(CompareAction.MenuTapped)
        assertTrue(vm.state.menuOpen)
        vm.takeAction(CompareAction.SaveComparisonTapped)
        assertFalse(vm.state.menuOpen)
        assertEquals("sep13-front", photos.comparisons.value.single().leftId)
        assertNotNull(vm.state.savedId)

        vm.takeAction(CompareAction.MenuTapped)
        vm.takeAction(CompareAction.RemoveComparisonTapped)
        assertTrue(photos.comparisons.value.isEmpty())
        assertNull(vm.state.savedId)

        vm.takeAction(CompareAction.MenuTapped)
        vm.takeAction(CompareAction.MenuDismissed)
        assertFalse(vm.state.menuOpen)
    }

    @Test
    fun unlockTapped_asks() = runUnitTest {
        val vm = vm(unlocked = false)

        vm.takeAction(CompareAction.UnlockTapped)

        assertEquals(PhotoLockState.Unlocked, vm.state.lock)
    }

    @Test
    fun back_closes() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(CompareAction.BackTapped)

        assertEquals(listOf<CompareEvent>(CompareEvent.Close), events)
    }
}
