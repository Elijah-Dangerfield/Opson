package com.opson.libraries.progressphotos.impl

import com.opson.libraries.progressphotos.DeviceLock
import com.opson.libraries.progressphotos.DeviceLockResult
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.photos_unlock_reason
import org.jetbrains.compose.resources.getString
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAErrorAppCancel
import platform.LocalAuthentication.LAErrorPasscodeNotSet
import platform.LocalAuthentication.LAErrorSystemCancel
import platform.LocalAuthentication.LAErrorUserCancel
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.coroutines.resume

/**
 * `LAPolicyDeviceOwnerAuthentication`: Face ID or Touch ID, falling back to
 * the passcode. Face ID needs `NSFaceIDUsageDescription` in Info.plist, or
 * iOS ends the app on the first prompt. A fresh `LAContext` per prompt, so an
 * earlier unlock is never reused.
 */
@OptIn(ExperimentalForeignApi::class)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosDeviceLock @Inject constructor() : DeviceLock {

    override fun isSet(): Boolean = LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error = null)

    override suspend fun authenticate(): DeviceLockResult {
        if (!isSet()) return DeviceLockResult.NotSet
        val reason = getString(Res.string.photos_unlock_reason)
        val context = LAContext()
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { context.invalidate() }
            context.evaluatePolicy(LAPolicyDeviceOwnerAuthentication, localizedReason = reason) { success, error ->
                val result = when {
                    success -> DeviceLockResult.Unlocked
                    error?.code == LAErrorPasscodeNotSet -> DeviceLockResult.NotSet
                    error?.code in CANCELLED -> DeviceLockResult.Cancelled
                    else -> DeviceLockResult.Failed
                }
                // The reply comes on a private queue.
                dispatch_async(dispatch_get_main_queue()) { if (cont.isActive) cont.resume(result) }
            }
        }
    }

    private companion object {
        val CANCELLED = setOf(LAErrorUserCancel, LAErrorSystemCancel, LAErrorAppCancel)
    }
}
