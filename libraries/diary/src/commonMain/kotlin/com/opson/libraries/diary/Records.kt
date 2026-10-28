package com.opson.libraries.diary

import kotlin.time.Instant

/** One weigh-in, always stored in kilograms. [WeightUnit] only changes how it reads. */
data class WeighIn(
    val id: String,
    val measuredAt: Instant,
    val kg: Double,
)

/**
 * A food the user made up. Nutrients are per serving, where one serving is
 * [servingAmount] [servingUnit] ("1 bar", "30 g"). Any nutrient can be unknown.
 *
 * [servingGrams] is what one serving weighs, once someone has weighed it: with
 * it the food can be counted in grams and in its [sizes]. A serving counted in
 * grams weighs its own amount and needs none. Each of [sizes] is one of a unit
 * with its weight ("tbsp" is 15 g). [micros] are per serving, keyed the way a
 * log entry's are (`sodium_mg`, `saturated_fat_g`).
 */
data class CustomFood(
    val id: String,
    val name: String,
    val servingAmount: Double,
    val servingUnit: String,
    val brand: String? = null,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
    val servingGrams: Double? = null,
    val sizes: List<FoodUnit> = emptyList(),
    val micros: Map<String, Double> = emptyMap(),
) {
    /** What one serving weighs: its own amount when it is counted in grams, else [servingGrams]. */
    val gramsPerServing: Double?
        get() = if (servingUnit.equals(GRAMS, ignoreCase = true)) servingAmount else servingGrams?.takeIf { it > 0.0 }

    /**
     * [servings] of this food as an item to log. Unknown nutrients stay
     * unknown. Grams are filled in whenever a serving's weight is known.
     */
    fun toDraft(servings: Double = 1.0, precision: Precision = Precision.Estimate): LogDraft {
        val amount = servingAmount * servings
        return LogDraft(
            name = name,
            precision = precision,
            customFoodId = id,
            amountText = HouseholdAmount.of(amount, servingUnit),
            quantity = amount,
            unit = servingUnit,
            grams = gramsPerServing?.times(servings),
            kcal = kcal?.times(servings),
            protein = protein?.times(servings),
            carbs = carbs?.times(servings),
            fat = fat?.times(servings),
            fiber = fiber?.times(servings),
            micros = micros.takeIf { it.isNotEmpty() }?.mapValues { it.value * servings },
            method = LogMethod.Custom,
        )
    }

    private companion object {
        const val GRAMS = "g"
    }
}

/** The fields Add a food and Edit a copy collect. [CustomFood] says what each one means. */
data class CustomFoodDraft(
    val name: String,
    val servingAmount: Double,
    val servingUnit: String,
    val brand: String? = null,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
    val servingGrams: Double? = null,
    val sizes: List<FoodUnit> = emptyList(),
    val micros: Map<String, Double> = emptyMap(),
)

/**
 * A named group of items logged together ("Sunday breakfast"). [items] are
 * snapshots, so logging it later uses the amounts and nutrients it was saved
 * with even if a catalog food changes.
 */
data class SavedMeal(
    val id: String,
    val name: String,
    val usualSlot: MealSlot? = null,
    val items: List<LogDraft> = emptyList(),
) {
    val totals: Totals get() = items.fold(Totals.Empty) { acc, d ->
        Totals(
            kcal = acc.kcal.plus(d.kcal),
            protein = acc.protein.plus(d.protein),
            carbs = acc.carbs.plus(d.carbs),
            fat = acc.fat.plus(d.fat),
            fiber = acc.fiber.plus(d.fiber),
        )
    }
}

