package com.opson.server.data

import com.opson.server.config.AnthropicConfig
import com.opson.server.config.FoodSourcesConfig
import com.opson.server.domain.ImageType
import com.opson.server.domain.LabelParseResult
import com.opson.server.domain.NutrientKey
import com.opson.server.domain.PackPhotos
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The real SDK against a local stand-in for the Messages API answering with
 * the recorded fixtures, so the request and the reading of the answer are
 * both checked with no key and no network.
 */
class AnthropicLabelParserTest {

    private class Received(val body: JsonObject, val headers: Map<String, String>)

    private val received = mutableListOf<Received>()

    /** One reply per request, in order; the last repeats. */
    private var replies: List<Pair<HttpStatusCode, String>> = listOf(HttpStatusCode.OK to fixture("us_granola_bar"))

    private val server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
        embeddedServer(Netty, port = 0, host = "127.0.0.1") {
            routing {
                post("/v1/messages") {
                    val headers = call.request.headers.names().associateWith { call.request.headers[it].orEmpty() }
                    received += Received(Json.parseToJsonElement(call.receiveText()).jsonObject, headers.mapKeys { it.key.lowercase() })
                    val (status, body) = replies[minOf(received.size, replies.size) - 1]
                    call.respondText(body, ContentType.Application.Json, status)
                }
            }
        }.start(wait = false)

    private val baseUrl = runBlocking { "http://127.0.0.1:${server.engine.resolvedConnectors().first().port}" }

    private val photos = PackPhotos(TestImages.jpeg, ImageType.Jpeg, TestImages.png, ImageType.Png)

    @AfterTest
    fun stop() {
        server.stop(0, 0)
    }

    private fun parser() = AnthropicLabelParser(
        AnthropicConfig(apiKey = "test-key", photoModel = "claude-opus-5-5", labelModel = "claude-fable-5-1"),
        AnthropicEstimateProvider.defaultClient("test-key", baseUrl),
    )

    @Test
    fun sendsBothPhotosInOrderWithTheSchemaAndReadsTheLabel() = runBlocking<Unit> {
        val result = assertIs<LabelParseResult.Read>(parser().parse(photos))

        assertEquals("claude-fable-5-1", result.model)
        assertEquals(190.0, result.reading.facts.perServing[NutrientKey.Energy])
        val request = received.single()
        assertEquals("claude-fable-5-1", request.body["model"]?.jsonPrimitive?.content)
        assertEquals("default", request.body["fallbacks"]?.jsonPrimitive?.content)
        assertTrue(request.headers["anthropic-beta"].orEmpty().contains(AnthropicEstimateProvider.FALLBACK_BETA))
        val output = request.body["output_config"]!!.jsonObject
        assertEquals("high", output["effort"]?.jsonPrimitive?.content)
        val schema = output["format"]!!.jsonObject["schema"]!!.jsonObject
        assertEquals("false", schema["additionalProperties"]?.jsonPrimitive?.content)
        assertTrue("nutrients" in schema["required"]!!.jsonArray.map { it.jsonPrimitive.content })
        val content = request.body["messages"]!!.jsonArray.single().jsonObject["content"]!!.jsonArray
        val images = content.filter { it.jsonObject["type"]?.jsonPrimitive?.content == "image" }.map { it.jsonObject["source"]!!.jsonObject }
        assertEquals(listOf("image/jpeg", "image/png"), images.map { it["media_type"]?.jsonPrimitive?.content })
        assertEquals(Base64.getEncoder().encodeToString(TestImages.jpeg), images[0]["data"]?.jsonPrimitive?.content)
        assertTrue(content[0].jsonObject["text"]!!.jsonPrimitive.content.contains("front"))
        assertTrue(content[2].jsonObject["text"]!!.jsonPrimitive.content.contains("nutrition label"))
    }

    @Test
    fun aRefusalOrACutOffAnswerIsAFailureNeverParsed() = runBlocking<Unit> {
        replies = listOf(HttpStatusCode.OK to fixture("refusal"))
        assertEquals(LabelParseResult.Failed("refusal"), parser().parse(photos))
        replies = listOf(HttpStatusCode.OK to fixture("cut_off"))
        assertEquals(LabelParseResult.Failed("max_tokens"), parser().parse(photos))
    }

    @Test
    fun proseInsteadOfTheSchemaIsAFailure() = runBlocking<Unit> {
        replies = listOf(HttpStatusCode.OK to fixture("prose"))
        assertEquals(LabelParseResult.Failed("unparsable"), parser().parse(photos))
    }

    @Test
    fun aModelThatRejectsTheRequestFallsBackToThePhotoModel() = runBlocking<Unit> {
        replies = listOf(
            HttpStatusCode.BadRequest to """{"type":"error","error":{"type":"invalid_request_error","message":"retention"}}""",
            HttpStatusCode.OK to fixture("eu_yogurt"),
        )
        val result = assertIs<LabelParseResult.Read>(parser().parse(photos))

        assertEquals(listOf("claude-fable-5-1", "claude-opus-5-5"), received.map { it.body["model"]?.jsonPrimitive?.content })
        assertEquals(146.0, result.reading.facts.perServing[NutrientKey.Energy])
    }

    @Test
    fun aServerErrorIsAFailure() = runBlocking<Unit> {
        replies = listOf(HttpStatusCode.InternalServerError to """{"type":"error","error":{"type":"api_error","message":"x"}}""")
        assertIs<LabelParseResult.Failed>(parser().parse(photos))
    }

    @Test
    fun noKeyMeansNotConfigured() = runBlocking<Unit> {
        assertEquals(LabelParseResult.NotConfigured, ConfiguredLabelParser(FoodSourcesConfig()).parse(photos))
    }

    private companion object {
        fun fixture(name: String): String = checkNotNull(AnthropicLabelParserTest::class.java.getResource("/labels/$name.message.json")).readText()
    }
}
