package com.opson.server.data

import com.opson.server.domain.CarbBasis
import com.opson.server.domain.LabelCheck
import com.opson.server.domain.LabelCheckCode
import com.opson.server.domain.LabelFacts
import com.opson.server.domain.LabelReading
import com.opson.server.domain.NutrientKey
import com.opson.server.domain.ServingUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * How a label's facts, checks and readings are kept in `food_submissions`'
 * JSONB columns. The same shape the routes speak (camelCase, nutrients by
 * their `micros` key), so a row reads like the request that made it.
 */
@Serializable
data class LabelFactsJson(
    val name: String? = null,
    val brand: String? = null,
    val barcode: String? = null,
    val servingSize: Double? = null,
    val servingUnit: String? = null,
    val servingLabel: String? = null,
    val servingsPerContainer: Double? = null,
    val basis: String = CarbBasis.Unknown.wire,
    val perServing: Map<String, Double> = emptyMap(),
    val per100: Map<String, Double> = emptyMap(),
    /** Check codes the person saw and kept their numbers through. Only on `submitted`. */
    val acknowledged: List<String> = emptyList(),
) {
    fun toFacts(): LabelFacts = LabelFacts(
        name = name,
        brand = brand,
        barcode = barcode,
        servingSize = servingSize,
        servingUnit = ServingUnit.fromWire(servingUnit),
        servingLabel = servingLabel,
        servingsPerContainer = servingsPerContainer,
        basis = CarbBasis.fromWire(basis),
        perServing = perServing.keyed(),
        per100 = per100.keyed(),
    )

    fun acknowledgedCodes(): List<LabelCheckCode> = acknowledged.mapNotNull(LabelCheckCode::fromWire)

    companion object {
        fun of(facts: LabelFacts, acknowledged: List<LabelCheckCode> = emptyList()) = LabelFactsJson(
            name = facts.name,
            brand = facts.brand,
            barcode = facts.barcode,
            servingSize = facts.servingSize,
            servingUnit = facts.servingUnit?.wire,
            servingLabel = facts.servingLabel,
            servingsPerContainer = facts.servingsPerContainer,
            basis = facts.basis.wire,
            perServing = facts.perServing.mapKeys { it.key.wire },
            per100 = facts.per100.mapKeys { it.key.wire },
            acknowledged = acknowledged.map { it.wire },
        )

        /** Unknown keys are dropped, so an older server reading a newer row loses a nutrient, not the row. */
        private fun Map<String, Double>.keyed(): Map<NutrientKey, Double> =
            mapNotNull { (key, value) -> NutrientKey.fromWire(key)?.let { it to value } }.toMap()
    }
}

@Serializable
data class LabelCheckJson(
    val code: String,
    val fields: List<String> = emptyList(),
    val expected: Double? = null,
    val actual: Double? = null,
) {
    fun toCheck(): LabelCheck? = LabelCheckCode.fromWire(code)?.let { LabelCheck(it, fields, expected, actual) }

    companion object {
        fun of(check: LabelCheck) = LabelCheckJson(check.code.wire, check.fields, check.expected, check.actual)
    }
}

/** The model's reading, as the server kept it from the parse. */
@Serializable
data class LabelReadingJson(
    val facts: LabelFactsJson,
    val confidence: Map<String, Double> = emptyMap(),
    val readable: Boolean = true,
    val unitChecks: List<LabelCheckJson> = emptyList(),
) {
    fun toReading(): LabelReading = LabelReading(
        facts = facts.toFacts(),
        confidence = confidence,
        readable = readable,
        unitChecks = unitChecks.mapNotNull { it.toCheck() },
    )

    companion object {
        fun of(reading: LabelReading) = LabelReadingJson(
            facts = LabelFactsJson.of(reading.facts),
            confidence = reading.confidence,
            readable = reading.readable,
            unitChecks = reading.unitChecks.map(LabelCheckJson::of),
        )
    }
}

internal object LabelJson {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val checks = ListSerializer(LabelCheckJson.serializer())

    fun facts(facts: LabelFacts, acknowledged: List<LabelCheckCode> = emptyList()): String =
        json.encodeToString(LabelFactsJson.serializer(), LabelFactsJson.of(facts, acknowledged))

    fun facts(text: String): LabelFactsJson = json.decodeFromString(LabelFactsJson.serializer(), text)

    fun reading(reading: LabelReading): String = json.encodeToString(LabelReadingJson.serializer(), LabelReadingJson.of(reading))

    fun reading(text: String): LabelReading = json.decodeFromString(LabelReadingJson.serializer(), text).toReading()

    fun checks(list: List<LabelCheck>): String = json.encodeToString(checks, list.map(LabelCheckJson::of))

    fun checks(text: String): List<LabelCheck> = json.decodeFromString(checks, text).mapNotNull { it.toCheck() }
}
