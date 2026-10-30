package com.opson.libraries.diary

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import kotlinx.coroutines.flow.Flow
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/** Where a recipe stands with the public. The server owns it; the app only reads it. */
enum class RecipeShareState(val wire: String) {
    Private("private"),
    Live("live"),

    /** Reports from several people hid it until someone at Opson looks. */
    Hidden("hidden"),

    /** Taken down by Opson. It can't be shared again. */
    Removed("removed"),
    ;

    companion object {
        fun fromWire(value: String?): RecipeShareState = entries.firstOrNull { it.wire == value } ?: Private
    }
}

/** A recipe's sharing as the server last said: its code once it has ever been shared, and whether the owner's name shows. */
data class RecipeSharing(
    val code: String? = null,
    val state: RecipeShareState = RecipeShareState.Private,
    val showName: Boolean = false,
) {
    val isShared: Boolean get() = code != null && state == RecipeShareState.Live

    companion object {
        val Private = RecipeSharing()
    }
}

/**
 * A recipe: [ingredients] for the whole batch, which makes [servings], and optional [notes] (steps,
 * where it came from). It logs as one entry a serving at a time ([servingDraft]), unlike a saved
 * meal, which logs every food at the amount saved.
 *
 * [savedFrom] is the share code of someone else's recipe this one was saved from: its numbers
 * are theirs, not checked by Opson, and it says so.
 */
data class Recipe(
    val id: String,
    val name: String,
    val servings: Double,
    val ingredients: List<LogDraft> = emptyList(),
    val notes: String? = null,
    val savedFrom: String? = null,
    val sharing: RecipeSharing = RecipeSharing.Private,
    /** When it last changed on a phone, the sync's last-write-wins stamp. Sharing sends it with the recipe. */
    val updatedAtEpochMs: Long = 0,
) {
    /** The whole batch. */
    val totals: Totals get() = ingredients.fold(Totals.Empty) { acc, d ->
        Totals(
            kcal = acc.kcal.plus(d.kcal),
            protein = acc.protein.plus(d.protein),
            carbs = acc.carbs.plus(d.carbs),
            fat = acc.fat.plus(d.fat),
            fiber = acc.fiber.plus(d.fiber),
        )
    }

    /**
     * [count] servings as one entry: "Chili", "1 serving", a [servings]th of everything per
     * serving. Unknown stays unknown; a nutrient only some ingredients have is the known part.
     * Grams only when every ingredient has a weight. An estimate, as a homemade dish is.
     */
    fun servingDraft(count: Double = 1.0): LogDraft {
        val share = count / servings
        fun part(total: Total): Double? = total.value?.times(share)
        val sums = totals
        val weighed = ingredients.isNotEmpty() && ingredients.all { it.grams != null }
        val micros = ingredients.mapNotNull { it.micros }.takeIf { it.isNotEmpty() }
            ?.flatMap { it.entries }
            ?.groupBy({ it.key }, { it.value })
            ?.mapValues { (_, values) -> values.sum() * share }
        return LogDraft(
            name = name,
            precision = Precision.Estimate,
            amountText = HouseholdAmount.of(count, SERVING),
            quantity = count,
            unit = SERVING,
            grams = if (weighed) ingredients.sumOf { it.grams ?: 0.0 } * share else null,
            kcal = part(sums.kcal),
            protein = part(sums.protein),
            carbs = part(sums.carbs),
            fat = part(sums.fat),
            fiber = part(sums.fiber),
            micros = micros,
            method = LogMethod.Saved,
        )
    }

    companion object {
        /** The unit a recipe's entry is counted in. Also what its amount reads: "1 serving", "2 servings". */
        const val SERVING = "serving"
        const val MIN_SERVINGS = 0.25
        const val MAX_SERVINGS = 1000.0
        const val MAX_INGREDIENTS = 60
        const val MAX_NAME = 200
        const val MAX_NOTES = 4000
    }
}

