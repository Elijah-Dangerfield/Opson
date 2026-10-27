package com.opson.libraries.identity.impl.auth

import com.opson.libraries.core.Catching
import com.opson.libraries.opson.AppCache
import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.versionedJsonSerializer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Tells a new install apart from a relaunch, for [SecureSessionManager].
 *
 * iOS keeps Keychain items when the app is deleted, so a reinstall reads the
 * previous install's session while everything else on disk is gone. That is
 * how a fresh TestFlight install skipped onboarding and opened on Today.
 */
interface FreshInstall {
    /**
     * True on the first call in an install that has neither touched session
     * storage nor finished onboarding, and false on every call after that.
     */
    suspend fun claim(): Boolean
}

/**
 * The marker lives in the app's ordinary files, which die with the app, unlike
 * the Keychain.
 *
 * An install that already finished onboarding is never fresh, even with no
 * marker yet: that is everyone updating from a build that predates the marker,
 * and their session must survive the update.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class FreshInstallStore(
    cacheFactory: CacheFactory,
    private val appCache: AppCache,
) : FreshInstall {

    private val mutex = Mutex()

    private val cache: Cache<Marker> = cacheFactory.persistent(
        name = "session_install_marker",
        serializer = versionedJsonSerializer(defaultValue = { Marker() }),
    )

    override suspend fun claim(): Boolean = mutex.withLock {
        if (cache.get().seen) return@withLock false
        val onboarded = Catching { appCache.get().hasUserOnboarded }.getOrNull() ?: true
        cache.set(Marker(seen = true))
        !onboarded
    }

    @Serializable
    internal data class Marker(val seen: Boolean = false)
}
