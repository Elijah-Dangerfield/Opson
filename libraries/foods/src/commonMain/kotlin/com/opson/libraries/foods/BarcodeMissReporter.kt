package com.opson.libraries.foods

/**
 * Tells the server a scanned code was found nowhere, so the catalog's gaps
 * show up on the Food catalog dashboard. Call it only for a definite miss
 * ([FoodCatalog.barcode] answered null), never for a lookup that failed.
 *
 * Sends the code and nothing else from here; the server takes the region,
 * platform and app version from the headers every request carries, and
 * stores no account.
 */
interface BarcodeMissReporter {
    /**
     * Fire and forget. A report that never arrives only undercounts a gap, so failures are dropped.
     * Returns once the report is done, but cancelling the caller doesn't cancel the report: it
     * outlives the screen that asked.
     */
    suspend fun report(code: String)
}
