package com.opson.server.plugins

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.writeFully
import kotlinx.io.readByteArray
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BodyLimitTest {

    private val limit = 1_024L

    private fun ApplicationTestBuilder.app() {
        application {
            installSerialization()
            installStatusPages()
            installBodyLimit(limit)
            routing {
                post("/json") { call.respondText("keys=${call.receive<JsonObject>().size}") }
                post("/raw") { call.respondText("bytes=${call.receiveChannel().readRemaining().readByteArray().size}") }
            }
        }
    }

    private fun json(bytes: Int) = "{\"a\":\"" + "x".repeat(bytes - 8) + "\"}"

    /** A body sent chunked, with no Content-Length to check up front. */
    private fun chunked(text: String) = object : OutgoingContent.WriteChannelContent() {
        override val contentType = ContentType.Application.Json
        override suspend fun writeTo(channel: ByteWriteChannel) = channel.writeFully(text.encodeToByteArray())
    }

    @Test
    fun aBodyUnderTheCapIsRead() = testApplication {
        app()
        val response = client.post("/json") {
            contentType(ContentType.Application.Json)
            setBody(json(900))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("keys=1", response.bodyAsText())
    }

    @Test
    fun aDeclaredLengthOverTheCapIs413() = testApplication {
        app()
        val response = client.post("/json") {
            contentType(ContentType.Application.Json)
            setBody(json(2_000))
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertTrue(response.bodyAsText().contains("request_too_large"))
    }

    @Test
    fun aChunkedBodyOverTheCapIs413() = testApplication {
        app()
        for (path in listOf("/json", "/raw")) {
            val response = client.post(path) { setBody(chunked(json(5_000))) }
            assertEquals(HttpStatusCode.PayloadTooLarge, response.status, path)
        }
    }
}
