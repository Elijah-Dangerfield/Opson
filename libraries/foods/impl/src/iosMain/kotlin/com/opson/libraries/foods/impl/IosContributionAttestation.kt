package com.opson.libraries.foods.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.foods.ContributionAttestation
import com.opson.libraries.foods.IosDeviceSignals
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

/**
 * The Keychain install key always, and a DeviceCheck token once [DeviceCheckEnabled] says the
 * server can read one. A token that takes too long is skipped rather than holding up the request.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class IosContributionAttestation(
    private val signals: IosDeviceSignals,
    private val deviceCheckEnabled: DeviceCheckEnabled,
) : ContributionAttestation {

    override suspend fun headers(requestId: String): Map<String, String> = buildMap {
        Catching { signals.keychainInstallId() }.getOrNull()?.let { put(ContributionAttestation.HEADER_DEVICE_KEY, it) }
        if (deviceCheckEnabled()) deviceCheckToken()?.let { put(ContributionAttestation.HEADER_INTEGRITY_TOKEN, it) }
    }

    private suspend fun deviceCheckToken(): String? = withTimeoutOrNull(TOKEN_TIMEOUT) {
        suspendCancellableCoroutine { continuation ->
            signals.deviceCheckToken { token -> if (continuation.isActive) continuation.resume(token) }
        }
    }

    private companion object {
        val TOKEN_TIMEOUT = 5.seconds
    }
}
