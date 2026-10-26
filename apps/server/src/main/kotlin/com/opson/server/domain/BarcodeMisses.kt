package com.opson.server.domain

import java.util.UUID

/**
 * A scanned code nobody could answer, reported by the app after a 404 from
 * `GET /v1/foods/barcode/{code}`. Feeds the "which products are we missing"
 * panels. No user, install or session id, by design: any of them would let
 * the rows be joined back into one person's shopping list.
 *
 * [country] is the phone's region setting (ISO 3166-1 alpha-2), not a
 * location. [gtin] is computed here from [barcode], never taken from the app.
 */
data class BarcodeMiss(
    val id: UUID,
    val barcode: String,
    val gtin: String?,
    val country: String?,
    val platform: String,
    val appVersion: String?,
) {
    companion object {
        const val MAX_APP_VERSION = 32
        val PLATFORMS = setOf("android", "ios", "other")

        /** Validated input to a row: the GTIN derived, the free-form header values bounded. */
        fun of(id: UUID, barcode: String, country: String?, platform: String, appVersion: String?): BarcodeMiss =
            BarcodeMiss(
                id = id,
                barcode = barcode,
                gtin = Barcodes.gtin14(barcode),
                country = country?.uppercase()?.takeIf { it.length == 2 && it.all { c -> c in 'A'..'Z' } },
                platform = platform.takeIf { it in PLATFORMS } ?: "other",
                appVersion = appVersion?.trim()?.take(MAX_APP_VERSION)?.ifEmpty { null },
            )
    }
}

interface BarcodeMissRepository {
    /** Stores [miss] once. A retry with the same id is a no-op, so the app can resend after a timeout. */
    suspend fun record(miss: BarcodeMiss)
}
