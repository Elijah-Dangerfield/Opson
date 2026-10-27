package com.opson.libraries.foods.impl

import com.opson.libraries.foods.MealPhotoEstimator
import com.opson.libraries.foods.PhotoEstimate
import com.opson.libraries.networking.NetworkClient
import com.opson.libraries.networking.authedCall
import com.opson.libraries.networking.isOfflineError
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Mirrors the server's `PhotoEstimateRequest`: the image as base64, and the optional note. */
@Serializable
data class PhotoEstimateRequest(val image: String, val note: String? = null)

/**
 * `POST /v1/describe/photo`. Never retried: every call is a paid model call
 * on the server, and "Try again" is the person's to press. The answer can
 * take a while (a vision model reads the photo), so the timeout is longer
 * than the app's default. Nothing about the photo or the note is logged.
 */
@ContributesBinding(AppScope::class)
@Inject
class HttpMealPhotoEstimator(private val networkClient: NetworkClient) : MealPhotoEstimator {

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun estimate(jpeg: ByteArray, note: String?): PhotoEstimate {
        val request = PhotoEstimateRequest(image = Base64.encode(jpeg), note = note?.trim()?.ifEmpty { null })
        return networkClient.authedCall("foods.photo_estimate") { client ->
            val response = client.post(PATH) {
                // Statuses are part of the answer here (503, 429), so they are read rather than thrown.
                expectSuccess = false
                // The socket timeout too: the server sends nothing until the model answers, and the
                // client's default socket timeout would give up long before the request timeout.
                timeout {
                    requestTimeoutMillis = TIMEOUT_MS
                    socketTimeoutMillis = TIMEOUT_MS
                }
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            when (response.status) {
                HttpStatusCode.OK -> PhotoEstimate.Items(response.body<DescribeResponse>().items.map { it.toDomain() })
                // 503: no key on the server. 404: a server from before the route existed.
                HttpStatusCode.ServiceUnavailable, HttpStatusCode.NotFound -> PhotoEstimate.Unavailable
                HttpStatusCode.TooManyRequests -> PhotoEstimate.TooMany
                else -> PhotoEstimate.Failed(offline = false)
            }
        }.getOrElse { PhotoEstimate.Failed(offline = it.isOfflineError()) }
    }

    internal companion object {
        const val PATH = "/v1/describe/photo"

        /** The server allows the model 30 s and one retry. */
        const val TIMEOUT_MS = 70_000L
    }
}
