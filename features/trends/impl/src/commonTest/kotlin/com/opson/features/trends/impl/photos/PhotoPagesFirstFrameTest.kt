package com.opson.features.trends.impl.photos

import com.opson.libraries.flowroutines.testing.CoroutineTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The state a photo page holds before its view model has handled anything: the first frame
 * Compose draws. On a dispatcher that runs nothing until asked, so the view model's own lock
 * action can't paper over a wrong starting value.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhotoPagesFirstFrameTest : CoroutineTest() {

    override val testDispatcher: TestDispatcher = StandardTestDispatcher()

    private val photos = FakeProgressPhotoRepository()
    private val lock = PhotoLock(FakeDeviceLock(), FakeAppLifecycle())

    @Test
    fun compare_openedFromTheUnlockedGrid_startsUnlocked() = runUnitTest {
        lock.unlock()

        val vm = CompareViewModel(null, null, photos, lock)

        assertEquals(PhotoLockState.Unlocked, vm.state.lock, "never a frame of the lock panel")
    }

    @Test
    fun aSinglePhoto_openedFromTheUnlockedGrid_startsUnlocked() = runUnitTest {
        lock.unlock()

        val vm = PhotoViewModel("oct25-front", photos, lock)

        assertEquals(PhotoLockState.Unlocked, vm.state.lock, "never a frame of the lock panel")
    }
}
