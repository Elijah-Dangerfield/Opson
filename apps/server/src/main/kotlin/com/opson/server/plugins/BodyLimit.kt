package com.opson.server.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.request.ApplicationReceivePipeline
import io.ktor.server.request.contentLength
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.util.AttributeKey
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

/**
 * The most any request body may carry. `/v1/sync` is the largest real body
 * (500 mutations, a few hundred KB in practice); routes that know their bodies
 * are smaller say so with [bodyLimit].
 */
const val MAX_REQUEST_BODY_BYTES: Long = 16L * 1024 * 1024

/**
 * Refuses a body over [maxBytes], or over a route's own [bodyLimit], with 413
 * `request_too_large` before it is decoded: by `Content-Length` when the client
 * sends one, and by stopping the read at the cap when it doesn't (chunked).
 * Without it `call.receive` read whatever arrived into memory, so one
 * authenticated client could exhaust the server's heap with a single request.
 */
fun Application.installBodyLimit(maxBytes: Long = MAX_REQUEST_BODY_BYTES) {
    receivePipeline.intercept(ApplicationReceivePipeline.Before) { body ->
        val cap = context.attributes.getOrNull(RouteBodyLimitKey)?.coerceAtMost(maxBytes) ?: maxBytes
        if ((context.request.contentLength() ?: 0L) > cap) throw RequestTooLargeException()
        if (body !is ByteReadChannel) return@intercept
        val bytes = body.readRemaining(cap + 1).readByteArray()
        if (bytes.size > cap) throw RequestTooLargeException()
        proceedWith(ByteReadChannel(bytes))
    }
}

/**
 * A tighter cap than the server's for the routes [build] declares: a declared length over
 * [maxBytes] is refused before the handler runs, and `call.receive` stops reading at it.
 * A route that reads `receiveChannel()` itself still bounds its own read.
 */
fun Route.bodyLimit(maxBytes: Long, build: Route.() -> Unit): Route {
    val route = createChild(BodyLimitSelector(maxBytes))
    route.install(RouteBodyLimit) { this.maxBytes = maxBytes }
    route.build()
    return route
}

/** A request body over the server's cap ([installBodyLimit]) or a route's ([bodyLimit]). StatusPages answers 413. */
class RequestTooLargeException : RuntimeException("request body too large")

private val RouteBodyLimitKey = AttributeKey<Long>("RouteBodyLimit")

private class RouteBodyLimitConfig {
    var maxBytes: Long = MAX_REQUEST_BODY_BYTES
}

private val RouteBodyLimit = createRouteScopedPlugin("RouteBodyLimit", ::RouteBodyLimitConfig) {
    val maxBytes = pluginConfig.maxBytes
    onCall { call ->
        call.attributes.put(RouteBodyLimitKey, maxBytes)
        if ((call.request.contentLength() ?: 0L) > maxBytes) throw RequestTooLargeException()
    }
}

/** Matches whatever its parent matches: it only carries the limit. */
private class BodyLimitSelector(private val maxBytes: Long) : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation =
        RouteSelectorEvaluation.Transparent

    override fun toString(): String = "(body limit $maxBytes)"
}
