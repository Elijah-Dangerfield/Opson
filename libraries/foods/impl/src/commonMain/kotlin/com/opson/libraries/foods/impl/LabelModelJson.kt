package com.opson.libraries.foods.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.foods.LabelFacts
import com.opson.libraries.foods.LabelNutrient
import com.opson.libraries.foods.ServingUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The on-device label model's JSON (see `LabelModelBridge`) to [LabelFacts]. Unknown nutrients,
 * negative and non-finite numbers are left out; the merge checks the rest against the OCR text.
 */
internal object LabelModelJson {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Wire(
        val servingSize: Double? = null,
        val servingUnit: String? = null,
        val servingsPerContainer: Double? = null,
        val perServing: Map<String, Double> = emptyMap(),
        val per100: Map<String, Double> = emptyMap(),
    )

    fun read(text: String): LabelFacts? {
        val wire = Catching { json.decodeFromString(Wire.serializer(), text) }
            .logOnFailure("The on-device label model answered with something that is not its JSON")
            .getOrNull() ?: return null
        return LabelFacts(
            servingSize = wire.servingSize?.takeIf { it.isFinite() && it > 0.0 },
            servingUnit = ServingUnit.fromWire(wire.servingUnit),
            servingsPerContainer = wire.servingsPerContainer?.takeIf { it.isFinite() && it > 0.0 },
            perServing = column(wire.perServing),
            per100 = column(wire.per100),
        )
    }

    private fun column(values: Map<String, Double>): Map<LabelNutrient, Double> = values.mapNotNull { (key, value) ->
        LabelNutrient.fromWire(key)?.takeIf { value.isFinite() && value >= 0.0 }?.let { it to value }
    }.toMap()
}
