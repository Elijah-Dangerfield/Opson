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
) {
    /**
     * [servings] of this food as an item to log. Unknown nutrients stay
     * unknown. Grams are only filled in when the serving unit is grams.
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
            grams = if (servingUnit.equals("g", ignoreCase = true)) amount else null,
            kcal = kcal?.times(servings),
            protein = protein?.times(servings),
            carbs = carbs?.times(servings),
            fat = fat?.times(servings),
            fiber = fiber?.times(servings),
            method = LogMethod.Custom,
        )
    }
}

/** The fields the Add a custom food screen collects. */
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

