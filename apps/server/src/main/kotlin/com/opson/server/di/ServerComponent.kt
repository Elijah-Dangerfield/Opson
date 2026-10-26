package com.opson.server.di

import com.opson.server.config.FoodSourcesConfig
import com.opson.server.config.SupabaseConfig
import com.opson.server.db.Database
import com.opson.server.domain.AppConfigAdminRepository
import com.opson.server.domain.AppConfigManifestRepository
import com.opson.server.domain.AppConfigSource
import com.opson.server.domain.BarcodeLookup
import com.opson.server.domain.ExportRepository
import com.opson.server.domain.FoodCatalogRepository
import com.opson.server.domain.FoodDescriber
import com.opson.server.domain.ExampleSource
import com.opson.server.domain.ModerationRepository
import com.opson.server.domain.PlayerReportRepository
import com.opson.server.domain.ProfileRepository
import com.opson.server.domain.SupabaseAdminClient
import com.opson.server.domain.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Root DI component for the server process. Mirrors the client's `AppComponent`
 * — anvil aggregates every `@ContributesBinding(ServerScope::class)` on the
 * classpath and KSP generates the merged implementation at compile time.
 *
 * Boot once at startup with the runtime [Database]:
 *
 * ```
 * val component = ServerComponent::class.create(database)
 * routing { meRoutes(component.profileRepository) }
 * ```
 *
 * Add a new service: annotate its impl with `@ContributesBinding(ServerScope::class)`,
 * expose its interface as an `abstract val` here. Done.
 *
 * Runtime inputs (the [Database], later parsed config) come in as constructor
 * params exposed with `@get:Provides` so impls can take them as ordinary
 * constructor parameters without a separate factory.
 */
@MergeComponent(ServerScope::class)
@SingleIn(ServerScope::class)
@OptIn(ExperimentalTime::class)
abstract class ServerComponent(
    @get:Provides val database: Database,
    /**
     * Null when Supabase isn't configured — consumers degrade (the admin
     * client answers NotConfigured) instead of failing at construction.
     */
    @get:Provides val supabaseConfig: SupabaseConfig? = null,
    /** Which outside food sources a barcode miss may ask. Default: Open Food Facts only. */
    @get:Provides val foodSourcesConfig: FoodSourcesConfig = FoodSourcesConfig(),
) {
    abstract val exampleSource: ExampleSource
    abstract val profileRepository: ProfileRepository
    abstract val moderationRepository: ModerationRepository
    abstract val playerReportRepository: PlayerReportRepository
    abstract val supabaseAdminClient: SupabaseAdminClient
    abstract val appConfigSource: AppConfigSource
    abstract val appConfigAdminRepository: AppConfigAdminRepository
    abstract val appConfigManifestRepository: AppConfigManifestRepository
    abstract val syncRepository: SyncRepository
    abstract val foodCatalogRepository: FoodCatalogRepository
    abstract val barcodeLookup: BarcodeLookup
    abstract val foodDescriber: FoodDescriber
    abstract val exportRepository: ExportRepository
    abstract val clock: Clock

    /**
     * Wall-clock source. Singleton so every component sees the same "now".
     * Tests pass a fixed clock to the class under test directly.
     */
    @Provides
    fun provideClock(): Clock = Clock.System

    /**
     * Long-lived application scope for server-owned background work (e.g. the
     * fire-and-forget config-change webhook). SupervisorJob so one failure
     * doesn't cascade. Singleton — the process owns exactly one and never
     * cancels it (it dies with the process).
     *
     * **If you trace work launched here, root a new trace for it.** A coroutine
     * that finishes with an OTel context installed can leave it on the pool
     * thread, and the next unrelated job scheduled there inherits it as parent.
     * `withSpan` parents to whatever is current, so the result is unrelated
     * background work stitched into someone else's trace, unbounded. See the
     * KDoc on `withSpan` in `plugins/Tracing.kt` for the full failure mode.
     */
    @Provides
    @SingleIn(ServerScope::class)
    fun provideServerCoroutineScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
