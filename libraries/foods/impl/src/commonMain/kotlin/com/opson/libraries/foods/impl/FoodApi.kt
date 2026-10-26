package com.opson.libraries.foods.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.networking.NetworkClient
import com.opson.libraries.networking.authedCall
import com.opson.libraries.networking.retry.RetryPolicy
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/*
 * Wire DTOs for `/v1/foods/...` and `/v1/describe`, mirrored from the server's
 * `apps/server/.../routes/FoodDto.kt`.
 */

@Serializable
data class FoodResponse(
    val id: String,
    val source: String,
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val per100g: NutrientsDto,
    val micros: Map<String, Double> = emptyMap(),
    val portions: List<PortionDto> = emptyList(),
)

@Serializable
data class NutrientsDto(
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
)

@Serializable
data class PortionDto(val label: String, val grams: Double)

@Serializable
data class FoodSearchResponse(
    val query: String,
    val foods: List<FoodResponse>,
)

@Serializable
data class DescribeRequest(val text: String? = null)

@Serializable
data class DescribeResponse(val items: List<DescribedItemDto>)

@Serializable
data class DescribedItemDto(
    val text: String,
    val name: String,
    val foodId: String? = null,
    val quantity: Double,
    val unit: String? = null,
    val amountText: String,
    val portionLabel: String? = null,
    val grams: Double? = null,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
    val micros: Map<String, Double>? = null,
    val confidence: String,
)

/** The catalog endpoints. An interface so the catalog's tests can fake the server. */
interface FoodApi {
    suspend fun search(query: String, limit: Int): Catching<FoodSearchResponse>

    suspend fun detail(id: String): Catching<FoodResponse>

    /** Null for a 404: neither the catalog nor Open Food Facts knows the code. */
    suspend fun barcode(code: String): Catching<FoodResponse?>

    suspend fun describe(text: String): Catching<DescribeResponse>
}

/**
 * Every call here is a read (describe is a pure parse), so all of them retry.
 * None of them logs its argument: what someone searches for is about what
 * they eat.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class HttpFoodApi(private val networkClient: NetworkClient) : FoodApi {

    override suspend fun search(query: String, limit: Int): Catching<FoodSearchResponse> =
        networkClient.authedCall("foods.search", retry = RetryPolicy.idempotent()) { client ->
            client.get("/v1/foods/search") {
                parameter("q", query)
                parameter("limit", limit)
            }.body<FoodSearchResponse>()
        }

    override suspend fun detail(id: String): Catching<FoodResponse> =
        networkClient.authedCall("foods.detail", retry = RetryPolicy.idempotent()) { client ->
            client.get("/v1/foods/${id.encodeURLPathPart()}").body<FoodResponse>()
        }

    override suspend fun barcode(code: String): Catching<FoodResponse?> =
        networkClient.authedCall("foods.barcode", retry = RetryPolicy.idempotent()) { client ->
            try {
                client.get("/v1/foods/barcode/${code.encodeURLPathPart()}").body<FoodResponse>()
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.NotFound) null else throw e
            }
        }

    override suspend fun describe(text: String): Catching<DescribeResponse> =
        networkClient.authedCall("foods.describe", retry = RetryPolicy.idempotent()) { client ->
            client.post("/v1/describe") {
                contentType(ContentType.Application.Json)
                setBody(DescribeRequest(text))
            }.body<DescribeResponse>()
        }
}
