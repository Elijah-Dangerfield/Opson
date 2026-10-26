package com.opson.server.data

import com.opson.server.config.AnthropicConfig
import com.opson.server.config.FoodSourcesConfig
import com.opson.server.domain.EstimateResult
import com.opson.server.domain.EstimatedFood
import com.opson.server.domain.ImageType
import com.opson.server.testing.TestImages
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The real SDK against a local stand-in for the Messages API, so the request
 * the server sends and the way it reads the answer are both checked without a
 * key or a network.
 */
class AnthropicEstimateProviderTest {

    private class Received(val body: JsonObject, val headers: Map<String, String>)

    private val received = mutableListOf<Received>()
    private var status = HttpStatusCode.OK
    private var reply: String = message(text = """{"foods":[{"name":"Olive oil","quantity":1,"unit":"tbsp","grams":14},{"name":"Egg","quantity":2,"unit":"","grams":100}]}""")

    private val server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
        embeddedServer(Netty, port = 0, host = "127.0.0.1") {
            routing {
                post("/v1/messages") {
                    val headers = call.request.headers.names().associateWith { call.request.headers[it].orEmpty() }
                    received += Received(Json.parseToJsonElement(call.receiveText()).jsonObject, headers.mapKeys { it.key.lowercase() })
                    call.respondText(reply, ContentType.Application.Json, status)
                }
            }
        }.start(wait = false)

    private val baseUrl = runBlocking { "http://127.0.0.1:${server.engine.resolvedConnectors().first().port}" }

    @AfterTest
    fun stop() {
        server.stop(0, 0)
    }

    private fun provider(model: String = "claude-opus-5-5") = AnthropicEstimateProvider(
        AnthropicConfig(apiKey = "test-key", photoModel = model),
        AnthropicEstimateProvider.defaultClient("test-key", baseUrl),
    )

    @Test
    fun sendsTheImageNoteAndSchemaAndReadsTheFoods() = runBlocking<Unit> {
        val result = provider().estimate(TestImages.jpeg, ImageType.Jpeg, "cooked in olive oil")

        assertEquals(
            EstimateResult.Estimated(
                listOf(EstimatedFood("Olive oil", 1.0, "tbsp", 14.0), EstimatedFood("Egg", 2.0, null, 100.0)),
            ),
            result,
        )
        val request = received.single()
        assertEquals("test-key", request.headers["x-api-key"])
        assertTrue(request.headers["anthropic-beta"].orEmpty().contains(AnthropicEstimateProvider.FALLBACK_BETA))
        val body = request.body
        assertEquals("claude-opus-5-5", body["model"]?.jsonPrimitive?.content)
        assertEquals("default", body["fallbacks"]?.jsonPrimitive?.content)
        assertEquals(AnthropicEstimateProvider.MAX_TOKENS.toString(), body["max_tokens"]?.jsonPrimitive?.content)
        val output = body["output_config"]!!.jsonObject
        assertEquals("low", output["effort"]?.jsonPrimitive?.content)
        val schema = output["format"]!!.jsonObject["schema"]!!.jsonObject
        assertEquals(listOf("foods"), schema["required"]!!.jsonArray.map { it.jsonPrimitive.content })
        val content = body["messages"]!!.jsonArray.single().jsonObject["content"]!!.jsonArray
        val source = content[0].jsonObject["source"]!!.jsonObject
        assertEquals("image", content[0].jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals("image/jpeg", source["media_type"]?.jsonPrimitive?.content)
        assertEquals(Base64.getEncoder().encodeToString(TestImages.jpeg), source["data"]?.jsonPrimitive?.content)
        assertTrue(content[1].jsonObject["text"]!!.jsonPrimitive.content.endsWith("cooked in olive oil"))
    }

    @Test
    fun haikuGetsNoFallbacks() = runBlocking<Unit> {
        provider(model = "claude-haiku-5-5").estimate(TestImages.png, ImageType.Png, null)
        val request = received.single()
        assertNull(request.body["fallbacks"])
        assertFalse(request.headers["anthropic-beta"].orEmpty().contains("server-side-fallback"))
        val content = request.body["messages"]!!.jsonArray.single().jsonObject["content"]!!.jsonArray
        assertEquals("image/png", content[0].jsonObject["source"]!!.jsonObject["media_type"]?.jsonPrimitive?.content)
    }

    @Test
    fun aRefusalIsAFailureNeverParsed() = runBlocking<Unit> {
        reply = message(text = """{"foods":[{"name":"x","quantity":1,"unit":"","grams":1}]}""", stopReason = "refusal")
        assertEquals(EstimateResult.Failed("refusal"), provider().estimate(TestImages.jpeg, ImageType.Jpeg, null))
    }

    @Test
    fun anAnswerOutsideTheSchemaIsAFailure() = runBlocking<Unit> {
        reply = message(text = "I see a lovely plate of food.")
        assertEquals(EstimateResult.Failed("unparsable"), provider().estimate(TestImages.jpeg, ImageType.Jpeg, null))
    }

    @Test
    fun anHttpErrorIsAFailure() = runBlocking<Unit> {
        status = HttpStatusCode.BadRequest
        reply = """{"type":"error","error":{"type":"invalid_request_error","message":"nope"}}"""
        assertIs<EstimateResult.Failed>(provider().estimate(TestImages.jpeg, ImageType.Jpeg, null))
    }

    @Test
    fun noKeyMeansNotConfigured() = runBlocking<Unit> {
        assertEquals(EstimateResult.NotConfigured, ConfiguredEstimateProvider(FoodSourcesConfig()).estimate(TestImages.jpeg, ImageType.Jpeg, null))
    }

    @Test
    fun blankNamesAndZeroWeightsAreDropped() {
        val result = AnthropicEstimateProvider.parseText(
            """{"foods":[{"name":" ","quantity":1,"unit":"","grams":5},{"name":"Rice","quantity":1,"unit":"cup","grams":0}]}""",
        )
        assertEquals(EstimateResult.Estimated(listOf(EstimatedFood("Rice", 1.0, "cup", null))), result)
    }

    private companion object {
        fun message(text: String, stopReason: String = "end_turn"): String = buildJsonObject {
            put("id", "msg_test")
            put("type", "message")
            put("role", "assistant")
            put("model", "claude-opus-5-5")
            put(
                "content",
                buildJsonArray {
                    add(buildJsonObject { put("type", "text"); put("text", text) })
                },
            )
            put("stop_reason", stopReason)
            put("stop_sequence", JsonPrimitive(null as String?))
            put("usage", buildJsonObject { put("input_tokens", 3000); put("output_tokens", 200) })
        }.toString()
    }
}
