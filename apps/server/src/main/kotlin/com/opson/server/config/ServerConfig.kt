package com.opson.server.config

/**
 * Root container for everything the server reads from the environment.
 *
 * Parsed once at startup from [Env] and then passed around as plain values.
 * Code that depends on config takes the specific sub-config it needs, not the
 * whole tree — keeps test wiring honest.
 *
 * Convention for adding a field (the whole point of this file):
 *  - boot-critical (server can't run without it) → `env.require("KEY")` — fail fast.
 *  - optional with a sensible default            → `env.int("KEY", default)` / `env["KEY"] ?: default`.
 *  - optional, feature degrades when absent      → nullable `env["KEY"]`, branch at the call site.
 *
 * [database] is nullable on purpose: with no `DATABASE_URL` the server still
 * boots (health + the example resource) so you can clone-and-run with zero
 * config. See apps/server/README.md → Quick start.
 */
data class ServerConfig(
    val http: HttpConfig,
    val database: DatabaseConfig?,
    val supabase: SupabaseConfig?,
    val sentry: SentryConfig,
    val observability: ObservabilityConfig,
    val accessControl: AccessControlConfig,
    val admin: AdminConfig,
    val configChange: ConfigChangeConfig,
    val foodSources: FoodSourcesConfig = FoodSourcesConfig(),
    val waitlist: WaitlistConfig = WaitlistConfig(),
) {
    /**
     * Every optional feature, on or off, in one line for the boot log, named by
     * what it does rather than the variable behind it. A feature that is off on
     * prod by accident (no Apple key, so deletion can't revoke the Apple token;
     * no Sentry DSN) shows up here instead of only when someone needs it.
     */
    fun featureSummary(): String = listOf(
        "database" to (database != null),
        "auth" to (supabase != null),
        "accountDeletion" to (supabase?.serviceRoleKey != null),
        "appleRevoke" to (supabase?.appleSignIn != null),
        "sentry" to (sentry.dsn != null),
        "otlp" to (observability.otlpEndpoint != null),
        "admin" to !admin.apiToken.isNullOrBlank(),
        "configWebhook" to (configChange.webhookUrl != null),
        "photo" to (foodSources.anthropic != null),
        "nutritionix" to (foodSources.nutritionix != null),
        "offSearch" to foodSources.searchOpenFoodFacts,
    ).joinToString(" ") { (name, on) -> "$name=${if (on) "on" else "off"}" }

    companion object {
        fun fromEnv(env: Env = Env()): ServerConfig = ServerConfig(
            http = HttpConfig.fromEnv(env),
            database = DatabaseConfig.fromEnv(env),
            supabase = SupabaseConfig.fromEnv(env),
            sentry = SentryConfig.fromEnv(env),
            observability = ObservabilityConfig.fromEnv(env),
            accessControl = AccessControlConfig.fromEnv(env),
            admin = AdminConfig.fromEnv(env),
            configChange = ConfigChangeConfig.fromEnv(env),
            foodSources = FoodSourcesConfig.fromEnv(env),
            waitlist = WaitlistConfig.fromEnv(env),
        )
    }
}

/**
 * The website's waitlist form (`POST /v1/waitlist`). [allowedOrigins] are the
 * exact browser origins that route answers CORS for, from
 * `WAITLIST_ALLOWED_ORIGINS` (comma-separated, `scheme://host[:port]`). No
 * other route uses them.
 *
 * Unset on dev (and locally), it defaults to [DEV_ORIGINS]: the Astro dev
 * server and the GitHub Pages site. Unset on prod, no browser origin is
 * allowed, so the site's form fails until someone sets it on purpose.
 */
data class WaitlistConfig(
    val allowedOrigins: Set<String> = emptySet(),
) {
    companion object {
        val DEV_ORIGINS = setOf("http://localhost:4321", "https://elijah-dangerfield.github.io")

        fun fromEnv(env: Env): WaitlistConfig {
            val raw = env["WAITLIST_ALLOWED_ORIGINS"]
            val isProd = SentryConfig.environmentFromFlyAppName(env) == "prod"
            return WaitlistConfig(
                allowedOrigins = when {
                    raw != null -> parseOrigins(raw)
                    isProd -> emptySet()
                    else -> DEV_ORIGINS
                },
            )
        }

        /** Trims, lower-cases and drops a trailing slash; anything that is not `http(s)://host[:port]` is left out. */
        internal fun parseOrigins(raw: String): Set<String> =
            raw.split(',')
                .map { it.trim().trimEnd('/').lowercase() }
                .filter { ORIGIN.matches(it) }
                .toSet()

        private val ORIGIN = Regex("^https?://[a-z0-9.-]+(:[0-9]{1,5})?$")
    }
}

