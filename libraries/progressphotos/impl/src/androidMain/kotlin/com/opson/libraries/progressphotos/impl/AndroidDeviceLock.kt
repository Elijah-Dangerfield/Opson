package com.opson.libraries.progressphotos.impl

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.opson.libraries.opson.ActivityProvider
import com.opson.libraries.progressphotos.DeviceLock
import com.opson.libraries.progressphotos.DeviceLockResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.photos_unlock_reason
import org.jetbrains.compose.resources.getString
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.coroutines.resume
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The phone's lock, through the platform's own prompts so no extra library and
 * no FragmentActivity are needed. Android 10 and up: BiometricPrompt with the
 * device credential (PIN, pattern, password) as the fallback. Below that, the
 * keyguard's confirm-credential screen, which offers the fingerprint where the
 * phone has one. Opson never sees what was entered.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidDeviceLock @Inject constructor(
    private val context: Context,
    private val activityProvider: ActivityProvider,
) : DeviceLock {

    private val keyguard: KeyguardManager? get() = context.getSystemService(KeyguardManager::class.java)

    override fun isSet(): Boolean = keyguard?.isDeviceSecure == true

    override suspend fun authenticate(): DeviceLockResult {
        if (!isSet()) return DeviceLockResult.NotSet
        val reason = getString(Res.string.photos_unlock_reason)
        val activity = activityProvider.currentActivity() ?: return DeviceLockResult.Failed
        return withContext(Dispatchers.Main) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                prompt(activity, reason)
            } else {
                confirmCredential(activity as? ComponentActivity ?: return@withContext DeviceLockResult.Failed, reason)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun prompt(activity: Activity, reason: String): DeviceLockResult = suspendCancellableCoroutine { cont ->
        val builder = BiometricPrompt.Builder(activity).setTitle(reason)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        val cancel = CancellationSignal()
        cont.invokeOnCancellation { cancel.cancel() }
        builder.build().authenticate(
            cancel,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) cont.resume(DeviceLockResult.Unlocked)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (cont.isActive) cont.resume(errorCode.toResult())
                }
                // onAuthenticationFailed is one wrong finger; the prompt stays up for another try.
            },
        )
    }

    /** The unowned registry `register`, as Health does: this runs long after the activity started. */
    @OptIn(ExperimentalUuidApi::class)
    private suspend fun confirmCredential(activity: ComponentActivity, reason: String): DeviceLockResult {
        @Suppress("DEPRECATION")
        val intent = keyguard?.createConfirmDeviceCredentialIntent(reason, null) ?: return DeviceLockResult.NotSet
        return suspendCancellableCoroutine { cont ->
            lateinit var launcher: ActivityResultLauncher<android.content.Intent>
            launcher = activity.activityResultRegistry.register(
                "opson.progressphotos.${Uuid.random()}",
                ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                launcher.unregister()
                if (cont.isActive) cont.resume(if (result.resultCode == Activity.RESULT_OK) DeviceLockResult.Unlocked else DeviceLockResult.Cancelled)
            }
            cont.invokeOnCancellation { activity.runOnUiThread { launcher.unregister() } }
            launcher.launch(intent)
        }
    }

    private fun Int.toResult(): DeviceLockResult = when (this) {
        BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED,
        BiometricPrompt.BIOMETRIC_ERROR_CANCELED,
        -> DeviceLockResult.Cancelled
        BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL -> DeviceLockResult.NotSet
        else -> DeviceLockResult.Failed
    }
}
