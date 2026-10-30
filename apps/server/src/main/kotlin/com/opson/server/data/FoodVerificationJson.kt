package com.opson.server.data

import com.opson.server.domain.SuggestedValues
import com.opson.server.domain.VerificationField
import com.opson.server.domain.VerificationProblem
import com.opson.server.domain.VerificationValues
import kotlinx.serialization.Serializable

/**
 * The values a Builder says are right (`problem.values`), the wire's and `food_verifications.problem`'s
 * shape alike: name, brand and barcode as on the pack, and the numbers for [basisLabel] ([basisGrams]),
 * like a report's `suggested`.
 */
@Serializable
data class VerificationValuesJson(
    val name: String? = null,
    val brand: String? = null,
    val barcode: String? = null,
    val basisLabel: String? = null,
    val basisGrams: Double? = null,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val servingLabel: String? = null,
    val servingGrams: Double? = null,
) {
    fun toDomain() = VerificationValues(
        name = name,
        brand = brand,
        barcode = barcode,
        suggested = SuggestedValues(basisLabel, basisGrams, kcal, protein, carbs, fat, servingLabel, servingGrams),
    )

    companion object {
        fun of(v: VerificationValues) = with(v.suggested) {
            VerificationValuesJson(v.name, v.brand, v.barcode, basisLabel, basisGrams, kcal, protein, carbs, fat, servingLabel, servingGrams)
        }
    }
}

/** "Something was wrong": field codes (`calories`, `macros`, `serving`, `name_brand`, `ingredients`, `barcode`). */
@Serializable
data class VerificationProblemJson(
    val wrong: List<String> = emptyList(),
    val missing: List<String> = emptyList(),
    val values: VerificationValuesJson? = null,
    val note: String? = null,
) {
    /** Codes the server does not know are dropped here; the route refuses them before this. */
    fun toDomain() = VerificationProblem(
        wrong = wrong.mapNotNullTo(LinkedHashSet(), VerificationField::fromWire),
        missing = missing.mapNotNullTo(LinkedHashSet(), VerificationField::fromWire),
        values = values?.toDomain(),
        note = note,
    )

    companion object {
        fun of(p: VerificationProblem) = VerificationProblemJson(
            wrong = p.wrong.map { it.wire },
            missing = p.missing.map { it.wire },
            values = p.values?.let(VerificationValuesJson::of),
            note = p.note,
        )
    }
}

/** A verification's two photos, base64 JPEG, PNG or WebP. */
@Serializable
data class VerificationPhotosJson(val front: String? = null, val label: String? = null)

internal object FoodVerificationJson {
    fun problem(p: VerificationProblem): String = FoodReportJson.json.encodeToString(VerificationProblemJson.serializer(), VerificationProblemJson.of(p))
    fun problem(text: String): VerificationProblem = FoodReportJson.json.decodeFromString(VerificationProblemJson.serializer(), text).toDomain()
}
