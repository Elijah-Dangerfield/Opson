package com.opson.libraries.opson.impl

import com.opson.libraries.core.BuildInfo
import com.opson.libraries.core.Catching
import com.opson.libraries.core.Platform
import com.opson.libraries.core.TelemetryInfo
import com.opson.libraries.core.buildType
import com.opson.libraries.core.versionString
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.LogLevel
import com.opson.libraries.core.logging.Logger
import com.opson.libraries.opson.Telemetry
import com.opson.libraries.opson.impl.logging.DevConsoleWriter
import com.opson.libraries.opson.impl.logging.KermitLogTree
import com.opson.libraries.opson.impl.logging.SentryLogTree
import co.touchlab.kermit.Logger as KermitLogger
import co.touchlab.kermit.Severity as KermitSeverity
import io.sentry.kotlin.multiplatform.Attachment
import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.SentryLevel
import io.sentry.kotlin.multiplatform.SentryOptions
import io.sentry.kotlin.multiplatform.protocol.User
import io.sentry.kotlin.multiplatform.protocol.UserFeedback
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AppTelemetry : Telemetry by ConfiguredTelemetry(
    configProvider = { SentryRuntimeConfig.forApp(BuildInfo) }
)

class IosExtensionTelemetry(
    private val configProvider: () -> SentryRuntimeConfig = { SentryRuntimeConfig.forIosExtension(BuildInfo) }
) : Telemetry by ConfiguredTelemetry(configProvider)

