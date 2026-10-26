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
) {
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
        )
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
) {
    companion object {
        fun fromEnv(env: Env): FoodSourcesConfig = FoodSourcesConfig(
            nutritionix = NutritionixConfig.fromEnv(env),
            searchOpenFoodFacts = env["FOOD_SEARCH_OFF_ENABLED"]?.trim()?.lowercase() == "true",
            anthropic = AnthropicConfig.fromEnv(env),
        )
    }
}

/**
 * The Anthropic API, for photo estimates (`POST /v1/describe/photo`). Null
 * until `ANTHROPIC_API_KEY` is set, and the route then answers 503
 * `photo_estimate_unavailable`, which the app shows as a calm "not available
 * right now". [photoModel] is `ANTHROPIC_PHOTO_MODEL`, so the model can change
 * without a release (see `AnthropicEstimateProvider` for the default and why).
 * Treat the key like a password: never log it.
 */
data class AnthropicConfig(
    val apiKey: String,
    val photoModel: String = DEFAULT_PHOTO_MODEL,
) {
    override fun toString(): String = "AnthropicConfig(apiKey=***, photoModel=$photoModel)"

    companion object {
        const val DEFAULT_PHOTO_MODEL = "claude-opus-5-5"

        fun fromEnv(env: Env): AnthropicConfig? {
            val key = env["ANTHROPIC_API_KEY"] ?: return null
            return AnthropicConfig(apiKey = key, photoModel = env["ANTHROPIC_PHOTO_MODEL"]?.trim() ?: DEFAULT_PHOTO_MODEL)
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
 * (the `/v1/me` endpoint isn't mounted). Set it to enable auth.
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
 */
data class DatabaseConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val poolMaxSize: Int,
    val poolMinIdle: Int,
) {
    companion object {
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
            )
        }
    }
}

/**
 * `postgresql://user:pass@host:port/db?params` →
 *   - jdbcUrl: `jdbc:postgresql://host:port/db?params` (no credentials)
 *   - username, password: extracted and URL-decoded
 *
 * Internal helper, public only for tests.
 */
internal data class ParsedPostgresUrl(
    val jdbcUrl: String,
    val username: String,
    val password: String,
) {
    companion object {
        fun parse(raw: String): ParsedPostgresUrl {
            require(raw.startsWith("postgresql://") || raw.startsWith("postgres://")) {
                "DATABASE_URL must start with postgresql:// (got: ${raw.take(20)}…)"
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

        private fun urlDecode(s: String): String =
            java.net.URLDecoder.decode(s, Charsets.UTF_8)
    }
}
