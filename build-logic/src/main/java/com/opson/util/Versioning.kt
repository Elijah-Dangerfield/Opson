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
 * fix beats a value that looks like real config.
 */
const val UNCONFIGURED_SUPABASE_PROJECT_ID = "set-supabase-projectId-in-local-properties"
const val UNCONFIGURED_SUPABASE_ANON_KEY = "set-supabase-anonKey-in-local-properties"

data class SupabaseMetadata(
    val projectId: String,
    val anonKey: String
) {
    val url: String = projectId.takeIf { it.isNotBlank() }
        ?.let { "https://$it.supabase.co" }
        ?: ""
}

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
    val properties = Properties()
    val metadataFile = rootProject.file("versions.properties")
    if (metadataFile.exists()) {
        FileInputStream(metadataFile).use(properties::load)
    }

    fun Properties.string(key: String, defaultValue: String): String =
        getProperty(key)?.takeIf { it.isNotBlank() } ?: defaultValue

    fun Properties.int(key: String, defaultValue: Int): Int =
        string(key, defaultValue.toString()).toIntOrNull() ?: defaultValue

    fun envOverride(name: String): String? =
        System.getenv(name)?.takeIf { it.isNotBlank() }

    val applicationId = properties.string("applicationId", DEFAULT_APPLICATION_ID)
    val versionName = envOverride("VERSION_NAME_OVERRIDE")
        ?: properties.string("versionName", DEFAULT_VERSION_NAME)
    val versionCode = envOverride("VERSION_CODE_OVERRIDE")?.toIntOrNull()
        ?: properties.int("versionCode", DEFAULT_VERSION_CODE)
    val releaseChannel = envOverride("RELEASE_CHANNEL_OVERRIDE")
        ?: properties.string("releaseChannel", DEFAULT_RELEASE_CHANNEL)
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
 * Supabase client config: `local.properties` (`supabase.projectId` /
 * `supabase.anonKey`) → CI env (`SUPABASE_PROJECT_ID` / `SUPABASE_ANON_KEY`) →
 * a placeholder that names itself.
 *
 * The build stays green with nothing configured, so a fresh clone compiles and
 * runs, but the placeholder cannot pass for working config: it is not a real
 * project ref, and the first auth call fails with the property name to set in
 * the message. A warning goes out at configuration time too, because the old
 * behaviour's worst part was silence — the build said nothing about Supabase at
 * all.
 */
fun Project.loadSupabaseMetadata(): SupabaseMetadata {
    val properties = Properties()
    val localProperties = rootProject.file("local.properties")
    if (localProperties.exists()) {
        FileInputStream(localProperties).use(properties::load)
    }

    fun env(key: String): String? = System.getenv(key)?.takeIf { it.isNotBlank() }

    val projectId = properties.stringOrNull("supabase.projectId")
        ?: env("SUPABASE_PROJECT_ID")
        ?: UNCONFIGURED_SUPABASE_PROJECT_ID
    val anonKey = properties.stringOrNull("supabase.anonKey")
        ?: env("SUPABASE_ANON_KEY")
        ?: UNCONFIGURED_SUPABASE_ANON_KEY

    if (projectId == UNCONFIGURED_SUPABASE_PROJECT_ID || anonKey == UNCONFIGURED_SUPABASE_ANON_KEY) {
        logger.warn(
            "Supabase is not configured — auth will fail at runtime. Set " +
                "supabase.projectId and supabase.anonKey in local.properties " +
                "(or SUPABASE_PROJECT_ID / SUPABASE_ANON_KEY in the environment). " +
                "See template/SETUP.md."
        )
    }

    return SupabaseMetadata(
        projectId = projectId,
        anonKey = anonKey
    )
}

fun BuildConfigExtension.writeSupabaseMetadata(metadata: SupabaseMetadata) {
    buildConfigField("String", "SUPABASE_PROJECT_ID", "\"${metadata.projectId}\"")
    buildConfigField("String", "SUPABASE_URL", "\"${metadata.url}\"")
    buildConfigField("String", "SUPABASE_ANON_KEY", "\"${metadata.anonKey}\"")
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
 * either way, so a fresh clone works with zero setup.
 */
fun Project.loadTelemetryMetadata(): TelemetryMetadata {
    val properties = Properties()
    val localProperties = rootProject.file("local.properties")
    if (localProperties.exists()) {
        FileInputStream(localProperties).use(properties::load)
    }

    val committed = Properties()
    val telemetryProperties = rootProject.file("telemetry.properties")
    if (telemetryProperties.exists()) {
        FileInputStream(telemetryProperties).use(committed::load)
    }

    fun resolve(env: String, key: String): String =
        System.getenv(env)?.takeIf { it.isNotBlank() }
            ?: properties.stringOrNull(key)
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

fun BuildConfigExtension.writeTelemetryMetadata(metadata: TelemetryMetadata) {
    buildConfigField("String", "GRAFANA_OTLP_BASE_URL", "\"${metadata.grafanaOtlpBaseUrl}\"")
    buildConfigField("String", "GRAFANA_OTLP_INSTANCE_ID", "\"${metadata.grafanaOtlpInstanceId}\"")
    buildConfigField("String", "GRAFANA_LOGS_WRITE_TOKEN", "\"${metadata.grafanaLogsWriteToken}\"")
    buildConfigField("String", "SENTRY_DSN", "\"${metadata.sentryDsn}\"")
}

private fun Properties.stringOrNull(key: String): String? =
    getProperty(key)?.takeIf { it.isNotBlank() }
