package com.opson.server.data

import com.opson.server.config.NutritionixConfig
import com.opson.server.domain.BarcodeOutcome
import com.opson.server.domain.CatalogSource
import com.opson.server.domain.ExternalProduct
import com.opson.server.domain.FoodSource
import com.opson.server.domain.FoodSourceResult
import com.opson.server.domain.Nutrients
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import org.slf4j.LoggerFactory

/**
 * Nutritionix branded-food lookup (`GET /v2/search/item?upc=`), a paid,
 * licensed source (tier 3). Off unless `NUTRITIONIX_APP_ID` and
 * `NUTRITIONIX_APP_KEY` are both set; [ConfiguredLiveFoodSources] then asks it
 * before Open Food Facts.
 *
 * **Caching is off by default, on purpose.** Nutritionix's API terms limit
 * storing their data in your own database, and what a plan allows depends on
 * the contract. With [NutritionixConfig.cacheResults] false every answer is
 * served once and not written to `foods`, so the next scan of the same code
 * calls Nutritionix again (and costs a request). Turn
 * `NUTRITIONIX_CACHE_RESULTS` on only once the contract in hand says cached
 * copies are allowed; cached rows are stored as source `nutritionix`, tier 3.
 *
 * Nutritionix reports nutrients per serving. They are scaled to per 100 g
 * from `serving_weight_grams` (or a gram `nf_metric_qty`). A serving given
 * only in ml is scaled per 100 ml and its portion carries no grams, the same
 * as Branded Foods. A product with neither is not used.
 */
class NutritionixSource(
    private val config: NutritionixConfig,
    engine: HttpClientEngine? = null,
    private val baseUrl: String = BASE_URL,
    private val timeoutMs: Long = TIMEOUT_MS,
) : FoodSource {

    override val catalogSource = CatalogSource.Nutritionix
    override val outcome = BarcodeOutcome.Nutritionix
    override val cacheable: Boolean get() = config.cacheResults

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

    override suspend fun lookup(barcode: String): FoodSourceResult {
        return try {
            val response = client.get("$baseUrl/v2/search/item") {
                parameter("upc", barcode)
                header("x-app-id", config.appId)
                header("x-app-key", config.appKey)
                header(HttpHeaders.UserAgent, HttpOpenFoodFactsClient.USER_AGENT)
            }
            when (response.status) {
                HttpStatusCode.OK -> map(barcode, JSON.parseToJsonElement(response.bodyAsText()).jsonObject)
                    ?.let { FoodSourceResult.Found(it) }
                    ?: FoodSourceResult.NotFound

                HttpStatusCode.NotFound -> FoodSourceResult.NotFound
                HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> {
                    // Wrong keys or a lapsed plan. Loud, because every scan that
                    // misses our catalog is now going without it.
                    logger.error("Nutritionix rejected the app credentials (HTTP ${response.status.value})")
                    FoodSourceResult.Unavailable(cause = null)
                }

                else -> FoodSourceResult.Unavailable(cause = null) // 429 daily limit, 5xx
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FoodSourceResult.Unavailable(e)
        }
    }

    internal companion object {
        const val BASE_URL = "https://trackapi.nutritionix.com"
        const val TIMEOUT_MS = 5_000L

        private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }
        private val logger = LoggerFactory.getLogger(NutritionixSource::class.java)

        /** `full_nutrients` attr_id (the USDA nutrient number) -> our micros key. Same units. */
        private val MICROS = mapOf(
            269 to "sugars_g", 539 to "added_sugars_g", 606 to "saturated_fat_g", 605 to "trans_fat_g",
            601 to "cholesterol_mg", 307 to "sodium_mg", 306 to "potassium_mg", 301 to "calcium_mg",
            303 to "iron_mg", 304 to "magnesium_mg", 305 to "phosphorus_mg", 309 to "zinc_mg",
            320 to "vitamin_a_rae_ug", 401 to "vitamin_c_mg", 328 to "vitamin_d_ug", 430 to "vitamin_k_ug",
            404 to "thiamin_mg", 405 to "riboflavin_mg", 406 to "niacin_mg", 415 to "vitamin_b6_mg",
            435 to "folate_dfe_ug", 418 to "vitamin_b12_ug",
        )

        /** The same keys from the top-level `nf_*` fields, for responses without `full_nutrients`. */
        private val NF_MICROS = mapOf(
            "nf_sugars" to "sugars_g", "nf_saturated_fat" to "saturated_fat_g", "nf_cholesterol" to "cholesterol_mg",
            "nf_sodium" to "sodium_mg", "nf_potassium" to "potassium_mg",
        )

        /** Null when there is no product, or no serving size to turn per-serving numbers into per-100 g. */
        fun map(barcode: String, body: JsonObject): ExternalProduct? {
            val item = (body["foods"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
            val name = item.string("food_name")?.trim()?.takeIf(String::isNotEmpty) ?: return null
            val servingGrams = item.number("serving_weight_grams")?.takeIf { it > 0 }
            val metricQty = item.number("nf_metric_qty")?.takeIf { it > 0 }
            val metricUnit = item.string("nf_metric_uom")?.lowercase()
            val (basis, gramsPerServing) = when {
                servingGrams != null -> servingGrams to servingGrams
                metricQty != null && metricUnit == "g" -> metricQty to metricQty
                metricQty != null && metricUnit == "ml" -> metricQty to null
                else -> return null
            }
            val factor = 100.0 / basis
            fun per100(key: String) = item.number(key)?.let { round2(it * factor) }

            val full = (item["full_nutrients"] as? JsonArray).orEmpty().mapNotNull { element ->
                val o = element as? JsonObject ?: return@mapNotNull null
                val id = o.number("attr_id")?.toInt() ?: return@mapNotNull null
                val value = o.number("value") ?: return@mapNotNull null
                id to value
            }.toMap()
            val micros = if (full.isNotEmpty()) {
                MICROS.mapNotNull { (id, key) -> full[id]?.let { key to round3(it * factor) } }.toMap()
            } else {
                NF_MICROS.mapNotNull { (field, key) -> item.number(field)?.let { key to round3(it * factor) } }.toMap()
            }

            val qty = item.number("serving_qty")?.takeIf { it > 0 }
            val unit = item.string("serving_unit")?.trim()?.takeIf(String::isNotEmpty)
            val household = listOfNotNull(qty?.let(::numberText), unit).joinToString(" ").ifEmpty { "1 serving" }
            val label = if (gramsPerServing == null) "$household (${numberText(basis)} ml)" else household
            return ExternalProduct(
                barcode = barcode,
                name = name.take(200),
                brand = item.string("brand_name")?.trim()?.takeIf(String::isNotEmpty)?.take(200),
                per100g = Nutrients(
                    kcal = item.number("nf_calories")?.let { Math.round(it * factor * 10) / 10.0 },
                    protein = per100("nf_protein"),
                    carbs = per100("nf_total_carbohydrate"),
                    fat = per100("nf_total_fat"),
                    fiber = per100("nf_dietary_fiber"),
                ),
                micros = micros,
                portions = listOf(Portion(label.take(80), gramsPerServing?.let(::round2))),
            )
        }

        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

        private fun JsonObject.number(key: String): Double? =
            (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }

        private fun numberText(value: Double): String =
            if (value == Math.floor(value)) value.toLong().toString() else value.toString()

        private fun round2(value: Double) = Math.round(value * 100) / 100.0
        private fun round3(value: Double) = Math.round(value * 1000) / 1000.0
    }
}