/**
 * Outside food sources a barcode miss can be sent to (Open Food Facts is
 * always on and needs no config). Each paid source is null, and so never
 * called, until its keys are set.
 *
 * [searchOpenFoodFacts] adds the `off_foods` table (the full Open Food Facts
 * export, V12) to name search. Off unless `FOOD_SEARCH_OFF_ENABLED=true`, so
 * search answers as it did in 0.1 until someone turns it on, after the table
 * is loaded and the app credits Open Food Facts where its foods appear.
 */
data class FoodSourcesConfig(
    val nutritionix: NutritionixConfig? = null,
    val searchOpenFoodFacts: Boolean = false,
    /** The model that reads meal photos. Null (photo estimates off) until `ANTHROPIC_API_KEY` is set. */
    val anthropic: AnthropicConfig? = null,
    /**
     * Where Open Food Facts lives. Never set from the environment; the end-to-end
     * tests point it at a stand-in so a barcode miss does not leave the machine.
     */
    val openFoodFactsUrl: String = OPEN_FOOD_FACTS_URL,
    /**
     * How many search answers the server keeps in memory
     * (`FOOD_SEARCH_CACHE_ENTRIES`, default [DEFAULT_SEARCH_CACHE_ENTRIES];
     * 0 turns it off), each for [searchCacheSeconds]
     * (`FOOD_SEARCH_CACHE_SECONDS`). Off unless set here, so a test that
     * writes a food and searches again sees it.
     */
    val searchCacheEntries: Int = 0,
    val searchCacheSeconds: Long = DEFAULT_SEARCH_CACHE_SECONDS,
) {
    companion object {
        fun fromEnv(env: Env): FoodSourcesConfig = FoodSourcesConfig(
            nutritionix = NutritionixConfig.fromEnv(env),
            searchOpenFoodFacts = env["FOOD_SEARCH_OFF_ENABLED"]?.trim()?.lowercase() == "true",
            anthropic = AnthropicConfig.fromEnv(env),
            searchCacheEntries = env.int("FOOD_SEARCH_CACHE_ENTRIES", default = DEFAULT_SEARCH_CACHE_ENTRIES),
            searchCacheSeconds = env.long("FOOD_SEARCH_CACHE_SECONDS", default = DEFAULT_SEARCH_CACHE_SECONDS),
        )

        const val OPEN_FOOD_FACTS_URL = "https://world.openfoodfacts.org"

        /** A page of 25 foods with their micros is up to about 60 KB on the heap: about 15 MB of the 256 MB at most. */
        const val DEFAULT_SEARCH_CACHE_ENTRIES = 256
        const val DEFAULT_SEARCH_CACHE_SECONDS = 300L
    }
}

/**
 * The Anthropic API, for photo estimates (`POST /v1/describe/photo`) and
 * reading pack labels (`POST /v1/foods/labels/parse`). Null until
 * `ANTHROPIC_API_KEY` is set, and both routes then answer 503, which the app
 * shows as a calm "not available right now". [photoModel] is
 * `ANTHROPIC_PHOTO_MODEL` and [labelModel] `ANTHROPIC_LABEL_MODEL`, so either
 * model can change without a release (see `AnthropicEstimateProvider` and
 * `AnthropicLabelParser` for the defaults and why). Treat the key like a
 * password: never log it.
 */
data class AnthropicConfig(
    val apiKey: String,
    val photoModel: String = DEFAULT_PHOTO_MODEL,
    val labelModel: String = DEFAULT_LABEL_MODEL,
    /**
     * Null for Anthropic's own API. Never set from the environment; the
     * end-to-end tests point it at a stand-in that answers like the Messages API.
     */
    val baseUrl: String? = null,
) {
    override fun toString(): String =
        "AnthropicConfig(apiKey=***, photoModel=$photoModel, labelModel=$labelModel, baseUrl=$baseUrl)"

    companion object {
        const val DEFAULT_PHOTO_MODEL = "claude-opus-5-5"
        const val DEFAULT_LABEL_MODEL = "claude-fable-5-1"

        fun fromEnv(env: Env): AnthropicConfig? {
            val key = env["ANTHROPIC_API_KEY"] ?: return null
            return AnthropicConfig(
                apiKey = key,
                photoModel = env["ANTHROPIC_PHOTO_MODEL"]?.trim() ?: DEFAULT_PHOTO_MODEL,
                labelModel = env["ANTHROPIC_LABEL_MODEL"]?.trim()?.ifEmpty { null } ?: DEFAULT_LABEL_MODEL,
            )
        }
    }
}

