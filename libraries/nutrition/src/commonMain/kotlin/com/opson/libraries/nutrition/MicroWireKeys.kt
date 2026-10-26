package com.opson.libraries.nutrition

/**
 * The key a logged entry or catalog food uses for this micronutrient. Those maps
 * carry the unit in the key and use the server's names (`sodium_mg`,
 * `vitamin_a_rae_ug`, `folate_dfe_ug`, see `apps/server/README.md`), which are
 * not [Micronutrient.key]: vitamin A is stored as RAE and folate as DFE, and
 * the reference in the table is in those same units.
 */
val Micronutrient.wireKey: String
    get() = when (this) {
        Micronutrient.VitaminA -> "vitamin_a_rae_ug"
        Micronutrient.VitaminB12 -> "vitamin_b12_ug"
        Micronutrient.VitaminC -> "vitamin_c_mg"
        Micronutrient.VitaminD -> "vitamin_d_ug"
        Micronutrient.VitaminE -> "vitamin_e_mg"
        Micronutrient.VitaminK -> "vitamin_k_ug"
        Micronutrient.Folate -> "folate_dfe_ug"
        Micronutrient.Calcium -> "calcium_mg"
        Micronutrient.Iron -> "iron_mg"
        Micronutrient.Magnesium -> "magnesium_mg"
        Micronutrient.Potassium -> "potassium_mg"
        Micronutrient.Sodium -> "sodium_mg"
        Micronutrient.Zinc -> "zinc_mg"
    }

private val byWireKey: Map<String, Micronutrient> = Micronutrient.entries.associateBy { it.wireKey }

/** `null` for a key the app does not track (sugars, choline, phosphorus). */
fun Micronutrient.Companion.fromWireKey(key: String): Micronutrient? = byWireKey[key]

/**
 * One entry's micros keyed by [Micronutrient], in each one's own unit, ready
 * for [MicroPercents.of]. Untracked keys are dropped.
 */
fun Map<String, Double>.toMicronutrients(): Map<Micronutrient, Double> = buildMap {
    for ((key, amount) in this@toMicronutrients) {
        val micro = Micronutrient.fromWireKey(key) ?: continue
        if (amount.isFinite()) put(micro, (get(micro) ?: 0.0) + amount)
    }
}

/**
 * A micronutrient's daily reference, `700 µg` or `2,300 mg limit`, spelled by
 * the policy's own formatter. It is a reference, not something eaten, so it is
 * not gated by visibility; screens only show it beside a visible micro row.
 */
fun VisibilityPolicy.microReference(micro: Micronutrient): String = formatter.microReference(micro)
