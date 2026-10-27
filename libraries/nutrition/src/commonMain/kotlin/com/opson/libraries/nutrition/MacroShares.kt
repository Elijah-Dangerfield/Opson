package com.opson.libraries.nutrition

private const val KCAL_PER_G_PROTEIN = 4.0
private const val KCAL_PER_G_CARBS = 4.0
private const val KCAL_PER_G_FAT = 9.0
private const val PERCENT = 100.0

/** The Atwater factors every tracker uses: 4 kcal a gram for protein and carbs, 9 for fat. */
val Metric.kcalPerGram: Double
    get() = when (this) {
        Metric.Protein -> KCAL_PER_G_PROTEIN
        Metric.Carbs -> KCAL_PER_G_CARBS
        Metric.Fat -> KCAL_PER_G_FAT
        else -> error("$this has no energy per gram")
    }

/**
 * The energy in each macro whose grams are known, at 4 kcal a gram for protein and carbs and 9
 * for fat, in that order.
 */
fun macroEnergy(protein: Double?, carbs: Double?, fat: Double?): Map<Metric, Double> = buildMap {
    protein?.let { put(Metric.Protein, it * KCAL_PER_G_PROTEIN) }
    carbs?.let { put(Metric.Carbs, it * KCAL_PER_G_CARBS) }
    fat?.let { put(Metric.Fat, it * KCAL_PER_G_FAT) }
}

/**
 * "% of energy": each [shown] macro's percent of the energy of the macros on show, protein,
 * carbs and fat in that order.
 *
 * The whole is the shown macros' own energy rather than the label's kcal (which rarely equals
 * 4/4/9 of its grams), so the shares make 100, and a hidden macro's share can't be read off the
 * gap the others leave. Empty when the macros on show have no energy.
 */
fun macroEnergyShares(protein: Double?, carbs: Double?, fat: Double?, shown: (Metric) -> Boolean): Map<Metric, Double> {
    val energy = macroEnergy(protein, carbs, fat).filterKeys(shown)
    val total = energy.values.sum()
    if (total <= 0.0) return emptyMap()
    return energy.mapValues { (_, kcal) -> kcal / total * PERCENT }
}
