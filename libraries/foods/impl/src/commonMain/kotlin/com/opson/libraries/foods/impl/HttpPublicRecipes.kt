package com.opson.libraries.foods.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.Recipe
import com.opson.libraries.diary.RecipeShareState
import com.opson.libraries.diary.RecipeSharing
import com.opson.libraries.foods.ContributionAttestation
import com.opson.libraries.foods.PublicIngredient
import com.opson.libraries.foods.PublicRecipe
import com.opson.libraries.foods.PublicRecipeLookup
import com.opson.libraries.foods.PublicRecipes
import com.opson.libraries.foods.PublishResult
import com.opson.libraries.foods.RecipeActionResult
import com.opson.libraries.foods.RecipeNutrients
import com.opson.libraries.foods.RecipeReportReason
import com.opson.libraries.networking.NetworkClient
import com.opson.libraries.networking.authedCall
import com.opson.libraries.networking.isOfflineError
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/*
 * Wire DTOs for the recipe routes, mirrored from the server's `routes/RecipeDto.kt`.
 */

@Serializable
internal data class RecipeNutrientsDto(
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
) {
    fun toDomain() = RecipeNutrients(kcal, protein, carbs, fat, fiber)
}

@Serializable
internal data class PublicIngredientDto(
    val name: String,
    val amountText: String? = null,
    val grams: Double? = null,
    val nutrients: RecipeNutrientsDto = RecipeNutrientsDto(),
    val foodId: String? = null,
    val checked: Boolean = false,
)

@Serializable
internal data class PublicRecipeDto(
    val code: String,
    val name: String,
    val servings: Double,
    val notes: String? = null,
    val by: String? = null,
    val ingredients: List<PublicIngredientDto> = emptyList(),
    val total: RecipeNutrientsDto = RecipeNutrientsDto(),
    val perServing: RecipeNutrientsDto = RecipeNutrientsDto(),
    val checkedCount: Int = 0,
    val publishedAtEpochMs: Long? = null,
    val updatedAtEpochMs: Long = 0,
) {
    fun toDomain() = PublicRecipe(
        code = code,
        name = name,
        servings = servings,
        notes = notes,
        by = by,
        ingredients = ingredients.map {
            PublicIngredient(it.name, it.amountText, it.grams, it.nutrients.toDomain(), it.foodId, it.checked)
        },
        total = total.toDomain(),
        perServing = perServing.toDomain(),
    )
}

@Serializable
internal data class RecipeSearchDto(val query: String = "", val recipes: List<PublicRecipeDto> = emptyList())

/** The server's `RecipeRowDto`, as the publish route takes it. */
@Serializable
internal data class PublishRowDto(
    val id: String,
    val name: String,
    val servings: Double,
    val items: JsonArray,
    val notes: String? = null,
    val clientUpdatedAtEpochMs: Long,
    val savedFrom: String? = null,
)

@Serializable
internal data class PublishRequestDto(val recipe: PublishRowDto, val showName: Boolean)

@Serializable
internal data class PublishResponseDto(val code: String, val state: String)

@Serializable
internal data class RecipeReportDto(val id: String, val reason: String, val note: String? = null)

/**
 * The recipe routes. Sharing and reporting are contributions, so they carry
 * [ContributionAttestation]'s headers; reading, searching and stopping don't. Logs carry outcomes
 * and status codes only, never a recipe's name or what is in it.
 */
