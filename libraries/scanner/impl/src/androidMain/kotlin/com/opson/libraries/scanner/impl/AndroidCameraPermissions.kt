package com.opson.libraries.scanner.impl

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.opson.libraries.core.Catching
import com.opson.libraries.opson.ActivityProvider
import com.opson.libraries.scanner.CameraPermission
import com.opson.libraries.scanner.CameraPermissions
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Android cannot tell "never asked" from "denied for good" without asking:
 * both answer false to `shouldShowRequestPermissionRationale`. So both read as
 * [CameraPermission.NotAsked], the scanner asks, and a permanent denial comes
 * straight back from the prompt without showing it, as
 * `Denied(canAskAgain = false)`. A single earlier denial reads as
 * `Denied(canAskAgain = true)`, so the screen offers to ask again rather than
 * asking on its own.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidCameraPermissions @Inject constructor(
    private val context: Context,
    private val activityProvider: ActivityProvider,
) : CameraPermissions {

    override fun current(): CameraPermission = when {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED ->
            CameraPermission.Granted
        activityProvider.currentActivity()?.canAskAgain() == true -> CameraPermission.Denied(canAskAgain = true)
        else -> CameraPermission.NotAsked
    }

    override fun openSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        Catching { context.startActivity(intent) }
    }
}

internal fun Activity.canAskAgain(): Boolean =
    ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)
