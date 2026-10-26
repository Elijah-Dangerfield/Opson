package com.opson.libraries.diary.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.diary.impl.wire.SyncRequestDto
import com.opson.libraries.diary.impl.wire.SyncResponseDto
import com.opson.libraries.networking.NetworkClient
import com.opson.libraries.networking.authedCall
import com.opson.libraries.networking.retry.RetryPolicy
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/** `POST /v1/sync`. An interface so the syncer's tests can fake the server. */
interface DiarySyncApi {
    suspend fun sync(request: SyncRequestDto): Catching<SyncResponseDto>
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class HttpDiarySyncApi(private val networkClient: NetworkClient) : DiarySyncApi {
    // Retry is safe here, unlike most POSTs: every mutation is a whole row
    // keyed by a client id and applied last-write-wins, so a replay changes
    // nothing on the server.
    override suspend fun sync(request: SyncRequestDto): Catching<SyncResponseDto> =
        networkClient.authedCall("diary.sync", retry = RetryPolicy.idempotent()) { client ->
            client.post("/v1/sync") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body<SyncResponseDto>()
        }
}
