package com.opson.server.data

import com.opson.server.config.FoodSourcesConfig
import com.opson.server.di.ServerScope
import com.opson.server.domain.BarcodeOutcome
import com.opson.server.domain.CatalogSource
import com.opson.server.domain.FoodSource
import com.opson.server.domain.FoodSourceResult
import com.opson.server.domain.LiveFoodSources
import com.opson.server.domain.OpenFoodFactsClient
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The outside sources a barcode miss goes to, from config. Open Food Facts is
 * always on (free, no key). Nutritionix joins only when both of its keys are
 * set, and goes first because a licensed source outranks a community one.
 * NCCDB is a file dataset, not a live source: it would arrive through an
 * importer, see apps/server/README.md.
 */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class ConfiguredLiveFoodSources(
    config: FoodSourcesConfig,
    openFoodFacts: OpenFoodFactsSource,
) : LiveFoodSources {

    private val sources: List<FoodSource> =
        listOfNotNull(config.nutritionix?.let { NutritionixSource(it) }, openFoodFacts)
            .sortedBy { it.catalogSource.tier }

    override fun inTierOrder(): List<FoodSource> = sources
}

/**
 * Open Food Facts as a [FoodSource]: community data (ODbL), cached into
 * `foods` as source `off` so the next scan of the code is instant. The
 * licence allows keeping it with attribution, which the Foods screen gives.
 */
@SingleIn(ServerScope::class)
@Inject
class OpenFoodFactsSource(private val client: OpenFoodFactsClient) : FoodSource {
    override val catalogSource = CatalogSource.OpenFoodFacts
    override val outcome = BarcodeOutcome.OpenFoodFacts
    override val cacheable = true

    override suspend fun lookup(barcode: String): FoodSourceResult = client.product(barcode)
}
