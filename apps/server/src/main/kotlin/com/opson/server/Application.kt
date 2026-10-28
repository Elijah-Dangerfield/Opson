package com.opson.server

import com.opson.server.config.AdminConfig
import com.opson.server.config.ServerConfig
import com.opson.server.config.WaitlistConfig
import com.opson.server.data.InMemoryExampleSource
import com.opson.server.data.WebhookConfigChangeNotifier
import com.opson.server.db.Database
import com.opson.server.di.ServerComponent
import com.opson.server.di.create
import com.opson.server.domain.ConfigChangeNotifier
import com.opson.server.plugins.BanGate
import com.opson.server.plugins.BearerVerifier
import com.opson.server.plugins.JwtVerification
import com.opson.server.plugins.installAdminWeb
import com.opson.server.plugins.installAuthentication
import com.opson.server.plugins.installBodyLimit
import com.opson.server.plugins.installCors
import com.opson.server.plugins.installHttpServerTracing
import com.opson.server.plugins.installObservability
import com.opson.server.plugins.installOpenTelemetry
import com.opson.server.plugins.installRateLimits
import com.opson.server.plugins.installSentry
import com.opson.server.plugins.installSerialization
import com.opson.server.plugins.installStatusPages
import com.opson.server.plugins.recordInstallSightings
import com.opson.server.routes.appConfigRoutes
import com.opson.server.routes.barcodeMissRoutes
import com.opson.server.routes.configAdminRoutes
import com.opson.server.routes.exampleRoutes
import com.opson.server.routes.exportRoutes
import com.opson.server.routes.foodRoutes
import com.opson.server.routes.healthRoutes
import com.opson.server.routes.meRoutes
import com.opson.server.routes.photoRoutes
import com.opson.server.routes.syncRoutes
import com.opson.server.routes.userAdminRoutes
import com.opson.server.routes.waitlistAdminRoutes
import com.opson.server.routes.waitlistRoutes
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import kotlin.time.Clock
import org.slf4j.LoggerFactory

/**
 * Single source of truth for how the app boots. Stays small on purpose — the
 * plugins/ and routes/ packages own their concerns and this wires them together
 * in the right order.
 *
 *  - [module] does production-only setup (observability, the DB connection),
 *    builds the DI graph, and picks the JWT verification strategy, then
 *    delegates to [installApp].
 *  - [installApp] installs the functional plugins + mounts every route. It is
 *    the seam reused by full-stack tests: a test builds a [ServerComponent]
 *    against a Testcontainers database and passes a [JwtVerification.Static]
 *    verifier to exercise the real plugins + routes + DB.
 *
 * Graceful degradation: with no `DATABASE_URL` the server runs in limited mode
 * (health + example); with no `SUPABASE_URL` the authenticated routes (`/v1/me`,
 * sync, foods and barcode misses, photo, export) aren't mounted and app config serves everyone
 * anonymously. Either way it boots — so you can clone and run with zero config.
 *
 * Order matters: serialization before status pages (so error envelopes encode),
 * auth after serialization (the 401 challenge writes a JSON body). CORS is
 * installed on the routing root, so a route can carry its own policy.
 */
