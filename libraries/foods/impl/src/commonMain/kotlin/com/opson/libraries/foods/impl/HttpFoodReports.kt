package com.opson.libraries.foods.impl

import com.opson.libraries.foods.ContributionAttestation
import com.opson.libraries.foods.FoodReport
import com.opson.libraries.foods.FoodReportResult
import com.opson.libraries.foods.FoodReports
import com.opson.libraries.foods.SuggestedFoodValues
import com.opson.libraries.networking.NetworkClient
import com.opson.libraries.networking.authedCall
import com.opson.libraries.networking.isOfflineError
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
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
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** The server's `SuggestedJson`. */
@Serializable
internal data class SuggestedDto(
    val basisLabel: String? = null,
    val basisGrams: Double? = null,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val servingLabel: String? = null,
    val servingGrams: Double? = null,
) {
    companion object {
        fun of(v: SuggestedFoodValues) = SuggestedDto(
            v.basisLabel, v.basisGrams, v.kcal, v.protein, v.carbs, v.fat, v.servingLabel?.trim()?.ifEmpty { null }, v.servingGrams,
        )
    }
}

@Serializable
internal data class FoodReportRequest(
    val id: String,
    val reason: String,
    val suggested: SuggestedDto? = null,
    val note: String? = null,
    val photo: String? = null,
)

@Serializable
internal data class FoodReportResponse(val reportId: String, val status: String)

/**
 * `POST /v1/foods/{id}/reports`. Not retried automatically: it can carry a photo, and "Try
 * again" is the person's (the same id, so a report that did reach the server is not doubled).
 * Nothing in the report is logged.
 */
@ContributesBinding(AppScope::class)
@Inject
class HttpFoodReports(
    private val networkClient: NetworkClient,
    private val attestation: ContributionAttestation = ContributionAttestation.None,
) : FoodReports {

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun send(report: FoodReport): FoodReportResult {
        val request = FoodReportRequest(
            id = report.id,
            reason = report.reason.wire,
            suggested = report.suggested?.takeUnless { it.isEmpty }?.let(SuggestedDto::of),
            note = report.note?.trim()?.ifEmpty { null },
            photo = report.photo?.let { Base64.encode(it) },
        )
        val device = attestation.headers(report.id)
        return networkClient.authedCall("foods.report") { client ->
            val response = client.post(path(report.foodId)) {
                expectSuccess = false
                device.forEach { (name, value) -> header(name, value) }
                timeout {
                    requestTimeoutMillis = TIMEOUT_MS
                    socketTimeoutMillis = TIMEOUT_MS
                }
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            when (response.status) {
                HttpStatusCode.Created, HttpStatusCode.OK -> FoodReportResult.Sent(applied = response.body<FoodReportResponse>().status == ACCEPTED)
                HttpStatusCode.TooManyRequests -> FoodReportResult.TooMany
                // A food that went from the catalog, or a server from before the route.
                HttpStatusCode.NotFound -> FoodReportResult.Gone
                else -> FoodReportResult.Failed(offline = false)
            }
        }.getOrElse { FoodReportResult.Failed(offline = it.isOfflineError()) }
    }

    internal companion object {
        fun path(foodId: String) = "/v1/foods/${foodId.encodeURLPathPart()}/reports"
        private const val ACCEPTED = "accepted"

        /** A photo up a slow connection. */
        const val TIMEOUT_MS = 60_000L
    }
}
