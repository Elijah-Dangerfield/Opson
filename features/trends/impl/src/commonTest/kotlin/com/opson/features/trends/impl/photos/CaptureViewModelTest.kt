package com.opson.features.trends.impl.photos

import com.opson.features.trends.impl.TrendsFixtures
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.progressphotos.Pose
import com.opson.libraries.scanner.CameraFacing
import com.opson.libraries.scanner.CameraPermission
import com.opson.libraries.scanner.StillResult
import com.opson.libraries.scanner.StillShot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaptureViewModelTest : CoroutineTest() {

    private val photos = FakeProgressPhotoRepository()
    private val lifecycle = FakeAppLifecycle()
    private val lock = PhotoLock(FakeDeviceLock(), lifecycle)
    private val permissions = FakeCameraPermissions()
    private val shot = StillResult.Taken(StillShot(byteArrayOf(1), byteArrayOf(2)))

    private suspend fun vm(unlocked: Boolean = true): CaptureViewModel {
        if (unlocked) lock.unlock()
        return CaptureViewModel(photos, lock, permissions, TrendsFixtures.clock, TrendsFixtures.timeZone)
    }

    private fun CoroutineScope.events(vm: CaptureViewModel) = mutableListOf<CaptureEvent>().also { list ->
        launch { vm.eventFlow.collect { list += it } }
    }

    @Test
    fun opens_onTheFront_withTheLastFrontAsTheGuide() = runUnitTest {
        val vm = vm()

        assertTrue(vm.state.cameraOn)
        assertEquals(Pose.Front, vm.state.pose)
        assertEquals(CameraFacing.Front, vm.state.facing)
        assertNotNull(vm.state.ghost)
        assertEquals(listOf("oct25-front"), photos.imageReads)
    }

    @Test
    fun cameraNotAskedYet_asks() = runUnitTest {
        permissions.permission = CameraPermission.NotAsked
        val vm = vm()
        val events = mutableListOf<CaptureEvent>()
        // The ask goes out on open; a late collector still sees it.
        launch { vm.eventFlow.collect { events += it } }

        assertEquals(listOf<CaptureEvent>(CaptureEvent.RequestPermission), events)
        assertFalse(vm.state.cameraOn)
        vm.takeAction(CaptureAction.PermissionAnswered(CameraPermission.Granted))
        assertTrue(vm.state.cameraOn)
    }

    @Test
    fun cameraRefused_allowAgainOrSettings() = runUnitTest {
        permissions.permission = CameraPermission.Denied(canAskAgain = false)
        val vm = vm()
        val events = events(vm)

        vm.takeAction(CaptureAction.OpenSettingsTapped)
        assertEquals(1, permissions.settingsOpened)
        vm.takeAction(CaptureAction.AllowCameraTapped)
        assertEquals(listOf<CaptureEvent>(CaptureEvent.RequestPermission), events)

        permissions.permission = CameraPermission.Granted
        vm.takeAction(CaptureAction.Resumed)
        assertTrue(vm.state.cameraOn)
    }

    @Test
    fun shutter_raisesTheCount_onlyOnceUntilTheShotIsBack() = runUnitTest {
        val vm = vm()

        vm.takeAction(CaptureAction.ShutterTapped)
        vm.takeAction(CaptureAction.ShutterTapped)

        assertEquals(1, vm.state.shutter)
        assertTrue(vm.state.busy)
    }

    @Test
    fun theFrontSaves_thenTheSide_thenItCloses() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        val before = photos.photos.value.size

        vm.takeAction(CaptureAction.ShutterTapped)
        vm.takeAction(CaptureAction.Captured(shot))
        assertEquals(Pose.Side, vm.state.pose)
        assertEquals(listOf(Pose.Front), vm.state.saved)
        assertFalse(vm.state.busy)
        assertEquals(TrendsFixtures.today, photos.photos.value.first().date)

        vm.takeAction(CaptureAction.ShutterTapped)
        vm.takeAction(CaptureAction.Captured(shot))

        assertEquals(before + 2, photos.photos.value.size)
        assertEquals(listOf<CaptureEvent>(CaptureEvent.Close), events)
    }

    @Test
    fun theSideGuide_isTheLastSide_notTheFrontJustTaken() = runUnitTest {
        val vm = vm()

        vm.takeAction(CaptureAction.ShutterTapped)
        vm.takeAction(CaptureAction.Captured(shot))

        assertEquals("oct25-side", photos.imageReads.last())
    }

    @Test
    fun aFailedShot_saysSo_andSavesNothing() = runUnitTest {
        val vm = vm()
        val before = photos.photos.value.size

        vm.takeAction(CaptureAction.ShutterTapped)
        vm.takeAction(CaptureAction.Captured(StillResult.Failed))

        assertTrue(vm.state.failed)
        assertFalse(vm.state.busy)
        assertEquals(before, photos.photos.value.size)
    }

    @Test
    fun locked_takesNothing_andShowsNoGuide() = runUnitTest {
        val vm = vm(unlocked = false)

        vm.takeAction(CaptureAction.ShutterTapped)

        assertEquals(0, vm.state.shutter)
        assertNull(vm.state.ghost)
        assertTrue(photos.imageReads.isEmpty())
    }

    @Test
    fun backgroundLocks_foregroundAsks() = runUnitTest {
        val vm = vm()

        lifecycle.background()
        assertFalse(vm.state.cameraOn)
        assertNull(vm.state.ghost)
        vm.takeAction(CaptureAction.Foregrounded)
        assertTrue(vm.state.cameraOn)
        vm.takeAction(CaptureAction.UnlockTapped)
        assertTrue(vm.state.cameraOn)
    }

    @Test
    fun flip_switchesCameras() = runUnitTest {
        val vm = vm()

        vm.takeAction(CaptureAction.FlipTapped)
        assertEquals(CameraFacing.Back, vm.state.facing)
        vm.takeAction(CaptureAction.FlipTapped)
        assertEquals(CameraFacing.Front, vm.state.facing)
    }

    @Test
    fun cameraUnavailable_saysSo() = runUnitTest {
        val vm = vm()

        vm.takeAction(CaptureAction.CameraUnavailable)

        assertTrue(vm.state.cameraUnavailable)
        assertFalse(vm.state.cameraOn)
    }

    @Test
    fun doneAndBack_close() = runUnitTest {
        val vm = vm()
        val events = events(vm)

        vm.takeAction(CaptureAction.DoneTapped)
        vm.takeAction(CaptureAction.BackTapped)

        assertEquals(listOf<CaptureEvent>(CaptureEvent.Close, CaptureEvent.Close), events)
    }
}