fun Application.module(config: ServerConfig) {
    val logger = LoggerFactory.getLogger("Bootstrap")
    logger.info("Booting server on ${config.http.host}:${config.http.port}")

    // Production-only observability, kept out of [installApp] so tests don't pay
    // for it. Sentry first (so later boot failures are captured), then OTel + HTTP
    // tracing (so subsequent plugins' spans export), then request logging.
    installSentry(config.sentry)
    val openTelemetry = installOpenTelemetry(config.observability)
    installHttpServerTracing(openTelemetry)
    installObservability()

    val database = config.database?.let {
        Database.connect(it).also { logger.info("Database connected and migrations applied") }
    }
    if (database == null) {
        logger.warn("DATABASE_URL not set — limited mode (no DB-backed routes). See apps/server/README.md.")
    }

    val verification = config.supabase?.let { JwtVerification.Jwks(it.jwksUrl, it.expectedIssuer) }
    if (verification == null) {
        logger.warn(
            "SUPABASE_URL not set — authenticated routes (/v1/me, sync, foods and barcode misses, photo, export) are disabled.",
        )
    }

    logger.info("Features ({}): {}", config.observability.environment, config.featureSummary())
    config.foodSources.nutritionix?.let {
        logger.info("Nutritionix caching ${if (it.cacheResults) "on" else "off"}")
    }
    config.foodSources.anthropic?.let { logger.info("Photo estimates use ${it.photoModel}") }
    val component = database?.let { ServerComponent::class.create(it, config.supabase, config.foodSources, Clock.System) }
    // Ban gate needs the DB (moderation reads live in auth.users), so limited
    // mode simply runs without it — same null-safe degradation as everything
    // else here.
    val banGate = component?.let { BanGate(it.moderationRepository, config.accessControl.appealUrl, it.clock) }
    installApp(
        component = component,
        verification = verification,
        banGate = banGate,
        adminConfig = config.admin,
        configChangeNotifier = component?.let {
            WebhookConfigChangeNotifier(
                webhookUrl = config.configChange.webhookUrl,
                environment = config.observability.environment,
                scope = it.provideServerCoroutineScope(),
            )
        } ?: ConfigChangeNotifier {},
        waitlistConfig = config.waitlist,
    )

    // The hosted admin console (static bundle at /admin). Outside installApp so
    // integration tests don't need a bundle on disk.
    installAdminWeb(config.admin.webDir)
}

/**
 * Installs the functional plugins + every route. Shared by production [module]
 * and full-stack tests (which pass a real [component] + a [JwtVerification.Static]).
 *
 * [component] is null only in limited mode (no `DATABASE_URL`); [verification] is
 * null only when Supabase isn't configured. Health + the example resource are
 * always served.
 */
fun Application.installApp(
    component: ServerComponent?,
    verification: JwtVerification?,
    banGate: BanGate? = null,
    adminConfig: AdminConfig = AdminConfig(apiToken = null),
    configChangeNotifier: ConfigChangeNotifier = ConfigChangeNotifier {},
    waitlistConfig: WaitlistConfig = WaitlistConfig(),
) {
    installSerialization()
    installBodyLimit()
    installRateLimits()
    installStatusPages()
    val bearer = verification?.let(::BearerVerifier)
    if (bearer != null) installAuthentication(bearer, banGate)

    routing {
        installCors()
        healthRoutes()
        exampleRoutes(component?.exampleSource ?: InMemoryExampleSource())
        if (component != null) {
            recordInstallSightings(component.userInstallRepository, component.clock)
            // Pre-session too: it carries the kill switch and the version floor.
            appConfigRoutes(component.appConfigSource, bearer)
            // Admin API is inert without a token: requireAdmin 401s every call
            // when ADMIN_API_TOKEN is unset, so mounting is gated for clarity,
            // not security.
            if (!adminConfig.apiToken.isNullOrBlank()) {
                configAdminRoutes(
                    config = adminConfig,
                    repository = component.appConfigAdminRepository,
                    manifestRepository = component.appConfigManifestRepository,
                    notifier = configChangeNotifier,
                )
                waitlistAdminRoutes(adminConfig, component.waitlistRepository)
                userAdminRoutes(adminConfig, component.userLookupRepository)
            }
            // The website's form: public, its own CORS and rate limits.
            waitlistRoutes(component.waitlistRepository, component.waitlistLimiter, waitlistConfig.allowedOrigins)
        }
        if (component != null && verification != null) {
            meRoutes(
                component.profileRepository,
                component.supabaseAdminClient,
                component.appleTokenRepository,
                component.appleSignInClient,
            )
            syncRoutes(component.syncRepository, component.clock)
            foodRoutes(component.foodCatalogRepository, component.barcodeLookup, component.foodDescriber)
            photoRoutes(component.photoDescriber, component.photoEstimateLimiter, component.clock)
            barcodeMissRoutes(component.barcodeMissRepository)
            exportRoutes(component.exportRepository)
        }
    }
}
