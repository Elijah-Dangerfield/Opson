package com.opson.libraries.foods

import kotlin.math.abs
import kotlin.math.max

/**
 * A nutrient a pack's label can state, keyed like the catalog's `micros`
 * (`sodium_mg`). A copy of the server's `NutrientKey`; a key added there needs
 * adding here, or the form drops it.
 */
enum class LabelNutrient(val wire: String, val unit: LabelUnit) {
    Energy("energy_kcal", LabelUnit.Kcal),
    Protein("protein_g", LabelUnit.Gram),
    Carbs("carbs_g", LabelUnit.Gram),
    Fat("fat_g", LabelUnit.Gram),
    Fiber("fiber_g", LabelUnit.Gram),
    Sugars("sugars_g", LabelUnit.Gram),
    AddedSugars("added_sugars_g", LabelUnit.Gram),
    SugarAlcohols("sugar_alcohols_g", LabelUnit.Gram),
    SaturatedFat("saturated_fat_g", LabelUnit.Gram),
    TransFat("trans_fat_g", LabelUnit.Gram),
    Cholesterol("cholesterol_mg", LabelUnit.Milligram),
    Sodium("sodium_mg", LabelUnit.Milligram),
    Potassium("potassium_mg", LabelUnit.Milligram),
    Calcium("calcium_mg", LabelUnit.Milligram),
    Iron("iron_mg", LabelUnit.Milligram),
    Magnesium("magnesium_mg", LabelUnit.Milligram),
    Phosphorus("phosphorus_mg", LabelUnit.Milligram),
    Zinc("zinc_mg", LabelUnit.Milligram),
    Copper("copper_mg", LabelUnit.Milligram),
    Manganese("manganese_mg", LabelUnit.Milligram),
    Selenium("selenium_ug", LabelUnit.Microgram),
    VitaminA("vitamin_a_rae_ug", LabelUnit.Microgram),
    VitaminC("vitamin_c_mg", LabelUnit.Milligram),
    VitaminD("vitamin_d_ug", LabelUnit.Microgram),
    VitaminE("vitamin_e_mg", LabelUnit.Milligram),
    VitaminK("vitamin_k_ug", LabelUnit.Microgram),
    Thiamin("thiamin_mg", LabelUnit.Milligram),
    Riboflavin("riboflavin_mg", LabelUnit.Milligram),
    Niacin("niacin_mg", LabelUnit.Milligram),
    VitaminB6("vitamin_b6_mg", LabelUnit.Milligram),
    Folate("folate_dfe_ug", LabelUnit.Microgram),
    VitaminB12("vitamin_b12_ug", LabelUnit.Microgram),
    Choline("choline_mg", LabelUnit.Milligram),
    ;

    companion object {
        val REQUIRED: List<LabelNutrient> = listOf(Energy, Protein, Carbs, Fat)

        fun fromWire(value: String): LabelNutrient? = entries.firstOrNull { it.wire == value }
    }
}

enum class LabelUnit(val symbol: String, val gramsPer: Double?) {
    Kcal("kcal", null),
    Gram("g", 1.0),
    Milligram("mg", 1e-3),
    Microgram("mcg", 1e-6),
}

enum class ServingUnit(val wire: String) {
    Gram("g"),
    Milliliter("ml"),
    ;

    companion object {
        fun fromWire(value: String?): ServingUnit? = entries.firstOrNull { it.wire == value }
    }
}

/** A US panel counts fiber inside total carbohydrate; an EU table lists it apart. */
enum class CarbBasis(val wire: String) {
    Us("us"),
    Eu("eu"),
    Unknown("unknown"),
    ;

    companion object {
        fun fromWire(value: String?): CarbBasis = entries.firstOrNull { it.wire == value } ?: Unknown
    }
}

/** What a nutrition label says, in the catalog's units. The server's `LabelFacts`, field for field. */
data class LabelFacts(
    val name: String? = null,
    val brand: String? = null,
    val barcode: String? = null,
    val servingSize: Double? = null,
    val servingUnit: ServingUnit? = null,
    val servingLabel: String? = null,
    val servingsPerContainer: Double? = null,
    val basis: CarbBasis = CarbBasis.Unknown,
    val perServing: Map<LabelNutrient, Double> = emptyMap(),
    val per100: Map<LabelNutrient, Double> = emptyMap(),
) {
    fun per100(key: LabelNutrient): Double? =
        per100[key] ?: perServing[key]?.let { value -> servingSize?.takeIf { it > 0 }?.let { value * 100.0 / it } }

    fun perServing(key: LabelNutrient): Double? =
        perServing[key] ?: per100[key]?.let { value -> servingSize?.takeIf { it > 0 }?.let { value * it / 100.0 } }
}

