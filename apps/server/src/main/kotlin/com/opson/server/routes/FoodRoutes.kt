package com.opson.server.routes

import com.opson.server.domain.BarcodeLookup
import com.opson.server.domain.BarcodeLookupResult
import com.opson.server.domain.DescribeParser
import com.opson.server.domain.FoodCatalogRepository
import com.opson.server.domain.FoodDescriber
import com.opson.server.plugins.BARCODE_LOOKUP_LIMIT
import com.opson.server.plugins.SUPABASE_JWT_AUTH
import com.opson.server.plugins.problem
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.util.UUID

/**
 * The food catalog: search, detail, barcode, and "Describe it".
 *
 *  - `GET /v1/foods/search?q=&limit=25` ranked matches (see
 *    [FoodCatalogRepository]); records one anonymous search event.
 *  - `GET /v1/foods/{id}` one food with portions and micros.
 *  - `GET /v1/foods/barcode/{code}` catalog, then the enabled outside sources
 *    in tier order ([BarcodeLookup]). 404
 *    `not_found` when nobody knows the code, 503 `barcode_lookup_unavailable`
 *    when no source could be reached. Rate-limited per IP because
 *    every miss costs an outside call from the server's one IP.
 *  - `POST /v1/describe {text}` heuristic parse into items ([DescribeParser]).
 *
 * All require a Supabase JWT. None of them reads or writes user data, and
 * none logs a query: what someone searches for is about what they eat.
 */
fun Route.foodRoutes(
    catalog: FoodCatalogRepository,
    barcodeLookup: BarcodeLookup,
    describer: FoodDescriber,
) {
    authenticate(SUPABASE_JWT_AUTH) {
        get("/v1/foods/search") {
            val query = FoodCatalogRepository.normalizeQuery(call.request.queryParameters["q"].orEmpty())
            if (query.isEmpty()) {
                return@get call.respond(HttpStatusCode.BadRequest, problem("invalid_query", "q is required"))
            }
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                ?.coerceIn(1, FoodCatalogRepository.MAX_LIMIT)
                ?: FoodCatalogRepository.DEFAULT_LIMIT
            val matches = catalog.search(query, limit)
            call.respond(FoodSearchResponse(query = query, foods = matches.map { it.food.toResponse() }))
        }

        rateLimit(RateLimitName(BARCODE_LOOKUP_LIMIT)) {
            get("/v1/foods/barcode/{code}") {
                val code = call.parameters["code"].orEmpty().trim()
                if (!BarcodeLookup.isValidBarcode(code)) {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        problem("invalid_barcode", "A barcode is 8 to 14 digits"),
                    )
                }
                when (val result = barcodeLookup.lookup(code)) {
                    is BarcodeLookupResult.Found -> call.respond(result.food.toResponse())
                    BarcodeLookupResult.NotFound ->
                        call.respond(HttpStatusCode.NotFound, problem("not_found", "No food for that barcode"))

                    BarcodeLookupResult.Unavailable -> call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        problem("barcode_lookup_unavailable", "Barcode lookup is unavailable right now. Try again."),
                    )
                }
            }
        }

        get("/v1/foods/{id}") {
            val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@get call.respond(HttpStatusCode.BadRequest, problem("invalid_id", "id must be a UUID"))
            val food = catalog.findById(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, problem("not_found", "No such food"))
            call.respond(food.toResponse())
        }

        post("/v1/describe") {
            val text = call.receive<DescribeRequest>().text?.trim().orEmpty()
            if (text.isEmpty() || text.length > DescribeParser.MAX_TEXT) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    problem("invalid_text", "text must be 1 to ${DescribeParser.MAX_TEXT} characters"),
                )
            }
            call.respond(DescribeResponse(items = describer.describe(text).map { it.toDto() }))
        }
    }
}