/**
 * Nutritionix API credentials. Both keys or nothing: one without the other is
 * treated as unset. [cacheResults] defaults to false because Nutritionix's
 * terms limit keeping their data; see `NutritionixSource` before turning it on.
 * Treat the key like a password: never log it.
 */
data class NutritionixConfig(
    val appId: String,
    val appKey: String,
    val cacheResults: Boolean = false,
) {
    override fun toString(): String = "NutritionixConfig(appId=$appId, appKey=***, cacheResults=$cacheResults)"

    companion object {
        fun fromEnv(env: Env): NutritionixConfig? {
            val appId = env["NUTRITIONIX_APP_ID"] ?: return null
            val appKey = env["NUTRITIONIX_APP_KEY"] ?: return null
            return NutritionixConfig(
                appId = appId,
                appKey = appKey,
                cacheResults = env["NUTRITIONIX_CACHE_RESULTS"]?.trim()?.lowercase() == "true",
            )
        }
    }
}

/**
 * Token-gated admin endpoints (the config admin API under `/v1/admin/config`).
 * The caller is a machine or the admin console, not a Supabase user, so these
 * routes take an `X-Admin-Token` header instead of a JWT. With [apiToken]
 * unset the admin routes aren't mounted — the server runs with remote config
 * read-only.
 */
data class AdminConfig(
    val apiToken: String?,
    /**
     * Directory holding the prebuilt admin console bundle served at `/admin`.
     * The default matches the repo layout for local `:apps:server:run`
     * (workingDir = rootDir); the Docker image overrides it. Missing dir →
     * `/admin` simply isn't served.
     */
    val webDir: String = "apps/server/admin-web",
) {
    companion object {
        fun fromEnv(env: Env): AdminConfig = AdminConfig(
            apiToken = env["ADMIN_API_TOKEN"],
            webDir = env["ADMIN_WEB_DIR"] ?: "apps/server/admin-web",
        )
    }
}

/**
 * Where to announce config changes. [webhookUrl] is a Slack-compatible incoming
 * webhook; null/blank disables notifications (the default). Set
 * `CONFIG_CHANGE_WEBHOOK_URL` to turn it on.
 */
data class ConfigChangeConfig(
    val webhookUrl: String?,
) {
    companion object {
        fun fromEnv(env: Env): ConfigChangeConfig = ConfigChangeConfig(
            webhookUrl = env["CONFIG_CHANGE_WEBHOOK_URL"]?.takeIf { it.isNotBlank() },
        )
    }
}

/**
 * Settings for the account-access gate that blocks banned users on every
 * authenticated route.
 *
 * [appealUrl] is handed to a blocked client verbatim in the `403` body so the
 * app can offer an "appeal this" link. Null when unset — the client then shows
 * the block without an appeal affordance, which is fine for V1 (the gate still
 * works, the user just has no in-app appeal path). Set `APPEAL_URL` once a
 * support/appeal page exists.
 */
data class AccessControlConfig(
    val appealUrl: String?,
) {
    companion object {
        fun fromEnv(env: Env): AccessControlConfig = AccessControlConfig(
            appealUrl = env["APPEAL_URL"]?.takeIf { it.isNotBlank() },
        )
    }
}

/**
 * Server-side Sentry. No-op unless [dsn] is set, so the default build reports
 * nothing until you wire a project. [environment]/[release] tag every event.
 */
data class SentryConfig(
    val dsn: String?,
    val environment: String,
    val release: String?,
) {
    companion object {
        fun fromEnv(env: Env): SentryConfig = SentryConfig(
            dsn = env["SENTRY_DSN"],
            // Fly sets FLY_APP_NAME; the convention is a `-prod`-suffixed prod
            // app beside the dev one (see fly.prod.toml), so any app name
            // ending in `-prod` → prod, anything else (dev app, local) → dev.
            // Override with SENTRY_ENVIRONMENT when needed.
            environment = env["SENTRY_ENVIRONMENT"] ?: environmentFromFlyAppName(env),
            release = env["SENTRY_RELEASE"],
        )

        internal fun environmentFromFlyAppName(env: Env): String =
            if (env["FLY_APP_NAME"]?.endsWith("-prod") == true) "prod" else "dev"
    }
}

/**
 * OpenTelemetry exporter wiring. With [otlpEndpoint] unset, the SDK still boots
 * but exports to stdout (spans/logs/metrics show in your logs). Set it (standard
 * env name, so vendor tooling drops in) to ship OTLP/HTTP instead.
 */
