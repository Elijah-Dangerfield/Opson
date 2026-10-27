package com.opson.integration.helpers

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
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
 * Stand-ins for the three services outside Opson that the server calls, on one
 * local port, so a journey that reaches them never leaves the machine:
 *
 *  - **Supabase Auth admin** (`/auth/v1/admin/users/{id}`): a hard delete removes
 *    the `auth.users` row, as GoTrue does, and the database's `ON DELETE CASCADE`
 *    does the rest. A display-name mirror is accepted and ignored.
 *  - **Anthropic Messages** (`/v1/messages`): answers [photoFoods] as the model's
 *    structured output, so the real `AnthropicEstimateProvider` and SDK parse it.
 *  - **Open Food Facts** (`/api/v2/product/{code}.json`): knows the products in
 *    [openFoodFactsProducts] and nothing else, so any other code the catalog
 *    lacks is a clean miss.
 *
 * Everything Opson owns stays real; only these boundaries are faked.
 */
class FakeUpstreams(private val deleteAuthUser: (String) -> Unit) : AutoCloseable {

    /** What the photo model "sees": name, quantity, unit, grams. */
    @Volatile
    var photoFoods: List<PhotoFood> = listOf(PhotoFood("Broccoli, steamed", 1.0, "cup", 150.0))

    /** What Open Food Facts knows, by the code as asked. */
    val openFoodFactsProducts: MutableMap<String, OffProduct> = ConcurrentHashMap()

    /** Codes Open Food Facts was asked about, in order. */
    val openFoodFactsLookups: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** User ids the server asked Supabase to delete. */
    val deletedUsers: MutableSet<String> = ConcurrentHashMap.newKeySet()

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
                post("/v1/messages") {
                    call.respondText(messagesReply(photoFoods), ContentType.Application.Json)
                }
                get("/api/v2/product/{file}") {
                    val code = call.parameters["file"].orEmpty().removeSuffix(".json")
                    openFoodFactsLookups += code
                    val product = openFoodFactsProducts[code]
                    if (product == null) {
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
}
