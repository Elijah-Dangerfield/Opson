package com.opson.libraries.foods.impl

import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.foods.BarcodeMissReporter
import com.opson.libraries.networking.NetworkClient
import com.opson.libraries.networking.authedCall
import com.opson.libraries.networking.retry.RetryPolicy
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import kotlin.uuid.Uuid

/** Mirrors the server's `BarcodeMissRequest`. */
@Serializable
data class BarcodeMissRequest(val id: String, val barcode: String)

/**
 * `POST /v1/foods/barcode-misses`. The id is made once per miss, outside the
 * retry, so a retried request is the same row on the server rather than a
 * second miss.
 *
 * The call runs in [appScope], not the caller's scope: "Search instead" right after a miss closes
 * the scanner and cancels its view model, and the report should still arrive.
 */
@ContributesBinding(AppScope::class)
@Inject
class HttpBarcodeMissReporter(
    private val networkClient: NetworkClient,
    private val appScope: AppCoroutineScope,
) : BarcodeMissReporter {

    override suspend fun report(code: String) {
        val request = BarcodeMissRequest(id = Uuid.random().toString(), barcode = code)
        appScope.launch {
            networkClient.authedCall("foods.barcode_miss", retry = RetryPolicy.idempotent()) { client ->
                client.post("/v1/foods/barcode-misses") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
            }
        }.join()
    }
}
