package com.opson.server.domain

import kotlin.math.abs
import kotlin.math.max

/**
 * A nutrient a pack's label can state, in the unit the catalog stores it in.
 * [wire] is the `micros` key the catalog already uses (`sodium_mg`,
 * `vitamin_d_ug`), so a submitted food's micros read like every other food's;
 * the first five are the catalog's own columns.
 *
 * The app keeps a copy of this list (`LabelNutrient` in `:libraries:foods`);
 * a key added here needs adding there, or the app drops it from the form.
 */
enum class NutrientKey(val wire: String, val unit: NutrientUnit, val dailyValue: Double? = null) {
    Energy("energy_kcal", NutrientUnit.Kcal),
    Protein("protein_g", NutrientUnit.Gram, 50.0),
    Carbs("carbs_g", NutrientUnit.Gram, 275.0),
    Fat("fat_g", NutrientUnit.Gram, 78.0),
    Fiber("fiber_g", NutrientUnit.Gram, 28.0),
    Sugars("sugars_g", NutrientUnit.Gram),
    AddedSugars("added_sugars_g", NutrientUnit.Gram, 50.0),
    SugarAlcohols("sugar_alcohols_g", NutrientUnit.Gram),
    SaturatedFat("saturated_fat_g", NutrientUnit.Gram, 20.0),
    TransFat("trans_fat_g", NutrientUnit.Gram),
    Cholesterol("cholesterol_mg", NutrientUnit.Milligram, 300.0),
    Sodium("sodium_mg", NutrientUnit.Milligram, 2300.0),
    Potassium("potassium_mg", NutrientUnit.Milligram, 4700.0),
    Calcium("calcium_mg", NutrientUnit.Milligram, 1300.0),
    Iron("iron_mg", NutrientUnit.Milligram, 18.0),
    Magnesium("magnesium_mg", NutrientUnit.Milligram, 420.0),
    Phosphorus("phosphorus_mg", NutrientUnit.Milligram, 1250.0),
    Zinc("zinc_mg", NutrientUnit.Milligram, 11.0),
    Copper("copper_mg", NutrientUnit.Milligram, 0.9),
    Manganese("manganese_mg", NutrientUnit.Milligram, 2.3),
    Selenium("selenium_ug", NutrientUnit.Microgram, 55.0),
    VitaminA("vitamin_a_rae_ug", NutrientUnit.Microgram, 900.0),
    VitaminC("vitamin_c_mg", NutrientUnit.Milligram, 90.0),
    VitaminD("vitamin_d_ug", NutrientUnit.Microgram, 20.0),
    VitaminE("vitamin_e_mg", NutrientUnit.Milligram, 15.0),
    VitaminK("vitamin_k_ug", NutrientUnit.Microgram, 120.0),
    Thiamin("thiamin_mg", NutrientUnit.Milligram, 1.2),
    Riboflavin("riboflavin_mg", NutrientUnit.Milligram, 1.3),
    Niacin("niacin_mg", NutrientUnit.Milligram, 16.0),
    VitaminB6("vitamin_b6_mg", NutrientUnit.Milligram, 1.7),
    Folate("folate_dfe_ug", NutrientUnit.Microgram, 400.0),
    VitaminB12("vitamin_b12_ug", NutrientUnit.Microgram, 2.4),
    Choline("choline_mg", NutrientUnit.Milligram, 550.0),
    ;

    /** True for the five the catalog keeps in columns rather than `micros`. */
    val isColumn: Boolean get() = this == Energy || this == Protein || this == Carbs || this == Fat || this == Fiber

    companion object {
        val REQUIRED: List<NutrientKey> = listOf(Energy, Protein, Carbs, Fat)

        fun fromWire(value: String): NutrientKey? = entries.firstOrNull { it.wire == value }
    }
}

enum class NutrientUnit(val gramsPer: Double?) {
    Kcal(null),
    Gram(1.0),
    Milligram(1e-3),
    Microgram(1e-6),
}

/** What a serving is measured in. A label in ounces is converted to one of these when it is read. */
enum class ServingUnit(val wire: String) {
    Gram("g"),
    Milliliter("ml"),
    ;

