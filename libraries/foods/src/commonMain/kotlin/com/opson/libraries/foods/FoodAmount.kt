package com.opson.libraries.foods

import com.opson.libraries.diary.HouseholdAmount
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.Precision

/** How much of a food the user picked on the amount sheet. */
sealed interface FoodAmount {
    data class Grams(val grams: Double) : FoodAmount

    /** [count] of one of the food's portions: 2 × "1 cup". */
    data class OfPortion(val portion: Portion, val count: Double = 1.0) : FoodAmount

    /**
     * [count] servings. A serving is the food's first listed portion (the
     * catalog's household measure), or 100 g when it lists none.
     */
    data class Servings(val count: Double = 1.0) : FoodAmount
}

/** A food's nutrients for a chosen amount. Unknown per-100 g values stay unknown. */
data class ScaledNutrients(
    val grams: Double,
    val kcal: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val fiber: Double?,
    val micros: Map<String, Double>,
)

/** The weight of [amount] of this food, in grams. */
fun Food.gramsFor(amount: FoodAmount): Double = when (amount) {
    is FoodAmount.Grams -> amount.grams
    is FoodAmount.OfPortion -> amount.portion.grams * amount.count
    is FoodAmount.Servings -> (portions.firstOrNull()?.grams ?: GRAMS_PER_100) * amount.count
}

fun Food.nutrientsFor(amount: FoodAmount): ScaledNutrients {
    val grams = gramsFor(amount).coerceAtLeast(0.0)
    val factor = grams / GRAMS_PER_100
    return ScaledNutrients(
        grams = grams,
        kcal = per100g.kcal?.times(factor),
        protein = per100g.protein?.times(factor),
        carbs = per100g.carbs?.times(factor),
        fat = per100g.fat?.times(factor),
        fiber = per100g.fiber?.times(factor),
        micros = micros.mapValues { (_, per100) -> per100 * factor },
    )
}

/** How the amount reads on an item row: `150 g`, `1 cup`, `1½ cups`, `2 slices`. */
fun Food.amountTextFor(amount: FoodAmount): String = when (amount) {
    is FoodAmount.Grams -> HouseholdAmount.of(amount.grams, "g")
    is FoodAmount.OfPortion -> HouseholdAmount.ofPortion(amount.portion.label, amount.count)
    is FoodAmount.Servings -> {
        val portion = portions.firstOrNull()
        if (portion != null) {
            HouseholdAmount.ofPortion(portion.label, amount.count)
        } else {
            HouseholdAmount.of(gramsFor(amount), "g")
        }
    }
}

/**
 * [amount] of this food as an item for the basket. [precision] is what the
 * user said on "How sure are you?"; Weighed only makes sense with grams, but
 * that is the screen's call.
 */
fun Food.toLogDraft(
    amount: FoodAmount,
    precision: Precision = Precision.Estimate,
    method: LogMethod? = null,
): LogDraft {
    val scaled = nutrientsFor(amount)
    val (quantity, unit) = when (amount) {
        is FoodAmount.Grams -> amount.grams to "g"
        is FoodAmount.OfPortion -> amount.count to amount.portion.label
        is FoodAmount.Servings -> amount.count to (portions.firstOrNull()?.label ?: "serving")
    }
    return LogDraft(
        name = name,
        precision = precision,
        foodId = id,
        amountText = amountTextFor(amount),
        quantity = quantity,
        unit = unit,
        grams = scaled.grams,
        kcal = scaled.kcal,
        protein = scaled.protein,
        carbs = scaled.carbs,
        fat = scaled.fat,
        fiber = scaled.fiber,
        micros = scaled.micros.takeIf { it.isNotEmpty() },
        method = method,
    )
}

/** A Describe result as an item. Always logged as an estimate. */
fun ParsedItem.toLogDraft(): LogDraft = LogDraft(
    name = name,
    precision = Precision.Estimate,
    foodId = foodId,
    amountText = HouseholdAmount.tidy(amountText),
    quantity = quantity,
    unit = unit ?: portionLabel,
    grams = grams,
    kcal = kcal,
    protein = protein,
    carbs = carbs,
    fat = fat,
    fiber = fiber,
    micros = micros?.takeIf { it.isNotEmpty() },
    method = LogMethod.Describe,
)

private const val GRAMS_PER_100 = 100.0