data class ObservabilityConfig(
    val otlpEndpoint: String?,
    val otlpHeaders: String?,
    val serviceName: String,
    val environment: String,
    val release: String?,
) {
    companion object {
        fun fromEnv(env: Env): ObservabilityConfig = ObservabilityConfig(
            otlpEndpoint = env["OTEL_EXPORTER_OTLP_ENDPOINT"],
            otlpHeaders = env["OTEL_EXPORTER_OTLP_HEADERS"],
            serviceName = env["OTEL_SERVICE_NAME"] ?: "opson-server",
            // Match Sentry's environment derivation so a single env tag groups
            // traces + errors consistently.
            environment = env["OTEL_DEPLOYMENT_ENVIRONMENT"]
                ?: env["SENTRY_ENVIRONMENT"]
                ?: SentryConfig.environmentFromFlyAppName(env),
            release = env["OTEL_SERVICE_VERSION"] ?: env["SENTRY_RELEASE"],
        )
    }
}

/**
 * Settings for verifying Supabase-issued JWTs.
 *
 * Supabase signs JWTs with ES256 (asymmetric). The server fetches the project's
 * public keys from `<projectUrl>/auth/v1/.well-known/jwks.json` at runtime and
 * verifies signatures against them — no shared secret lives on the server.
 *
 * Nullable: with no `SUPABASE_URL` the server boots without authenticated routes
 * (`/v1/me`, sync, foods, photo and export aren't mounted). Set it to enable auth.
 *
 * [serviceRoleKey] is the project's service_role JWT, required for Admin API
 * calls (e.g. `DELETE /auth/v1/admin/users/<id>` for account deletion).
 * Optional because most routes don't need it — endpoints that do (see
 * `DELETE /v1/me`) respond 503 NotConfigured when it isn't set, so
 * non-deletion deployments stay functional. Treat as a root password: never
 * log it, never return it from any endpoint.
 */
data class SupabaseConfig(
    /** e.g. `https://abcdefgh.supabase.co`. */
    val projectUrl: String,
    val serviceRoleKey: String? = null,
    /** Lets account deletion revoke the person's Sign in with Apple token. Null skips it. */
    val appleSignIn: AppleSignInConfig? = null,
) {
    /** Issuer the Auth service stamps on every JWT it issues. */
    val expectedIssuer: String get() = "$projectUrl/auth/v1"

    /** Discovery endpoint for the project's public signing keys. */
    val jwksUrl: String get() = "$projectUrl/auth/v1/.well-known/jwks.json"

    companion object {
        fun fromEnv(env: Env): SupabaseConfig? =
            env["SUPABASE_URL"]?.trimEnd('/')?.let {
                SupabaseConfig(
                    projectUrl = it,
                    serviceRoleKey = env["SUPABASE_SERVICE_ROLE_KEY"],
                    appleSignIn = AppleSignInConfig.fromEnv(env),
                )
            }
    }
}

/**
 * The Sign in with Apple key the server signs its client secret with, for Apple's token
 * and revoke endpoints. Apple asks an app that offers Sign in with Apple to revoke the
 * person's token when they delete their account, and revoking needs a token only this
 * exchange can produce.
 *
 * Null unless all three of `APPLE_TEAM_ID`, `APPLE_KEY_ID` and `APPLE_PRIVATE_KEY` are
 * set. While it is null the app's authorization codes are refused (503
 * `apple_not_configured`) and deletion skips revocation with a warning, which App Review
 * does not accept: set all three on prod before a release that offers Sign in with Apple. [privateKeyPem] is the `.p8` file's contents: treat it as a password.
 */
data class AppleSignInConfig(
    val teamId: String,
    val keyId: String,
    val privateKeyPem: String,
    /** The app's bundle id, which native Sign in with Apple uses as its client id. */
    val clientId: String = DEFAULT_CLIENT_ID,
    val baseUrl: String = "https://appleid.apple.com",
) {
    override fun toString(): String = "AppleSignInConfig(teamId=$teamId, keyId=$keyId, clientId=$clientId, baseUrl=$baseUrl)"

    companion object {
        const val DEFAULT_CLIENT_ID = "com.opson.Opson"

        fun fromEnv(env: Env): AppleSignInConfig? {
            val teamId = env["APPLE_TEAM_ID"]?.takeIf { it.isNotBlank() } ?: return null
            val keyId = env["APPLE_KEY_ID"]?.takeIf { it.isNotBlank() } ?: return null
            // Fly secrets and .env files flatten newlines, so a literal "\n" stands for one.
            val key = env["APPLE_PRIVATE_KEY"]?.takeIf { it.isNotBlank() }?.replace("\\n", "\n") ?: return null
            return AppleSignInConfig(
                teamId = teamId,
                keyId = keyId,
                privateKeyPem = key,
                clientId = env["APPLE_CLIENT_ID"]?.takeIf { it.isNotBlank() } ?: DEFAULT_CLIENT_ID,
            )
        }
    }
}

