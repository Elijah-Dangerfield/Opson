package com.opson.server.routes

import com.opson.server.domain.ExampleData
import kotlinx.serialization.Serializable

/**
 * Wire DTO for `GET /v1/example`. Kept separate from the [ExampleData] domain
 * type so the JSON shape can evolve without touching the domain layer. Naming:
 * `*Response` for outbound, `*Request` for inbound, `*Dto` for nested elements.
 */
@Serializable
data class ExampleResponse(
    val message: String,
    val items: List<String>,
)

fun ExampleData.toResponse(): ExampleResponse = ExampleResponse(
    message = message,
    items = items,
)
