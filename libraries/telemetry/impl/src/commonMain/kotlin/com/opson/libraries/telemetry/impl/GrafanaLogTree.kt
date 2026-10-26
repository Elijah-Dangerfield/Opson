package com.opson.libraries.telemetry.impl

import com.opson.libraries.core.BuildInfo
import com.opson.libraries.core.Catching
import com.opson.libraries.core.Platform
import com.opson.libraries.core.logging.EXTRA_APP_EVENT
import com.opson.libraries.core.logging.LogEntry
import com.opson.libraries.core.logging.LogId
import com.opson.libraries.core.logging.LogLevel
import com.opson.libraries.core.logging.LogTree
import com.opson.libraries.core.versionString
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.createOpenTelemetry
import io.opentelemetry.kotlin.export.TelemetryCloseable
import io.opentelemetry.kotlin.init.LogExportConfigDsl
import io.opentelemetry.kotlin.logging.SeverityNumber
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor

/**
 * Forwards `logEvent` entries (those carrying [EXTRA_APP_EVENT]) to Grafana
 * Cloud as OTLP log records. Planted alongside KermitLogTree/SentryLogTree,
 * so the same entry reaches logcat and Sentry breadcrumbs through those
 * trees regardless of what this one does.
 *
 * Second mode ([klogForwardingEnabled], `telemetry.klogForwardingEnabled`):
 * plain Warn+ entries are forwarded too, as ordinary OTLP logs (the message,
 * tag and exception type, no event name) so client errors land in Loki
 * without waiting on a Sentry crash. Events are never gated by that flag;
 * both modes sit behind the kill switch and per-session sampling.
 *
 * Deliberately direct-to-Grafana rather than through our backend: the
 * reliability events (`net.backend_unreachable`, reconnect failures) must
 * survive a backend outage. See `docs/plans/client-app-events-otel.md`.
 *
 * `session_id` / `install_id` / `is_offline` ride on every record (never as
 * resource attributes — the session rolls over mid-process on a 15-min
 * background, and connectivity flips freely). `is_offline` is captured at
 * emit time, so records that ship later from the disk buffer still say what
 * connectivity looked like when the event happened.
 *
 * [InstallFacts] go the other way — onto the Resource, resolved once at SDK
 * init — because they cannot change for the life of the install. That is also
 * what makes them cheap to filter on: resource attributes reach Loki as
 * structured metadata on every record without being restated per event.
 * All OTel types stay confined to this class: if 0.5.0 misbehaves, the tree
 * gets re-backed without touching call sites.
 */
