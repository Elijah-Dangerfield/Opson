package com.opson.libraries.nutrition

import com.opson.libraries.core.fillTemplate
import kotlin.concurrent.Volatile

/**
 * The units the app writes itself for an amount of food, "150 g", "8 fl oz", as the person reads
 * them. Not nutrients, which [NutritionWords] spells, and not a catalog's own portion names
 * ("1 cup, sliced"), which are the food's data.
 *
 * What is stored and synced stays canonical: an amount is saved as "150 g" in every language and
 * spelled with these only when shown ([AmountCopy.measure], `HouseholdAmount.display`).
 *
 * The defaults are `strings_nutrition.xml`'s English, for tests and previews; the app installs the
 * device language's at start, and `NutritionWordsDriftTest` keeps the two equal.
 */
data class AmountWords(
    val grams: String = "%1\$s g",
    val kilograms: String = "%1\$s kg",
    val milligrams: String = "%1\$s mg",
    val micrograms: String = "%1\$s mcg",
    val millilitres: String = "%1\$s ml",
    val fluidOunces: String = "%1\$s fl oz",
    val ounces: String = "%1\$s oz",
    val pounds: String = "%1\$s lb",
    /** The grams chip on the amount sheet, spelled out. */
    val gramsWord: String = "grams",
    val kcalSymbol: String = "kcal",
    val gramsSymbol: String = "g",
    val milligramsSymbol: String = "mg",
    val microgramsSymbol: String = "mcg",
    val millilitresSymbol: String = "ml",
    val fluidOuncesSymbol: String = "fl oz",
    val ouncesSymbol: String = "oz",
    val poundsSymbol: String = "lb",
)

/** The installed [AmountWords]: English until the app installs the device language's. */
object AmountCopy {
    @Volatile
    var words: AmountWords = AmountWords()
        private set

    fun install(words: AmountWords) {
        this.words = words
    }

    /**
     * [number] of [unit] as shown: `measure("150", "g")` is "150 g" in English. [unit] is a
     * canonical unit as the app stores it (`g`, `kg`, `mg`, `mcg`, `ml`, `fl oz`, `oz`, `lb`), any
     * case; null for anything else, which the caller shows as it is.
     */
    fun measure(number: String, unit: String): String? = template(unit)?.fillTemplate(number)

    /** [unit] on its own, as a column heading or a chip shows it; null for a unit not listed in [measure]. */
    fun symbol(unit: String): String? = when (unit.trim().lowercase()) {
        "kcal" -> words.kcalSymbol
        "g" -> words.gramsSymbol
        "mg" -> words.milligramsSymbol
        "mcg", "µg" -> words.microgramsSymbol
        "ml" -> words.millilitresSymbol
        "fl oz" -> words.fluidOuncesSymbol
        "oz" -> words.ouncesSymbol
        "lb" -> words.poundsSymbol
        else -> null
    }

    private fun template(unit: String): String? = when (unit.trim().lowercase()) {
        "g" -> words.grams
        "kg" -> words.kilograms
        "mg" -> words.milligrams
        "mcg", "µg" -> words.micrograms
        "ml" -> words.millilitres
        "fl oz" -> words.fluidOunces
        "oz" -> words.ounces
        "lb" -> words.pounds
        else -> null
    }
}
