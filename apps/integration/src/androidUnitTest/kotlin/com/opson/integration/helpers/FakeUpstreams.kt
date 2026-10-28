package com.opson.integration.helpers

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Stand-ins for the services outside Opson that the server calls, on one
 * local port, so a journey that reaches them never leaves the machine:
 *
 *  - **Supabase Auth admin** (`/auth/v1/admin/users/{id}`): a hard delete removes
 *    the `auth.users` row, as GoTrue does, and the database's `ON DELETE CASCADE`
 *    does the rest. A display-name mirror is accepted and ignored.
 *  - **Sign in with Apple** (`/auth/token`, `/auth/revoke`): a code becomes the
 *    refresh token `apple-refresh-<code>`, and revocations are recorded in
 *    [revokedAppleTokens].
 *  - **Anthropic Messages** (`/v1/messages`): answers [photoFoods] as the model's
 *    structured output, so the real `AnthropicEstimateProvider` and SDK parse it;
 *    a request with the label schema gets [labelOutput] instead, for
 *    `AnthropicLabelParser`, and is counted in [labelReads].
 *  - **Open Food Facts** (`/api/v2/product/{code}.json`): down when [openFoodFactsDown], otherwise knows the products in
 *    [openFoodFactsProducts] and nothing else, so any other code the catalog
 *    lacks is a clean miss.
 *
 * Everything Opson owns stays real; only these boundaries are faked.
 */
class FakeUpstreams(private val deleteAuthUser: (String) -> Unit) : AutoCloseable {

    /** What the photo model "sees": name, quantity, unit, grams. */
    @Volatile
    var photoFoods: List<PhotoFood> = listOf(PhotoFood("Broccoli, steamed", 1.0, "cup", 150.0))

    /**
     * What the label model "reads" off a pack: its structured output, in the
     * transcription schema (`LabelOutput` on the server). Units as printed.
     */
    @Volatile
    var labelOutput: String = GRANOLA_LABEL

    /** How many label reads reached the model. */
    val labelReads = java.util.concurrent.atomic.AtomicInteger()

    /** What Open Food Facts knows, by the code as asked. */
    val openFoodFactsProducts: MutableMap<String, OffProduct> = ConcurrentHashMap()

    /** Open Food Facts answers every lookup with a 500, as during an outage. */
    @Volatile
    var openFoodFactsDown = false

    /** Codes Open Food Facts was asked about, in order. */
    val openFoodFactsLookups: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** User ids the server asked Supabase to delete. */
    val deletedUsers: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Refresh tokens the server asked Apple to revoke, in order. */
    val revokedAppleTokens: MutableList<String> = Collections.synchronizedList(mutableListOf())

    data class PhotoFood(val name: String, val quantity: Double, val unit: String, val grams: Double)

    data class OffProduct(val name: String, val brand: String, val kcalPer100g: Double)

    private val engine: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
        embeddedServer(Netty, port = 0, host = "127.0.0.1") {
            routing {
                delete("/auth/v1/admin/users/{id}") {
                    val id = call.parameters["id"].orEmpty()
                    // GoTrue soft-deletes unless told otherwise, and a soft
                    // delete never fires the cascade.
                    if (call.receiveText().contains("\"should_soft_delete\":false")) {
                        deleteAuthUser(id)
                        deletedUsers += id
                    }
                    call.respondText("{}", ContentType.Application.Json)
                }
                put("/auth/v1/admin/users/{id}") {
                    call.respondText("{}", ContentType.Application.Json)
                }
                post("/auth/token") {
                    val code = call.receiveParameters()["code"].orEmpty()
                    call.respondText("""{"refresh_token":"apple-refresh-$code"}""", ContentType.Application.Json)
                }
                post("/auth/revoke") {
                    revokedAppleTokens += call.receiveParameters()["token"].orEmpty()
                    call.respondText("", ContentType.Text.Plain)
                }
                post("/v1/messages") {
                    // The label parser's schema is the only one with this key.
                    val body = call.receiveText()
                    if (body.contains("servings_per_container")) {
                        labelReads.incrementAndGet()
                        call.respondText(message(labelOutput), ContentType.Application.Json)
                    } else {
                        call.respondText(messagesReply(photoFoods), ContentType.Application.Json)
                    }
                }
                get("/api/v2/product/{file}") {
                    val code = call.parameters["file"].orEmpty().removeSuffix(".json")
                    openFoodFactsLookups += code
                    val product = openFoodFactsProducts[code]
                    if (openFoodFactsDown) {
                        call.respondText("upstream error", ContentType.Text.Plain, HttpStatusCode.InternalServerError)
                    } else if (product == null) {
                        call.respondText(
                            """{"status":0,"status_verbose":"product not found"}""",
                            ContentType.Application.Json,
                            HttpStatusCode.NotFound,
                        )
                    } else {
                        call.respondText(offReply(code, product), ContentType.Application.Json)
                    }
                }
            }
        }.start(wait = false)

