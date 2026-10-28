package com.opson.server.data

import com.opson.server.domain.Barcodes
import com.opson.server.domain.CarbBasis
import com.opson.server.domain.FoodLabelLimits
import com.opson.server.domain.LabelCheck
import com.opson.server.domain.LabelChecks
import com.opson.server.domain.LabelFacts
import com.opson.server.domain.LabelReading
import com.opson.server.domain.NutrientKey
import com.opson.server.domain.NutrientUnit
import com.opson.server.domain.ServingUnit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The model's answer for a pack, exactly as the structured-output schema in
 * [AnthropicLabelParser] asks for it: what is printed, in the units printed.
 * [LabelOutputReader] turns it into the catalog's units. Keeping the model's
 * job to transcription, and doing every conversion here, is what makes the
 * conversions testable without a model.
 */
@Serializable
internal data class LabelOutput(
    @SerialName("label_found") val labelFound: Boolean = false,
    val name: TextField = TextField(),
    val brand: TextField = TextField(),
    val barcode: TextField = TextField(),
    val serving: ServingField = ServingField(),
    @SerialName("servings_per_container") val servingsPerContainer: NumberField = NumberField(),
    val basis: String = "unknown",
    val nutrients: List<NutrientLine> = emptyList(),
) {
    @Serializable
    data class TextField(val value: String? = null, val confidence: Double = 0.0)

    @Serializable
    data class NumberField(val value: Double? = null, val confidence: Double = 0.0)

    @Serializable
    data class ServingField(
        val amount: Double? = null,
        val unit: String? = null,
        val household: String? = null,
        val confidence: Double = 0.0,
    )

    @Serializable
    data class NutrientLine(
        val nutrient: String,
        @SerialName("per_serving") val perServing: Double? = null,
        @SerialName("per_100") val per100: Double? = null,
        val unit: String,
        @SerialName("less_than") val lessThan: Boolean = false,
        val confidence: Double = 0.0,
    )

    companion object {
        /** The names the schema lets the model use, and the nutrient each is. `salt` becomes sodium. */
        val NUTRIENT_NAMES: Map<String, NutrientKey?> = mapOf(
            "energy" to NutrientKey.Energy,
            "protein" to NutrientKey.Protein,
            "carbohydrate" to NutrientKey.Carbs,
            "fat" to NutrientKey.Fat,
            "fiber" to NutrientKey.Fiber,
            "sugars" to NutrientKey.Sugars,
            "added_sugars" to NutrientKey.AddedSugars,
            "sugar_alcohols" to NutrientKey.SugarAlcohols,
            "saturated_fat" to NutrientKey.SaturatedFat,
            "trans_fat" to NutrientKey.TransFat,
            "cholesterol" to NutrientKey.Cholesterol,
            "sodium" to NutrientKey.Sodium,
            "salt" to null,
            "potassium" to NutrientKey.Potassium,
            "calcium" to NutrientKey.Calcium,
            "iron" to NutrientKey.Iron,
            "magnesium" to NutrientKey.Magnesium,
            "phosphorus" to NutrientKey.Phosphorus,
            "zinc" to NutrientKey.Zinc,
            "copper" to NutrientKey.Copper,
            "manganese" to NutrientKey.Manganese,
            "selenium" to NutrientKey.Selenium,
            "vitamin_a" to NutrientKey.VitaminA,
            "vitamin_c" to NutrientKey.VitaminC,
            "vitamin_d" to NutrientKey.VitaminD,
            "vitamin_e" to NutrientKey.VitaminE,
            "vitamin_k" to NutrientKey.VitaminK,
            "thiamin" to NutrientKey.Thiamin,
            "riboflavin" to NutrientKey.Riboflavin,
            "niacin" to NutrientKey.Niacin,
            "vitamin_b6" to NutrientKey.VitaminB6,
            "folate" to NutrientKey.Folate,
            "vitamin_b12" to NutrientKey.VitaminB12,
            "choline" to NutrientKey.Choline,
        )

        val UNITS = listOf("g", "mg", "mcg", "kcal", "kj", "percent_dv", "iu")
        val BASES = listOf("us", "eu", "unknown")
    }
}

/**
 * [LabelOutput] to [LabelReading]: every unit to the catalog's, kJ to kcal,
 * salt to sodium, %DV to an amount (US panels only), ounces to grams, and
 * whatever the model could not have meant (a barcode with a bad check digit,
 * a negative amount) to nothing. Pure, so `LabelOutputReaderTest` runs it on
 * recorded answers.
 */
internal object LabelOutputReader {

    private const val GRAMS_PER_OZ = 28.3495
    private const val ML_PER_FL_OZ = 29.5735

    /** Salt is sodium chloride: 2.5 g of salt carries 1 g of sodium. */
    private const val SALT_PER_SODIUM = 2.5

    /** Vitamin D: 40 IU per microgram. IU for other vitamins depends on the form, so they are dropped. */
    private const val VITAMIN_D_IU_PER_UG = 40.0

