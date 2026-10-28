package com.opson.libraries.diary

import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Nutrient
import com.opson.libraries.nutrition.toMicronutrients

/**
 * One food's part of a nutrient over a day or a week: [amount] in the nutrient's own unit
 * (grams, mg or µg) and [percent] of what the listed foods add up to.
 */
data class FoodContribution(val name: String, val amount: Double, val percent: Double)

/**
 * How much of [nutrient] this item lists, or `null` when it says nothing about it. A 0 is a
 * real answer and stays 0.
 */
fun LogItem.amountOf(nutrient: Nutrient): Double? = when (nutrient) {
    is Nutrient.Micro -> micros?.toMicronutrients()?.get(nutrient.micro)
    is Nutrient.Macro -> when (nutrient.metric) {
        Metric.Protein -> protein
        Metric.Carbs -> carbs
        Metric.Fat -> fat
        else -> fiber
    }
}?.takeIf { it.isFinite() }

/**
 * The foods that gave [nutrient], most first. The same food logged twice is one row with both
 * amounts (by catalog or custom food, else by name), and a food that lists none of it, or 0, is
 * not in the list. [FoodContribution.percent] is of the listed total, so the rows add up to 100.
 */
fun Iterable<LogItem>.contributions(nutrient: Nutrient): List<FoodContribution> {
    val byFood = linkedMapOf<String, Pair<String, Double>>()
    for (item in this) {
        val amount = item.amountOf(nutrient)?.takeIf { it > 0.0 } ?: continue
        val key = item.foodId?.let { "f:$it" } ?: item.customFoodId?.let { "c:$it" } ?: "n:${item.name.trim().lowercase()}"
        val (name, sum) = byFood[key] ?: (item.name to 0.0)
        byFood[key] = name to sum + amount
    }
    val total = byFood.values.sumOf { it.second }
    if (total <= 0.0) return emptyList()
    return byFood.values
        .map { (name, amount) -> FoodContribution(name, amount, amount / total * PERCENT) }
        .sortedWith(compareByDescending<FoodContribution> { it.amount }.thenBy { it.name.lowercase() })
}

private const val PERCENT = 100.0
