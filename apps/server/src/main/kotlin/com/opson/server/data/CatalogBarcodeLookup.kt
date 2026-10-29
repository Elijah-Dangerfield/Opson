package com.opson.server.data

import com.opson.server.di.ServerScope
import com.opson.server.domain.BarcodeLookup
import com.opson.server.domain.BarcodeLookupResult
import com.opson.server.domain.BarcodeOutcome
import com.opson.server.domain.ExternalProduct
import com.opson.server.domain.Food
import com.opson.server.domain.FoodCatalogRepository
import com.opson.server.domain.FoodSource
import com.opson.server.domain.FoodSourceResult
import com.opson.server.domain.LiveFoodSources
import com.opson.server.domain.ServedFood
import me.tatarka.inject.annotations.Inject
import org.slf4j.LoggerFactory
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.util.UUID

/**
 * Our catalog first, in any equivalent form of the code. On a miss, each
 * enabled outside source in tier order ([LiveFoodSources]); the first one
 * that knows the product answers. A cacheable answer is upserted into
 * `foods`, so the next scan of that code is a catalog hit and every user gets
 * the same food id for it. Every lookup records its outcome in
 * `barcode_lookups` for the Food catalog dashboard.
 *
 * A miss is not cached: outside sources gain products every day. The lookup
 * is Unavailable only when no source could give a definite answer; a source
 * that is down while another says "not found" still makes a miss.
 */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class CatalogBarcodeLookup(
    private val catalog: FoodCatalogRepository,
    private val sources: LiveFoodSources,
) : BarcodeLookup {

    override suspend fun lookup(barcode: String): BarcodeLookupResult {
        catalog.findByBarcode(barcode)?.let { food ->
            catalog.recordBarcodeLookup(barcode, BarcodeOutcome.Catalog)
            return BarcodeLookupResult.Found(food)
        }
        var answered = false
        for (source in sources.inTierOrder()) {
            when (val result = source.lookup(barcode)) {
                is FoodSourceResult.Found -> {
                    val food = if (source.cacheable) {
                        catalog.upsertExternalProduct(source.catalogSource, result.product)
                    } else {
                        result.product.servedOnce(source)
                    }
                    catalog.recordBarcodeLookup(barcode, source.outcome)
                    return BarcodeLookupResult.Found(food)
                }

                FoodSourceResult.NotFound -> answered = true

                is FoodSourceResult.Unavailable ->
                    logger.warn("${source.catalogSource.wire} barcode lookup failed", result.cause)
            }
        }
        val noSources = sources.inTierOrder().isEmpty()
        return if (answered || noSources) {
            catalog.recordBarcodeLookup(barcode, BarcodeOutcome.Miss)
            BarcodeLookupResult.NotFound
        } else {
            catalog.recordBarcodeLookup(barcode, BarcodeOutcome.Error)
            BarcodeLookupResult.Unavailable
        }
    }

    /**
     * A food from a source we may not store. The id is derived from the
     * source and the code, so two scans of the same product agree on it; it is
     * not in `foods`, so a log entry naming it keeps its own snapshot and syncs
     * with no food id.
     */
    private fun ExternalProduct.servedOnce(source: FoodSource): Food = ServedFood.of(stored(source), correction = null)

    private fun ExternalProduct.stored(source: FoodSource): Food = Food(
        id = UUID.nameUUIDFromBytes("${source.catalogSource.wire}:$barcode".toByteArray()),
        source = source.catalogSource,
        sourceId = barcode,
        name = name,
        brand = brand,
        barcode = barcode,
        per100g = per100g,
        micros = micros,
        portions = portions,
    )

    private companion object {
        val logger = LoggerFactory.getLogger(CatalogBarcodeLookup::class.java)
    }
}
