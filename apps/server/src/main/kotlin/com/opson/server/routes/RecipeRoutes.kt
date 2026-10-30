package com.opson.server.routes

import com.opson.server.domain.ContributionGate
import com.opson.server.domain.DiaryRules
import com.opson.server.domain.Food
import com.opson.server.domain.FoodCatalogRepository
import com.opson.server.domain.NewRecipeReport
import com.opson.server.domain.OpenContributionGate
import com.opson.server.domain.PublicRecipeRepository
import com.opson.server.domain.PublicRecipes
import com.opson.server.domain.PublicState
import com.opson.server.domain.PublishOutcome
import com.opson.server.domain.Recipe
import com.opson.server.domain.RecipeLimiter
import com.opson.server.domain.RecipeReportOutcome
import com.opson.server.domain.RecipeReportReason
import com.opson.server.domain.RecipeRules
import com.opson.server.domain.StoredRecipe
import com.opson.server.plugins.RECIPE_READ_LIMIT
import com.opson.server.plugins.RECIPE_WRITE_LIMIT
import com.opson.server.plugins.SUPABASE_JWT_AUTH
import com.opson.server.plugins.problem
import com.opson.server.plugins.userId
import io.ktor.http.CacheControl
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.cacheControl
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.time.Clock

/**
 * The one recipe route that needs no account: `GET /v1/recipes/public/{code}`, which the app and
 * the website's recipe page (`www.opsontracker.com/r/{code}`) both read. 404 `not_found` for a
 * code nobody has used, 410 `not_shared` for one whose owner stopped sharing, that a moderator
 * removed, that reports have hidden, or that was deleted. CORS is open for this GET alone: it
 * carries no credentials and answers only what anyone with the link can see.
 */
fun Route.publicRecipeRoutes(recipes: PublicRecipeRepository, catalog: FoodCatalogRepository) {
    route("/v1/recipes/public/{code}") {
        install(CORS) {
            anyHost()
            allowMethod(HttpMethod.Get)
            maxAgeInSeconds = PREFLIGHT_MAX_AGE_SECONDS
        }
        rateLimit(RateLimitName(RECIPE_READ_LIMIT)) {
            get {
                val code = call.parameters["code"]?.lowercase()?.takeIf(RecipeRules::isCode)
                    ?: return@get call.respond(HttpStatusCode.NotFound, problem("not_found", "No recipe has that code."))
                val stored = recipes.byCode(code)
                    ?: return@get call.respond(HttpStatusCode.NotFound, problem("not_found", "No recipe has that code."))
                if (!stored.isLive()) {
                    return@get call.respond(HttpStatusCode.Gone, problem("not_shared", "This recipe isn't shared right now."))
                }
                call.response.cacheControl(CacheControl.MaxAge(maxAgeSeconds = PUBLIC_MAX_AGE_SECONDS))
                call.respond(PublicRecipes.serve(stored, catalogLookup(catalog)).toResponse())
            }
        }
    }
}