@ContributesBinding(AppScope::class)
@Inject
class HttpPublicRecipes(
    private val networkClient: NetworkClient,
    private val attestation: ContributionAttestation = ContributionAttestation.None,
) : PublicRecipes {

    private val logger = KLog.withTag("PublicRecipes")

    override suspend fun get(code: String): PublicRecipeLookup =
        networkClient.authedCall("recipes.get") { client ->
            val response = client.get("/v1/recipes/public/${code.encodeURLPathPart()}") { expectSuccess = false }
            when (response.status) {
                HttpStatusCode.OK -> PublicRecipeLookup.Found(response.body<PublicRecipeDto>().toDomain())
                HttpStatusCode.Gone -> PublicRecipeLookup.NotShared
                // A code nobody used, or a server from before recipes.
                HttpStatusCode.NotFound -> PublicRecipeLookup.NotFound
                else -> PublicRecipeLookup.Failed(offline = false)
            }
        }.getOrElse { PublicRecipeLookup.Failed(offline = it.isOfflineError()) }

    override suspend fun search(query: String): List<PublicRecipe> {
        val q = query.trim()
        if (q.length < MIN_QUERY) return emptyList()
        return networkClient.authedCall("recipes.search") { client ->
            val response = client.get("/v1/recipes/search") {
                expectSuccess = false
                parameter("q", q.take(MAX_QUERY))
            }
            if (response.status == HttpStatusCode.OK) response.body<RecipeSearchDto>().recipes.map { it.toDomain() } else emptyList()
        }.getOrElse { emptyList() }
    }

    override suspend fun publish(recipe: Recipe, showName: Boolean): PublishResult {
        val body = PublishRequestDto(
            recipe = PublishRowDto(
                id = recipe.id,
                name = recipe.name,
                servings = recipe.servings,
                items = Wire.encodeToJsonElement(DRAFTS, recipe.ingredients) as JsonArray,
                notes = recipe.notes,
                clientUpdatedAtEpochMs = recipe.updatedAtEpochMs,
                savedFrom = recipe.savedFrom,
            ),
            showName = showName,
        )
        val device = attestation.headers(recipe.id)
        return networkClient.authedCall("recipes.publish") { client ->
            val response = client.post("/v1/recipes/${recipe.id.encodeURLPathPart()}/publish") {
                expectSuccess = false
                device.forEach { (name, value) -> header(name, value) }
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            when (response.status) {
                HttpStatusCode.OK -> response.body<PublishResponseDto>().let {
                    PublishResult.Published(RecipeSharing(it.code, RecipeShareState.fromWire(it.state), showName))
                }
                HttpStatusCode.Forbidden -> if (response.problemCode() == REMOVED) PublishResult.Removed else PublishResult.Restricted
                HttpStatusCode.TooManyRequests -> PublishResult.TooMany
                else -> {
                    logger.w { "Sharing a recipe answered ${response.status.value}" }
                    PublishResult.Failed(offline = false)
                }
            }
        }.getOrElse { PublishResult.Failed(offline = it.isOfflineError()) }
    }

    override suspend fun unpublish(recipeId: String): RecipeActionResult =
        networkClient.authedCall("recipes.unpublish") { client ->
            client.post("/v1/recipes/${recipeId.encodeURLPathPart()}/unpublish") { expectSuccess = false }.toActionResult()
        }.getOrElse { RecipeActionResult.Failed(offline = it.isOfflineError()) }

    override suspend fun report(code: String, reportId: String, reason: RecipeReportReason, note: String?): RecipeActionResult {
        val device = attestation.headers(reportId)
        return networkClient.authedCall("recipes.report") { client ->
            client.post("/v1/recipes/public/${code.encodeURLPathPart()}/reports") {
                expectSuccess = false
                device.forEach { (name, value) -> header(name, value) }
                contentType(ContentType.Application.Json)
                setBody(RecipeReportDto(reportId, reason.wire, note?.trim()?.ifEmpty { null }))
            }.toActionResult()
        }.getOrElse { RecipeActionResult.Failed(offline = it.isOfflineError()) }
    }

    override suspend fun countSave(code: String) {
        networkClient.authedCall("recipes.save") { client ->
            client.post("/v1/recipes/public/${code.encodeURLPathPart()}/saves") { expectSuccess = false }
        }
    }

    private fun HttpResponse.toActionResult(): RecipeActionResult = when (status) {
        HttpStatusCode.OK, HttpStatusCode.Created, HttpStatusCode.NoContent -> RecipeActionResult.Done
        HttpStatusCode.NotFound -> RecipeActionResult.NotFound
        HttpStatusCode.Forbidden -> RecipeActionResult.Restricted
        HttpStatusCode.TooManyRequests -> RecipeActionResult.TooMany
        else -> RecipeActionResult.Failed(offline = false)
    }

    private suspend fun HttpResponse.problemCode(): String? =
        Catching { body<ProblemDto>().error.code }.getOrNull()

    @Serializable
    internal data class ProblemDto(val error: ProblemErrorDto)

    @Serializable
    internal data class ProblemErrorDto(val code: String, val message: String = "")

    private companion object {
        const val REMOVED = "removed"
        const val MIN_QUERY = 2
        const val MAX_QUERY = 100
        val DRAFTS = ListSerializer(LogDraft.serializer())

        /** As the diary writes its outbox: defaults written, nulls left out. */
        val Wire = Json {
            encodeDefaults = true
            explicitNulls = false
        }
    }
}
