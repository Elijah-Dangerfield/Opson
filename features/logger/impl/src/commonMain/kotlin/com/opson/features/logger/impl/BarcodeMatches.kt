package com.opson.features.logger.impl

import com.opson.features.logger.impl.FoodPicks.toPick
import com.opson.libraries.foods.BarcodeMatch
import com.opson.libraries.foods.NutrientProfile
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.MacroLineStyle
import com.opson.libraries.nutrition.MacroPart
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.macroLine
import kotlin.math.abs
import kotlin.math.max
import com.opson.libraries.foods.FoodCheck

/**
 * One row of "Pick a match": a food a scanned code matched, at one serving, or several that are
 * the same food in different databases ([sources] names each). [suggested] is the server's own
 * answer, first and marked. [corrected] and [numbersLookOff] are about the numbers, so they only
 * show where calories do.
 */
data class MatchRowUi(
    val foodId: String,
    val name: String,
    val brand: String?,
    /** "1 can (473 ml)": one serving, the amount the calories are for. */
    val amount: String,
    val calories: Display,
    val macros: List<MacroPart>,
    /** Every database the row stands for, the suggested one's first. Empty for a source with no short name. */
    val sources: List<SourceBadge>,
    val suggested: Boolean,
    /** Verified: a person checked the food against its package. */
    val verified: Boolean = false,
    /** A packaged food nobody has checked yet: Pick a match says so, since it is choosing between sources. */
    val notChecked: Boolean = false,
    val corrected: Boolean = false,
    val numbersLookOff: Boolean = false,
    /** The person's own food, not checked yet. */
    val unverified: Boolean = false,
)

/** Turning the server's matches for a code into the rows of "Pick a match". */
object BarcodeMatches {

    /**
     * [matches] in the server's order, with any that are effectively the same food folded into
     * the first of them: a product in USDA Branded and Open Food Facts usually carries the same
     * label. One group left means there is nothing to choose, and the scan opens it directly.
     */
    fun groups(matches: List<BarcodeMatch>): List<List<BarcodeMatch>> {
        val groups = mutableListOf<MutableList<BarcodeMatch>>()
        matches.distinctBy { it.food.id }.forEach { match ->
            groups.firstOrNull { sameNumbers(it.first().food.per100g, match.food.per100g) }?.add(match) ?: groups.add(mutableListOf(match))
        }
        return groups
    }

    /**
     * The same within a label's rounding: energy within [KCAL_SLACK] kcal or [SHARE_SLACK] of
     * itself per 100 g, and protein, carbs and fat each within [GRAMS_SLACK] g or [SHARE_SLACK].
     * A number one source has and the other lacks is a difference.
     */
    fun sameNumbers(a: NutrientProfile, b: NutrientProfile): Boolean =
        close(a.kcal, b.kcal, KCAL_SLACK) &&
            close(a.protein, b.protein, GRAMS_SLACK) &&
            close(a.carbs, b.carbs, GRAMS_SLACK) &&
            close(a.fat, b.fat, GRAMS_SLACK)

    /** The rows for [groups], under [policy] on the logger, numbers bare as search shows them. */
    fun rows(groups: List<List<BarcodeMatch>>, policy: VisibilityPolicy, bare: FormatContext): List<MatchRowUi> =
        groups.mapIndexed { index, group ->
            val match = group.first()
            val food = match.food
            val draft = food.toPick().draft
            val calories = policy.resolve(Metric.Calories, draft.kcal, Surface.Logger, bare)
            // "Corrected" and "Numbers look off" are about numbers someone in a mode without them never sees.
            val numbersShow = calories != Display.Hidden
            MatchRowUi(
                foodId = food.id,
                name = food.name,
                brand = food.brand?.takeIf { it.isNotBlank() },
                amount = draft.amountLabel,
                calories = calories,
                macros = if (draft.hasMacros) policy.macroLine(draft.amounts(), Surface.Logger, bare, MacroLineStyle.Compact) else emptyList(),
                sources = group.mapNotNull { SourceBadge.of(it.food.source) }.distinct(),
                suggested = index == 0,
                verified = food.check == FoodCheck.Verified,
                notChecked = food.check == FoodCheck.Unverified,
                corrected = numbersShow && match.corrected,
                numbersLookOff = numbersShow && match.numbersLookOff,
                unverified = food.unverified,
            )
        }

    private fun close(a: Double?, b: Double?, slack: Double): Boolean = when {
        a == null || b == null -> a == b
        else -> abs(a - b) <= max(slack, SHARE_SLACK * max(abs(a), abs(b)))
    }

    private const val KCAL_SLACK = 1.0
    private const val GRAMS_SLACK = 0.5
    private const val SHARE_SLACK = 0.02
}
