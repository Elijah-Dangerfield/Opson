package com.opson.server.data

import com.opson.server.di.ServerScope
import com.opson.server.domain.Nutrients
import com.opson.server.domain.OpenFoodFactsClient
import com.opson.server.domain.OpenFoodFactsProduct
import com.opson.server.domain.OpenFoodFactsResult
import com.opson.server.domain.Portion
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Open Food Facts product lookup (`/api/v2/product/{code}.json`). No key; OFF
 * asks for a User-Agent that names the app, so every request sends one. The
 * whole call is capped at [TIMEOUT_MS]: a slow OFF must not hold a scan open.
 *
 * Values come back per 100 g in grams for every nutrient; [map] converts the
 * micros to the unit their key names (`sodium_mg`, `vitamin_d_ug`) so they line
 * up with the USDA rows.
 *
 * Tests inject Ktor's `MockEngine` through the secondary constructor.
 */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
class HttpOpenFoodFactsClient(
    engine: HttpClientEngine?,
    private val baseUrl: String = BASE_URL,
    private val timeoutMs: Long = TIMEOUT_MS,
) : OpenFoodFactsClient {

    @Inject
    constructor() : this(engine = null)

    private val client: HttpClient by lazy {
        val configure: io.ktor.client.HttpClientConfig<*>.() -> Unit = {
            install(HttpTimeout) {
                requestTimeoutMillis = timeoutMs
                connectTimeoutMillis = timeoutMs
                socketTimeoutMillis = timeoutMs
            }
            expectSuccess = false
        }
        if (engine != null) HttpClient(engine, configure) else HttpClient(CIO, configure)
    }

    override suspend fun product(barcode: String): OpenFoodFactsResult {
        return try {
            val response = client.get("$baseUrl/api/v2/product/$barcode.json") {
                header(HttpHeaders.UserAgent, USER_AGENT)
                parameter("fields", FIELDS)
            }
            when {
                response.status == HttpStatusCode.NotFound -> OpenFoodFactsResult.NotFound
                response.status.value >= 500 || response.status == HttpStatusCode.TooManyRequests ->
                    OpenFoodFactsResult.Unavailable(cause = null)

                response.status != HttpStatusCode.OK -> OpenFoodFactsResult.NotFound
                else -> map(barcode, JSON.parseToJsonElement(response.bodyAsText()).jsonObject)
                    ?.let { OpenFoodFactsResult.Found(it) }
                    ?: OpenFoodFactsResult.NotFound
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OpenFoodFactsResult.Unavailable(e)
        }
    }

    internal companion object {
        const val BASE_URL = "https://world.openfoodfacts.org"
        const val USER_AGENT = "Opson/0.1 (food logger)"
        const val TIMEOUT_MS = 5_000L
        const val FIELDS = "code,product_name,product_name_en,generic_name,brands,nutriments,serving_size,serving_quantity"

        private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }

        /** OFF key -> (our micros key, multiplier from grams). */
        private val MICROS = mapOf(
            "sugars_100g" to ("sugars_g" to 1.0),
            "added-sugars_100g" to ("added_sugars_g" to 1.0),
            "saturated-fat_100g" to ("saturated_fat_g" to 1.0),
            "cholesterol_100g" to ("cholesterol_mg" to 1_000.0),
            "sodium_100g" to ("sodium_mg" to 1_000.0),
            "potassium_100g" to ("potassium_mg" to 1_000.0),
            "calcium_100g" to ("calcium_mg" to 1_000.0),
            "iron_100g" to ("iron_mg" to 1_000.0),
            "magnesium_100g" to ("magnesium_mg" to 1_000.0),
            "zinc_100g" to ("zinc_mg" to 1_000.0),
            "vitamin-a_100g" to ("vitamin_a_rae_ug" to 1_000_000.0),
            "vitamin-c_100g" to ("vitamin_c_mg" to 1_000.0),
            "vitamin-d_100g" to ("vitamin_d_ug" to 1_000_000.0),
        )

        /** Null when OFF has no such product or nothing usable to show for it. */
        fun map(barcode: String, body: JsonObject): OpenFoodFactsProduct? {
            if ((body["status"] as? JsonPrimitive)?.intOrNull == 0) return null
            val product = body["product"] as? JsonObject ?: return null
            val name = listOf("product_name", "product_name_en", "generic_name")
                .firstNotNullOfOrNull { product.string(it)?.trim()?.takeIf(String::isNotEmpty) }
                ?: return null
            val brand = product.string("brands")?.split(',')?.firstOrNull()?.trim()?.takeIf(String::isNotEmpty)
            val n = product["nutriments"] as? JsonObject ?: JsonObject(emptyMap())
            val kcal = n.number("energy-kcal_100g") ?: n.number("energy_100g")?.let { kj -> kj / 4.184 }
            val micros = MICROS.mapNotNull { (offKey, target) ->
                n.number(offKey)?.let { grams -> target.first to round3(grams * target.second) }
            }.toMap()
            val servingGrams = product.number("serving_quantity")?.takeIf { it > 0 }
            val portions = servingGrams?.let {
                listOf(Portion(product.string("serving_size")?.trim()?.take(80)?.ifEmpty { null } ?: "1 serving", it))
            }.orEmpty()
            return OpenFoodFactsProduct(
                barcode = barcode,
                name = name.take(200),
                brand = brand?.take(200),
                per100g = Nutrients(
                    kcal = kcal?.let(::round1),
                    protein = n.number("proteins_100g"),
                    carbs = n.number("carbohydrates_100g"),
                    fat = n.number("fat_100g"),
                    fiber = n.number("fiber_100g"),
                ),
                micros = micros,
                portions = portions,
            )
        }

        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

        /** OFF sends numbers as numbers or strings depending on the product. Negative is garbage. */
        private fun JsonObject.number(key: String): Double? {
            val element: JsonElement = this[key] ?: return null
            val value = (element as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: return null
            return value.takeIf { it.isFinite() && it >= 0.0 }
        }

        private fun round1(value: Double) = Math.round(value * 10) / 10.0
        private fun round3(value: Double) = Math.round(value * 1000) / 1000.0
    }
}
