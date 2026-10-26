package com.opson.libraries.opson

import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.versionedJsonSerializer
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * In-memory + persistent cache for app-wide state that doesn't need to be in the database.
 */
@Serializable
data class AppData(
    // Onboarding
    val hasUserOnboarded: Boolean = false,

    /**
     * Stable per-install identifier, minted on first read and persisted for
     * the app's lifetime on this device (survives sign-out; dies with
     * uninstall). Sent as X-Install-Id on authenticated requests so the
     * server can associate anonymous accounts from the same install.
     * Stored as a string (UUID canonical form) so the JSON serializer
     * doesn't need a Uuid-aware adapter on every cache read.
     */
    val installId: String? = null,

    // Screen visits - automatically tracked for any TrackableRoute
    val screenVisits: Map<String, Int> = emptyMap(),

    // User actions
    val feedbacksGiven: Int = 0,
    val bugsReported: Int = 0,

    /** Epoch-ms — first observed by the review coordinator. 0 = uncaptured. */
    val reviewInstallAt: Long = 0L,

    /** Epoch-ms — last review prompt the coordinator forwarded to the platform. 0 = never. */
    val lastReviewPromptAt: Long = 0L,

    /**
     * The person tapped Connect for Apple Health or Health Connect and it went through. Only
     * the choice is kept here; health values are never stored. Account-scoped, so the next
     * account on this phone starts disconnected.
     */
    val healthConnected: Boolean = false,
) {
    /**
     * Get the visit count for a screen by its tracking key.
     */
    fun getVisitCount(trackingKey: String): Int = screenVisits[trackingKey] ?: 0
    
    /**
     * Increment the visit count for a screen.
     */
    fun incrementVisit(trackingKey: String): AppData = copy(
        screenVisits = screenVisits + (trackingKey to (getVisitCount(trackingKey) + 1))
    )
}

interface AppCache : Cache<AppData>

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = AppCache::class)
@Inject
class AppCacheImpl(
    cacheFactory: CacheFactory
) : AppCache, Cache<AppData> by cacheFactory.persistent(
    name = "app_data",
    serializer = versionedJsonSerializer(
        defaultValue = { AppData() },
    )
)
/**
 * Reset the **account-scoped** fields back to defaults while preserving every
 * device-scoped setting (install id, screen visits, feedback counters…). Used
 * whenever the active user changes (account switch or sign-out / delete) so
 * the next account doesn't inherit the previous one's state.
 *
 * This is one `UserScopedClearer` in the dump the auth layer runs on a user
 * change: DB tables are wiped by `UserScopedDaoCleaner`, the profile caches by
 * `UserScopedProfileCacheCleaner`, and this covers the account-scoped fields
 * that live in [AppData]. Add any new account-scoped field here.
 */
fun AppData.resetAccountScoped(): AppData = copy(
    // A full sign-out → continue-as-guest is a deliberate fresh start, so the
    // next identity is re-offered onboarding rather than inheriting the
    // previous user's completion.
    hasUserOnboarded = false,
    healthConnected = false,
)