private class ConfiguredTelemetry(
    private val configProvider: () -> SentryRuntimeConfig
) : Telemetry {

    private val logger: Logger = KLog.withTag("Telemetry")
    private var initialized = false

    // The planted Sentry tree, held so captureUserFeedback can dump its
    // in-memory log buffer as an attachment. Null until Sentry initializes.
    private var sentryLogTree: SentryLogTree? = null

    override fun initialize() {
        if (initialized) return
        initialized = true

        KLog.plant(KermitLogTree())

        // Debug-only: drop Kermit's global min-severity to Verbose so nothing
        // is pre-filtered before reaching any writer. The platform writers
        // (OSLogWriter on iOS, LogcatWriter on Android) handle Info+ natively.
        // [DevConsoleWriter] adds a pretty stdout-only path for Debug-and-
        // below entries, because Android Studio's KMM plugin filters those
        // out of its Run window when running iOS apps. See the writer's
        // header for the full reasoning.
        if (BuildInfo.isDebug) {
            KermitLogger.setMinSeverity(KermitSeverity.Verbose)
            KermitLogger.addLogWriter(DevConsoleWriter())
        }

        val config = configProvider()

        if (!config.isEnabled) {
            logger.i { scope ->
                scope.tag("environment", config.environment)
                scope.tag("platform", config.platformTag)
                "Sentry disabled for ${config.environment}"
            }
            return
        }

        Catching {
            Sentry.init(config::applyTo)
        }.onFailure {
            logger.e(it) { scope ->
                scope.tag("environment", config.environment)
                scope.tag("platform", config.platformTag)
                scope.tag("build_type", config.buildTypeTag)
            }
        }.onSuccess {
            val tree = SentryLogTree(
                minBreadcrumbLevel = config.logPolicy.minBreadcrumbLevel,
                minEventLevel = config.logPolicy.minEventLevel,
                minBufferLevel = config.logPolicy.minBufferLevel,
            )
            sentryLogTree = tree
            KLog.plant(tree)
            Sentry.configureScope {
                it.setExtra("platform", config.platformTag)
                it.setExtra("build_type", config.buildTypeTag)
                it.setExtra("release_channel", BuildInfo.releaseChannel)
                // Tags (not extras) so triage can filter issues by the exact
                // commit a build shipped from.
                it.setTag(COMMIT_SHA_KEY, BuildInfo.commitSha)
                it.setTag(COMMIT_BRANCH_KEY, BuildInfo.commitBranch)
            }
            logger.i { scope ->
                scope.extra("environment", config.environment)
                scope.extra("platform", config.platformTag)
                scope.extra("build_type", config.buildTypeTag)
                "Sentry initialized for ${config.environment}"
            }
        }
    }

    override fun setUser(
        email: String?,
        name: String?,
        id: String?
    ) {
        // Same guard as every other scope mutator below: with a blank DSN
        // Sentry.init never ran, so there is no scope to write to and this is
        // pure overhead on a path the sign-in flow hits on every session.
        if (!Sentry.isEnabled()) return
        Sentry.setUser(
            User(
                id = id,
                email = email,
                username = name
            )
        )
    }

    override fun setCurrentRoute(route: String) {
        // Best-effort: when Sentry isn't initialized (e.g. disabled
        // environment) configureScope has no scope to mutate, so skip quietly
        // rather than logging on every navigation.
        if (!Sentry.isEnabled()) return
        Sentry.configureScope {
            // Tag = searchable/filterable in the issues list; extra = shown on
            // the event detail.
            it.setTag(ROUTE_KEY, route)
            it.setExtra(ROUTE_KEY, route)
        }
    }

    override fun setSession(sessionId: String) {
        // Best-effort, same scope-persistence reasoning as setCurrentRoute:
        // writing the tag on the scope means a later native crash (turned into
        // an event on next launch) still carries the session it happened in.
        if (!Sentry.isEnabled()) return
        Sentry.configureScope { it.setTag(SESSION_ID_KEY, sessionId) }
    }

    override fun setInstallId(installId: String) {
        if (!Sentry.isEnabled()) return
        Sentry.configureScope { it.setTag(INSTALL_ID_KEY, installId) }
    }

    override fun setContext(key: String, value: String?) {
        if (!Sentry.isEnabled()) return
        Sentry.configureScope {
            if (value.isNullOrBlank()) it.removeTag(key) else it.setTag(key, value)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override fun captureUserFeedback(
        message: String,
        isBugReport: Boolean,
        eventId: String?,
        errorCode: Int?,
        email: String?,
        screenshots: List<ByteArray>,
    ) {
        val payload = message.trim()
        if (payload.isBlank()) {
            logger.w {
                it.tag("feedback_type", if (isBugReport) "bug_report" else "feedback")
                "Ignoring empty feedback payload"
            }
            return
        }

        if (!Sentry.isEnabled()) {
            logger.w {
                it.tag("feedback_type", if (isBugReport) "bug_report" else "feedback")
                "Sentry disabled, feedback dropped"
            }
            return
        }

        val typeTag = if (isBugReport) "bug_report" else "feedback"
        val sanitizedEmail = email?.trim()?.takeIf { it.isNotBlank() }

        // The legacy User Feedback API only persists feedback attached to an
        // event Sentry has already ingested — an empty or unknown event id is
        // silently dropped on ingest, which is why feedback never surfaced.
        // `eventId` here is our internal KLog id (or null for general feedback),
        // never a real Sentry id, so mint a carrier event via captureMessage and
        // attach the feedback to that. Mirrors Sentry's documented
        // captureMessage → captureUserFeedback flow. The KLog id / error code
        // ride along in the comment for correlation back to the logs.
        // Mint a unique id for this report and stamp it on a LOCAL scope for
        // just the carrier event: beforeSend reads it to fingerprint the event
        // into its own issue (see init), and the in-memory log buffer rides
        // along as an attachment — the fine-grained Debug/Verbose we never ship
        // as breadcrumbs, captured only when the user actually files feedback.
        // Local scope means none of this leaks onto later events.
        val logDump = sentryLogTree?.snapshot()?.takeIf { it.isNotBlank() }
        val feedbackId = Uuid.random().toString()

        // Composed once and used twice: as the legacy UserFeedback comment
        // below, and on the carrier event itself. The duplication is the point.
        // The KMP SDK only has the LEGACY User Feedback API — 0.22.0 has no
        // `captureFeedback` and no `SentryFeedback`, checked against the
        // published sources, so upgrading does not help. Legacy feedback renders
        // wherever the org's feedback settings decide, which downstream meant
        // the words the user actually typed were the one thing missing from the
        // issue: log attachment there, screenshot there, message nowhere.
        // Extras and attachments render on the issue page unconditionally, and
        // that is the page where the rest of the evidence already is.
        val reportBody = buildFeedbackBody(isBugReport, errorCode, eventId, payload)

        val sentryId = Sentry.captureMessage(if (isBugReport) "Bug report" else "User feedback") { scope ->
            scope.setTag(FEEDBACK_EVENT_TAG, feedbackId)
            // Explicit, not inherited. A report is not a fault: left at the
            // default this sorts and alerts alongside crashes, and a triage
            // queue that mixes "the app died" with "I have a suggestion" gets
            // ignored at the wrong end.
            scope.level = SentryLevel.INFO
            scope.setExtra(FEEDBACK_BODY_KEY, reportBody)
            scope.addAttachment(
                Attachment(reportBody.encodeToByteArray(), "feedback.txt", "text/plain"),
            )
            if (logDump != null) {
                scope.addAttachment(Attachment(logDump.encodeToByteArray(), "session-log.txt", "text/plain"))
            }
            // User-attached screenshots ride along as image attachments, so a
            // triager sees the report and what it's about side by side. Capped
            // and skip-empty defensively; the picker already downscales them.
            screenshots.asSequence()
                .filter { it.isNotEmpty() }
                .take(MAX_FEEDBACK_SCREENSHOTS)
                .forEachIndexed { index, bytes ->
                    scope.addAttachment(Attachment(bytes, "screenshot-${index + 1}.jpg", "image/jpeg"))
                }
        }

        val feedback = UserFeedback(sentryId).apply {
            comments = reportBody
            sanitizedEmail?.let { this.email = it }
        }

        Sentry.captureUserFeedback(feedback)

        logger.i { scope ->
            scope.tag("feedback_type", typeTag)
            scope.extra("event_id", sentryId.toString())
            if (isBugReport) {
                errorCode?.let { scope.extra("error_code", it) }
            }
            scope.extra("payload_length", payload.length)
            scope.extra("has_email", sanitizedEmail != null)
            "Feedback forwarded to Sentry ($typeTag)"
        }
    }
}

/**
 * The human-readable body of a feedback report.
 *
 * Provenance up top so triage can tell which code produced the report without
 * cross-referencing tags, and whether it is already fixed on a later commit.
 */
private fun buildFeedbackBody(
    isBugReport: Boolean,
    errorCode: Int?,
    eventId: String?,
    payload: String,
): String = buildString {
    append("Build: ${BuildInfo.versionString()} @ ${BuildInfo.commitSha} (${BuildInfo.commitBranch})\n")
    if (isBugReport) {
        errorCode?.let { append("Error code: $it\n") }
        eventId?.let { append("Log ID: $it\n") }
    }
    append('\n')
    append(payload)
}

// The typed report, mirrored onto the carrier event. Legacy User Feedback
// renders at the org's discretion; an extra always renders on the issue page.
private const val FEEDBACK_BODY_KEY = "feedback_body"

// Scope key for the current navigation route (set via [Telemetry.setCurrentRoute]).
// Shared by the tag and the extra so they read identically in Sentry.
private const val ROUTE_KEY = "route"

// Correlation keys mirrored on the backend (OTel span attributes + log
// fields), so the same value queries Sentry, Tempo, and Loki.
private const val SESSION_ID_KEY = "session_id"
private const val INSTALL_ID_KEY = "install_id"

// Build provenance: the exact commit + branch the installed binary was
// produced from, baked into the generated BuildConfig at build time. Lets
// triage pin a report to code and spot already-fixed-on-main issues.
private const val COMMIT_SHA_KEY = "commit_sha"
private const val COMMIT_BRANCH_KEY = "commit_branch"

// Per-feedback id stamped on the carrier event; `beforeSend` turns it into the
// event fingerprint so each feedback report is its own Sentry issue despite the
// shared "User feedback" / "Bug report" message.
private const val FEEDBACK_EVENT_TAG = "feedback_event"
private const val FEEDBACK_FINGERPRINT = "feedback"

// Hard cap on attached screenshots, mirrored on the UI side. Defensive: the
// picker already limits selection, this just guarantees a malformed caller
// can't flood Sentry.
private const val MAX_FEEDBACK_SCREENSHOTS = 3

data class SentryRuntimeConfig(
    val dsn: String,
    val environment: String,
    val release: String,
    val sendDefaultPii: Boolean,
    val attachStacktrace: Boolean,
    val tracesSampleRate: Double?,
    val platformTag: String,
    val buildTypeTag: String,
    val logPolicy: LogPolicy,
    val enableAutoSessionTracking: Boolean
) {
    val isEnabled: Boolean get() = dsn.isNotBlank()

    /** Maps this config onto the SDK's [SentryOptions] — the single seam
     *  between our config surface and Sentry's knobs. */
    internal fun applyTo(options: SentryOptions) {
        options.dsn = dsn
        options.environment = environment
        options.release = release
        options.sendDefaultPii = sendDefaultPii
        options.attachStackTrace = attachStacktrace
        options.enableAutoSessionTracking = enableAutoSessionTracking
        // Deliberately never touches options.sampleRate: that knob samples
        // *error events*, and every error/feedback/crash must ship. Traces
        // are the only thing we sample (statistical data, heavy volume).
        tracesSampleRate?.let { options.tracesSampleRate = it }
        // Every feedback carrier event has an identical message
        // ("User feedback" / "Bug report") and no stacktrace, so Sentry
        // would group them all into one issue. Give each its own
        // fingerprint (keyed by a per-feedback id set in
        // captureUserFeedback) so every report is its own issue —
        // individually triageable and resolvable. Other events fall
        // through untouched.
        options.beforeSend = { event ->
            event.getTag(FEEDBACK_EVENT_TAG)?.let { id ->
                event.fingerprint = mutableListOf(FEEDBACK_FINGERPRINT, id)
            }
            event
        }
    }

    data class LogPolicy(
        val minBreadcrumbLevel: LogLevel,
        val minEventLevel: LogLevel,
        /**
         * Lowest level retained in the in-memory ring buffer dumped onto user
         * feedback (null = no buffer). Set below [minBreadcrumbLevel] to keep
         * the fine-grained detail we don't ship — debug builds buffer Verbose+,
         * release buffers Debug+ (skips per-frame Verbose churn).
         */
        val minBufferLevel: LogLevel? = null,
    )

    companion object {
        fun forApp(buildInfo: BuildInfo): SentryRuntimeConfig {
            val platformTag = when (buildInfo.platform) {
                Platform.Android -> "android"
                Platform.iOS -> "ios"
            }
            val buildTypeTag = buildInfo.buildType
            // All platforms / build types report to a single Sentry project.
            // The `environment` tag (releaseChannel-platform-buildType) and the
            // `platform` extra separate debug vs release and iOS vs Android
            // within it, so one DSN is enough. Injected at build time (CI env /
            // local.properties — see loadTelemetryMetadata in build-logic);
            // blank leaves crash reporting disabled.
            val dsn = TelemetryInfo.sentryDsn
            val environment = "${buildInfo.releaseChannel}-$platformTag-$buildTypeTag"
            // Must stay in lockstep with the release-upload step in the iOS
            // deploy lane — Sentry attaches events to releases by exact
            // string match.
            val release = "opson@${buildInfo.versionName}+${buildInfo.buildNumber}"
            val tracesSampleRate = if (buildInfo.isDebug) 1.0 else 0.15
            val breadcrumbLevel = if (buildInfo.isDebug) LogLevel.Debug else LogLevel.Info
            return SentryRuntimeConfig(
                dsn = dsn,
                environment = environment,
                release = release,
                sendDefaultPii = false,
                attachStacktrace = true,
                tracesSampleRate = tracesSampleRate,
                platformTag = platformTag,
                buildTypeTag = buildTypeTag,
                logPolicy = LogPolicy(
                    minBreadcrumbLevel = breadcrumbLevel,
                    minEventLevel = LogLevel.Error,
                    minBufferLevel = if (buildInfo.isDebug) LogLevel.Verbose else LogLevel.Debug,
                ),
                enableAutoSessionTracking = true
            )
        }

        fun forIosExtension(buildInfo: BuildInfo): SentryRuntimeConfig {
            val base = forApp(buildInfo)
            val environment = "${buildInfo.releaseChannel}-ios-extension-${buildInfo.buildType}"
            return base.copy(
                environment = environment,
                release = base.release + "-extension",
                tracesSampleRate = if (buildInfo.isDebug) 0.25 else 0.05,
                logPolicy = LogPolicy(
                    minBreadcrumbLevel = LogLevel.Info,
                    minEventLevel = LogLevel.Error
                ),
                enableAutoSessionTracking = false
            )
        }
    }
}
