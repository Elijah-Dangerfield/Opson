package com.opson.features.trends.impl.photos

import com.opson.features.trends.impl.TrendsFixtures
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.progressphotos.DeviceLockResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProgressPhotosViewModelTest : CoroutineTest() {

    private val photos = FakeProgressPhotoRepository()
    private val lifecycle = FakeAppLifecycle()

    private fun vm(device: FakeDeviceLock = FakeDeviceLock(), lock: PhotoLock = PhotoLock(device, lifecycle)) =
        ProgressPhotosViewModel(photos, lock, TrendsFixtures.clock, TrendsFixtures.timeZone)

    private fun CoroutineScope.events(vm: ProgressPhotosViewModel) = mutableListOf<ProgressPhotosEvent>().also { list ->
        launch { vm.eventFlow.collect { list += it } }
    }

    @Test
    fun opening_asksForTheLock_thenShowsMonthsNewestFirst() = runUnitTest {
        val device = FakeDeviceLock()
        val vm = vm(device)

        assertEquals(1, device.prompts)
        assertEquals(PhotoLockState.Unlocked, vm.state.lock)
        assertEquals(listOf(Month.OCTOBER, Month.SEPTEMBER), vm.state.months.map { it.month })
        assertEquals(listOf(3, 3), vm.state.months.map { it.tiles.size })
        assertTrue(vm.state.canCompare)
    }

    @Test
    fun opening_readsNoThumbnail_untilItsTileAsks() = runUnitTest {
        val vm = vm()

        assertTrue(photos.imageReads.isEmpty(), "a year of photos is not read to show the dozen on screen")
        assertTrue(vm.state.months.flatMap { it.tiles }.all { it.image == null })

        vm.takeAction(ProgressPhotosAction.ThumbnailWanted("oct18-front"))

        assertEquals(listOf("oct18-front"), photos.imageReads)
        val tiles = vm.state.months.flatMap { it.tiles }
        assertNotNull(tiles.single { it.id == "oct18-front" }.image)
        assertTrue(tiles.filter { it.id != "oct18-front" }.all { it.image == null })
    }

    @Test
    fun aTileAskingTwice_readsItsThumbnailOnce() = runUnitTest {
        val disk = CompletableDeferred<Unit>()
        photos.thumbnailsWaitFor = disk
        val vm = vm()

        vm.takeAction(ProgressPhotosAction.ThumbnailWanted("oct18-front"))
        vm.takeAction(ProgressPhotosAction.ThumbnailWanted("oct18-front"))
        disk.complete(Unit)
        vm.takeAction(ProgressPhotosAction.ThumbnailWanted("oct18-front"))

        assertEquals(listOf("oct18-front"), photos.imageReads)
    }

    @Test
    fun theGridShowsBeforeTheThumbnailsAreRead_andBackStillWorksMeanwhile() = runUnitTest {
        val disk = CompletableDeferred<Unit>()
        photos.thumbnailsWaitFor = disk
        val vm = vm()
        val events = events(vm)

        vm.takeAction(ProgressPhotosAction.ThumbnailWanted("oct25-front"))
        assertEquals(listOf(3, 3), vm.state.months.map { it.tiles.size }, "the dates and poses are already known")
        assertTrue(vm.state.months.flatMap { it.tiles }.all { it.image == null })
        vm.takeAction(ProgressPhotosAction.BackTapped)
        assertEquals(listOf<ProgressPhotosEvent>(ProgressPhotosEvent.Close), events)

        disk.complete(Unit)

        assertNotNull(vm.state.months.flatMap { it.tiles }.single { it.id == "oct25-front" }.image)
    }

    @Test
    fun locking_whileAThumbnailIsBeingRead_dropsIt_andAsksNothingMoreUntilUnlocked() = runUnitTest {
        val disk = CompletableDeferred<Unit>()
        photos.thumbnailsWaitFor = disk
        val vm = vm()

        vm.takeAction(ProgressPhotosAction.ThumbnailWanted("oct25-front"))
        lifecycle.background()
        disk.complete(Unit)
        vm.takeAction(ProgressPhotosAction.ThumbnailWanted("oct18-front"))

        assertTrue(vm.state.months.isEmpty())
        assertTrue(photos.imageReads.isEmpty(), "the read in flight was cancelled and none starts while locked")
    }

    @Test
    fun opening_locksEvenIfAnEarlierScreenHadUnlocked() = runUnitTest {
        val device = FakeDeviceLock()
        val lock = PhotoLock(device, lifecycle)
        lock.unlock()

        vm(device, lock)

        assertEquals(2, device.prompts, "every time the screen opens")
    }

    @Test
    fun locked_showsNothingAndReadsNoPhoto() = runUnitTest {
        val vm = vm(FakeDeviceLock(answer = DeviceLockResult.Cancelled))

        assertEquals(PhotoLockState.Locked, vm.state.lock)
        assertTrue(vm.state.months.isEmpty())
        assertTrue(photos.imageReads.isEmpty())
    }

    @Test
    fun noPhoneLock_showsNothing() = runUnitTest {
        val vm = vm(FakeDeviceLock(set = false))

        assertEquals(PhotoLockState.NoDeviceLock, vm.state.lock)
        assertTrue(vm.state.months.isEmpty())
    }

    @Test
    fun goingToTheBackground_dropsThePhotos_andComingBackAsksAgain() = runUnitTest {
        val device = FakeDeviceLock()
        val vm = vm(device)

        lifecycle.background()
        assertEquals(PhotoLockState.Locked, vm.state.lock)
        assertTrue(vm.state.months.isEmpty())

        vm.takeAction(ProgressPhotosAction.Foregrounded)
        assertEquals(2, device.prompts)
        assertEquals(PhotoLockState.Unlocked, vm.state.lock)
    }

    @Test
    fun unlockTapped_asksAgain() = runUnitTest {
        val device = FakeDeviceLock(answer = DeviceLockResult.Cancelled)
        val vm = vm(device)
        device.answer = DeviceLockResult.Unlocked

        vm.takeAction(ProgressPhotosAction.UnlockTapped)

        assertEquals(PhotoLockState.Unlocked, vm.state.lock)
    }

    @Test
    fun oneOrNoPhotos_noCompare_andEmptySays() = runUnitTest {
        photos.photos.value = emptyList()
        val vm = vm()

        assertFalse(vm.state.canCompare)
        assertTrue(vm.state.empty)
    }

    @Test
    fun taps_navigate() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(ProgressPhotosAction.TileTapped("oct18-front"))
        vm.takeAction(ProgressPhotosAction.CompareTapped)
        vm.takeAction(ProgressPhotosAction.TakePhotosTapped)
        vm.takeAction(ProgressPhotosAction.BackTapped)

        assertEquals(
            listOf(
                ProgressPhotosEvent.OpenPhoto("oct18-front"),
                ProgressPhotosEvent.OpenCompare(null, null),
                ProgressPhotosEvent.OpenCapture,
                ProgressPhotosEvent.Close,
            ),
            events,
        )
    }

    @Test
    fun savedPairTapped_opensThatPair() = runUnitTest {
        val saved = photos.saveComparison("sep13-front", "oct25-front")
        val vm = vm()
        val events = events(vm)

        assertEquals(listOf(saved.id), vm.state.saved.map { it.id })
        vm.takeAction(ProgressPhotosAction.SavedPairTapped(saved.id))

        assertEquals(listOf<ProgressPhotosEvent>(ProgressPhotosEvent.OpenCompare("sep13-front", "oct25-front")), events)
    }

    @Test
    fun menu_opensAndCloses() = runUnitTest {
        val vm = vm()

        vm.takeAction(ProgressPhotosAction.MenuTapped)
        assertTrue(vm.state.menuOpen)
        vm.takeAction(ProgressPhotosAction.MenuDismissed)
        assertFalse(vm.state.menuOpen)
    }

    @Test
    fun deleteAll_asksFirst_cancelKeepsThem() = runUnitTest {
        val vm = vm()

        vm.takeAction(ProgressPhotosAction.MenuTapped)
        vm.takeAction(ProgressPhotosAction.DeleteAllTapped)
        assertTrue(vm.state.confirmDeleteAll)
        assertFalse(vm.state.menuOpen)
        vm.takeAction(ProgressPhotosAction.DeleteAllCancelled)

        assertFalse(vm.state.confirmDeleteAll)
        assertFalse(photos.deletedAll)
    }

    @Test
    fun deleteAllConfirmed_deletesEveryPhoto() = runUnitTest {
        val vm = vm()

        vm.takeAction(ProgressPhotosAction.DeleteAllTapped)
        vm.takeAction(ProgressPhotosAction.DeleteAllConfirmed)

        assertTrue(photos.deletedAll)
        assertTrue(vm.state.empty)
        assertFalse(vm.state.confirmDeleteAll)
    }

    @Test
    fun aDeleteAllThatFails_keepsTheGrid_andThePageWorking() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        photos.failWrites = true

        vm.takeAction(ProgressPhotosAction.DeleteAllTapped)
        vm.takeAction(ProgressPhotosAction.DeleteAllConfirmed)
        vm.takeAction(ProgressPhotosAction.BackTapped)

        assertEquals(listOf(3, 3), vm.state.months.map { it.tiles.size })
        assertFalse(vm.state.confirmDeleteAll)
        assertEquals(listOf<ProgressPhotosEvent>(ProgressPhotosEvent.Close), events)
    }

    @Test
    fun anOlderYearSaysWhichYear() {
        val state = ProgressPhotosStateBuilder.build(
            photos = ProgressPhotosFixtures.photos,
            comparisons = emptyList(),
            thumbnails = emptyMap(),
            today = kotlinx.datetime.LocalDate(2027, 1, 2),
            lock = PhotoLockState.Unlocked,
            ui = ProgressPhotosUi(),
        )

        assertTrue(state.months.all { it.showYear })
        assertNotNull(state.months.firstOrNull())
    }
}
