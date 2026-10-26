package com.opson.libraries.nutrition

/**
 * The energy and macros of one item, a meal or a day, in kcal and grams.
 *
 * [kcal] is `null` for a name-only item ("Just the name"), and a total is `null`
 * only when nothing in it carried energy. That keeps a meal of name-only items
 * from reading as zero: it resolves to [Display.Hidden] instead. A caller that
 * wants an empty day to show as 0 says `kcal ?: 0.0` on purpose.
 *
 * Macros have no such distinction. A name-only item adds 0 g, the same as the
 * prototype, so a builder decides whether a macro line is worth showing at all.
 */
data class NutrientAmounts(
    val kcal: Double?,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val fiber: Double = 0.0,
) {
    operator fun plus(other: NutrientAmounts): NutrientAmounts = NutrientAmounts(
        kcal = if (kcal == null && other.kcal == null) null else (kcal ?: 0.0) + (other.kcal ?: 0.0),
        protein = protein + other.protein,
        carbs = carbs + other.carbs,
        fat = fat + other.fat,
        fiber = fiber + other.fiber,
    )

    /** Scales every amount: a portion from per-100 g values is `per100g * (grams / 100)`. */
    operator fun times(factor: Double): NutrientAmounts = NutrientAmounts(
        kcal = kcal?.times(factor),
        protein = protein * factor,
        carbs = carbs * factor,
        fat = fat * factor,
        fiber = fiber * factor,
    )

    /** The amount for [metric], or `null` for a metric this does not carry. */
    fun of(metric: Metric): Double? = when (metric) {
        Metric.Calories -> kcal
        Metric.Protein -> protein
        Metric.Carbs -> carbs
        Metric.Fat -> fat
        Metric.Fiber -> fiber
        else -> null
    }

    companion object {
        /** Nothing logged: no energy, no macros. */
        val None = NutrientAmounts(kcal = null)
    }
}

/** The sum, [NutrientAmounts.None] for an empty list. */
fun Iterable<NutrientAmounts>.total(): NutrientAmounts = fold(NutrientAmounts.None) { sum, item -> sum + item }
