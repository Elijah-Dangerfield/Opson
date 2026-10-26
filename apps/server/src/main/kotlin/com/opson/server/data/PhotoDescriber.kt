package com.opson.server.data

import com.opson.server.di.ServerScope
import com.opson.server.domain.DescribeUnit
import com.opson.server.domain.EstimateProvider
import com.opson.server.domain.EstimateResult
import com.opson.server.domain.EstimatedFood
import com.opson.server.domain.ImageType
import com.opson.server.domain.ParsedPhrase
import com.opson.server.domain.PhotoDescribeResult
import com.opson.server.domain.PortionSize
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * A meal photo to catalog rows: the [EstimateProvider] names the foods and
 * guesses amounts, then each one goes through [CatalogFoodDescriber], the
 * matching `/v1/describe` uses, so a photo and typed text come back as the
 * same `DescribedItem`s.
 *
 * Each food becomes a phrase in the parser's own terms: a household unit or a
 * size the parser knows ("cup", "tbsp", "medium") is kept so the catalog's
 * portion supplies the grams, with the model's weight as the fallback when no
 * portion fits; a weight unit, or no unit at all, falls back to the model's
 * grams.
 */
@SingleIn(ServerScope::class)
@Inject
class PhotoDescriber(
    private val provider: EstimateProvider,
    private val describer: CatalogFoodDescriber,
) {
    suspend fun describe(image: ByteArray, type: ImageType, note: String?): PhotoDescribeResult =
        when (val estimate = provider.estimate(image, type, note)) {
            EstimateResult.NotConfigured -> PhotoDescribeResult.NotConfigured
            is EstimateResult.Failed -> PhotoDescribeResult.Failed(estimate.reason)
            is EstimateResult.Estimated -> PhotoDescribeResult.Described(
                describer.describePhrases(estimate.foods.take(MAX_FOODS).mapNotNull(::phrase)),
            )
        }

    companion object {
        /** The same cap typed text has, so one photo cannot fan out into many catalog searches. */
        const val MAX_FOODS = 20

        private const val MAX_NAME = 120

        fun phrase(food: EstimatedFood): ParsedPhrase? {
            val name = food.name.trim().take(MAX_NAME).ifEmpty { return null }
            val grams = food.grams?.takeIf { it.isFinite() && it > 0 }
            val quantity = food.quantity.takeIf { it.isFinite() && it > 0 } ?: 1.0
            val words = food.unit.orEmpty().lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
            val unit = words.firstNotNullOfOrNull(DescribeUnit::fromWord)
            val size = words.firstNotNullOfOrNull(PortionSize::fromWord)
            return when {
                // "120 g", "4 oz": a weight the model stated outright.
                unit?.gramsPerUnit != null -> ParsedPhrase(name, quantity, unit, null, name.lowercase())
                unit != null || size != null ->
                    ParsedPhrase(name, quantity, unit, size, name.lowercase(), gramsHint = grams)
                grams != null -> ParsedPhrase(name, grams, DescribeUnit.Gram, null, name.lowercase())
                else -> ParsedPhrase(name, quantity, null, null, name.lowercase())
            }
        }
    }
}