/**
 * Sharing, for someone signed in:
 *
 *  - `POST /v1/recipes/{id}/publish {recipe, showName}`: writes the recipe as sync would, then
 *    shares it. `{code, state}`. A contribution: `refuseIfRestricted` first, then
 *    [RecipeLimiter]. 400 `invalid_id`, `invalid_recipe`, `empty_recipe`; 403
 *    `contributions_restricted`, `removed` (a moderator took it down); 404 `not_found` (someone
 *    else's id, or deleted); 429 `rate_limited`.
 *  - `POST /v1/recipes/{id}/unpublish`: 204, or 404. Never gated: stopping is always allowed.
 *  - `GET /v1/recipes/search?q=&limit=10`: live recipes whose name holds `q`, 2 to 100 characters.
 *  - `POST /v1/recipes/public/{code}/reports {id, reason, note?}`: 201, or 200 for a repeat. A
 *    contribution, like a food report. 400 `invalid_id`, `invalid_reason`, `invalid_note`,
 *    `own_recipe`; 404; 429.
 *  - `POST /v1/recipes/public/{code}/saves`: someone saved it to their recipes. 204, or 404.
 *
 * Logs carry outcomes and codes only, never a recipe's name or contents.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun Route.recipeRoutes(
    recipes: PublicRecipeRepository,
    catalog: FoodCatalogRepository,
    limiter: RecipeLimiter,
    clock: Clock,
    contributionGate: ContributionGate = OpenContributionGate,
) {
    val logger = LoggerFactory.getLogger("RecipeRoutes")

    authenticate(SUPABASE_JWT_AUTH) {
        rateLimit(RateLimitName(RECIPE_WRITE_LIMIT)) {
            post("/v1/recipes/{id}/publish") {
                val userId = call.userId() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_id", "The recipe id must be a UUID"))
                val request = runCatching { call.receive<PublishRecipeRequest>() }.getOrNull()
                val row = request?.recipe
                    ?: return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_recipe", "recipe is required"))
                val recipe = try {
                    DiaryRules.clampClock(row.toDomain(), clock.now()) as Recipe
                } catch (e: IllegalArgumentException) {
                    return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_recipe", e.message ?: "recipe is invalid"))
                }
                if (recipe.id != id) return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_id", "The id doesn't match the recipe"))
                DiaryRules.validate(recipe, clock.now())?.let { message ->
                    return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_recipe", message))
                }
                if (recipe.deleted || recipe.itemCount == 0) {
                    return@post call.respond(HttpStatusCode.BadRequest, problem("empty_recipe", "A recipe needs an ingredient to share"))
                }
                if (call.refuseIfRestricted(contributionGate, userId, id.toString(), logger, "recipe publish")) return@post
                if (!limiter.tryPublish(userId)) {
                    logger.info("recipe publish outcome=rate_limited")
                    return@post call.respond(HttpStatusCode.TooManyRequests, problem("rate_limited", "That's a lot of sharing for now. Try again later."))
                }
                when (val outcome = recipes.publish(userId, recipe, request.showName)) {
                    is PublishOutcome.Published -> {
                        logger.info("recipe publish outcome=published state={}", outcome.state.wire)
                        call.respond(PublishRecipeResponse(outcome.code, outcome.state.wire))
                    }
                    PublishOutcome.Removed -> {
                        logger.info("recipe publish outcome=removed")
                        call.respond(HttpStatusCode.Forbidden, problem("removed", "This recipe was taken down and can't be shared again."))
                    }
                    PublishOutcome.NotYours -> call.respond(HttpStatusCode.NotFound, problem("not_found", "No such recipe"))
                }
            }

            post("/v1/recipes/{id}/unpublish") {
                val userId = call.userId() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_id", "The recipe id must be a UUID"))
                if (recipes.unpublish(userId, id)) {
                    logger.info("recipe unpublish outcome=done")
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound, problem("not_found", "No such recipe"))
                }
            }

            post("/v1/recipes/public/{code}/reports") {
                val userId = call.userId() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val code = call.parameters["code"]?.lowercase()?.takeIf(RecipeRules::isCode)
                    ?: return@post call.respond(HttpStatusCode.NotFound, problem("not_found", "No recipe has that code."))
                val request = runCatching { call.receive<RecipeReportRequest>() }.getOrNull() ?: RecipeReportRequest()
                val reportId = request.id?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_id", "id must be a UUID"))
                val reason = RecipeReportReason.fromWire(request.reason)
                    ?: return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_reason", "Unknown reason"))
                val note = request.note?.trim()?.ifEmpty { null }
                if (note != null && (note.length > RecipeRules.MAX_REPORT_NOTE || '\u0000' in note)) {
                    return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_note", "At most ${RecipeRules.MAX_REPORT_NOTE} characters"))
                }
                if (call.refuseIfRestricted(contributionGate, userId, reportId.toString(), logger, "recipe report")) return@post
                if (!limiter.tryReport(userId)) {
                    logger.info("recipe report outcome=rate_limited")
                    return@post call.respond(HttpStatusCode.TooManyRequests, problem("rate_limited", "That's a lot of reports for now. Try again later."))
                }
                when (recipes.report(NewRecipeReport(reportId, userId, code, reason, note))) {
                    RecipeReportOutcome.Created -> {
                        logger.info("recipe report outcome=created reason={}", reason.wire)
                        call.respond(HttpStatusCode.Created)
                    }
                    RecipeReportOutcome.Repeat -> call.respond(HttpStatusCode.OK)
                    RecipeReportOutcome.NotFound -> call.respond(HttpStatusCode.NotFound, problem("not_found", "No recipe has that code."))
                    RecipeReportOutcome.OwnRecipe -> call.respond(HttpStatusCode.BadRequest, problem("own_recipe", "This is your own recipe."))
                }
            }
        }

        rateLimit(RateLimitName(RECIPE_READ_LIMIT)) {
            get("/v1/recipes/search") {
                call.userId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val query = call.request.queryParameters["q"]?.trim().orEmpty()
                if (query.length < RecipeRules.MIN_QUERY || query.length > RecipeRules.MAX_QUERY || '\u0000' in query) {
                    return@get call.respond(HttpStatusCode.BadRequest, problem("invalid_query", "q must be ${RecipeRules.MIN_QUERY} to ${RecipeRules.MAX_QUERY} characters"))
                }
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, MAX_SEARCH) ?: DEFAULT_SEARCH
                val lookup = catalogLookup(catalog)
                val hits = recipes.search(query, limit).map { PublicRecipes.serve(it, lookup).toResponse() }
                call.respond(RecipeSearchResponse(query, hits))
            }

            post("/v1/recipes/public/{code}/saves") {
                call.userId() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val code = call.parameters["code"]?.lowercase()?.takeIf(RecipeRules::isCode)
                    ?: return@post call.respond(HttpStatusCode.NotFound, problem("not_found", "No recipe has that code."))
                if (recipes.countSave(code)) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound, problem("not_found", "No recipe has that code."))
                }
            }
        }
    }
}

private fun StoredRecipe.isLive(): Boolean = state == PublicState.Live && !deleted && code != null

/** Catalog foods by id for one request, each looked up once however many recipes name it. */
private fun catalogLookup(catalog: FoodCatalogRepository): suspend (UUID) -> Food? {
    val seen = HashMap<UUID, Food?>()
    return { id -> if (seen.containsKey(id)) seen[id] else catalog.findById(id).also { seen[id] = it } }
}

private const val PREFLIGHT_MAX_AGE_SECONDS = 600L
private const val PUBLIC_MAX_AGE_SECONDS = 60
private const val DEFAULT_SEARCH = 10
private const val MAX_SEARCH = 20
