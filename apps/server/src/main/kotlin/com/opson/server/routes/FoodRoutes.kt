package com.opson.server.routes

import com.opson.server.domain.BarcodeLookup
import com.opson.server.domain.BarcodeLookupResult
import com.opson.server.domain.CatalogSource
import com.opson.server.domain.DescribeParser
import com.opson.server.domain.FoodCatalogRepository
import com.opson.server.domain.FoodDescriber
import com.opson.server.domain.FoodSubmissionRepository
import com.opson.server.domain.SearchCategory
import com.opson.server.domain.SearchFilter
import com.opson.server.domain.SearchSort
import com.opson.server.domain.SearchSource
import com.opson.server.http.clientContext
import com.opson.server.plugins.BARCODE_LOOKUP_LIMIT
import com.opson.server.plugins.DESCRIBE_LIMIT
import com.opson.server.plugins.FOOD_SEARCH_LIMIT
import com.opson.server.plugins.SUPABASE_JWT_AUTH
import com.opson.server.plugins.problem
import com.opson.server.plugins.userId
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
 *    [FoodCatalogRepository]); records one anonymous search event. Optional
 *    `category` (`restaurant`, `beverage`, `common`), `source` (`usda`,
 *    `off`) and `sort` (`best`, `name`) narrow or reorder it ([SearchFilter]);
 *    without them the call is exactly 0.1's. An unknown value is a 400.
 *    `credits=nevo` says the app shows NEVO's citation, which lets NEVO's
 *    foods into the results; without it there are none ([SearchFilter.nevo]).
 *  - `GET /v1/foods/search/options` which of those this server offers: `off`
 *    only when `FOOD_SEARCH_OFF_ENABLED` is on.
 *  - `GET /v1/foods/{id}` one food with portions and micros. A food someone
 *    submitted is 404 to everyone else until a reviewer approves it.
 *  - `GET /v1/foods/barcode/{code}` catalog, then the enabled outside sources
 *    in tier order ([BarcodeLookup]), then the caller's own submission still
 *    waiting for review, so a second scan finds what they just added. 404
 *    `not_found` when nobody knows the code, 503 `barcode_lookup_unavailable`
 *    when no source could be reached. Rate-limited per IP because
 *    every miss costs an outside call from the server's one IP.
 *  - `POST /v1/describe {text}` heuristic parse into items ([DescribeParser]).
 *
 * Search and describe have their own per-IP limits too (`RateLimits.kt`):
 * a search is the catalog's heaviest statement, and a description is up to
 * 20 of them.
 *
 * All require a Supabase JWT. None of them writes user data, and none logs
 * a query: what someone searches for is about what they eat.
 */
fun Route.foodRoutes(
    catalog: FoodCatalogRepository,
    barcodeLookup: BarcodeLookup,
    describer: FoodDescriber,
    submissions: FoodSubmissionRepository,
) {
    authenticate(SUPABASE_JWT_AUTH) {
        rateLimit(RateLimitName(FOOD_SEARCH_LIMIT)) {
            get("/v1/foods/search") {
                val query = FoodCatalogRepository.normalizeQuery(call.request.queryParameters["q"].orEmpty())
                if (query.isEmpty()) {
                    return@get call.respond(HttpStatusCode.BadRequest, problem("invalid_query", "q is required"))
                }
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                    ?.coerceIn(1, FoodCatalogRepository.MAX_LIMIT)
                    ?: FoodCatalogRepository.DEFAULT_LIMIT
                val params = call.request.queryParameters
                val category = params["category"]?.let {
                    SearchCategory.fromWire(it)
                        ?: return@get call.respond(HttpStatusCode.BadRequest, problem("invalid_category", "Unknown category"))
                }
                val source = params["source"]?.let {
                    SearchSource.fromWire(it)
                        ?: return@get call.respond(HttpStatusCode.BadRequest, problem("invalid_source", "Unknown source"))
                }
                val sort = params["sort"]?.let {
                    SearchSort.fromWire(it)
                        ?: return@get call.respond(HttpStatusCode.BadRequest, problem("invalid_sort", "Unknown sort"))
                } ?: SearchSort.BestMatch
                // Sources whose citation the app shows. Unknown names are ignored, so a newer app can list more.
                val credits = params["credits"].orEmpty().split(',').map { it.trim() }
                val filter = SearchFilter(category, source, sort, nevo = CatalogSource.Nevo.wire in credits)
                val matches = catalog.search(
                    query,
                    limit,
                    country = call.clientContext().countryCode,
                    openFoodFacts = true, // still only when the server's FOOD_SEARCH_OFF_ENABLED is on
                    filter = filter,
                )
                call.respond(FoodSearchResponse(query = query, foods = matches.map { it.food.toResponse() }))
            }
        }

        get("/v1/foods/search/options") {
            val sources = buildList {
                add(SearchSource.Usda.wire)
                if (catalog.searchesOpenFoodFacts) add(SearchSource.OpenFoodFacts.wire)
            }
            call.respond(SearchOptionsResponse(sources = sources, categories = SearchCategory.entries.map { it.wire }))
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
                    BarcodeLookupResult.NotFound -> {
                        val own = call.userId()?.let { submissions.pendingFoodFor(it, code) }?.let { catalog.findById(it) }
                        if (own != null) {
                            call.respond(own.toResponse())
                        } else {
                            call.respond(HttpStatusCode.NotFound, problem("not_found", "No food for that barcode"))
                        }
                    }

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
                ?.takeIf { it.source != CatalogSource.UserSubmitted || call.userId()?.let { user -> submissions.isVisible(id, user) } == true }
                ?: return@get call.respond(HttpStatusCode.NotFound, problem("not_found", "No such food"))
            call.respond(food.toResponse())
        }

        rateLimit(RateLimitName(DESCRIBE_LIMIT)) {
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
}