enum class LabelCheckCode(val wire: String, val blocking: Boolean) {
    MissingRequired("missing_required", blocking = true),
    ImpossibleValue("impossible_value", blocking = true),
    EnergyMacrosMismatch("energy_macros_mismatch", blocking = false),
    EnergyLooksLikeKj("energy_looks_like_kj", blocking = false),
    EnergyUnitsDisagree("energy_units_disagree", blocking = false),
    ServingMismatch("serving_per_100_mismatch", blocking = false),
    SodiumUnit("sodium_unit", blocking = false),
    ;

    companion object {
        fun fromWire(value: String): LabelCheckCode? = entries.firstOrNull { it.wire == value }
    }
}

/** One failed check: the form fields it concerns, and the figure the numbers point to where there is one. */
data class LabelCheck(
    val code: LabelCheckCode,
    val fields: List<String>,
    val expected: Double? = null,
    val actual: Double? = null,
)

/**
 * The label checks, run live as the person edits. The server runs the same
 * rules (`LabelChecks` in `:apps:server`) on what the model read and on what
 * is submitted; `LabelChecksTest` on each side holds the same cases, so read
 * the server's KDoc for why each tolerance is what it is. In short: energy
 * within 15 kcal or 12% of the nearest Atwater method; per serving within
 * 10% (or the label's rounding) of per 100; sodium never a thousand times off.
 */
object LabelChecks {

    const val ENERGY_ABS_TOLERANCE = 15.0
    const val ENERGY_REL_TOLERANCE = 0.12
    const val KJ_PER_KCAL = 4.184

    private const val SERVING_REL_TOLERANCE = 0.10
    private const val MAX_SODIUM_SHARE = 0.393
    private const val ML_DENSITY_ALLOWANCE = 1.5
    private const val KJ_MIN_KCAL = 40.0

    fun energyTolerance(declared: Double): Double = max(ENERGY_ABS_TOLERANCE, ENERGY_REL_TOLERANCE * declared)

    fun atwaterCandidates(values: Map<LabelNutrient, Double>, basis: CarbBasis): List<Double>? {
        val protein = values[LabelNutrient.Protein] ?: return null
        val carbs = values[LabelNutrient.Carbs] ?: return null
        val fat = values[LabelNutrient.Fat] ?: return null
        val fiber = values[LabelNutrient.Fiber] ?: 0.0
        val polyols = values[LabelNutrient.SugarAlcohols] ?: 0.0
        val base = 4 * protein + 9 * fat
        val general = base + 4 * carbs
        val usTotal = base + 4 * max(0.0, carbs - fiber - polyols) + 2 * fiber + 2.4 * polyols
        val euExclusive = base + 4 * carbs + 2 * fiber + 2.4 * polyols
        return when (basis) {
            CarbBasis.Us -> listOf(general, usTotal)
            CarbBasis.Eu -> listOf(euExclusive, general)
            CarbBasis.Unknown -> listOf(general, usTotal, euExclusive)
        }
    }

    fun atwater(values: Map<LabelNutrient, Double>, basis: CarbBasis, declared: Double): Double? =
        atwaterCandidates(values, basis)?.minBy { abs(it - declared) }

    fun check(facts: LabelFacts): List<LabelCheck> = buildList {
        missing(facts)?.let(::add)
        addAll(impossible(facts))
        energy(facts)?.let(::add)
        addAll(serving(facts))
        sodium(facts)?.let(::add)
    }

    private fun missing(facts: LabelFacts): LabelCheck? {
        val fields = buildList {
            if (facts.name.isNullOrBlank()) add("name")
            if ((facts.servingSize ?: 0.0) <= 0.0 || facts.servingUnit == null) add("serving_size")
            LabelNutrient.REQUIRED.forEach { if (facts.perServing(it) == null && facts.per100(it) == null) add(it.wire) }
        }
        return fields.takeIf { it.isNotEmpty() }?.let { LabelCheck(LabelCheckCode.MissingRequired, it) }
    }

    private fun columns(facts: LabelFacts): List<Pair<Map<LabelNutrient, Double>, Double?>> {
        val serving = facts.servingSize?.takeIf { it > 0 }
        return listOf(facts.perServing to serving, facts.per100 to 100.0)
    }

