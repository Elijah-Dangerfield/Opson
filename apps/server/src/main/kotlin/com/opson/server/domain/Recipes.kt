package com.opson.server.domain

import com.opson.server.di.ServerScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Where a recipe stands with the public. Wire values match `recipes.public_state` (V35). */
enum class PublicState(val wire: String) {
    Private("private"),
    Live("live"),

    /** Reported by enough people that it waits for a person before anyone else sees it. */
    Hidden("hidden"),

    /** Taken down by a moderator. Its owner can't share it again. */
    Removed("removed"),
    ;

    companion object {
        fun fromWire(value: String?): PublicState = entries.firstOrNull { it.wire == value } ?: Private
    }
}

/** The server's sharing state on a recipe, which sync hands its owner and never takes from them. */
data class RecipeSharing(val code: String?, val state: PublicState, val showName: Boolean)

/** What someone says is wrong with a shared recipe. Wire values match the `recipe_reports.reason` CHECK. */
enum class RecipeReportReason(val wire: String) {
    WrongNutrition("wrong_nutrition"),
    Unsafe("unsafe"),
    Offensive("offensive"),
    Spam("spam"),
    Other("other"),
    ;

    companion object {
        fun fromWire(value: String?): RecipeReportReason? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * One ingredient as the client saved it: the fields of its `LogDraft` the public copy uses. The
 * rest of the snapshot (a custom food's id, micros, how it was logged) stays private.
 */
@Serializable
data class RecipeItemJson(
    val name: String = "",
    val amountText: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val grams: Double? = null,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
    val foodId: String? = null,
)

/** A shared recipe as stored: the synced row with its owner and sharing state. */
data class StoredRecipe(
    val id: UUID,
    val ownerId: UserId,
    val name: String,
    val servings: Double,
    val itemsJson: String,
    val notes: String?,
    val code: String?,
    val state: PublicState,
    val showName: Boolean,
    val ownerName: String?,
    val deleted: Boolean,
    val publishedAt: Instant?,
    val updatedAt: Instant,
    val saveCount: Int,
    val removedReason: String? = null,
)

/**
 * One ingredient as the public sees it. [checked] when its numbers were worked out from a verified
 * catalog food by its weight; otherwise they are the creator's.
 */
data class PublicIngredient(
    val name: String,
    val amountText: String?,
    val grams: Double?,
    val nutrients: Nutrients,
    val foodId: UUID?,
    val checked: Boolean,
)

/** A shared recipe as anyone may see it. [total] is the whole batch; divide by [servings] for one. */
data class PublicRecipe(
    val code: String,
    val name: String,
    val servings: Double,
    val notes: String?,
    /** The owner's display name when they chose to show it; null reads "an Opson user". */
    val by: String?,
    val ingredients: List<PublicIngredient>,
    val total: Nutrients,
    val publishedAt: Instant?,
    val updatedAt: Instant,
) {
    val perServing: Nutrients
        get() = Nutrients(
            kcal = total.kcal?.div(servings),
            protein = total.protein?.div(servings),
            carbs = total.carbs?.div(servings),
            fat = total.fat?.div(servings),
            fiber = total.fiber?.div(servings),
        )
}

/**
 * Turns a stored recipe into what the public sees. Nutrition is never the creator's totals: each
 * ingredient from the catalog with a weight is worked out again from the catalog's numbers per
 * 100 g, and counts as checked when that food is verified and approved. Anything else (a custom
 * food, a typed name, a catalog food logged by count) keeps the creator's numbers, unchecked. A
 * total with any ingredient unknown stays a floor of the known ones; with none known, null.
 */
object PublicRecipes {
    private val json = Json { ignoreUnknownKeys = true }

    fun items(itemsJson: String): List<RecipeItemJson> =
        runCatching { json.decodeFromString(ITEMS, itemsJson) }.getOrDefault(emptyList())

    suspend fun serve(recipe: StoredRecipe, findFood: suspend (UUID) -> Food?): PublicRecipe {
        val ingredients = items(recipe.itemsJson).map { item -> ingredient(item, findFood) }
        fun sum(pick: (Nutrients) -> Double?): Double? =
            ingredients.mapNotNull { pick(it.nutrients) }.takeIf { it.isNotEmpty() }?.sum()
        return PublicRecipe(
            code = checkNotNull(recipe.code) { "a shared recipe has a code" },
            name = recipe.name,
            servings = recipe.servings,
            notes = recipe.notes,
            by = recipe.ownerName?.takeIf { recipe.showName },
            ingredients = ingredients,
            total = Nutrients(sum { it.kcal }, sum { it.protein }, sum { it.carbs }, sum { it.fat }, sum { it.fiber }),
            publishedAt = recipe.publishedAt,
            updatedAt = recipe.updatedAt,
        )
    }

    private suspend fun ingredient(item: RecipeItemJson, findFood: suspend (UUID) -> Food?): PublicIngredient {
        val foodId = item.foodId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val grams = item.grams?.takeIf { it.isFinite() && it > 0.0 }
        val food = if (foodId != null && grams != null) findFood(foodId) else null
        val own = Nutrients(item.kcal.clean(), item.protein.clean(), item.carbs.clean(), item.fat.clean(), item.fiber.clean())
        if (food == null || grams == null) {
            return PublicIngredient(item.name, item.amountText, grams, own, foodId, checked = false)
        }
        fun scaled(per100: Double?): Double? = per100?.times(grams)?.div(GRAMS_PER_100)
        val fromCatalog = Nutrients(
            kcal = scaled(food.per100g.kcal),
            protein = scaled(food.per100g.protein),
            carbs = scaled(food.per100g.carbs),
            fat = scaled(food.per100g.fat),
            fiber = scaled(food.per100g.fiber),
        )
        val checked = food.verified && food.reviewStatus == SubmissionStatus.Approved
        return PublicIngredient(item.name, item.amountText, grams, fromCatalog, foodId, checked)
    }

    private fun Double?.clean(): Double? = this?.takeIf { it.isFinite() && it >= 0.0 && it <= DiaryRules.MAX_NUTRIENT }

    private const val GRAMS_PER_100 = 100.0
    private val ITEMS = kotlinx.serialization.builtins.ListSerializer(RecipeItemJson.serializer())
}

/** The limits on a recipe, checked by [DiaryRules] on sync and again on publish. */
object RecipeRules {
    const val MAX_NAME = 200
    const val MAX_NOTES = 4_000
    const val MAX_ITEMS = 60
    const val MIN_SERVINGS = 0.25
    const val MAX_SERVINGS = 1_000.0
    const val CODE_LENGTH = 8

    /** No 0, 1, i, l or o: a code read off a screen or a print can't be misread. */
    const val CODE_ALPHABET = "23456789abcdefghjkmnpqrstuvwxyz"

    /** The longest search a person types for a recipe. */
    const val MAX_QUERY = 100
    const val MIN_QUERY = 2
    const val MAX_REPORT_NOTE = 1_000

    /** Open reports from this many different people hide a shared recipe until someone looks. */
    const val HIDE_AFTER_REPORTS = 3

    /**
     * A publish body: the row sync would send, whose items are at most [DiaryRules.MAX_JSON_CHARS]
     * characters, with room for escaping and the notes.
     */
    const val MAX_PUBLISH_BODY_BYTES = 512L * 1024

    /** A report body: an id, a reason and a note of [MAX_REPORT_NOTE] characters. */
    const val MAX_REPORT_BODY_BYTES = 16L * 1024

    fun newCode(random: Random = Random.Default): String =
        String(CharArray(CODE_LENGTH) { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] })

    fun isCode(value: String): Boolean = value.length == CODE_LENGTH && value.all { it in CODE_ALPHABET }

    fun itemCount(items: JsonArray): Int = items.size
}

sealed interface PublishOutcome {
    data class Published(val code: String, val state: PublicState) : PublishOutcome

    /** A moderator took this recipe down; it can't be shared again. */
    data object Removed : PublishOutcome

    /** The id is someone else's, or the recipe was deleted. */
    data object NotYours : PublishOutcome
}

sealed interface RecipeReportOutcome {
    data object Created : RecipeReportOutcome

    /** This person already has an open report on it. */
    data object Repeat : RecipeReportOutcome
    data object NotFound : RecipeReportOutcome
    data object OwnRecipe : RecipeReportOutcome
}

data class NewRecipeReport(
    val id: UUID,
    val reporter: UserId,
    val code: String,
    val reason: RecipeReportReason,
    val note: String?,
)

/** One report as the console shows it. */
data class RecipeReport(
    val id: UUID,
    val reason: RecipeReportReason,
    val note: String?,
    val createdAt: Instant,
    val status: String,
)

/** A shared recipe in the console's list. */
data class AdminRecipeSummary(
    val recipe: StoredRecipe,
    val itemCount: Int,
    val openReports: Int,
    val reasons: Map<String, Int>,
    val oldestOpenReportAt: Instant?,
)

enum class AdminRecipeFilter(val wire: String) {
    Reported("reported"),
    Hidden("hidden"),
    Live("live"),
    Removed("removed"),
    ;

    companion object {
        fun fromWire(value: String?): AdminRecipeFilter? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * Shared recipes: the publish routes, the public reads and the console. Private recipes go
 * through [SyncRepository]; this only ever touches the sharing columns, apart from [publish],
 * which writes the row the same way sync would first, so a recipe can be shared before its first
 * sync has landed.
 */
interface PublicRecipeRepository {
    suspend fun publish(userId: UserId, recipe: Recipe, showName: Boolean): PublishOutcome

    /** False when the recipe isn't this person's. A removed recipe stays removed. */
    suspend fun unpublish(userId: UserId, recipeId: UUID): Boolean

    /** Any recipe that has ever been shared under [code], whatever its state; the route decides. */
    suspend fun byCode(code: String): StoredRecipe?

    /** Live recipes whose name holds [query], best first. */
    suspend fun search(query: String, limit: Int): List<StoredRecipe>

    /** Someone saved it to their recipes. Counted for ranking and the board. */
    suspend fun countSave(code: String): Boolean

    suspend fun report(report: NewRecipeReport): RecipeReportOutcome

    suspend fun adminList(filter: AdminRecipeFilter, limit: Int): List<AdminRecipeSummary>

    suspend fun adminGet(id: UUID): StoredRecipe?

    suspend fun reports(recipeId: UUID): List<RecipeReport>

    /** Takes it down and upholds its open reports. False when it was never shared. */
    suspend fun remove(id: UUID, actor: String, reason: String): Boolean

    /** A hidden recipe goes back up; a removed one goes back to private, for its owner to share again. Open reports are dismissed. */
    suspend fun restore(id: UUID, actor: String): Boolean

    /** Dismisses the open reports; a hidden recipe goes back up. Returns how many. */
    suspend fun dismissReports(id: UUID, actor: String): Int
}

/**
 * Per person: publishing is 10 an hour and 30 a day, a report 10 in 10 minutes and 30 a day.
 * In memory, like the other per-user limiters; a restart forgets, which only ever errs generous.
 */
@OptIn(ExperimentalTime::class)
@SingleIn(ServerScope::class)
@Inject
class RecipeLimiter(private val clock: Clock) {
    private val publishes = ConcurrentHashMap<UserId, ArrayDeque<Instant>>()
    private val reports = ConcurrentHashMap<UserId, ArrayDeque<Instant>>()

    fun tryPublish(userId: UserId): Boolean = take(publishes, userId, burst = PUBLISH_BURST, window = 1.hours, daily = PUBLISH_DAILY)

    fun tryReport(userId: UserId): Boolean = take(reports, userId, burst = REPORT_BURST, window = 10.minutes, daily = REPORT_DAILY)

    private fun take(calls: ConcurrentHashMap<UserId, ArrayDeque<Instant>>, userId: UserId, burst: Int, window: Duration, daily: Int): Boolean {
        val now = clock.now()
        val history = calls.computeIfAbsent(userId) { ArrayDeque() }
        synchronized(history) {
            while (history.isNotEmpty() && now - history.first() >= 1.days) history.removeFirst()
            if (history.size >= daily) return false
            if (history.count { now - it < window } >= burst) return false
            history.addLast(now)
            return true
        }
    }

    companion object {
        const val PUBLISH_BURST = 10
        const val PUBLISH_DAILY = 30
        const val REPORT_BURST = 10
        const val REPORT_DAILY = 30
    }
}
