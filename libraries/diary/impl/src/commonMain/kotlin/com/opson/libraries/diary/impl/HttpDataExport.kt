package com.opson.libraries.diary.impl

import com.opson.libraries.diary.DataExport
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.networking.NetworkClient
import com.opson.libraries.networking.authedCall
import com.opson.libraries.networking.retry.RetryPolicy
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * `GET /v1/export.zip`. A read, so retrying it is safe. The phone's time zone goes in a header,
 * not the URL, so the file's local times match the phone's clock.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class HttpDataExport(
    private val networkClient: NetworkClient,
    private val calendar: LocalCalendar,
) : DataExport {
    override suspend fun everything(): Result<ByteArray> =
        networkClient.authedCall("diary.export", retry = RetryPolicy.idempotent()) { client ->
            client.get("/v1/export.zip") { header(TIME_ZONE_HEADER, calendar.timeZone().id) }.body<ByteArray>()
        }

    private companion object {
        /** The server's `TIME_ZONE_HEADER`. */
        const val TIME_ZONE_HEADER = "X-Time-Zone"
    }
}