    companion object {
        fun fromWire(value: String?): ServingUnit? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * How the label counts carbohydrate. A US panel's total carbohydrate includes
 * fiber and sugar alcohols; an EU panel's excludes fiber and lists it apart.
 * The energy check needs to know which, so the model says which panel it saw.
 */
enum class CarbBasis(val wire: String) {
    Us("us"),
    Eu("eu"),
    Unknown("unknown"),
    ;

    companion object {
        fun fromWire(value: String?): CarbBasis = entries.firstOrNull { it.wire == value } ?: Unknown
    }
}

/**
 * Everything a nutrition label says, in the catalog's units: energy in kcal,
 * the rest in the unit its [NutrientKey] names. [perServing] and [per100] are
 * what the label printed (per 100 g, or per 100 ml for a drink), each filled
 * from the other where the serving size allows.
 */
data class LabelFacts(
    val name: String? = null,
    val brand: String? = null,
    val barcode: String? = null,
    val servingSize: Double? = null,
    val servingUnit: ServingUnit? = null,
    /** The household measure as printed: "2/3 cup (55 g)". */
    val servingLabel: String? = null,
    val servingsPerContainer: Double? = null,
    val basis: CarbBasis = CarbBasis.Unknown,
    val perServing: Map<NutrientKey, Double> = emptyMap(),
    val per100: Map<NutrientKey, Double> = emptyMap(),
) {
    /** [key] per 100 g or ml: as printed, else worked out from the serving. */
    fun per100(key: NutrientKey): Double? =
        per100[key] ?: perServing[key]?.let { value -> servingSize?.takeIf { it > 0 }?.let { value * 100.0 / it } }

    /** [key] per serving: as printed, else worked out from per 100. */
    fun perServing(key: NutrientKey): Double? =
        perServing[key] ?: per100[key]?.let { value -> servingSize?.takeIf { it > 0 }?.let { value * it / 100.0 } }

    /** Both columns filled from each other where the serving size allows. */
    fun completed(): LabelFacts {
        val keys = perServing.keys + per100.keys
        return copy(
            perServing = keys.mapNotNull { key -> perServing(key)?.let { key to it } }.toMap(),
            per100 = keys.mapNotNull { key -> per100(key)?.let { key to it } }.toMap(),
        )
    }
}

enum class LabelCheckCode(val wire: String, val blocking: Boolean) {
    /** Name, serving size, energy, protein, carbs or fat is missing. */
    MissingRequired("missing_required", blocking = true),

    /** Something no food can hold: a negative amount, more fat than the serving weighs, sugars over carbs. */
    ImpossibleValue("impossible_value", blocking = true),

    /** Energy and the 4/4/9 sum of the macros are further apart than [LabelChecks.energyTolerance]. */
    EnergyMacrosMismatch("energy_macros_mismatch", blocking = false),

    /** The kcal figure is the macros' energy in kJ: the kJ line was read as kcal. */
    EnergyLooksLikeKj("energy_looks_like_kj", blocking = false),

    /** The label printed kJ and kcal, and they disagree by more than rounding. */
    EnergyUnitsDisagree("energy_units_disagree", blocking = false),

    /** Per serving and per 100 disagree for a nutrient, or for all of them (then the serving size is off). */
    ServingMismatch("serving_per_100_mismatch", blocking = false),

    /** Sodium is 1000 times off: grams read as milligrams, or the other way. */
    SodiumUnit("sodium_unit", blocking = false),
    ;

    companion object {
        fun fromWire(value: String): LabelCheckCode? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * One failed check. [fields] are the form's field keys it concerns (a
 * [NutrientKey.wire], or `name`, `serving_size`). [expected] is what the
 * numbers say the value should be, where there is one answer ("Use 230 kcal"),
 * and [actual] is what the label or the person gave.
 */
data class LabelCheck(
    val code: LabelCheckCode,
    val fields: List<String>,
    val expected: Double? = null,
    val actual: Double? = null,
)

/**
 * The deterministic checks on a label, run on the model's reading and again
 * on what the person submits. The app runs the same rules (`LabelChecks` in
 * `:libraries:foods`) as the person types; the two are held to the same cases
 * by `LabelChecksTest` here and its twin there.
 *
 * **Energy against the macros.** Labels may use any of several Atwater
 * methods, and round every figure, so the declared energy is compared with
 * each way of counting and the nearest wins:
 *
 *  - general factors: 4 protein + 9 fat + 4 carbohydrate;
 *  - US total carbohydrate (fiber and sugar alcohols inside it): fiber at
 *    2 kcal/g and sugar alcohols at 2.4 kcal/g instead of 4;
 *  - EU carbohydrate (fiber outside it): plus 2 kcal/g of fiber and 2.4 of
 *    polyols.
 *
 * The check fails when even the nearest is more than [energyTolerance] away:
 * 15 kcal or 12% of the declared energy, whichever is larger. 15 kcal covers
 * a label's rounding (energy to 5 or 10 kcal, each macro to the gram); 12%
 * covers the specific factors some foods use (nuts, wholegrains).
 */
object LabelChecks {

    const val ENERGY_ABS_TOLERANCE = 15.0
    const val ENERGY_REL_TOLERANCE = 0.12
    const val KJ_PER_KCAL = 4.184

    /** kJ and kcal printed side by side may differ by rounding: 2% or 5 kcal. */
    private const val UNITS_REL_TOLERANCE = 0.02
    private const val UNITS_ABS_TOLERANCE = 5.0

    /** Per serving against per 100: 10%, or the label's rounding for the nutrient's unit. */
    private const val SERVING_REL_TOLERANCE = 0.10

    /** Sodium is at most 39.3% of what salt weighs, and no food is saltier than salt. */
    private const val MAX_SODIUM_SHARE = 0.393

    /**
     * Below this the kJ explanation fits almost any small mismatch (20 kcal
     * declared for 5 kcal of macros is also 20 kJ), so it is only offered
     * where it is the likely one.
     */
    private const val KJ_MIN_KCAL = 40.0

    /** A drink can be denser than water (syrup is about 1.3 g/ml), so a ml serving gets room. */
    private const val ML_DENSITY_ALLOWANCE = 1.5

    fun energyTolerance(declared: Double): Double = max(ENERGY_ABS_TOLERANCE, ENERGY_REL_TOLERANCE * declared)

    /** Every way the label could have counted energy from these macros, null without protein, carbs and fat. */
    fun atwaterCandidates(values: Map<NutrientKey, Double>, basis: CarbBasis): List<Double>? {
        val protein = values[NutrientKey.Protein] ?: return null
        val carbs = values[NutrientKey.Carbs] ?: return null
        val fat = values[NutrientKey.Fat] ?: return null
        val fiber = values[NutrientKey.Fiber] ?: 0.0
        val polyols = values[NutrientKey.SugarAlcohols] ?: 0.0
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

    /** The candidate nearest [declared]. */
    fun atwater(values: Map<NutrientKey, Double>, basis: CarbBasis, declared: Double): Double? =
        atwaterCandidates(values, basis)?.minBy { abs(it - declared) }

    fun check(facts: LabelFacts): List<LabelCheck> = buildList {
        missing(facts)?.let(::add)
        addAll(impossible(facts))
        energy(facts)?.let(::add)
        addAll(serving(facts))
        sodium(facts)?.let(::add)
    }

    /** kJ and kcal both printed: they should agree within rounding. */
    fun unitsDisagree(kcal: Double, kj: Double): LabelCheck? {
        val expected = kj / KJ_PER_KCAL
        val allowed = max(UNITS_ABS_TOLERANCE, UNITS_REL_TOLERANCE * max(kcal, expected))
        if (abs(kcal - expected) <= allowed) return null
        return LabelCheck(LabelCheckCode.EnergyUnitsDisagree, listOf(NutrientKey.Energy.wire), expected = expected, actual = kcal)
    }

    private fun missing(facts: LabelFacts): LabelCheck? {
        val fields = buildList {
            if (facts.name.isNullOrBlank()) add("name")
            if ((facts.servingSize ?: 0.0) <= 0.0 || facts.servingUnit == null) add("serving_size")
            NutrientKey.REQUIRED.forEach { if (facts.perServing(it) == null && facts.per100(it) == null) add(it.wire) }
        }
        return fields.takeIf { it.isNotEmpty() }?.let { LabelCheck(LabelCheckCode.MissingRequired, it) }
    }

    /** The two places the label states amounts, with the weight each is for. */
    private fun columns(facts: LabelFacts): List<Pair<Map<NutrientKey, Double>, Double?>> {
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
                add(NutrientKey.Protein); add(NutrientKey.Fat); add(NutrientKey.Carbs)
                if (facts.basis == CarbBasis.Eu) add(NutrientKey.Fiber)
            }
            val macroSum = macroKeys.sumOf { values[it] ?: 0.0 }
            if (macroSum > mass + max(1.0, 0.02 * mass)) macroKeys.filter { (values[it] ?: 0.0) > 0 }.forEach { found += it.wire }
            // Energy over 9 kcal a gram is left to the energy check: it is nearly always kJ read as kcal, which has its own fix.
        }
        for ((values, _) in columns(facts)) {
            fun over(part: NutrientKey, whole: NutrientKey) {
                val p = values[part] ?: return
                val w = values[whole] ?: return
                if (p > w + 0.5) found += part.wire
            }
            over(NutrientKey.Sugars, NutrientKey.Carbs)
            over(NutrientKey.AddedSugars, NutrientKey.Sugars)
            over(NutrientKey.SaturatedFat, NutrientKey.Fat)
            over(NutrientKey.TransFat, NutrientKey.Fat)
            if (facts.basis == CarbBasis.Us) {
                over(NutrientKey.Fiber, NutrientKey.Carbs)
                over(NutrientKey.SugarAlcohols, NutrientKey.Carbs)
            }
        }
        return if (found.isEmpty()) emptyList() else listOf(LabelCheck(LabelCheckCode.ImpossibleValue, found.toList()))
    }

    /**
     * Energy against the macros, per serving when the label has a whole serving
     * to check, else per 100. A kcal figure that is the macros' energy in kJ
     * says the kJ line was read; that is its own check, with the fix.
     */
    private fun energy(facts: LabelFacts): LabelCheck? {
        val values = listOf(facts.perServing, facts.per100).firstOrNull { column ->
            NutrientKey.Energy in column && NutrientKey.REQUIRED.all { it in column }
        } ?: return null
        val declared = values.getValue(NutrientKey.Energy)
        val expected = atwater(values, facts.basis, declared) ?: return null
        if (abs(declared - expected) <= energyTolerance(declared)) return null
        val asKcal = declared / KJ_PER_KCAL
        val fields = listOf(NutrientKey.Energy.wire, NutrientKey.Protein.wire, NutrientKey.Carbs.wire, NutrientKey.Fat.wire)
        val fromKj = atwater(values, facts.basis, asKcal)
        if (fromKj != null && fromKj >= KJ_MIN_KCAL && abs(asKcal - fromKj) <= energyTolerance(asKcal)) {
            return LabelCheck(LabelCheckCode.EnergyLooksLikeKj, listOf(NutrientKey.Energy.wire), expected = asKcal, actual = declared)
        }
        return LabelCheck(LabelCheckCode.EnergyMacrosMismatch, fields, expected = expected, actual = declared)
    }

    private fun servingTolerance(key: NutrientKey): Double = when (key.unit) {
        NutrientUnit.Kcal -> 10.0
        NutrientUnit.Gram -> 1.0
        NutrientUnit.Milligram -> 10.0
        NutrientUnit.Microgram -> 5.0
    }

    /**
     * Per serving against per 100 g, for each nutrient the label gives both
     * ways. When three or more disagree by the same factor the serving size is
     * what is wrong, and it is reported once with the size the numbers imply.
     */
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

    /**
     * Sodium a thousand times off. More sodium than salt could carry means
     * milligrams were read as grams; a fraction of a milligram, which no
     * label prints, means grams were read as milligrams.
     */
    private fun sodium(facts: LabelFacts): LabelCheck? {
        val sodium = facts.per100(NutrientKey.Sodium) ?: return null
        val maxMg = MAX_SODIUM_SHARE * 100.0 * 1000.0
        val perServing = facts.perServing(NutrientKey.Sodium)
        return when {
            sodium > maxMg -> LabelCheck(
                LabelCheckCode.SodiumUnit,
                listOf(NutrientKey.Sodium.wire),
                expected = (perServing ?: sodium) / 1000.0,
                actual = perServing ?: sodium,
            )
            perServing != null && perServing > 0 && perServing < 1 -> LabelCheck(
                LabelCheckCode.SodiumUnit,
                listOf(NutrientKey.Sodium.wire),
                expected = perServing * 1000.0,
                actual = perServing,
            )
            else -> null
        }
    }
}
