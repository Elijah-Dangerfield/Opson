package com.opson.libraries.nutrition

enum class MicroGroup { Vitamins, Minerals }

enum class MicroUnit { Mg, Ug }

/**
 * The thirteen vitamins and minerals the app tracks, from the prototype's
 * `MICROS` table, in its order. [reference] is a typical daily target in
 * [unit]; amounts keyed by a micronutrient are always in its own unit.
 *
 * Sodium [isLimit]: its reference is a ceiling, not a target. It still draws
 * with the same colours as the rest (no red), and the reference copy says
 * "limit". Labels are UI copy and live in string resources keyed by [key].
 */
enum class Micronutrient(
    val key: String,
    val group: MicroGroup,
    val reference: Double,
    val unit: MicroUnit,
    val isLimit: Boolean = false,
) {
    VitaminA("vitamin_a", MicroGroup.Vitamins, 700.0, MicroUnit.Ug),
    VitaminB12("vitamin_b12", MicroGroup.Vitamins, 2.4, MicroUnit.Ug),
    VitaminC("vitamin_c", MicroGroup.Vitamins, 90.0, MicroUnit.Mg),
    VitaminD("vitamin_d", MicroGroup.Vitamins, 15.0, MicroUnit.Ug),
    VitaminE("vitamin_e", MicroGroup.Vitamins, 15.0, MicroUnit.Mg),
    VitaminK("vitamin_k", MicroGroup.Vitamins, 120.0, MicroUnit.Ug),
    Folate("folate", MicroGroup.Vitamins, 400.0, MicroUnit.Ug),
    Calcium("calcium", MicroGroup.Minerals, 1000.0, MicroUnit.Mg),
    Iron("iron", MicroGroup.Minerals, 18.0, MicroUnit.Mg),
    Magnesium("magnesium", MicroGroup.Minerals, 420.0, MicroUnit.Mg),
    Potassium("potassium", MicroGroup.Minerals, 4700.0, MicroUnit.Mg),
    Sodium("sodium", MicroGroup.Minerals, 2300.0, MicroUnit.Mg, isLimit = true),
    Zinc("zinc", MicroGroup.Minerals, 11.0, MicroUnit.Mg),
    ;

    /** [amount] as a percent of the daily reference, uncapped: 134 means a third over. */
    fun percentOf(amount: Double): Double = amount / reference * 100

    companion object {
        fun inGroup(group: MicroGroup): List<Micronutrient> = entries.filter { it.group == group }
    }
}

/** The six scores on the Today scores page and the nutrients detail, in display order. */
enum class NutritionScore(val micros: List<Micronutrient>) {
    AllTargets(Micronutrient.entries),
    Vitamins(Micronutrient.inGroup(MicroGroup.Vitamins)),
    Minerals(Micronutrient.inGroup(MicroGroup.Minerals)),
    BoneHealth(listOf(Micronutrient.Calcium, Micronutrient.VitaminD, Micronutrient.VitaminK, Micronutrient.Magnesium)),
    ImmuneSupport(listOf(Micronutrient.VitaminA, Micronutrient.VitaminC, Micronutrient.VitaminD, Micronutrient.Zinc)),
    EnergyAndMetabolism(listOf(Micronutrient.VitaminB12, Micronutrient.Iron, Micronutrient.Magnesium, Micronutrient.Folate)),
}

object NutritionScores {

    /**
     * Sums [amounts] per micronutrient and turns each total into a percent of its
     * reference. A micronutrient nobody logged is 0%: foods without micro data
     * (custom foods, name-only items) count as none, which the nutrients caption
     * explains.
     */
    fun percents(amounts: Iterable<Map<Micronutrient, Double>>): Map<Micronutrient, Double> {
        val totals = mutableMapOf<Micronutrient, Double>()
        for (item in amounts) {
            for ((micro, amount) in item) totals[micro] = (totals[micro] ?: 0.0) + amount
        }
        return Micronutrient.entries.associateWith { it.percentOf(totals[it] ?: 0.0) }
    }

    /**
     * Each score is the mean of its micronutrients' percents, each capped at 100
     * first, so a lot of vitamin C cannot make up for no vitamin D. Missing
     * entries count as 0. Resolve the result through the policy as
     * [Metric.Micros] with `of = 100`.
     */
    fun compute(percentByMicro: Map<Micronutrient, Double>): Map<NutritionScore, Double> =
        NutritionScore.entries.associateWith { score ->
            score.micros.sumOf { (percentByMicro[it] ?: 0.0).coerceIn(0.0, MAX_PERCENT) } / score.micros.size
        }

    private const val MAX_PERCENT = 100.0
}
