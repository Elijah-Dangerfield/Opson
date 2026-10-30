package com.opson.server.routes

import com.opson.server.config.AdminConfig
import com.opson.server.domain.AdminRecipeFilter
import com.opson.server.domain.AdminRecipeSummary
import com.opson.server.domain.FoodCatalogRepository
import com.opson.server.domain.PublicRecipeRepository
import com.opson.server.domain.PublicRecipes
import com.opson.server.domain.RecipeReport
import com.opson.server.plugins.problem
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.UUID

/** One shared recipe in the console's list. */
@Serializable
data class AdminRecipeSummaryResponse(
    val id: String,
    val code: String? = null,
    val name: String,
    val ownerId: String,
    /** The owner's display name, whether or not they show it. */
    val ownerName: String? = null,
    val showName: Boolean,
    val state: String,
    val servings: Double,
    val ingredientCount: Int,
    val openReports: Int,
    val reasons: Map<String, Int> = emptyMap(),
    val oldestOpenReportAtEpochMs: Long? = null,
    val publishedAtEpochMs: Long? = null,
    val updatedAtEpochMs: Long,
    val saveCount: Int,
    val removedReason: String? = null,
)

@Serializable
data class AdminRecipeListResponse(val filter: String, val recipes: List<AdminRecipeSummaryResponse>)

@Serializable
data class AdminRecipeReportResponse(
    val id: String,
    val reason: String,
    val note: String? = null,
    val createdAtEpochMs: Long,
    val status: String,
)

/** Everything about one shared recipe: as the public sees it, and every report. */
@Serializable
data class AdminRecipeDetailResponse(
    val id: String,
    val ownerId: String,
    val ownerName: String? = null,
    val state: String,
    val notes: String? = null,
    val recipe: PublicRecipeResponse,
    val reports: List<AdminRecipeReportResponse>,
    val removedReason: String? = null,
)

@Serializable
data class RemoveRecipeRequest(val reason: String? = null)

@Serializable
data class RecipeModerationResponse(val changed: Boolean, val reports: Int = 0)

/**
 * Shared recipes in the console's Review area, behind the `X-Admin-Token` gate. The reviewer is
 * `X-Admin-Actor`.
 *
 *  - `GET  /v1/admin/recipes?filter=reported|hidden|live|removed&limit=50`
 *  - `GET  /v1/admin/recipes/{id}`                   as the public sees it, with every report
 *  - `POST /v1/admin/recipes/{id}/remove {reason}`   takes it down; its open reports are upheld
 *  - `POST /v1/admin/recipes/{id}/restore`           hidden goes back up, removed goes back to private
 *  - `POST /v1/admin/recipes/{id}/dismiss-reports`   closes the open reports; hidden goes back up
 */
fun Route.recipeAdminRoutes(config: AdminConfig, recipes: PublicRecipeRepository, catalog: FoodCatalogRepository) {
    val logger = LoggerFactory.getLogger("RecipeAdminRoutes")

    get("/v1/admin/recipes") {
        if (!call.requireAdmin(config)) return@get
        val filter = AdminRecipeFilter.fromWire(call.request.queryParameters["filter"] ?: AdminRecipeFilter.Reported.wire)
            ?: return@get call.respond(HttpStatusCode.BadRequest, problem("invalid_filter", "filter is reported, hidden, live or removed"))
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, MAX_LIST) ?: DEFAULT_LIST
        call.respond(AdminRecipeListResponse(filter.wire, recipes.adminList(filter, limit).map { it.toResponse() }))
    }

    get("/v1/admin/recipes/{id}") {
        if (!call.requireAdmin(config)) return@get
        val id = call.recipeId() ?: return@get
        val stored = recipes.adminGet(id) ?: return@get call.respond(HttpStatusCode.NotFound, problem("not_found", "No shared recipe with that id."))
        val served = PublicRecipes.serve(stored) { catalog.findById(it) }
        call.respond(
            AdminRecipeDetailResponse(
                id = stored.id.toString(),
                ownerId = stored.ownerId.value.toString(),
                ownerName = stored.ownerName,
                state = stored.state.wire,
                notes = stored.notes,
                recipe = served.toResponse(),
                reports = recipes.reports(id).map { it.toResponse() },
                removedReason = stored.removedReason,
            ),
        )
    }

    post("/v1/admin/recipes/{id}/remove") {
        if (!call.requireAdmin(config)) return@post
        val id = call.recipeId() ?: return@post
        val reason = runCatching { call.receive<RemoveRecipeRequest>() }.getOrNull()?.reason?.trim()?.takeIf { it.isNotEmpty() }
            ?: return@post call.respond(HttpStatusCode.BadRequest, problem("invalid_reason", "A reason is required."))
        val changed = recipes.remove(id, call.actor(), reason)
        logger.info("recipe admin action=remove changed={}", changed)
        if (changed) call.respond(RecipeModerationResponse(changed = true)) else call.respond(HttpStatusCode.NotFound, problem("not_found", "No shared recipe with that id."))
    }

    post("/v1/admin/recipes/{id}/restore") {
        if (!call.requireAdmin(config)) return@post
        val id = call.recipeId() ?: return@post
        val changed = recipes.restore(id, call.actor())
        logger.info("recipe admin action=restore changed={}", changed)
        if (changed) call.respond(RecipeModerationResponse(changed = true)) else call.respond(HttpStatusCode.NotFound, problem("not_found", "No shared recipe with that id."))
    }

    post("/v1/admin/recipes/{id}/dismiss-reports") {
        if (!call.requireAdmin(config)) return@post
        val id = call.recipeId() ?: return@post
        val dismissed = recipes.dismissReports(id, call.actor())
        logger.info("recipe admin action=dismiss reports={}", dismissed)
        call.respond(RecipeModerationResponse(changed = dismissed > 0, reports = dismissed))
    }
}

private fun AdminRecipeSummary.toResponse() = AdminRecipeSummaryResponse(
    id = recipe.id.toString(),
    code = recipe.code,
    name = recipe.name,
    ownerId = recipe.ownerId.value.toString(),
    ownerName = recipe.ownerName,
    showName = recipe.showName,
    state = recipe.state.wire,
    servings = recipe.servings,
    ingredientCount = itemCount,
    openReports = openReports,
    reasons = reasons,
    oldestOpenReportAtEpochMs = oldestOpenReportAt?.toEpochMilliseconds(),
    publishedAtEpochMs = recipe.publishedAt?.toEpochMilliseconds(),
    updatedAtEpochMs = recipe.updatedAt.toEpochMilliseconds(),
    saveCount = recipe.saveCount,
    removedReason = recipe.removedReason,
)

private fun RecipeReport.toResponse() = AdminRecipeReportResponse(
    id = id.toString(),
    reason = reason.wire,
    note = note,
    createdAtEpochMs = createdAt.toEpochMilliseconds(),
    status = status,
)

private suspend fun ApplicationCall.recipeId(): UUID? {
    val id = parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    if (id == null) respond(HttpStatusCode.BadRequest, problem("invalid_id", "Not an id."))
    return id
}

private fun ApplicationCall.actor(): String = request.header("X-Admin-Actor")?.trim()?.take(MAX_ACTOR)?.ifEmpty { null } ?: "admin"

private const val MAX_ACTOR = 100
private const val DEFAULT_LIST = 50
private const val MAX_LIST = 200
