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

class PhotoViewModelTest : CoroutineTest() {

    private val photos = FakeProgressPhotoRepository()
    private val lifecycle = FakeAppLifecycle()
    private val lock = PhotoLock(FakeDeviceLock(), lifecycle)

    private suspend fun vm(id: String = "oct25-front", unlocked: Boolean = true): PhotoViewModel {
        if (unlocked) lock.unlock()
        return PhotoViewModel(id, photos, lock)
    }

    private fun CoroutineScope.events(vm: PhotoViewModel) = mutableListOf<PhotoEvent>().also { list ->
        launch { vm.eventFlow.collect { list += it } }
    }

    @Test
    fun showsThePhotoAndItsNote() = runUnitTest {
        val vm = vm()

        assertEquals("oct25-front", vm.state.photo?.id)
        assertNotNull(vm.state.photo?.image)
        assertEquals("Lifting three times a week since September", vm.state.note)
    }

    @Test
    fun locked_showsAndReadsNothing() = runUnitTest {
        val vm = vm(unlocked = false)

        assertNull(vm.state.photo)
        assertTrue(photos.imageReads.isEmpty())
        vm.takeAction(PhotoAction.MenuTapped)
        assertFalse(vm.state.menuOpen, "no menu over a locked photo")

        vm.takeAction(PhotoAction.UnlockTapped)
        assertNotNull(vm.state.photo)
    }

    @Test
    fun background_locks_foreground_asks() = runUnitTest {
        val vm = vm()

        lifecycle.background()
        assertNull(vm.state.photo)
        vm.takeAction(PhotoAction.Foregrounded)
        assertNotNull(vm.state.photo)
    }

    @Test
    fun delete_asksFirst_thenDeletesAndCloses() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(PhotoAction.MenuTapped)
        assertTrue(vm.state.menuOpen)
        vm.takeAction(PhotoAction.DeleteTapped)
        assertTrue(vm.state.confirmDelete)
        vm.takeAction(PhotoAction.DeleteCancelled)
        assertFalse(vm.state.confirmDelete)
        assertEquals(6, photos.photos.value.size)

        vm.takeAction(PhotoAction.DeleteTapped)
        vm.takeAction(PhotoAction.DeleteConfirmed)

        assertTrue(photos.photos.value.none { it.id == "oct25-front" })
        assertEquals(listOf<PhotoEvent>(PhotoEvent.Close), events)
    }

    @Test
    fun aDeleteThatFails_keepsThePhoto_andThePageWorking() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        photos.failWrites = true

        vm.takeAction(PhotoAction.DeleteTapped)
        vm.takeAction(PhotoAction.DeleteConfirmed)
        vm.takeAction(PhotoAction.BackTapped)

        assertEquals("oct25-front", vm.state.photo?.id)
        assertFalse(vm.state.confirmDelete)
        assertEquals(listOf<PhotoEvent>(PhotoEvent.Close), events)
    }

    @Test
    fun menuDismissed_andBack() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(PhotoAction.MenuTapped)
        vm.takeAction(PhotoAction.MenuDismissed)
        vm.takeAction(PhotoAction.BackTapped)

        assertFalse(vm.state.menuOpen)
        assertEquals(listOf<PhotoEvent>(PhotoEvent.Close), events)
    }
}
