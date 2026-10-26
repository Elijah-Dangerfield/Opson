package com.opson.features.trends.impl.photos

import com.opson.libraries.opson.AppLifecycle
import com.opson.libraries.opson.AppLifecycleObserver
import com.opson.libraries.progressphotos.DeviceLock
import com.opson.libraries.progressphotos.DeviceLockResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/** Where the photos' lock stands. Only [Unlocked] draws a photo. */
enum class PhotoLockState { Locked, Unlocking, Unlocked, NoDeviceLock }

/**
 * One lock for every progress photo screen, so Compare and the camera don't ask
 * again on top of the grid, while leaving the app locks all of them. Built the
 * first time a photo screen opens, never at launch, and it starts listening
 * for the app going to the background only then.
 */
@SingleIn(AppScope::class)
@Inject
class PhotoLock(private val deviceLock: DeviceLock, appLifecycle: AppLifecycle) {

    private val mutableState = MutableStateFlow(PhotoLockState.Locked)
    val state: StateFlow<PhotoLockState> = mutableState.asStateFlow()

    init {
        appLifecycle.addObserver(
            object : AppLifecycleObserver {
                override fun onEnterForeground() = Unit

                override fun onEnterBackground() = lock()
            },
        )
    }

    /** Asks for the phone's lock unless it is already being asked for. */
    suspend fun unlock() {
        if (mutableState.value == PhotoLockState.Unlocking || mutableState.value == PhotoLockState.Unlocked) return
        if (!deviceLock.isSet()) {
            mutableState.value = PhotoLockState.NoDeviceLock
            return
        }
        mutableState.value = PhotoLockState.Unlocking
        val result = try {
            deviceLock.authenticate()
        } catch (cancelled: CancellationException) {
            mutableState.value = PhotoLockState.Locked
            throw cancelled
        }
        mutableState.value = when (result) {
            DeviceLockResult.Unlocked -> PhotoLockState.Unlocked
            DeviceLockResult.NotSet -> PhotoLockState.NoDeviceLock
            DeviceLockResult.Cancelled, DeviceLockResult.Failed -> PhotoLockState.Locked
        }
    }

    /**
     * Locks again: the grid opening, or the app going to the background.
     * Ignored while the prompt is up, because on older Android the prompt is
     * its own screen and stops the app's activity underneath it.
     */
    fun lock() {
        if (mutableState.value == PhotoLockState.Unlocking) return
        mutableState.value = PhotoLockState.Locked
    }
}