    fun read(output: LabelOutput): LabelReading {
        val basis = CarbBasis.fromWire(output.basis)
        val confidence = mutableMapOf<String, Double>()
        fun sure(key: String, value: Double) {
            confidence[key] = value.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0
        }

        val name = output.name.value?.trim()?.take(FoodLabelLimits.MAX_NAME)?.ifEmpty { null }
        sure("name", output.name.confidence)
        val brand = output.brand.value?.trim()?.take(FoodLabelLimits.MAX_BRAND)?.ifEmpty { null }
        sure("brand", output.brand.confidence)
        val barcode = output.barcode.value?.filter { it in '0'..'9' }?.takeIf { Barcodes.gtin14(it) != null }
        sure("barcode", output.barcode.confidence)

        val (servingSize, servingUnit) = serving(output.serving.amount, output.serving.unit)
        sure("serving_size", output.serving.confidence)
        val servingLabel = output.serving.household?.trim()?.take(FoodLabelLimits.MAX_SERVING_LABEL)?.ifEmpty { null }
        val servingsPerContainer = output.servingsPerContainer.value?.takeIf { it.isFinite() && it > 0 }
        sure("servings_per_container", output.servingsPerContainer.confidence)

        val perServing = mutableMapOf<NutrientKey, Double>()
        val per100 = mutableMapOf<NutrientKey, Double>()
        val kj = mutableMapOf<Boolean, Double>()
        val salt = mutableMapOf<Boolean, Double>()

        for (line in output.nutrients) {
            val named = line.nutrient.trim().lowercase()
            if (named !in LabelOutput.NUTRIENT_NAMES) continue
            val key = LabelOutput.NUTRIENT_NAMES[named]
            val unit = line.unit.trim().lowercase()
            for ((isServing, raw) in listOf(true to line.perServing, false to line.per100)) {
                val printed = raw?.takeIf { it.isFinite() && it >= 0 } ?: continue
                // "Less than 1 g" is somewhere between nothing and 1 g; half is the least wrong single figure.
                val value = if (line.lessThan) printed / 2 else printed
                when {
                    key == null -> toUnit(value, unit, NutrientUnit.Gram, null, basis)?.let { salt.putIfAbsent(isServing, it) }
                    key == NutrientKey.Energy && unit == "kj" -> kj.putIfAbsent(isServing, value)
                    else -> toUnit(value, unit, key.unit, key, basis)?.let { converted ->
                        (if (isServing) perServing else per100).putIfAbsent(key, converted)
                    }
                }
            }
            val confidenceKey = (key ?: NutrientKey.Sodium).wire
            if (confidenceKey !in confidence) sure(confidenceKey, line.confidence)
        }

        val unitChecks = mutableListOf<LabelCheck>()
        for ((isServing, column) in listOf(true to perServing, false to per100)) {
            val kilojoules = kj[isServing]
            val kcal = column[NutrientKey.Energy]
            when {
                kilojoules != null && kcal != null -> LabelChecks.unitsDisagree(kcal, kilojoules)?.let(unitChecks::add)
                kilojoules != null -> column[NutrientKey.Energy] = kilojoules / LabelChecks.KJ_PER_KCAL
            }
            salt[isServing]?.let { grams ->
                column.putIfAbsent(NutrientKey.Sodium, grams / SALT_PER_SODIUM * 1000.0)
            }
        }

        return LabelReading(
            facts = LabelFacts(
                name = name,
                brand = brand,
                barcode = barcode,
                servingSize = servingSize,
                servingUnit = servingUnit,
                servingLabel = servingLabel,
                servingsPerContainer = servingsPerContainer,
                basis = basis,
                perServing = perServing,
                per100 = per100,
            ),
            confidence = confidence,
            readable = output.labelFound,
            unitChecks = unitChecks.distinct().take(1),
        )
    }

    /** The serving in grams or ml. A serving given only as a household measure ("1 cup") has no size here. */
    fun serving(amount: Double?, unit: String?): Pair<Double?, ServingUnit?> {
        val value = amount?.takeIf { it.isFinite() && it > 0 } ?: return null to null
        return when (unit?.trim()?.lowercase()?.replace(".", "")?.replace(" ", "")) {
            "g", "gram", "grams" -> value to ServingUnit.Gram
            "kg" -> value * 1000 to ServingUnit.Gram
            "oz" -> value * GRAMS_PER_OZ to ServingUnit.Gram
            "ml", "milliliter", "millilitre" -> value to ServingUnit.Milliliter
            "l" -> value * 1000 to ServingUnit.Milliliter
            "cl" -> value * 10 to ServingUnit.Milliliter
            "floz" -> value * ML_PER_FL_OZ to ServingUnit.Milliliter
            else -> null to null
        }
    }

    /**
     * [value] printed in [unit] as an amount in [target]. %DV only on a US
     * panel, where the daily values are the FDA's; null when the unit cannot
     * be converted (a weight given as kcal, IU of anything but vitamin D).
     */
    fun toUnit(value: Double, unit: String, target: NutrientUnit, key: NutrientKey?, basis: CarbBasis): Double? {
        if (target == NutrientUnit.Kcal) return value.takeIf { unit == "kcal" }
        val grams = when (unit) {
            "g" -> value
            "mg" -> value / 1000.0
            "mcg" -> value / 1_000_000.0
            "iu" -> if (key == NutrientKey.VitaminD) value / VITAMIN_D_IU_PER_UG / 1_000_000.0 else return null
            "percent_dv" -> {
                if (basis != CarbBasis.Us) return null
                val daily = key?.dailyValue ?: return null
                return value / 100.0 * daily
            }
            else -> return null
        }
        return grams / (target.gramsPer ?: return null)
    }
}
