package com.opson.server.plugins

import com.opson.server.http.ClientContext
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.slf4j.event.Level
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Wire request IDs and structured call logging. Every request gets a unique
 * X-Request-Id header that's surfaced in CallLogging's MDC so logs from one
 * request can be grepped together. Health checks are skipped to keep dev logs
 * readable; toggle the filter if you want them in production.
 *
 * The client's `session_id` / `install_id` are also lifted into MDC for the
 * duration of each call. The OTel logback appender forwards MDC entries as log
 * attributes (see logback.xml `captureMdcAttributes`), so every backend log
 * line is filterable in Loki by the same `session_id` the client tags its
 * Sentry events and request spans with — one id, all three systems.
 */
@OptIn(ExperimentalUuidApi::class)
fun Application.installObservability() {
    install(CallId) {
        retrieveFromHeader("X-Request-Id")
        generate { Uuid.random().toString() }
        verify { it.isNotBlank() }
    }
    install(CallLogging) {
        level = Level.INFO
        filter { call -> !call.request.path().startsWith("/_health") }
        format { call ->
            val status = call.response.status()?.toString() ?: "Unhandled"
            "$status: ${call.request.httpMethod.value} - ${redactedPath(call.request.path())} in ${call.processingTimeMillis()}ms"
        }
        mdc(MDC_SESSION_ID) { call ->
            call.request.header(ClientContext.HEADER_SESSION_ID)?.takeIf { it.isNotBlank() }
        }
        mdc(MDC_INSTALL_ID) { call ->
            call.request.header(ClientContext.HEADER_INSTALL_ID)?.takeIf { it.isNotBlank() }
        }
    }
}

/**
 * [path] with ids and barcodes masked: `/v1/foods/0b9c…` → `/v1/foods/{id}`,
 * `/v1/foods/barcode/0012345678905` → `/v1/foods/barcode/{code}`. A request
 * log line carries the session and install ids, so the raw path would be a
 * per-install list of what someone eats and scans (V13 keeps even barcode
 * misses unlinkable for this reason).
 */
internal fun redactedPath(path: String): String =
    path.split('/').joinToString("/") { segment ->
        when {
            UUID_SEGMENT.matches(segment) -> "{id}"
            CODE_SEGMENT.matches(segment) -> "{code}"
            else -> segment
        }
    }

private val UUID_SEGMENT = Regex("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
private val CODE_SEGMENT = Regex("[0-9]{4,}")

// MDC keys mirror the Sentry tags and OTel span attribute keys so a session's
// logs, traces, and crash reports all answer to the same query string.
private const val MDC_SESSION_ID = "session_id"
private const val MDC_INSTALL_ID = "install_id"
