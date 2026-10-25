package com.opson.server.plugins

import com.opson.server.http.ClientContext
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.instrumentation.ktor.v3_0.KtorServerTelemetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * Installs automatic HTTP-server tracing: the OpenTelemetry Ktor plugin opens a
 * span per inbound request and closes it on response, so ordinary traffic
 * produces traces with no manual instrumentation. Pass the live SDK from
 * [installOpenTelemetry] (not [GlobalOpenTelemetry]) so the plugin binds to the
 * same instance even if the global slot was locked to noop first.
 */
fun Application.installHttpServerTracing(openTelemetry: OpenTelemetry) {
    install(KtorServerTelemetry) {
        setOpenTelemetry(openTelemetry)
        // Pin the correlation ids onto the HTTP root span directly from headers.
        // The root span's parent context is built by the plugin before any
        // request-scoped interceptor runs, so baggage isn't reliably present at
        // root-span creation — the extractor guarantees the most-queried span.
        // Child spans are covered by the baggage path below.
        attributesExtractor {
            onStart {
                request.headers[ClientContext.HEADER_SESSION_ID]?.takeIf { it.isNotBlank() }
                    ?.let { attributes.put(SpanAttrs.SessionIdCorrelation, it) }
                request.headers[ClientContext.HEADER_INSTALL_ID]?.takeIf { it.isNotBlank() }
                    ?.let { attributes.put(SpanAttrs.InstallIdCorrelation, it) }
            }
        }
    }

    // Carry the same ids in OTel Baggage for the rest of the request. Baggage
    // rides the OTel context across suspend boundaries, so every span created
    // during handling — including manual [withSpan] chains — inherits it as
    // parent-context baggage, and [BaggageAttributeSpanProcessor] copies it
    // onto each span. One value (`session_id`) therefore matches the whole
    // trace tree in Tempo, not just the HTTP root. Runs in the Plugins phase,
    // before route handlers, and wraps `proceed()` so the context propagates
    // downstream. Logs get the same ids via CallLogging MDC (Observability.kt).
    intercept(ApplicationCallPipeline.Plugins) {
        val session = call.request.headers[ClientContext.HEADER_SESSION_ID]?.takeIf { it.isNotBlank() }
        val install = call.request.headers[ClientContext.HEADER_INSTALL_ID]?.takeIf { it.isNotBlank() }
        if (session == null && install == null) return@intercept

        var builder = Baggage.current().toBuilder()
        session?.let { builder = builder.put(BAGGAGE_SESSION_ID, it) }
        install?.let { builder = builder.put(BAGGAGE_INSTALL_ID, it) }
        val context = builder.build().storeInContext(Context.current())

        withContext(context.asContextElement()) { proceed() }
    }
}

// Baggage keys — must match BaggageAttributeSpanProcessor's CORRELATION_BAGGAGE_KEYS
// and the SpanAttrs correlation keys so the value lands under one query string.
private const val BAGGAGE_SESSION_ID = "session_id"
private const val BAGGAGE_INSTALL_ID = "install_id"

/**
 * Span attribute keys shared across manually-instrumented paths. Centralised
 * so a rename touches one file and the same attribute reads identically in
 * Tempo / Jaeger / Honeycomb regardless of which span set it.
 */
internal object SpanAttrs {
    val UserId: AttributeKey<String> = AttributeKey.stringKey("user.id")

    /**
     * Client app-session correlation id from `X-Session-Id`. Underscore key,
     * deliberately matching the Sentry tag and Loki log field exactly so the
     * same query string works in all three systems. Set on every HTTP span via
     * the attributes extractor in [Application.installHttpServerTracing].
     */
    val SessionIdCorrelation: AttributeKey<String> = AttributeKey.stringKey("session_id")

    /** Per-install id from `X-Install-Id`; matches the Sentry `install_id` tag. */
    val InstallIdCorrelation: AttributeKey<String> = AttributeKey.stringKey("install_id")
}

internal const val SERVER_TRACER_NAME = "com.opson.server"

/** One tracer for the whole server; the span name disambiguates. */
internal val serverTracer: Tracer
    get() = GlobalOpenTelemetry.getTracer(SERVER_TRACER_NAME)

/**
 * Run [block] inside a fresh span, parented to the current OTel context (so
 * nested calls form a tree), recorded as `ERROR` on throw, ended in `finally`.
 * Propagates across `suspend` boundaries via [asContextElement]. Cancellation is
 * not an error — the span ends cleanly and re-throws.
 *
 * Use for manual spans around notable work; HTTP requests are traced
 * automatically by [installHttpServerTracing].
 *
 * ## Parenting to the *current* context is a trap on long-lived work
 *
 * "Parented to the current OTel context" is right for a request handler, where
 * the current context is that request's span and ends with it. It is wrong
 * anywhere the current context outlives the unit of work, and there are two
 * such places in this server the moment anyone uses them:
 *
 * 1. **A WebSocket handler.** [installWebSockets] is installed, and
 *    [KtorServerTelemetry] opens a span for the upgrade request. That span
 *    stays current for the entire life of the socket, so every `withSpan` in
 *    the handler — for hours, across every message and every connected user —
 *    nests under one root. Downstream this produced a single trace id covering
 *    six rooms, several users and hours of wall time, permanently stuck at
 *    "root span not yet received". Effectively one trace, so trace-level
 *    debugging stopped working entirely.
 * 2. **The shared `Dispatchers.Default` scope** (`provideServerCoroutineScope`).
 *    A coroutine that finishes while a context is installed can leave it on the
 *    pool thread, and the next unrelated unit of work scheduled there picks it
 *    up as its parent.
 *
 * The fix in both cases is to **root a new trace at the originator of each unit
 * of work** — per message, per job — and let everything below nest under that.
 * Be exhaustive about what counts as an originator: rooting only the obvious
 * handlers leaves the trace unbounded, just smaller.
 *
 * There are no `webSocket(...)` routes and no `withSpan` callers here yet, so
 * this is a trap rather than a live bug. It is written down because the
 * mechanism is fully assembled and the symptom — one enormous trace that never
 * completes — does not look like a tracing-setup problem when you meet it.
 */
internal suspend inline fun <T> withSpan(
    name: String,
    crossinline configure: Span.() -> Unit = {},
    crossinline block: suspend () -> T,
): T {
    val span = serverTracer.spanBuilder(name).startSpan().apply(configure)
    return try {
        withContext(span.asContextElement()) { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        span.setStatus(StatusCode.ERROR, t.message ?: t::class.java.simpleName)
        span.recordException(t)
        throw t
    } finally {
        span.end()
    }
}
