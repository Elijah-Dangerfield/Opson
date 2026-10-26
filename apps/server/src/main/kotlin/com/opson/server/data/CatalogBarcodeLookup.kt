package com.opson.server.data

import com.opson.server.di.ServerScope
import com.opson.server.domain.BarcodeLookup
import com.opson.server.domain.BarcodeLookupResult
import com.opson.server.domain.BarcodeOutcome
import com.opson.server.domain.FoodCatalogRepository
import com.opson.server.domain.OpenFoodFactsClient
import com.opson.server.domain.OpenFoodFactsResult
import me.tatarka.inject.annotations.Inject
import org.slf4j.LoggerFactory
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Catalog first, then Open Food Facts. A product OFF knows is upserted into
 * `foods` with source `off`, so the next scan of the same code is a catalog
 * hit and every user gets the same food id for it. Every lookup records its
 * outcome in `barcode_lookups` for the catalog dashboard.
 *
 * A miss is not cached: OFF gains products every day, and a code that was
 * unknown last week may resolve now.
 */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class CatalogBarcodeLookup(
    private val catalog: FoodCatalogRepository,
    private val openFoodFacts: OpenFoodFactsClient,
) : BarcodeLookup {

    override suspend fun lookup(barcode: String): BarcodeLookupResult {
        catalog.findByBarcode(barcode)?.let { food ->
            catalog.recordBarcodeLookup(barcode, BarcodeOutcome.Catalog)
            return BarcodeLookupResult.Found(food)
        }
        return when (val result = openFoodFacts.product(barcode)) {
            is OpenFoodFactsResult.Found -> {
                val food = catalog.upsertOpenFoodFactsProduct(result.product)
                catalog.recordBarcodeLookup(barcode, BarcodeOutcome.OpenFoodFacts)
                BarcodeLookupResult.Found(food)
            }

            OpenFoodFactsResult.NotFound -> {
                catalog.recordBarcodeLookup(barcode, BarcodeOutcome.Miss)
                BarcodeLookupResult.NotFound
            }

            is OpenFoodFactsResult.Unavailable -> {
                logger.warn("Open Food Facts lookup failed", result.cause)
                catalog.recordBarcodeLookup(barcode, BarcodeOutcome.Error)
                BarcodeLookupResult.Unavailable
            }
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(CatalogBarcodeLookup::class.java)
    }
}
