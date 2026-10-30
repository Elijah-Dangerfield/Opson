package com.opson.libraries.foods.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.foods.FoodSearchFilter
import com.opson.libraries.foods.FoodSource
import com.opson.libraries.foods.SearchOrder
import com.opson.libraries.networking.NetworkClient
import com.opson.libraries.networking.authedCall
import com.opson.libraries.networking.retry.RetryPolicy
import com.opson.libraries.networking.retry.isTransientNetworkFailure
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.timeout
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
import kotlin.time.Duration.Companion.milliseconds

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
    /** 1 reference, 2 label, 3 licensed, 4 community. Absent from servers older than V10. */
    val tier: Int? = null,
    val verified: Boolean = false,
    /** `pending` or `rejected` on a food the person added that no reviewer approved yet; absent otherwise. */
    val reviewStatus: String? = null,
    /** Only from the barcode route asked with `matches=all`: every food for the code, this one first. */
    val matches: List<BarcodeMatchDto>? = null,
    /** A person checked it against its package (the Verified badge). Only sent when true, by V34 servers. */
    val checked: Boolean? = null,
)

/** One food a code matched. `energyCheck` is the server's energy guard; `corrected` a reviewer's fix. */
@Serializable
data class BarcodeMatchDto(
    val food: FoodResponse,
    val energyCheck: List<String> = emptyList(),
    val corrected: Boolean = false,
)

@Serializable
data class NutrientsDto(
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
)

/** `grams` is absent for a serving the label gives only in ml ("1 can (355 ml)"). */
@Serializable
data class PortionDto(val label: String, val grams: Double? = null)

@Serializable
data class FoodSearchResponse(
    val query: String,
    val foods: List<FoodResponse>,
)

/** `GET /v1/foods/search/options`. */
@Serializable
data class SearchOptionsResponse(val sources: List<String> = emptyList(), val categories: List<String> = emptyList())

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
    /** The matched food with its portions, from 0.2 servers. */
    val food: FoodResponse? = null,
    /** The next best foods for the same words, from 0.2 servers. */
    val alternatives: List<FoodResponse> = emptyList(),
)

/** The catalog endpoints. An interface so the catalog's tests can fake the server. */
interface FoodApi {
    suspend fun search(query: String, limit: Int, filter: FoodSearchFilter = FoodSearchFilter.None): Catching<FoodSearchResponse>

    suspend fun searchOptions(): Catching<SearchOptionsResponse>

    suspend fun detail(id: String): Catching<FoodResponse>

    /** [detail], with null for a 404 or 410: the server no longer has the food. */
    suspend fun detailOrGone(id: String): Catching<FoodResponse?> = detail(id).map { it }

    /** Null for a 404: neither the catalog nor Open Food Facts knows the code. */
    suspend fun barcode(code: String): Catching<FoodResponse?>

    /** [barcode] asking for every match too (`matches=all`). A server without the list ignores the ask. */
    suspend fun barcodeMatches(code: String): Catching<FoodResponse?> = barcode(code)

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

    override suspend fun search(query: String, limit: Int, filter: FoodSearchFilter): Catching<FoodSearchResponse> =
        networkClient.authedCall("foods.search", retry = WAITED_ON_RETRY) { client ->
            client.get("/v1/foods/search") {
                timeout { requestTimeoutMillis = SEARCH_TIMEOUT_MS }
                parameter("q", query)
                parameter("limit", limit)
                // Only the options that differ from the default, so an unfiltered search asks what 0.1 did.
                filter.category?.let { parameter("category", it.wire) }
                filter.source?.let { parameter("source", it.wire) }
                if (filter.order != SearchOrder.BestMatch) parameter("sort", filter.order.wire)
                // This app shows NEVO's citation (its source badge, the amount sheet, the Foods credit),
                // which RIVM requires wherever its foods appear; without this the server leaves them out.
                parameter("credits", FoodSource.Nevo.wire)
            }.body<FoodSearchResponse>()
        }

    override suspend fun searchOptions(): Catching<SearchOptionsResponse> =
        networkClient.authedCall("foods.search_options", retry = RetryPolicy.idempotent()) { client ->
            client.get("/v1/foods/search/options").body<SearchOptionsResponse>()
        }

    override suspend fun detail(id: String): Catching<FoodResponse> =
        networkClient.authedCall("foods.detail", retry = RetryPolicy.idempotent()) { client ->
            client.get("/v1/foods/${id.encodeURLPathPart()}").body<FoodResponse>()
        }

    override suspend fun detailOrGone(id: String): Catching<FoodResponse?> =
        networkClient.authedCall("foods.detail", retry = RetryPolicy.idempotent()) { client ->
            try {
                client.get("/v1/foods/${id.encodeURLPathPart()}").body<FoodResponse>()
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.NotFound || e.response.status == HttpStatusCode.Gone) null else throw e
            }
        }

    override suspend fun barcode(code: String): Catching<FoodResponse?> = barcode(code, allMatches = false)

    override suspend fun barcodeMatches(code: String): Catching<FoodResponse?> = barcode(code, allMatches = true)

    private suspend fun barcode(code: String, allMatches: Boolean): Catching<FoodResponse?> =
        networkClient.authedCall("foods.barcode", retry = WAITED_ON_RETRY) { client ->
            try {
                client.get("/v1/foods/barcode/${code.encodeURLPathPart()}") {
                    if (allMatches) parameter("matches", "all")
                }.body<FoodResponse>()
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.NotFound) null else throw e
            }
        }

    override suspend fun describe(text: String): Catching<DescribeResponse> =
        networkClient.authedCall("foods.describe", retry = WAITED_ON_RETRY) { client ->
            client.post("/v1/describe") {
                contentType(ContentType.Application.Json)
                setBody(DescribeRequest(text))
            }.body<DescribeResponse>()
        }

    internal companion object {
        /**
         * For the calls someone watches a spinner for: search and describe
         * (typed, and the next pause asks again anyway) and a barcode scan.
         * A 503 or a timeout means the server, or the outside sources a
         * barcode goes to, is behind, and three more tries (up to 23 s of
         * backoff on top of each wait) only added to its load, and to a
         * barcode's per-IP limit, before the cached answer or the error
         * showed. A dropped connection is retried once, quickly.
         */
        val WAITED_ON_RETRY: RetryPolicy = RetryPolicy.exponential(initial = 200.milliseconds, retries = 1)
            .retryIf { it !is ResponseException && it !is HttpRequestTimeoutException && isTransientNetworkFailure(it) }

        /** The server gives a search 5 s and the pool 3 s before it answers busy. */
        const val SEARCH_TIMEOUT_MS = 10_000L
    }
}
