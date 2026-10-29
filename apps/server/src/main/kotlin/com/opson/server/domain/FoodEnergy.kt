package com.opson.server.domain

import kotlin.math.min

/**
 * What the energy check found on a food. Wire values are what the admin
 * console and the server's own reports (`energy_check`) carry.
 */
enum class EnergyFlag(val wire: String) {
    /**
     * Energy is 0 while protein, carbs and fat come to more than
     * [FoodEnergy.THRESHOLD_KCAL] in a serving. A US label may print 0 for
     * anything under 5 kcal a serving, and USDA Branded scales that 0 to per
     * 100 ml, so a zero-sugar energy drink came out at 0 for a whole can.
     * Served as an estimate ([FoodEnergy.estimateForZero]), never as a 0.
     */
    ZeroWithMacros("zero_with_macros"),

    /** The source gave no energy at all, only the macros: worked out at 4, 4 and 9 kcal a gram. */
    FromMacros("from_macros"),

    /**
     * More than [FoodEnergy.THRESHOLD_KCAL] a serving while protein, carbs
     * and fat are all 0. Spirits are like this (alcohol is 7 kcal a gram and
     * no macro), so it is only flagged, never changed.
     */
    EnergyWithoutMacros("energy_without_macros"),
    ;

    companion object {
        fun fromWire(value: String): EnergyFlag? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * The energy guard, applied to every food the catalog serves (search, detail,
 * barcode) so a stored row is never changed by it and a fix in here reaches
 * every row already loaded.
 *
 * Null energy means unknown and stays unknown unless the macros can stand in.
 * A 0 is only trusted when the macros agree it is about 0. Judged on a serving
 * (the food's first portion) when its size is known, because that is where a
 * label rounds; per 100 otherwise.
 */
object FoodEnergy {
    /** Below this a serving's energy can honestly read 0 (the FDA's own line for "0 calories"). */
    const val THRESHOLD_KCAL = 5.0
    private const val PROTEIN = 4.0
    private const val CARBS = 4.0
    private const val FAT = 9.0
    private const val PER = 100.0

    /** 4/4/9 kcal per 100, or null without all three macros. Fibre and sugar alcohols are not split out: this is a check, not a label. */
    fun atwater(per100: Nutrients): Double? {
        val protein = per100.protein ?: return null
        val carbs = per100.carbs ?: return null
        val fat = per100.fat ?: return null
        return PROTEIN * protein + CARBS * carbs + FAT * fat
    }

    /** The amount a serving holds in the food's own basis (grams, or ml for a label stated in ml), or null. */
    fun servingAmount(food: Food): Double? = food.portions.firstOrNull()?.let { it.grams ?: Portions.millilitres(it.label) }?.takeIf { it > 0 }

    /** What the guard makes of [food]'s energy, before anything is changed. Empty when it looks right. */
    fun flags(food: Food): Set<EnergyFlag> {
        val atwater = atwater(food.per100g) ?: return emptySet()
        val kcal = food.per100g.kcal ?: return setOf(EnergyFlag.FromMacros)
        val amount = servingAmount(food) ?: PER
        val fromMacros = atwater * amount / PER
        val declared = kcal * amount / PER
        return when {
            kcal == 0.0 && fromMacros > THRESHOLD_KCAL -> setOf(EnergyFlag.ZeroWithMacros)
            atwater == 0.0 && declared > THRESHOLD_KCAL -> setOf(EnergyFlag.EnergyWithoutMacros)
            else -> emptySet()
        }
    }

    /**
     * The energy per 100 to serve for a 0 the macros contradict. A label's 0
     * is a rounding of something under 5 kcal in the amount it was printed
     * for (a US label may print 0 below 5 a serving), so the estimate is the
     * macros' energy held under that line: the nearest value to what the
     * macros say that the label still allows. Monster Energy Zero Ultra: 0.83 g
     * of carbs per 100 ml is 3.3 kcal, the 240 ml serving caps it at 2.08, so
     * a 473 ml can reads 10 kcal, as the can says.
     *
     * The cap matters as much as the macros. Zero-sugar sodas and syrups
     * sweetened with erythritol carry grams of "carbohydrate" that give almost
     * no energy: Reed's Zero Sugar Cola has 5 g per 100 ml, which 4/4/9 counts
     * as 72 kcal a can. Capped, it reads 5. With no serving, the cap is per 100.
     */
    fun estimateForZero(food: Food): Double? {
        val atwater = atwater(food.per100g) ?: return null
        val amount = servingAmount(food) ?: PER
        return min(atwater, THRESHOLD_KCAL * PER / amount)
    }

    /** [food] as it is served: energy filled in or estimated where the guard says so, with [Food.energyFlags] set. */
    fun served(food: Food): Food {
        val flags = flags(food)
        if (flags.isEmpty()) return food
        val kcal = when {
            EnergyFlag.ZeroWithMacros in flags -> estimateForZero(food)
            EnergyFlag.FromMacros in flags -> atwater(food.per100g)
            else -> food.per100g.kcal
        }
        return food.copy(per100g = food.per100g.copy(kcal = kcal?.let(::round1)), energyFlags = flags)
    }

    private fun round1(value: Double) = Math.round(value * 10) / 10.0
}

/** Reading a portion's label. */
object Portions {
    private val PER_100_ML_SERVINGS = setOf(CatalogSource.UsdaBranded, CatalogSource.OpenFoodFacts, CatalogSource.UserSubmitted)
    private val MILLILITRES = Regex("""(\d+(?:[.,]\d+)?)\s*(?:ml|milliliters?|millilitres?)\b""", RegexOption.IGNORE_CASE)

    /** "8.0 fl oz (240 ml)" is 240. Null when the label names no millilitres. */
    fun millilitres(label: String): Double? =
        MILLILITRES.find(label)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0 }

    /**
     * A USDA Branded serving stated in ml has no grams ([Portion]), and the
     * app could only offer 100 g of it: a 473 ml can of Monster logged as
     * 100 g. Its nutrients are per 100 ml, as the label states them, so the
     * serving's millilitres are already the amount the numbers scale by:
     * served as its grams, the label unchanged. A food someone added from its
     * pack is the same (V25), and so is an Open Food Facts drink, whose
     * export keeps an ml serving without grams too (V12). Other sources
     * give grams or nothing.
     */
    fun served(food: Food): Food {
        if (food.source !in PER_100_ML_SERVINGS || food.portions.none { it.grams == null }) return food
        return food.copy(portions = food.portions.map { p -> if (p.grams == null) p.copy(grams = millilitres(p.label)) else p })
    }
}
