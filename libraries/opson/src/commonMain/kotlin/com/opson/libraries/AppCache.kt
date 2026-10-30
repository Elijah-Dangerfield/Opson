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

    /** Epoch-ms: when the review coordinator first saw this install, at boot. 0 = uncaptured. */
    val reviewInstallAt: Long = 0L,

    /** Epoch-ms: the last time the review coordinator asked the store for its rating sheet. 0 = never. */
    val lastReviewPromptAt: Long = 0L,

    /** The happy moment this install asks for a rating at (`ReviewMoment.wire`), "" until assigned. Device-scoped. */
    val reviewVariant: String = "",

    /** The `review.experiment` name [reviewVariant] was assigned under; a new name assigns again. */
    val reviewExperiment: String = "",

    /**
     * The Builder application whose decision this phone last showed (the Builders page or its
     * prompt on Today), so the news is told once. An id is one account's, so a user change needs
     * no reset.
     */
    val builderDecisionSeen: String? = null,

    /**
     * Apple Health or Health Connect has been connected on this account: Connect went through,
     * or the store already let Opson read. Only the choice is kept here; health values are never
     * stored. Account-scoped.
     */
    val healthConnected: Boolean = false,

    /**
     * The person tapped Disconnect. HealthKit has no way for an app to give permission back, so
     * this is what stops Opson reading until they connect again. Account-scoped.
     */
    val healthPaused: Boolean = false,

    /** Weigh-ins from the health store go into the weight log (off until the person turns it on). Account-scoped. */
    val healthWeighIns: Boolean = false,

    /**
     * The store's ids of weigh-ins already brought in, newest last and capped, so one deleted
     * from the log is never brought back. Ids only, never a weight. Account-scoped.
     */
    val healthImportedWeighIns: List<String> = emptyList(),

    /**
     * The last energy reading from the health store, so a cold start shows it at once instead of
     * waiting on the store. On this phone only, never synced; dropped on Disconnect and on a user
     * change. Account-scoped.
     */
    val healthEnergy: HealthEnergySnapshot? = null,

    /**
     * The last step counts read from the health store, by day, so Trends' Steps card and Today's
     * steps show at once on a cold start. On this phone only, never synced; dropped on Disconnect
     * and on a user change. Account-scoped.
     */
    val healthSteps: HealthStepsSnapshot? = null,

    /** The day ("2026-10-29") the person put away Today's happy birthday line, so it stays away that day. Account-scoped. */
    val birthdayThankedOn: String? = null,

    /**
     * The day ("2030-04-18") someone who was too young at onboarding's age question turns old
     * enough, so the gate stays closed on this phone until then rather than taking another
     * answer. Only the date the gate opens, never the birthday. Device-scoped: it outlives
     * sign-outs, and goes with the app.
     */
    val ageGateOpensOn: String? = null,

    /**
     * When "Why not cut back?" was opened (epoch ms, newest last, capped), for the hard-stretch
     * check on this phone. Never synced or sent. Account-scoped.
     */
    val cutBackLooks: List<Long> = emptyList(),
)

/** One reading of a day's energy, in kcal, and when it was read (epoch ms). [day] is ISO, `2026-10-29`. */
@Serializable
data class HealthEnergySnapshot(
    val day: String,
    val readAtMs: Long,
    val restingKcal: Double? = null,
    val activeKcal: Double? = null,
    val workoutKcal: Double? = null,
)

/**
 * Step counts by ISO day (`2026-10-29` to 8214), the latest [HealthStepsSnapshot.MAX_DAYS] days
 * read, and when the last read landed (epoch ms).
 */
@Serializable
data class HealthStepsSnapshot(
    val readAtMs: Long = 0L,
    val days: Map<String, Int> = emptyMap(),
) {
    companion object {
        /** Three months and a little: Trends' longest span. */
        const val MAX_DAYS = 100
    }
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
 * device-scoped setting (install id, feedback counters, review prompt times). Used
 * whenever the active user changes (account switch or sign-out / delete) so
 * the next account doesn't inherit the previous one's state.
 *
 * This is one `UserScopedClearer` in the dump the auth layer runs on a user
 * change: DB tables are wiped by `UserScopedDaoCleaner`, the profile caches by
 * `UserScopedProfileCacheCleaner`, and this covers the account-scoped fields
 * that live in [AppData]. Add any new account-scoped field here.
 */
fun AppData.resetAccountScoped(): AppData = copy(
    // A sign-out is a deliberate fresh start, so the next account is offered
    // onboarding rather than inheriting the previous user's completion.
    hasUserOnboarded = false,
    healthConnected = false,
    healthPaused = false,
    healthWeighIns = false,
    healthImportedWeighIns = emptyList(),
    healthEnergy = null,
    healthSteps = null,
    birthdayThankedOn = null,
    cutBackLooks = emptyList(),
)