/** The fields a person fills in to make or change a recipe. */
data class RecipeDraft(
    val name: String,
    val servings: Double,
    val ingredients: List<LogDraft>,
    val notes: String? = null,
    val savedFrom: String? = null,
)

/**
 * The person's recipes, on the device and synced like the rest of the diary. Sharing is not here:
 * it is online only, through `PublicRecipes` in `:libraries:foods`, and comes back here as
 * [Recipe.sharing] on the next sync, or at once through [markShared].
 */
interface RecipeRepository {
    /** Every recipe, by name. */
    fun observeAll(): Flow<List<Recipe>>

    fun observe(id: String): Flow<Recipe?>

    suspend fun get(id: String): Recipe?

    /** The person's recipe shared under [code], if one is. */
    suspend fun byShareCode(code: String): Recipe?

    suspend fun create(draft: RecipeDraft): Recipe

    suspend fun update(id: String, draft: RecipeDraft)

    suspend fun delete(id: String)

    /**
     * What the server said when the person shared or stopped sharing, so the screen is right
     * before the next sync brings the same. Not a change of the person's: nothing is queued.
     */
    suspend fun markShared(id: String, sharing: RecipeSharing)
}

/** Whether recipes are on, read where it is used. */
fun interface RecipesRelease {
    fun isOn(): Boolean
}

/**
 * Recipes and sharing them. Off, the Foods tab has no Recipes card, search shows no recipes and a
 * recipe link opens nothing. Recipes already made stay, and sync whatever the flag says.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
@ContributesBinding(AppScope::class, boundType = RecipesRelease::class)
class RecipesReleaseFlag(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap), RecipesRelease {
    override val name = "Recipes"
    override val path = "release.recipes"
    override val default = false
    override val description = "Recipes on the Foods tab, sharing them by link and QR code, and shared recipes in search"

    override fun isOn(): Boolean = value
}

/**
 * The links a shared recipe answers to. A printed QR code carries [web]; the app reads that and
 * [app] alike, and the website's page offers [app] as "Open in Opson".
 */
object RecipeLinks {
    const val WEB_HOST = "www.opsontracker.com"
    private const val WEB_PREFIX = "https://$WEB_HOST/r/"
    private const val APP_PREFIX = "opson://recipe/"

    /** No 0, 1, i, l or o, as the server makes them. */
    private const val ALPHABET = "23456789abcdefghjkmnpqrstuvwxyz"
    private const val LENGTH = 8
    private val SCHEMES = listOf("https://", "http://")

    fun web(code: String): String = WEB_PREFIX + code

    fun app(code: String): String = APP_PREFIX + code

    /**
     * The website's page for the recipe at an address the app does not claim (it claims only `/r/` addresses), so
     * the app can open it in the browser without the link coming straight back to the app.
     */
    fun page(code: String): String = "https://$WEB_HOST/recipe/?c=$code"

    fun isCode(value: String): Boolean = value.length == LENGTH && value.all { it in ALPHABET }

    /**
     * The share code in [url], or null when it is not a recipe link: `https://www.opsontracker.com/r/<code>`
     * (with or without `www`, a trailing slash, a query), `opson://recipe/<code>`, or a bare code
     * typed or scanned on its own.
     */
    fun codeFrom(url: String): String? {
        val trimmed = url.trim()
        val scheme = SCHEMES.firstOrNull { trimmed.startsWith(it, ignoreCase = true) }
        val candidate = when {
            trimmed.startsWith(APP_PREFIX, ignoreCase = true) -> trimmed.substring(APP_PREFIX.length)
            scheme == null -> return trimmed.lowercase().takeIf(::isCode)
            else -> {
                val rest = trimmed.substring(scheme.length)
                val host = rest.substringBefore('/').lowercase()
                if (host != WEB_HOST && host != WEB_HOST.removePrefix("www.")) return null
                val path = rest.substringAfter('/', missingDelimiterValue = "")
                if (!path.startsWith("r/")) return null
                path.removePrefix("r/")
            }
        }
        return candidate.substringBefore('?').substringBefore('#').trimEnd('/').lowercase().takeIf(::isCode)
    }
}
