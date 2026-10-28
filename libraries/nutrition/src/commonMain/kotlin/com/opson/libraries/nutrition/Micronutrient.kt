package com.opson.libraries.nutrition

enum class MicroGroup { Vitamins, Minerals }

enum class MicroUnit { Mg, Ug }

/**
 * The vitamins and minerals the app tracks, in the order screens list them: vitamins by letter
 * with the B group together, then choline; minerals alphabetically. Amounts keyed by a
 * micronutrient are always in its own [unit].
 *
 * [dailyValue] is the FDA's Daily Value for adults (21 CFR 101.9(c)(8)(iv) and (c)(9)), the
 * number a nutrition label's "% Daily Value" uses. It is the reference only for someone the app
 * knows nothing about; a person's own reference comes from [MicroReferences], which picks by age
 * and sex and takes a number of their own over either.
 *
 * Sodium [isLimit]: its reference is a ceiling, not a target. It still draws with the same
 * colours as the rest (no red), and the reference copy says "limit". Labels are UI copy and
 * live in string resources keyed by [key].
 */
enum class Micronutrient(
    val key: String,
    val group: MicroGroup,
    val dailyValue: Double,
    val unit: MicroUnit,
    val isLimit: Boolean = false,
) {
    VitaminA("vitamin_a", MicroGroup.Vitamins, 900.0, MicroUnit.Ug),
    Thiamin("thiamin", MicroGroup.Vitamins, 1.2, MicroUnit.Mg),
    Riboflavin("riboflavin", MicroGroup.Vitamins, 1.3, MicroUnit.Mg),
    Niacin("niacin", MicroGroup.Vitamins, 16.0, MicroUnit.Mg),
    VitaminB6("vitamin_b6", MicroGroup.Vitamins, 1.7, MicroUnit.Mg),
    Folate("folate", MicroGroup.Vitamins, 400.0, MicroUnit.Ug),
    VitaminB12("vitamin_b12", MicroGroup.Vitamins, 2.4, MicroUnit.Ug),
    VitaminC("vitamin_c", MicroGroup.Vitamins, 90.0, MicroUnit.Mg),
    VitaminD("vitamin_d", MicroGroup.Vitamins, 20.0, MicroUnit.Ug),
    VitaminE("vitamin_e", MicroGroup.Vitamins, 15.0, MicroUnit.Mg),
    VitaminK("vitamin_k", MicroGroup.Vitamins, 120.0, MicroUnit.Ug),
    Choline("choline", MicroGroup.Vitamins, 550.0, MicroUnit.Mg),
    Calcium("calcium", MicroGroup.Minerals, 1300.0, MicroUnit.Mg),
    Copper("copper", MicroGroup.Minerals, 0.9, MicroUnit.Mg),
    Iron("iron", MicroGroup.Minerals, 18.0, MicroUnit.Mg),
    Magnesium("magnesium", MicroGroup.Minerals, 420.0, MicroUnit.Mg),
    Manganese("manganese", MicroGroup.Minerals, 2.3, MicroUnit.Mg),
    Phosphorus("phosphorus", MicroGroup.Minerals, 1250.0, MicroUnit.Mg),
    Potassium("potassium", MicroGroup.Minerals, 4700.0, MicroUnit.Mg),
    Selenium("selenium", MicroGroup.Minerals, 55.0, MicroUnit.Ug),
    Sodium("sodium", MicroGroup.Minerals, 2300.0, MicroUnit.Mg, isLimit = true),
    Zinc("zinc", MicroGroup.Minerals, 11.0, MicroUnit.Mg),
    ;

    companion object {
        private val byKey = entries.associateBy { it.key }

        fun inGroup(group: MicroGroup): List<Micronutrient> = entries.filter { it.group == group }

        /** `null` for a key this build does not track. */
        fun fromKey(key: String): Micronutrient? = byKey[key]
    }
}

/**
 * How many of the items a sum is made of list a micronutrient at all: "9 of 12 foods list
 * vitamin D". A food that lists 0 counts as listing it; one that says nothing does not.
 */
data class MicroCoverage(val listing: Int, val of: Int) {
    /** Nothing listed it, so there is no amount to show, not an amount of 0. */
    val isNone: Boolean get() = listing == 0

    /** Some items list it and some do not: the percent is a floor. */
    val isPartial: Boolean get() = listing in 1 until of
}

/**
 * Micronutrient amounts as percents of each daily reference. The Nutrients page
 * shows these per micronutrient; there is no combined score (design v3 cut the
 * scores page: a score built from hidden data is a number with no source).
 */
object MicroPercents {

    /**
     * Sums [amounts], one map per logged item, and turns each total into a percent of the
     * person's reference. A micronutrient no item lists has no percent at all rather than 0%:
     * nothing said there was none, so screens say "Not listed" instead.
     */
    fun of(
        amounts: Iterable<Map<Micronutrient, Double>>,
        references: MicroReferences = MicroReferences.DailyValues,
    ): Map<Micronutrient, Double> {
        val totals = mutableMapOf<Micronutrient, Double>()
        for (item in amounts) {
            for ((micro, amount) in item) totals[micro] = (totals[micro] ?: 0.0) + amount
        }
        return totals.mapValues { (micro, total) -> references.percentOf(micro, total) }
    }

    /** For each micronutrient, how many of [amounts] list it, of all of them. */
    fun coverage(amounts: List<Map<Micronutrient, Double>>): Map<Micronutrient, MicroCoverage> =
        Micronutrient.entries.associateWith { micro -> MicroCoverage(amounts.count { micro in it }, amounts.size) }
}
