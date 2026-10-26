package com.opson.libraries.scanner.impl

import com.opson.libraries.scanner.CameraPermission
import com.opson.libraries.scanner.CameraPermissions
import me.tatarka.inject.annotations.Inject
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/** iOS shows its camera prompt once; after any answer but yes, only Settings can change it. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosCameraPermissions @Inject constructor() : CameraPermissions {

    override fun current(): CameraPermission = when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
        AVAuthorizationStatusAuthorized -> CameraPermission.Granted
        AVAuthorizationStatusNotDetermined -> CameraPermission.NotAsked
        // Denied, or Restricted (parental controls, device management): not ours to ask again.
        else -> CameraPermission.Denied(canAskAgain = false)
    }

    override fun openSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }
}
