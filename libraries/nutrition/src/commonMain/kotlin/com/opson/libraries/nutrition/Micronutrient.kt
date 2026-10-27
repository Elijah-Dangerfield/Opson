package com.opson.libraries.nutrition

enum class MicroGroup { Vitamins, Minerals }

enum class MicroUnit { Mg, Ug }

/**
 * The vitamins and minerals the app tracks, in the order screens list them: vitamins by letter
 * with the B group together, then choline; minerals alphabetically. [reference] is a typical
 * daily target in [unit]; amounts keyed by a micronutrient are always in its own unit.
 *
 * The first thirteen are the prototype's `MICROS` table. The rest (the B vitamins but B12,
 * choline, copper, manganese, phosphorus, selenium) were added because the USDA and Open Food
 * Facts catalogs carry them for most foods; their references are the FDA's Daily Values for
 * adults, the number a nutrition label's "% Daily Value" uses.
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
    Thiamin("thiamin", MicroGroup.Vitamins, 1.2, MicroUnit.Mg),
    Riboflavin("riboflavin", MicroGroup.Vitamins, 1.3, MicroUnit.Mg),
    Niacin("niacin", MicroGroup.Vitamins, 16.0, MicroUnit.Mg),
    VitaminB6("vitamin_b6", MicroGroup.Vitamins, 1.7, MicroUnit.Mg),
    Folate("folate", MicroGroup.Vitamins, 400.0, MicroUnit.Ug),
    VitaminB12("vitamin_b12", MicroGroup.Vitamins, 2.4, MicroUnit.Ug),
    VitaminC("vitamin_c", MicroGroup.Vitamins, 90.0, MicroUnit.Mg),
    VitaminD("vitamin_d", MicroGroup.Vitamins, 15.0, MicroUnit.Ug),
    VitaminE("vitamin_e", MicroGroup.Vitamins, 15.0, MicroUnit.Mg),
    VitaminK("vitamin_k", MicroGroup.Vitamins, 120.0, MicroUnit.Ug),
    Choline("choline", MicroGroup.Vitamins, 550.0, MicroUnit.Mg),
    Calcium("calcium", MicroGroup.Minerals, 1000.0, MicroUnit.Mg),
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

    /** [amount] as a percent of the daily reference, uncapped: 134 means a third over. */
    fun percentOf(amount: Double): Double = amount / reference * 100

    companion object {
        fun inGroup(group: MicroGroup): List<Micronutrient> = entries.filter { it.group == group }
    }
}

/**
 * Micronutrient amounts as percents of each daily reference. The Nutrients page
 * shows these per micronutrient; there is no combined score (design v3 cut the
 * scores page: a score built from hidden data is a number with no source).
 */
object MicroPercents {

    /**
     * Sums [amounts] per micronutrient and turns each total into a percent of its
     * reference. A micronutrient nobody logged is 0%: foods without micro data
     * (custom foods, name-only items) count as none, which the nutrients caption
     * explains.
     */
    fun of(amounts: Iterable<Map<Micronutrient, Double>>): Map<Micronutrient, Double> {
        val totals = mutableMapOf<Micronutrient, Double>()
        for (item in amounts) {
            for ((micro, amount) in item) totals[micro] = (totals[micro] ?: 0.0) + amount
        }
        return Micronutrient.entries.associateWith { it.percentOf(totals[it] ?: 0.0) }
    }
}
