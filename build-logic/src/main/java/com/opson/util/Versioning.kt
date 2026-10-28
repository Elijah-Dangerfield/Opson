package com.opson.util

import com.github.gmazzo.buildconfig.BuildConfigExtension
import org.gradle.api.Project
import java.io.FileInputStream
import java.util.Properties

private const val DEFAULT_APPLICATION_ID = "com.opson"
private const val DEFAULT_VERSION_NAME = "0.0.1"
private const val DEFAULT_VERSION_CODE = 1
private const val DEFAULT_RELEASE_CHANNEL = "dev"
private const val DEFAULT_BUILD_NUMBER = 1

data class VersionMetadata(
    val applicationId: String,
    val versionName: String,
    val versionCode: Int,
    val releaseChannel: String,
    val buildNumber: Int,
    val commitSha: String,
    val commitBranch: String,
) {
    val releaseDisplay: String = "$versionName ($buildNumber)"
}

/**
 * Placeholders used when no Supabase project is configured. They are deliberately
 * self-describing: supabase-kt refuses to build a client from blank values, so
 * something has to be there, and whatever is there ends up quoted verbatim in the
 * runtime failure ("Unable to resolve host
 * set-supabase-projectId-in-local-properties.supabase.co"). A value that names the
 * fix beats a value that looks like real config. They name local.properties because
 * that is where the dev project goes; a release build never gets this far with one
 * ([releaseProblems]).
 */
const val UNCONFIGURED_SUPABASE_PROJECT_ID = "set-supabase-projectId-in-local-properties"
const val UNCONFIGURED_SUPABASE_ANON_KEY = "set-supabase-anonKey-in-local-properties"

data class SupabaseMetadata(
    val projectId: String,
    val anonKey: String,
    val prodProjectId: String,
    val prodAnonKey: String,
) {
    val url: String = supabaseUrl(projectId)
    val prodUrl: String = supabaseUrl(prodProjectId)
}

private fun supabaseUrl(projectId: String): String =
    projectId.takeIf { it.isNotBlank() }?.let { "https://$it.supabase.co" } ?: ""

/**
 * Resolves the app's version/build metadata with this precedence:
 *  1. **CI env overrides** — `VERSION_NAME_OVERRIDE`, `VERSION_CODE_OVERRIDE`,
 *     `BUILD_NUMBER_OVERRIDE`, `RELEASE_CHANNEL_OVERRIDE` (set by
 *     `release.yml` / `beta.yml` so the store-facing versionCode + build
 *     number climb monotonically without a commit). Blank/unset is ignored.
 *  2. **`versions.properties`** — the checked-in values every *local* build
 *     uses. Local builds set no env overrides, so they always see these
 *     (currently `buildNumber=1`). That's fine: a local build never uploads to
 *     a store, and the static number is enough for on-device debugging.
 *  3. Hard-coded defaults.
 *
 * Both the Android `versionCode` (see [ApplicationConventionPlugin]) and the
 * generated BuildConfig that backs `BuildInfo.buildNumber` / `versionName` /
 * `releaseChannel` (rendered on the Settings screen) read from here — so this
 * one override point keeps the installed binary and the in-app "About" string
 * in lockstep.
 */
fun Project.loadVersionMetadata(): VersionMetadata {
    val properties = readProperties("versions.properties")

    fun Properties.int(key: String, defaultValue: Int): Int =
        string(key, defaultValue.toString()).toIntOrNull() ?: defaultValue

    val applicationId = properties.string("applicationId", DEFAULT_APPLICATION_ID)
    val versionName = envOverride("VERSION_NAME_OVERRIDE")
        ?: properties.string("versionName", DEFAULT_VERSION_NAME)
    val versionCode = envOverride("VERSION_CODE_OVERRIDE")?.toIntOrNull()
        ?: properties.int("versionCode", DEFAULT_VERSION_CODE)
    val releaseChannel = resolveReleaseChannel(envOverride(RELEASE_CHANNEL_ENV), properties)
    val buildNumber = envOverride("BUILD_NUMBER_OVERRIDE")?.toIntOrNull()
        ?: properties.int("buildNumber", DEFAULT_BUILD_NUMBER)

    // GitHub Actions exports GITHUB_SHA / GITHUB_REF_NAME into every job, so
    // CI builds get the exact commit for free; local builds ask git. "unknown"
    // is the last resort (e.g. a server-only Docker image has no .git dir).
    val commitSha = envOverride("GITHUB_SHA")?.take(COMMIT_SHA_LENGTH)
        ?: gitOutput("rev-parse", "--short=$COMMIT_SHA_LENGTH", "HEAD")
        ?: UNKNOWN_COMMIT
    val commitBranch = envOverride("GITHUB_REF_NAME")
        ?: gitOutput("rev-parse", "--abbrev-ref", "HEAD")
        ?: UNKNOWN_COMMIT

    return VersionMetadata(
        applicationId = applicationId,
        versionName = versionName,
        versionCode = versionCode,
        releaseChannel = releaseChannel,
        buildNumber = buildNumber,
        commitSha = commitSha,
        commitBranch = commitBranch,
    )
}