class GrafanaLogTree(
    private val exportEnabled: () -> Boolean,
    private val sampleRate: () -> Double,
    private val klogForwardingEnabled: () -> Boolean,
    private val currentSessionId: () -> String?,
    private val currentInstallId: () -> String?,
    private val isOffline: () -> Boolean,
    private val installFacts: () -> InstallFacts,
    private val processorFactory: LogExportConfigDsl.() -> LogRecordProcessor,
) : LogTree() {

    private val openTelemetryLazy: Lazy<OpenTelemetry> = lazy {
        createOpenTelemetry {
            loggerProvider {
                serviceName = SERVICE_NAME
                resource {
                    setStringAttribute("service.version", BuildInfo.versionString())
                    setStringAttribute("deployment.environment", if (BuildInfo.isDebug) "dev" else "prod")
                    setStringAttribute(
                        "platform",
                        when (BuildInfo.platform) {
                            Platform.Android -> "android"
                            Platform.iOS -> "ios"
                        },
                    )
                    setLongAttribute("build_number", BuildInfo.buildNumber.toLong())
                    setStringAttribute("commit_sha", BuildInfo.commitSha)
                    setStringAttribute("release_channel", BuildInfo.releaseChannel)
                    installFacts().let { facts ->
                        setBooleanAttribute(GENUINE_INSTALL_KEY, facts.isGenuineInstall)
                        setBooleanAttribute(IS_EMULATOR_KEY, facts.isEmulator)
                        setBooleanAttribute(IS_SIDELOADED_KEY, facts.isSideloaded)
                        setBooleanAttribute(IS_ROOTED_KEY, facts.isRooted)
                        setStringAttribute(INSTALLER_PACKAGE_KEY, facts.source.value)
                        setStringAttribute(DEVICE_CLASS_KEY, facts.deviceClass.value)
                        setStringAttribute(OS_VERSION_KEY, facts.osVersion)
                    }
                }
                export { processorFactory() }
            }
        }
    }

    private val openTelemetry: OpenTelemetry by openTelemetryLazy

    private val eventLogger by lazy {
        openTelemetry.loggerProvider.getLogger(name = SERVICE_NAME, version = BuildInfo.versionString())
    }

    /**
     * Pushes any batched-in-RAM records through the disk buffer and attempts
     * an export. Called on app background ([TelemetryBackgroundFlusher]) so
     * the tail of a session doesn't ride only in memory when the OS suspends
     * or kills the process. A tree that never exported anything has no SDK to
     * flush — don't construct one just to flush it.
     */
    suspend fun flushExports() {
        if (!openTelemetryLazy.isInitialized()) return
        Catching { (openTelemetry as? TelemetryCloseable)?.forceFlush() }
    }

    override fun log(entry: LogEntry): LogId? {
        val eventName = entry.context.extras[EXTRA_APP_EVENT] as? String
        val forwardAsPlainLog = eventName == null &&
            entry.level.priority >= LogLevel.Warn.priority &&
            klogForwardingEnabled()
        if (eventName != null || forwardAsPlainLog) {
            Catching { forward(eventName, entry) }
        }
        return null
    }

    private fun forward(eventName: String?, entry: LogEntry) {
        if (!exportEnabled()) return
        val sessionId = currentSessionId()
        if (!isSessionSampledIn(sessionId)) return

        eventLogger.emit(
            body = entry.ownMessage() ?: entry.throwable?.typeName(),
            eventName = eventName,
            severityNumber = entry.level.toSeverityNumber(),
            attributes = {
                sessionId?.let { setStringAttribute(SESSION_ID_KEY, it) }
                currentInstallId()?.let { setStringAttribute(INSTALL_ID_KEY, it) }
                setBooleanAttribute(IS_OFFLINE_KEY, isOffline())
                if (eventName == null) {
                    entry.tag?.let { setStringAttribute(TAG_KEY, it) }
                    entry.throwable?.let { setStringAttribute(EXCEPTION_TYPE_KEY, it.typeName()) }
                }
                entry.context.tags.forEach { (key, value) -> setStringAttribute(key, value) }
                entry.context.extras.forEach { (key, value) ->
                    if (key == EXTRA_APP_EVENT) return@forEach
                    when (value) {
                        null -> Unit
                        is String -> setStringAttribute(key, value)
                        is Boolean -> setBooleanAttribute(key, value)
                        is Int -> setLongAttribute(key, value.toLong())
                        is Long -> setLongAttribute(key, value)
                        is Float -> setDoubleAttribute(key, value.toDouble())
                        is Double -> setDoubleAttribute(key, value)
                        else -> setStringAttribute(key, value.toString())
                    }
                }
            },
        )
    }

    /**
     * Stable per-session decision: hash the session id into [0, 1) and
     * compare against the rate, so one session's events are all-or-nothing
     * and a funnel never loses its middle step to per-event dice rolls.
     */
    private fun isSessionSampledIn(sessionId: String?): Boolean {
        val rate = sampleRate().coerceIn(0.0, 1.0)
        if (rate >= 1.0) return true
        if (rate <= 0.0) return false
        val id = sessionId ?: return false
        val bucket = (id.hashCode().toLong() and 0x7FFFFFFFL).toDouble() / Int.MAX_VALUE.toDouble()
        return bucket < rate
    }

    /**
     * The type and never the message. Exception messages are payloads: Ktor's
     * name the full request URL, query included (`foods.search` puts the
     * search text in `q`), and a `ResponseException` appends the response
     * body. `NetworkCall` logs every failed call at warn with its throwable,
     * so forwarding the message shipped what people search for to Loki.
     * Sentry still gets the whole throwable for debugging.
     */
    private fun Throwable.typeName(): String = this::class.simpleName ?: "Throwable"

    /** The caller's message, not the one `KLog` borrows from the throwable when there is none. */
    private fun LogEntry.ownMessage(): String? =
        message?.takeUnless { throwable.let { t -> t != null && it == t.message } }

    private fun LogLevel.toSeverityNumber(): SeverityNumber = when (this) {
        LogLevel.Verbose -> SeverityNumber.TRACE
        LogLevel.Debug -> SeverityNumber.DEBUG
        LogLevel.Info -> SeverityNumber.INFO
        LogLevel.Warn -> SeverityNumber.WARN
        LogLevel.Error -> SeverityNumber.ERROR
        LogLevel.Assert, LogLevel.Fatal -> SeverityNumber.FATAL
    }

    companion object {
        const val SERVICE_NAME = "opson-client"
        private const val SESSION_ID_KEY = "session_id"
        private const val INSTALL_ID_KEY = "install_id"
        private const val IS_OFFLINE_KEY = "is_offline"
        private const val TAG_KEY = "tag"
        private const val EXCEPTION_TYPE_KEY = "exception_type"
        internal const val GENUINE_INSTALL_KEY = "genuine_install"
        internal const val IS_EMULATOR_KEY = "is_emulator"
        internal const val IS_SIDELOADED_KEY = "is_sideloaded"
        internal const val IS_ROOTED_KEY = "is_rooted"
        internal const val INSTALLER_PACKAGE_KEY = "installer_package"
        internal const val DEVICE_CLASS_KEY = "device_class"
        internal const val OS_VERSION_KEY = "os_version"
    }
}