data class HttpConfig(
    val host: String,
    val port: Int,
) {
    companion object {
        fun fromEnv(env: Env): HttpConfig = HttpConfig(
            host = env["SERVER_HOST"] ?: "0.0.0.0",
            port = env.int("SERVER_PORT", default = 8080),
        )
    }
}

/**
 * Connection settings for the Postgres pool. We accept a single `DATABASE_URL`
 * (12-factor) and parse user + password out of it so Hikari can take them as
 * separate fields — some drivers / pools reject JDBC URLs with embedded
 * credentials.
 *
 * The two timeouts bound how long a request can wait on the database, so a
 * phone gets a quick 503 rather than hanging until its own 30 s timeout when
 * Postgres is saturated (a catalog import building indexes once made search
 * take 27 s): [poolTimeoutMs] for a free connection, [statementTimeoutMs] for
 * any one statement. Migrations are exempt (see `Database.connect`).
 */
data class DatabaseConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val poolMaxSize: Int,
    val poolMinIdle: Int,
    val poolTimeoutMs: Long = DEFAULT_POOL_TIMEOUT_MS,
    val statementTimeoutMs: Long = DEFAULT_STATEMENT_TIMEOUT_MS,
) {
    companion object {
        const val DEFAULT_POOL_TIMEOUT_MS = 3_000L
        const val DEFAULT_STATEMENT_TIMEOUT_MS = 5_000L

        /** Returns null when `DATABASE_URL` is unset — the server then runs in limited mode. */
        fun fromEnv(env: Env): DatabaseConfig? {
            val raw = env["DATABASE_URL"] ?: return null
            val parsed = ParsedPostgresUrl.parse(raw)
            return DatabaseConfig(
                jdbcUrl = parsed.jdbcUrl,
                username = parsed.username,
                password = parsed.password,
                poolMaxSize = env.int("DATABASE_POOL_MAX_SIZE", default = 10),
                poolMinIdle = env.int("DATABASE_POOL_MIN_IDLE", default = 2),
                poolTimeoutMs = env.long("DATABASE_POOL_TIMEOUT_MS", default = DEFAULT_POOL_TIMEOUT_MS),
                statementTimeoutMs = env.long("DATABASE_STATEMENT_TIMEOUT_MS", default = DEFAULT_STATEMENT_TIMEOUT_MS),
            )
        }
    }
}

/**
 * `postgresql://user:pass@host:port/db?params` →
 *   - jdbcUrl: `jdbc:postgresql://host:port/db?params` (no credentials)
 *   - username, password: extracted and URL-decoded
 *
 * A malformed value fails boot with a message that never quotes it: the
 * credentials sit near the front, and the message reaches the logs and Sentry.
 */
internal data class ParsedPostgresUrl(
    val jdbcUrl: String,
    val username: String,
    val password: String,
) {
    companion object {
        fun parse(raw: String): ParsedPostgresUrl {
            require(raw.startsWith("postgresql://") || raw.startsWith("postgres://")) {
                val scheme = raw.substringBefore("://", missingDelimiterValue = "").takeIf { SCHEME.matches(it) } ?: "none"
                "DATABASE_URL must start with postgresql:// (got scheme: $scheme)"
            }
            val noScheme = raw.substringAfter("://")
            val atIndex = noScheme.lastIndexOf('@')
            require(atIndex > 0) { "DATABASE_URL must include `user:password@host` credentials" }
            val credsPart = noScheme.substring(0, atIndex)
            val hostPart = noScheme.substring(atIndex + 1)
            val colon = credsPart.indexOf(':')
            require(colon > 0) { "DATABASE_URL credentials must be `user:password`" }
            val user = urlDecode(credsPart.substring(0, colon))
            val pass = urlDecode(credsPart.substring(colon + 1))
            return ParsedPostgresUrl(
                jdbcUrl = "jdbc:postgresql://$hostPart",
                username = user,
                password = pass,
            )
        }

        private val SCHEME = Regex("[a-z][a-z0-9+.-]{0,20}")

        /** URLDecoder's own message quotes the bad escape, which is part of the password. */
        private fun urlDecode(s: String): String = try {
            java.net.URLDecoder.decode(s, Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("DATABASE_URL credentials have a malformed %-escape")
        }
    }
}
