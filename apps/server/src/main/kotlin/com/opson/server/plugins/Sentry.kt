package com.opson.server.plugins

import com.opson.server.config.SentryConfig
import io.opentelemetry.api.trace.Span
import io.sentry.Sentry
import io.sentry.SentryOptions
import org.slf4j.LoggerFactory
import org.slf4j.MDC

/**
 * Centralised Sentry setup. Called once at startup before any other plugin so
 * later code can rely on `Sentry.captureException(...)` being live.
 *
 * **No-op when [SentryConfig.dsn] is null/blank** — the SDK stays uninitialised,
 * every `Sentry.captureXxx` becomes a cheap no-op, and we log one boot warning.
 * That's the default: the dependency is paid (jar size) but costs nothing at
 * runtime until you set `SENTRY_DSN`.
 *
 * **Never log [SentryConfig.dsn] verbatim** — it encodes a project secret.
 */
fun installSentry(config: SentryConfig) {
    val logger = LoggerFactory.getLogger("Sentry")
    val dsn = config.dsn?.takeUnless { it.isBlank() }
    if (dsn == null) {
        logger.warn("SENTRY_DSN not set — server error reporting disabled. Set it to enable.")
        return
    }

    Sentry.init { options: SentryOptions ->
        options.dsn = dsn
        options.environment = config.environment
        options.release = config.release
        // Performance tracing off by default — sampling is a separate, cost/PII
        // decision. Errors and breadcrumbs are still captured.
        options.tracesSampleRate = 0.0
        options.isAttachServerName = true
        options.isAttachThreads = false
        options.isEnableUncaughtExceptionHandler = true
    }
    logger.info("Sentry initialised (environment={}, release={})", config.environment, config.release ?: "<unset>")
}

/**
 * Forwards an exception to Sentry if it's been initialised; no-op otherwise.
 * Stamps the active OpenTelemetry span's `trace_id`/`span_id` as tags on a
 * **local** scope (not the global one) so the Sentry issue links to its trace
 * and the tags don't leak onto later events.
 */
fun captureToSentry(throwable: Throwable, context: String? = null) {
    if (!Sentry.isEnabled()) return
    val spanContext = Span.current().spanContext
    // CallLogging seeds session_id/install_id into MDC for the duration of the
    // call (plugins/Observability.kt), so tag the error with the same session
    // the client tags its own Sentry events with — backend and frontend errors
    // for one session line up under a single id.
    val sessionId = MDC.get("session_id")
    val installId = MDC.get("install_id")
    Sentry.captureException(throwable) { scope ->
        if (context != null) scope.setTag("context", context)
        if (spanContext.isValid) {
            scope.setTag("trace_id", spanContext.traceId)
            scope.setTag("span_id", spanContext.spanId)
        }
        if (sessionId != null) scope.setTag("session_id", sessionId)
        if (installId != null) scope.setTag("install_id", installId)
    }
}
