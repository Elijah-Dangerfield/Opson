package com.opson.features.trends.impl.photos

import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.progressphotos.DeviceLockResult
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoLockTest : CoroutineTest() {

    private val lifecycle = FakeAppLifecycle()

    @Test
    fun startsLocked() {
        assertEquals(PhotoLockState.Locked, PhotoLock(FakeDeviceLock(), lifecycle).state.value)
    }

    @Test
    fun passingThePhonesLockUnlocks() = runUnitTest {
        val device = FakeDeviceLock()
        val lock = PhotoLock(device, lifecycle)

        lock.unlock()

        assertEquals(PhotoLockState.Unlocked, lock.state.value)
        assertEquals(1, device.prompts)
    }

    @Test
    fun backingOutOrFailingStaysLocked() = runUnitTest {
        val cancelled = PhotoLock(FakeDeviceLock(answer = DeviceLockResult.Cancelled), lifecycle)
        val failed = PhotoLock(FakeDeviceLock(answer = DeviceLockResult.Failed), lifecycle)

        cancelled.unlock()
        failed.unlock()

        assertEquals(PhotoLockState.Locked, cancelled.state.value)
        assertEquals(PhotoLockState.Locked, failed.state.value)
    }

    @Test
    fun aPhoneWithNoLockNeverPromptsAndShowsNothing() = runUnitTest {
        val device = FakeDeviceLock(set = false)
        val lock = PhotoLock(device, lifecycle)

        lock.unlock()

        assertEquals(PhotoLockState.NoDeviceLock, lock.state.value)
        assertEquals(0, device.prompts)
    }

    @Test
    fun aLockRemovedMidPromptIsNoDeviceLock() = runUnitTest {
        val lock = PhotoLock(FakeDeviceLock(answer = DeviceLockResult.NotSet), lifecycle)

        lock.unlock()

        assertEquals(PhotoLockState.NoDeviceLock, lock.state.value)
    }

    @Test
    fun whileThePromptIsUp_lockingAndAskingAgainAreIgnored() = runUnitTest {
        val device = FakeDeviceLock(hold = true)
        val lock = PhotoLock(device, lifecycle)
        launch { lock.unlock() }
        assertEquals(PhotoLockState.Unlocking, lock.state.value)

        // Older Android's prompt is its own screen: the app stops underneath it.
        lifecycle.background()
        lock.lock()
        lock.unlock()
        device.release(DeviceLockResult.Unlocked)

        assertEquals(PhotoLockState.Unlocked, lock.state.value)
        assertEquals(1, device.prompts)
    }

    @Test
    fun alreadyUnlocked_doesNotAskAgain() = runUnitTest {
        val device = FakeDeviceLock()
        val lock = PhotoLock(device, lifecycle)
        lock.unlock()

        lock.unlock()

        assertEquals(1, device.prompts)
    }

    @Test
    fun theAppGoingToTheBackgroundLocks() = runUnitTest {
        val lock = PhotoLock(FakeDeviceLock(), lifecycle)
        lock.unlock()

        lifecycle.background()

        assertEquals(PhotoLockState.Locked, lock.state.value)
    }
}
