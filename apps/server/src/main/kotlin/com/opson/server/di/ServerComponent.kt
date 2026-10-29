package com.opson.server.di

import com.opson.server.config.DeviceIntegrityConfig
import com.opson.server.config.FoodSourcesConfig
import com.opson.server.config.SupabaseConfig
import com.opson.server.data.LabelParseCache
import com.opson.server.data.PhotoDescriber
import com.opson.server.db.Database
import com.opson.server.domain.AccessAuditRepository
import com.opson.server.domain.AppConfigAdminRepository
import com.opson.server.domain.AppConfigManifestRepository
import com.opson.server.domain.AppConfigSource
import com.opson.server.domain.AppleSignInClient
import com.opson.server.domain.AppleTokenRepository
import com.opson.server.domain.BarcodeLinkRepository
import com.opson.server.domain.CorrectionAuthority
import com.opson.server.domain.FoodReportLimiter
import com.opson.server.domain.FoodReportRepository
import com.opson.server.domain.BarcodeMissRepository
import com.opson.server.domain.BarcodeLookup
import com.opson.server.domain.EntitlementGrantRepository
import com.opson.server.domain.EntitlementService
import com.opson.server.domain.ExportRepository
import com.opson.server.domain.FoodCatalogRepository
import com.opson.server.domain.FoodDescriber
import com.opson.server.domain.FoodLabelLimiter
import com.opson.server.domain.FoodSubmissionRepository
import com.opson.server.domain.LabelParser
import com.opson.server.domain.ExampleSource
import com.opson.server.domain.ContributionGate
import com.opson.server.domain.DeviceIntegrity
import com.opson.server.domain.ModerationActions
import com.opson.server.domain.ModerationRepository
import com.opson.server.domain.ModerationService
import com.opson.server.domain.Permissions
import com.opson.server.domain.PhotoEstimateLimiter
import com.opson.server.domain.ProfileRepository
import com.opson.server.domain.SupabaseAdminClient
import com.opson.server.domain.SyncRepository
import com.opson.server.domain.UserInstallRepository
import com.opson.server.domain.UserLookupRepository
import com.opson.server.domain.UserRoleRepository
import com.opson.server.domain.WaitlistLimiter
import com.opson.server.domain.WaitlistRepository
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
 * val component = ServerComponent::class.create(database, supabaseConfig, foodSourcesConfig, Clock.System)
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
    /** Which outside food sources a barcode miss may ask (default: Open Food Facts only), and the photo estimate provider. */
    @get:Provides val foodSourcesConfig: FoodSourcesConfig = FoodSourcesConfig(),
    /**
     * Wall-clock source, one for the whole graph so every component sees the
     * same "now". Production never passes it; the end-to-end suite does, to
     * step past time-based caches without waiting.
     */
    @get:Provides val clock: Clock = Clock.System,
    /** DeviceCheck and Play Integrity keys; both null (inert) unless their secrets are set. */
    @get:Provides val deviceIntegrityConfig: DeviceIntegrityConfig = DeviceIntegrityConfig(),
) {
    abstract val exampleSource: ExampleSource
    abstract val profileRepository: ProfileRepository
    abstract val moderationRepository: ModerationRepository
    abstract val supabaseAdminClient: SupabaseAdminClient
    abstract val appleSignInClient: AppleSignInClient
    abstract val appleTokenRepository: AppleTokenRepository
    abstract val appConfigSource: AppConfigSource
    abstract val appConfigAdminRepository: AppConfigAdminRepository
    abstract val appConfigManifestRepository: AppConfigManifestRepository
    abstract val syncRepository: SyncRepository
    abstract val foodCatalogRepository: FoodCatalogRepository
    abstract val barcodeLookup: BarcodeLookup
    abstract val barcodeMissRepository: BarcodeMissRepository
    abstract val foodDescriber: FoodDescriber
    abstract val photoDescriber: PhotoDescriber
    abstract val photoEstimateLimiter: PhotoEstimateLimiter
    abstract val exportRepository: ExportRepository
    abstract val waitlistRepository: WaitlistRepository
    abstract val waitlistLimiter: WaitlistLimiter
    abstract val userInstallRepository: UserInstallRepository
    abstract val userLookupRepository: UserLookupRepository
    abstract val labelParser: LabelParser
    abstract val labelParseCache: LabelParseCache
    abstract val foodLabelLimiter: FoodLabelLimiter
    abstract val foodSubmissionRepository: FoodSubmissionRepository
    abstract val barcodeLinkRepository: BarcodeLinkRepository
    abstract val foodReportRepository: FoodReportRepository
    abstract val foodReportLimiter: FoodReportLimiter
    abstract val correctionAuthority: CorrectionAuthority

    /** The Builder program: membership, trust, routing and checks against the pack. See builder-program.md. */
    abstract val builderRepository: com.opson.server.domain.BuilderRepository
    abstract val builderService: com.opson.server.domain.BuilderService
    abstract val contributionRouter: com.opson.server.domain.ContributionRouter
    abstract val foodVerificationRepository: com.opson.server.domain.FoodVerificationRepository

    /** Roles, grants and their resolution. See "Roles and entitlements" in apps/server/README.md. */
    abstract val userRoleRepository: UserRoleRepository
    abstract val permissions: Permissions
    abstract val entitlementGrantRepository: EntitlementGrantRepository
    abstract val entitlementService: EntitlementService
    abstract val accessAuditRepository: AccessAuditRepository

    /** Bans, restrictions, provenance and device markers. See "Moderation" in apps/server/README.md. */
    abstract val moderationActions: ModerationActions
    abstract val moderationService: ModerationService
    abstract val contributionGate: ContributionGate
    abstract val deviceIntegrity: DeviceIntegrity

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
