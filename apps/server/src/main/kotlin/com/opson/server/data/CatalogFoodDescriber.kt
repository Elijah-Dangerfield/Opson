package com.opson.server.data

import com.opson.server.di.ServerScope
import com.opson.server.domain.DescribeConfidence
import com.opson.server.domain.DescribeParser
import com.opson.server.domain.DescribedItem
import com.opson.server.domain.Food
import com.opson.server.domain.FoodCatalogRepository
import com.opson.server.domain.FoodDescriber
import com.opson.server.domain.MatchTier
import com.opson.server.domain.Nutrients
import com.opson.server.domain.ParsedPhrase
import com.opson.server.domain.Portion
import com.opson.server.domain.PortionSize
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.math.round

/**
 * Turns [DescribeParser] phrases into items: the top catalog match for each
 * phrase, then grams from, in order,
 *  1. a mass unit ("200 g", "3 oz"),
 *  2. a portion whose label names the unit or size ("1 cup, sliced", "1 medium"),
 *  3. the food's default portion (one "serving"),
 *  4. 100 g when the food has no portions at all.
 * Each step down lowers [DescribeConfidence]. Searches here are not recorded
 * as `food_search_events`: they are not something a person typed into search.
 */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class CatalogFoodDescriber(
    private val catalog: FoodCatalogRepository,
) : FoodDescriber {

    override suspend fun describe(text: String): List<DescribedItem> =
        DescribeParser.parse(text).map { phrase ->
            val match = catalog.search(phrase.foodText, limit = 1, record = false).firstOrNull()
            if (match == null) unmatched(phrase) else matched(phrase, match.food, match.tier)
        }

    private fun unmatched(phrase: ParsedPhrase): DescribedItem {
        val grams = phrase.unit?.gramsPerUnit?.let { it * phrase.quantity }
        return DescribedItem(
            text = phrase.text,
            name = phrase.foodText,
            foodId = null,
            quantity = phrase.quantity,
            unit = phrase.unit,
            amountText = amountText(phrase),
            portionLabel = null,
            grams = grams?.let(::round1),
            nutrients = null,
            micros = null,
            confidence = DescribeConfidence.Low,
        )
    }

    private fun matched(phrase: ParsedPhrase, food: Food, tier: MatchTier): DescribedItem {
        val resolved = resolveGrams(phrase, food)
        val grams = round1(resolved.grams)
        val factor = grams / 100.0
        return DescribedItem(
            text = phrase.text,
            name = food.name,
            foodId = food.id,
            quantity = phrase.quantity,
            unit = phrase.unit,
            amountText = amountText(phrase),
            portionLabel = resolved.portion?.label,
            grams = grams,
            nutrients = Nutrients(
                kcal = food.per100g.kcal?.let { round1(it * factor) },
                protein = food.per100g.protein?.let { round1(it * factor) },
                carbs = food.per100g.carbs?.let { round1(it * factor) },
                fat = food.per100g.fat?.let { round1(it * factor) },
                fiber = food.per100g.fiber?.let { round1(it * factor) },
            ),
            micros = food.micros.mapValues { (_, v) -> round3(v * factor) },
            confidence = confidence(tier, resolved.how),
        )
    }

    private enum class How { Mass, Portion, DefaultPortion, Guess }

    private data class Resolved(val grams: Double, val portion: Portion?, val how: How)

    private fun resolveGrams(phrase: ParsedPhrase, food: Food): Resolved {
        val qty = phrase.quantity
        phrase.unit?.gramsPerUnit?.let { return Resolved(qty * it, null, How.Mass) }

        val unitWords = phrase.unit?.portionWords.orEmpty()
        // A serving known only by volume ("1 can (355 ml)") has no grams to scale by.
        val weighed = food.portions.filter { it.grams != null }
        val candidates = weighed.filter { p ->
            (unitWords.isEmpty() || unitWords.any { w -> p.label.hasWord(w) }) &&
                (phrase.size == null || phrase.size.words.any { w -> p.label.hasWord(w) })
        }
        if ((unitWords.isNotEmpty() || phrase.size != null) && candidates.isNotEmpty()) {
            val portion = candidates.first()
            return Resolved(qty * perUnit(portion), portion, How.Portion)
        }
        val fallback = defaultPortion(weighed)
            ?: return Resolved(qty * 100.0, null, How.Guess)
        return Resolved(qty * perUnit(fallback), fallback, How.DefaultPortion)
    }

    /**
     * The portion that best stands for "one" of a food: a medium one, else a
     * count-like measure ("1 banana", "1 large"), else the first listed.
     */
    private fun defaultPortion(portions: List<Portion>): Portion? =
        portions.firstOrNull { p -> PortionSize.Medium.words.any { p.label.hasWord(it) } }
            ?: portions.firstOrNull { p -> VOLUME_OR_MASS.none { p.label.hasWord(it) } }
            ?: portions.firstOrNull()

    private fun perUnit(portion: Portion): Double =
        checkNotNull(portion.grams) { "only weighed portions reach here" } / DescribeParser.labelAmount(portion.label)

    private fun confidence(tier: MatchTier, how: How): DescribeConfidence {
        val matchScore = when (tier) {
            MatchTier.Exact, MatchTier.Head, MatchTier.Prefix -> 2
            MatchTier.FullText -> 1
            MatchTier.Similar -> 0
        }
        val amountPenalty = when (how) {
            How.Mass, How.Portion -> 0
            How.DefaultPortion -> 1
            How.Guess -> 2
        }
        return when (matchScore - amountPenalty) {
            2 -> DescribeConfidence.High
            1 -> DescribeConfidence.Medium
            else -> DescribeConfidence.Low
        }
    }

    private fun amountText(phrase: ParsedPhrase): String = buildString {
        append(DescribeParser.formatNumber(phrase.quantity))
        phrase.size?.let { append(' ').append(it.wire) }
        phrase.unit?.let { append(' ').append(it.wire) }
    }

    private fun String.hasWord(word: String): Boolean =
        Regex("""\b${Regex.escape(word)}s?\b""").containsMatchIn(lowercase())

    private companion object {
        val VOLUME_OR_MASS = listOf("cup", "tbsp", "tablespoon", "tsp", "teaspoon", "oz", "ounce", "fl", "ml", "lb", "g")

        fun round1(value: Double) = round(value * 10) / 10
        fun round3(value: Double) = round(value * 1000) / 1000
    }
}
