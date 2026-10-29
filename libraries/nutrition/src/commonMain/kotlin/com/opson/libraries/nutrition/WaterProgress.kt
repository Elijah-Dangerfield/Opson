package com.opson.libraries.nutrition

import kotlin.math.floor

/** Glasses for the segmented water bar: [filled] of [total]. */
data class WaterGlasses(val filled: Int, val total: Int)

object WaterProgress {

    /**
     * Whole glasses drunk out of the goal. Capped at the goal: the bar never
     * grows past its segments, so it never says by how much. The small epsilon
     * keeps eight 250 ml glasses at 8 after floating point sums.
     */
    fun glasses(ml: Double, glassMl: Int, goalGlasses: Int): WaterGlasses {
        val total = goalGlasses.coerceAtLeast(0)
        return WaterGlasses(wholeGlasses(ml, glassMl).coerceAtMost(total), total)
    }

    /**
     * Whole glasses in [ml], not capped at any goal. For arithmetic on what was drunk, such as a
     * tap that adds or takes off a glass; what is shown goes through [glasses].
     */
    fun wholeGlasses(ml: Double, glassMl: Int): Int {
        if (glassMl <= 0 || !ml.isFinite()) return 0
        return floor(ml / glassMl + EPSILON).toInt().coerceAtLeast(0)
    }

    /**
     * A glass size or a daily goal in the user's unit: `250 ml`, `2,000 ml`,
     * `8 fl oz`. A setting, not an amount drunk, so it does not go through the
     * policy; Water settings shows it whatever the water level is.
     */
    fun glassSize(ml: Int, unit: WaterUnit, formatter: NutritionFormatter = EnglishNutritionFormatter): String =
        when (unit) {
            WaterUnit.Ml -> formatter.glassSize(ml.toDouble(), WaterUnit.Ml)
            WaterUnit.FlOz -> formatter.glassSize(Units.mlToFlOz(ml.toDouble()), WaterUnit.FlOz)
        }

    private const val EPSILON = 1e-6
}