    val baseUrl: String = runBlocking { "http://127.0.0.1:${engine.engine.resolvedConnectors().first().port}" }

    override fun close() {
        engine.stop(0, 0)
    }

    private fun offReply(code: String, product: OffProduct): String = buildJsonObject {
        put("status", 1)
        put("code", code)
        put(
            "product",
            buildJsonObject {
                put("code", code)
                put("product_name", product.name)
                put("brands", product.brand)
                put("nutriments", buildJsonObject { put("energy-kcal_100g", product.kcalPer100g) })
            },
        )
    }.toString()

    private fun message(text: String): String = buildJsonObject {
        put("id", "msg_integration")
        put("type", "message")
        put("role", "assistant")
        put("model", "claude-fable-5-1")
        put("content", buildJsonArray { add(buildJsonObject { put("type", "text"); put("text", text) }) })
        put("stop_reason", "end_turn")
        put("stop_sequence", JsonPrimitive(null as String?))
        put("usage", buildJsonObject { put("input_tokens", 1); put("output_tokens", 1) })
    }.toString()

    private fun messagesReply(foods: List<PhotoFood>): String {
        val output = buildJsonObject {
            put(
                "foods",
                buildJsonArray {
                    foods.forEach { food ->
                        add(
                            buildJsonObject {
                                put("name", food.name)
                                put("quantity", food.quantity)
                                put("unit", food.unit)
                                put("grams", food.grams)
                            },
                        )
                    }
                },
            )
        }
        return buildJsonObject {
            put("id", "msg_integration")
            put("type", "message")
            put("role", "assistant")
            put("model", "claude-opus-5-5")
            put("content", buildJsonArray { add(buildJsonObject { put("type", "text"); put("text", output.toString()) }) })
            put("stop_reason", "end_turn")
            put("stop_sequence", JsonPrimitive(null as String?))
            put("usage", buildJsonObject { put("input_tokens", 1); put("output_tokens", 1) })
        }.toString()
    }

    companion object {
        /** A US granola bar, 40 g: 190 kcal on 4 g protein, 29 g carbs and 7 g fat, which add up. */
        val GRANOLA_LABEL: String = """
            {"label_found":true,
             "name":{"value":"Oats & honey granola bar","confidence":0.95},
             "brand":{"value":"Hillside","confidence":0.9},
             "barcode":{"value":null,"confidence":0},
             "serving":{"amount":40,"unit":"g","household":"1 bar","confidence":0.95},
             "servings_per_container":{"value":6,"confidence":0.9},
             "basis":"us",
             "nutrients":[
               {"nutrient":"energy","per_serving":190,"per_100":null,"unit":"kcal","less_than":false,"confidence":0.95},
               {"nutrient":"protein","per_serving":4,"per_100":null,"unit":"g","less_than":false,"confidence":0.6},
               {"nutrient":"carbohydrate","per_serving":29,"per_100":null,"unit":"g","less_than":false,"confidence":0.95},
               {"nutrient":"fat","per_serving":7,"per_100":null,"unit":"g","less_than":false,"confidence":0.95},
               {"nutrient":"sodium","per_serving":150,"per_100":null,"unit":"mg","less_than":false,"confidence":0.9}
             ]}
        """.trimIndent()
    }
}