private const val COMMIT_SHA_LENGTH = 12
private const val UNKNOWN_COMMIT = "unknown"

private const val RELEASE_CHANNEL_ENV = "RELEASE_CHANNEL_OVERRIDE"

/**
 * The channels that may carry QA code. Everything else counts as a store build, so a typo in
 * `RELEASE_CHANNEL_OVERRIDE` ships without the QA menu rather than with it.
 */
private val QA_CHANNELS = setOf(DEFAULT_RELEASE_CHANNEL, "beta")

/** The channel this build is for, resolved exactly as [loadVersionMetadata] resolves it. */
fun Project.releaseChannel(): String =
    resolveReleaseChannel(envOverride(RELEASE_CHANNEL_ENV), readProperties("versions.properties"))

/**
 * Whether this is a store build: the one `release.yml` and the Fastfile's `release` lane make for
 * the App Store and Play production. `:apps:compose` leaves `:features:qa` out of these, so the QA
 * menu is not in the binary at all. Debug or release variant makes no difference; only the
 * channel decides.
 */
fun Project.isStoreBuild(): Boolean = isStoreChannel(releaseChannel())

fun isStoreChannel(channel: String): Boolean = channel !in QA_CHANNELS

internal fun resolveReleaseChannel(envOverride: String?, properties: Properties): String =
    envOverride?.takeIf { it.isNotBlank() } ?: properties.string("releaseChannel", DEFAULT_RELEASE_CHANNEL)

private fun Properties.string(key: String, defaultValue: String): String =
    getProperty(key)?.takeIf { it.isNotBlank() } ?: defaultValue

private fun envOverride(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() }