    private fun impossible(facts: LabelFacts): List<LabelCheck> {
        val found = LinkedHashSet<String>()
        if ((facts.servingSize ?: 0.0) < 0 || (facts.servingsPerContainer ?: 0.0) < 0) found += "serving_size"
        val allowance = if (facts.servingUnit == ServingUnit.Milliliter) ML_DENSITY_ALLOWANCE else 1.0
        for ((values, weight) in columns(facts)) {
            values.forEach { (key, value) -> if (value < 0 || !value.isFinite()) found += key.wire }
            if (weight == null) continue
            val mass = weight * allowance
            values.forEach { (key, value) ->
                val grams = key.unit.gramsPer?.let { value * it } ?: return@forEach
                if (grams > mass + 0.5) found += key.wire
            }
            val macroKeys = buildList {
                add(LabelNutrient.Protein); add(LabelNutrient.Fat); add(LabelNutrient.Carbs)
                if (facts.basis == CarbBasis.Eu) add(LabelNutrient.Fiber)
            }
            val macroSum = macroKeys.sumOf { values[it] ?: 0.0 }
            if (macroSum > mass + max(1.0, 0.02 * mass)) macroKeys.filter { (values[it] ?: 0.0) > 0 }.forEach { found += it.wire }
        }
        for ((values, _) in columns(facts)) {
            fun over(part: LabelNutrient, whole: LabelNutrient) {
                val p = values[part] ?: return
                val w = values[whole] ?: return
                if (p > w + 0.5) found += part.wire
            }
            over(LabelNutrient.Sugars, LabelNutrient.Carbs)
            over(LabelNutrient.AddedSugars, LabelNutrient.Sugars)
            over(LabelNutrient.SaturatedFat, LabelNutrient.Fat)
            over(LabelNutrient.TransFat, LabelNutrient.Fat)
            if (facts.basis == CarbBasis.Us) {
                over(LabelNutrient.Fiber, LabelNutrient.Carbs)
                over(LabelNutrient.SugarAlcohols, LabelNutrient.Carbs)
            }
        }
        return if (found.isEmpty()) emptyList() else listOf(LabelCheck(LabelCheckCode.ImpossibleValue, found.toList()))
    }

    private fun energy(facts: LabelFacts): LabelCheck? {
        val values = listOf(facts.perServing, facts.per100).firstOrNull { column ->
            LabelNutrient.Energy in column && LabelNutrient.REQUIRED.all { it in column }
        } ?: return null
        val declared = values.getValue(LabelNutrient.Energy)
        val expected = atwater(values, facts.basis, declared) ?: return null
        if (abs(declared - expected) <= energyTolerance(declared)) return null
        val asKcal = declared / KJ_PER_KCAL
        val fromKj = atwater(values, facts.basis, asKcal)
        if (fromKj != null && fromKj >= KJ_MIN_KCAL && abs(asKcal - fromKj) <= energyTolerance(asKcal)) {
            return LabelCheck(LabelCheckCode.EnergyLooksLikeKj, listOf(LabelNutrient.Energy.wire), expected = asKcal, actual = declared)
        }
        val fields = listOf(LabelNutrient.Energy.wire, LabelNutrient.Protein.wire, LabelNutrient.Carbs.wire, LabelNutrient.Fat.wire)
        return LabelCheck(LabelCheckCode.EnergyMacrosMismatch, fields, expected = expected, actual = declared)
    }

    private fun servingTolerance(key: LabelNutrient): Double = when (key.unit) {
        LabelUnit.Kcal -> 10.0
        LabelUnit.Gram -> 1.0
        LabelUnit.Milligram -> 10.0
        LabelUnit.Microgram -> 5.0
    }

    private fun serving(facts: LabelFacts): List<LabelCheck> {
        val size = facts.servingSize?.takeIf { it > 0 } ?: return emptyList()
        val shared = facts.perServing.keys.intersect(facts.per100.keys)
        val off = shared.filter { key ->
            val printed = facts.perServing.getValue(key)
            val expected = facts.per100.getValue(key) * size / 100.0
            abs(printed - expected) > max(servingTolerance(key), SERVING_REL_TOLERANCE * max(printed, expected))
        }
        if (off.isEmpty()) return emptyList()
        val implied = off.mapNotNull { key ->
            val per100 = facts.per100.getValue(key)
            if (per100 <= 0) null else facts.perServing.getValue(key) * 100.0 / per100
        }.sorted()
        if (off.size >= 3 && implied.size == off.size) {
            val median = implied[implied.size / 2]
            if (implied.all { abs(it - median) <= SERVING_REL_TOLERANCE * median }) {
                return listOf(LabelCheck(LabelCheckCode.ServingMismatch, listOf("serving_size"), expected = median, actual = size))
            }
        }
        return off.map { key ->
            LabelCheck(
                LabelCheckCode.ServingMismatch,
                listOf(key.wire),
                expected = facts.per100.getValue(key) * size / 100.0,
                actual = facts.perServing.getValue(key),
            )
        }
    }

    private fun sodium(facts: LabelFacts): LabelCheck? {
        val sodium = facts.per100(LabelNutrient.Sodium) ?: return null
        val maxMg = MAX_SODIUM_SHARE * 100.0 * 1000.0
        val perServing = facts.perServing(LabelNutrient.Sodium)
        return when {
            sodium > maxMg -> LabelCheck(
                LabelCheckCode.SodiumUnit,
                listOf(LabelNutrient.Sodium.wire),
                expected = (perServing ?: sodium) / 1000.0,
                actual = perServing ?: sodium,
            )
            perServing != null && perServing > 0 && perServing < 1 -> LabelCheck(
                LabelCheckCode.SodiumUnit,
                listOf(LabelNutrient.Sodium.wire),
                expected = perServing * 1000.0,
                actual = perServing,
            )
            else -> null
        }
    }
}
