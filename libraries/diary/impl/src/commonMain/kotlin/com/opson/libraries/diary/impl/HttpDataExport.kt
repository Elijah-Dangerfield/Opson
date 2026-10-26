package com.opson.libraries.diary.impl

import com.opson.libraries.diary.DataExport
import com.opson.libraries.networking.NetworkClient
import com.opson.libraries.networking.authedCall
import com.opson.libraries.networking.retry.RetryPolicy
import io.ktor.client.call.body
import io.ktor.client.request.get
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/** `GET /v1/export.csv`. A read, so retrying it is safe. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class HttpDataExport(private val networkClient: NetworkClient) : DataExport {
    override suspend fun csv(): Result<ByteArray> =
        networkClient.authedCall("diary.export", retry = RetryPolicy.idempotent()) { client ->
            client.get("/v1/export.csv").body<ByteArray>()
        }
}