// providers.exec (not ProcessBuilder) so the configuration cache treats the
// git call as a tracked build input instead of failing the build.
private fun Project.gitOutput(vararg args: String): String? = try {
    providers.exec {
        commandLine("git", *args)
        workingDir = rootProject.rootDir
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().takeIf { it.isNotBlank() }
} catch (_: Exception) {
    null
}

fun BuildConfigExtension.writeCommonMetadata(metadata: VersionMetadata) {
    buildConfigField("String", "APPLICATION_ID", "\"${metadata.applicationId}\"")
    buildConfigField("String", "VERSION_NAME", "\"${metadata.versionName}\"")
    buildConfigField("Int", "VERSION_CODE", metadata.versionCode.toString())
    buildConfigField("String", "RELEASE_CHANNEL", "\"${metadata.releaseChannel}\"")
    buildConfigField("Int", "BUILD_NUMBER", metadata.buildNumber.toString())
    buildConfigField("String", "COMMIT_SHA", "\"${metadata.commitSha}\"")
    buildConfigField("String", "COMMIT_BRANCH", "\"${metadata.commitBranch}\"")
}

/**
 * Supabase client config, one source per environment:
 *
 * - **Dev** (what debug builds sign in to): `local.properties`
 *   (`supabase.projectId` / `supabase.anonKey`), then CI env
 *   (`SUPABASE_PROJECT_ID` / `SUPABASE_ANON_KEY`), then a placeholder that names
 *   itself, so a fresh clone still builds and the first auth call says what to set.
 * - **Prod** (what release builds sign in to): the committed `supabase.properties`
 *   only. No env override and no local file, so a release build cannot depend on
 *   whose machine or shell built it. A missing value becomes the placeholder, which
 *   [releaseProblems] refuses.
 */
fun Project.loadSupabaseMetadata(): SupabaseMetadata = supabaseMetadata(
    local = readProperties("local.properties"),
    committed = readProperties("supabase.properties"),
    env = { System.getenv(it) },
)

internal fun supabaseMetadata(local: Properties, committed: Properties, env: (String) -> String?): SupabaseMetadata {
    fun envOrNull(key: String): String? = env(key)?.takeIf { it.isNotBlank() }
    return SupabaseMetadata(
        projectId = local.stringOrNull("supabase.projectId")
            ?: envOrNull("SUPABASE_PROJECT_ID")
            ?: UNCONFIGURED_SUPABASE_PROJECT_ID,
        anonKey = local.stringOrNull("supabase.anonKey")
            ?: envOrNull("SUPABASE_ANON_KEY")
            ?: UNCONFIGURED_SUPABASE_ANON_KEY,
        prodProjectId = committed.stringOrNull("supabase.prod.projectId") ?: UNCONFIGURED_SUPABASE_PROJECT_ID,
        prodAnonKey = committed.stringOrNull("supabase.prod.anonKey") ?: UNCONFIGURED_SUPABASE_ANON_KEY,
    )
}

/**
 * Debug builds sign in to the dev project, so a missing one only matters there. Release
 * builds read prod from `supabase.properties` and [releaseProblems] checks that instead,
 * which is why CI release builds (no `local.properties`) may print this and still be fine.
 */
fun Project.warnIfDevSupabaseMissing(metadata: SupabaseMetadata) {
    if (metadata.projectId != UNCONFIGURED_SUPABASE_PROJECT_ID && metadata.anonKey != UNCONFIGURED_SUPABASE_ANON_KEY) return
    logger.warn(
        "No dev Supabase project, so sign-in fails in debug builds. Set supabase.projectId and " +
            "supabase.anonKey in local.properties (or SUPABASE_PROJECT_ID / SUPABASE_ANON_KEY in the " +
            "environment). Release builds use supabase.properties and are not affected. See SETUP.md."
    )
}

private val SUPABASE_PROJECT_REF = Regex("^[a-z]{20}$")
private const val SUPABASE_PUBLISHABLE_KEY_PREFIX = "sb_publishable_"

/**
 * Why a release build made from this config would sign in somewhere it shouldn't. Empty
 * when it's fine. 0.1.0 shipped signing in to the dev project while calling the prod
 * server, and every request came back 401; this is the build-time half of making that
 * fail before it reaches a phone.
 */
fun SupabaseMetadata.releaseProblems(): List<String> = buildList {
    if (!SUPABASE_PROJECT_REF.matches(prodProjectId)) {
        add("supabase.prod.projectId in supabase.properties is \"$prodProjectId\", not a Supabase project ref (20 lower-case letters).")
    }
    if (!prodAnonKey.startsWith(SUPABASE_PUBLISHABLE_KEY_PREFIX)) {
        add("supabase.prod.anonKey in supabase.properties is not a publishable key ($SUPABASE_PUBLISHABLE_KEY_PREFIX...).")
    }
    if (prodProjectId == projectId) {
        add("supabase.prod.projectId is the dev project ($projectId). Release builds would sign in to dev and call the prod server.")
    }
}

fun BuildConfigExtension.writeSupabaseMetadata(metadata: SupabaseMetadata) {
    buildConfigField("String", "SUPABASE_PROJECT_ID", "\"${metadata.projectId}\"")
    buildConfigField("String", "SUPABASE_URL", "\"${metadata.url}\"")
    buildConfigField("String", "SUPABASE_ANON_KEY", "\"${metadata.anonKey}\"")
    buildConfigField("String", "SUPABASE_PROD_PROJECT_ID", "\"${metadata.prodProjectId}\"")
    buildConfigField("String", "SUPABASE_PROD_URL", "\"${metadata.prodUrl}\"")
    buildConfigField("String", "SUPABASE_PROD_ANON_KEY", "\"${metadata.prodAnonKey}\"")
}

data class TelemetryMetadata(
    val grafanaOtlpBaseUrl: String,
    val grafanaOtlpInstanceId: String,
    val grafanaLogsWriteToken: String,
    val sentryDsn: String,
)

/**
 * Client telemetry credentials. Resolution for each value: CI env
 * (`GRAFANA_OTLP_BASE_URL` / `GRAFANA_OTLP_INSTANCE_ID` /
 * `GRAFANA_LOGS_WRITE_TOKEN` / `SENTRY_DSN`, set from repo secrets in
 * beta/release workflows) → `local.properties` (per-dev) → the committed
 * `telemetry.properties` → blank.
 *
 * Only the Sentry DSN is meant to be in the committed file. It is a write-only
 * ingest endpoint that ships inside every store binary, so it is not a secret,
 * and keeping it in a per-developer file is what made every fresh clone report
 * nothing. `scripts/setup_sentry.main.kts` writes it there. The Grafana values
 * are real write credentials — Grafana auto-revokes `glc_` tokens it finds in
 * public repos — so they stay in CI secrets or `local.properties`.
 *
 * Blank values leave the corresponding pipe dormant: no Grafana credentials →
 * `GrafanaCloud.isConfigured` is false and app events stay local; no Sentry
 * DSN → `SentryRuntimeConfig.isEnabled` is false, `Sentry.init` is never
 * called, and crash reporting costs nothing at runtime. The app builds and runs
 * either way, so a fresh clone works with zero setup. Release builds are held to
 * more: `verifyReleaseEnvironment` fails on [releaseProblems] and prints
 * [releaseWarnings].
 */
fun Project.loadTelemetryMetadata(): TelemetryMetadata {
    val local = readProperties("local.properties")
    val committed = readProperties("telemetry.properties")

    fun resolve(env: String, key: String): String =
        System.getenv(env)?.takeIf { it.isNotBlank() }
            ?: local.stringOrNull(key)
            ?: committed.stringOrNull(key)
            ?: (findProperty(key) as? String)?.takeIf { it.isNotBlank() }
            ?: ""

    return TelemetryMetadata(
        grafanaOtlpBaseUrl = resolve("GRAFANA_OTLP_BASE_URL", "grafana.otlpBaseUrl"),
        grafanaOtlpInstanceId = resolve("GRAFANA_OTLP_INSTANCE_ID", "grafana.otlpInstanceId"),
        grafanaLogsWriteToken = resolve("GRAFANA_LOGS_WRITE_TOKEN", "grafana.logsWriteToken"),
        sentryDsn = resolve("SENTRY_DSN", "sentry.dsn"),
    )
}

private val TelemetryMetadata.grafanaValues: List<String>
    get() = listOf(grafanaOtlpBaseUrl, grafanaOtlpInstanceId, grafanaLogsWriteToken)

/**
 * Why a release build made from this config would ship with telemetry broken. No Sentry
 * DSN means crashes go unreported. Grafana with some values but not all is a typo or a
 * missing secret: `GrafanaCloud.isConfigured` needs all three, so it is off without a word.
 */
fun TelemetryMetadata.releaseProblems(): List<String> = buildList {
    if (sentryDsn.isBlank()) {
        add("No Sentry DSN, so this build would not report crashes. Run ./scripts/setup_sentry.main.kts and commit telemetry.properties.")
    }
    if (grafanaValues.any { it.isBlank() } && grafanaValues.any { it.isNotBlank() }) {
        add(
            "Grafana is half configured: GRAFANA_OTLP_BASE_URL, GRAFANA_OTLP_INSTANCE_ID and " +
                "GRAFANA_LOGS_WRITE_TOKEN must all be set, or none.",
        )
    }
}

/**
 * Telemetry that is off on purpose but worth saying so on every release build. Grafana app
 * events are off until the three Grafana secrets exist; that is allowed, but not silently.
 */
fun TelemetryMetadata.releaseWarnings(): List<String> = buildList {
    if (grafanaValues.all { it.isBlank() }) {
        add(
            "Grafana app events are off in this build: GRAFANA_OTLP_BASE_URL, GRAFANA_OTLP_INSTANCE_ID " +
                "and GRAFANA_LOGS_WRITE_TOKEN are not set. ./scripts/setup_github_secrets.main.kts sets them.",
        )
    }
}

fun BuildConfigExtension.writeTelemetryMetadata(metadata: TelemetryMetadata) {
    buildConfigField("String", "GRAFANA_OTLP_BASE_URL", "\"${metadata.grafanaOtlpBaseUrl}\"")
    buildConfigField("String", "GRAFANA_OTLP_INSTANCE_ID", "\"${metadata.grafanaOtlpInstanceId}\"")
    buildConfigField("String", "GRAFANA_LOGS_WRITE_TOKEN", "\"${metadata.grafanaLogsWriteToken}\"")
    buildConfigField("String", "SENTRY_DSN", "\"${metadata.sentryDsn}\"")
}

private fun Properties.stringOrNull(key: String): String? =
    getProperty(key)?.takeIf { it.isNotBlank() }

private fun Project.readProperties(rootFile: String): Properties {
    val properties = Properties()
    val file = rootProject.file(rootFile)
    if (file.exists()) {
        FileInputStream(file).use(properties::load)
    }
    return properties
}
