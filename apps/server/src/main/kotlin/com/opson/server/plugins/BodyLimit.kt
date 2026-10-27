package com.opson.server.plugins

import io.ktor.server.application.Application
import io.ktor.server.request.ApplicationReceivePipeline
import io.ktor.server.request.contentLength
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

/**
 * The most any request body may carry. `/v1/sync` is the largest real body
 * (500 mutations, a few hundred KB in practice); the photo route reads its
 * own, tighter, 6 MB.
 */
const val MAX_REQUEST_BODY_BYTES: Long = 16L * 1024 * 1024

/**
 * Refuses a body over [maxBytes] with 413 `request_too_large` before it is
 * decoded: by `Content-Length` when the client sends one, and by stopping the
 * read at the cap when it doesn't (chunked). Without it `call.receive` read
 * whatever arrived into memory, so one authenticated client could exhaust the
 * server's heap with a single request.
 */
fun Application.installBodyLimit(maxBytes: Long = MAX_REQUEST_BODY_BYTES) {
    receivePipeline.intercept(ApplicationReceivePipeline.Before) { body ->
        if ((context.request.contentLength() ?: 0L) > maxBytes) throw RequestTooLargeException()
        if (body !is ByteReadChannel) return@intercept
        val bytes = body.readRemaining(maxBytes + 1).readByteArray()
        if (bytes.size > maxBytes) throw RequestTooLargeException()
        proceedWith(ByteReadChannel(bytes))
    }
}

/** A request body over the server's cap ([installBodyLimit]). StatusPages answers 413. */
class RequestTooLargeException : RuntimeException("request body too large")
