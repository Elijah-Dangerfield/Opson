package com.opson.server.data

import com.opson.server.domain.CorrectionValues
import com.opson.server.domain.FoodSnapshot
import com.opson.server.domain.Nutrients
import com.opson.server.domain.Portion
import com.opson.server.domain.SuggestedValues
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The JSON shapes of `food_reports.suggested`, `food_reports.snapshot` and
 * `food_corrections.fields` / `base`. The admin routes send the same shapes,
 * so the console reads what is stored.
 */
@Serializable
data class SuggestedJson(
    val basisLabel: String? = null,
    val basisGrams: Double? = null,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val servingLabel: String? = null,
    val servingGrams: Double? = null,
) {
    fun toDomain() = SuggestedValues(basisLabel, basisGrams, kcal, protein, carbs, fat, servingLabel, servingGrams)

    companion object {
        fun of(v: SuggestedValues) = SuggestedJson(v.basisLabel, v.basisGrams, v.kcal, v.protein, v.carbs, v.fat, v.servingLabel, v.servingGrams)
    }
}

@Serializable
data class CorrectionValuesJson(
    val name: String? = null,
    val brand: String? = null,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val servingLabel: String? = null,
    val servingGrams: Double? = null,
) {
    fun toDomain() = CorrectionValues(name, brand, kcal, protein, carbs, fat, servingLabel, servingGrams)

    companion object {
        fun of(v: CorrectionValues) = CorrectionValuesJson(v.name, v.brand, v.kcal, v.protein, v.carbs, v.fat, v.servingLabel, v.servingGrams)
    }
}

@Serializable
data class Per100Json(
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
) {
    fun toDomain() = Nutrients(kcal, protein, carbs, fat, fiber)

    companion object {
        fun of(n: Nutrients) = Per100Json(n.kcal, n.protein, n.carbs, n.fat, n.fiber)
    }
}

@Serializable
data class ServingJson(val label: String, val grams: Double? = null)

@Serializable
data class SnapshotJson(
    val name: String,
    val brand: String? = null,
    val source: String,
    val per100g: Per100Json = Per100Json(),
    val serving: ServingJson? = null,
) {
    fun toDomain() = FoodSnapshot(name, brand, source, per100g.toDomain(), serving?.let { Portion(it.label, it.grams) })

    companion object {
        fun of(s: FoodSnapshot) = SnapshotJson(s.name, s.brand, s.source, Per100Json.of(s.per100g), s.serving?.let { ServingJson(it.label, it.grams) })
    }
}

internal object FoodReportJson {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun suggested(v: SuggestedValues): String = json.encodeToString(SuggestedJson.serializer(), SuggestedJson.of(v))
    fun suggested(text: String): SuggestedValues = json.decodeFromString(SuggestedJson.serializer(), text).toDomain()
    fun snapshot(s: FoodSnapshot): String = json.encodeToString(SnapshotJson.serializer(), SnapshotJson.of(s))
    fun snapshot(text: String): FoodSnapshot = json.decodeFromString(SnapshotJson.serializer(), text).toDomain()
    fun values(v: CorrectionValues): String = json.encodeToString(CorrectionValuesJson.serializer(), CorrectionValuesJson.of(v))
    fun values(text: String): CorrectionValues = json.decodeFromString(CorrectionValuesJson.serializer(), text).toDomain()
}
