package com.opson.libraries.foods.impl

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import com.opson.libraries.core.Catching
import com.opson.libraries.foods.ContributionAttestation
import com.opson.libraries.networking.InstallIdProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

/**
 * The install id as the install key, and a Play Integrity standard token bound to the request
 * once [PlayIntegrityCloudProject] names the Cloud project. The token provider is prepared once
 * per project (Google's advice: prepare ahead, request per action) and the request hash is the
 * contribution's id, so a token can't be lifted from one request onto another. Any failure
 * (no Play Store, a device Google won't vouch for, a slow answer) sends no token; the server
 * never blocks on its absence.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class PlayIntegrityAttestation(
    private val context: Context,
    private val installIds: InstallIdProvider,
    private val cloudProject: PlayIntegrityCloudProject,
) : ContributionAttestation {

    private val lock = Mutex()
    private var prepared: Pair<Long, StandardIntegrityTokenProvider>? = null

    override suspend fun headers(requestId: String): Map<String, String> = buildMap {
        Catching { installIds.current() }.getOrNull()?.let { put(ContributionAttestation.HEADER_DEVICE_KEY, it) }
        val project = cloudProject()
        if (project > 0) token(project, requestId)?.let { put(ContributionAttestation.HEADER_INTEGRITY_TOKEN, it) }
    }

    private suspend fun token(project: Long, requestId: String): String? = withTimeoutOrNull(TOKEN_TIMEOUT) {
        Catching {
            val provider = provider(project)
            provider.request(StandardIntegrityTokenRequest.builder().setRequestHash(requestId).build()).await().token()
        }.getOrNull()
    }

    private suspend fun provider(project: Long): StandardIntegrityTokenProvider = lock.withLock {
        prepared?.takeIf { it.first == project }?.let { return@withLock it.second }
        val manager = IntegrityManagerFactory.createStandard(context.applicationContext)
        val provider = manager.prepareIntegrityToken(PrepareIntegrityTokenRequest.builder().setCloudProjectNumber(project).build()).await()
        prepared = project to provider
        provider
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
    }

    private companion object {
        val TOKEN_TIMEOUT = 5.seconds
    }
}
