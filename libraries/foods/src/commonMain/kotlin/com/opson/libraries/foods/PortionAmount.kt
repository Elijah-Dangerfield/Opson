package com.opson.libraries.foods

import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.Precision
import kotlin.math.max
import kotlin.math.round

/** One unit chip on the amount sheet. */
sealed interface AmountUnit {
    val label: String

    /** Grams in one of this unit, or null when nobody knows (a custom food's "bar"). */
    val gramsEach: Double?

    data object Grams : AmountUnit {
        override val label: String = "g"
        override val gramsEach: Double = 1.0
    }

    /** One of a catalog food's household measures: "1 cup" is 158 g. */
    data class OfPortion(val portion: Portion) : AmountUnit {
        override val label: String get() = portion.label
        override val gramsEach: Double get() = portion.grams
    }

    /** A custom food's serving unit ("bar", "slice"), weight unknown. */
    data class Serving(val unit: String) : AmountUnit {
        override val label: String get() = unit
        override val gramsEach: Double? get() = null
    }
}

/**
 * The amount sheet's stepper, as the prototype's `af`: grams step by 10 and
 * never go below 10; every other unit steps by half and never goes below a
 * half. Switching to grams rounds the current weight to a whole gram;
 * switching away rounds to the nearest half.
 */
object PortionAmount {
    private const val GRAM_STEP = 10.0
    private const val UNIT_STEP = 0.5
    private const val GRAMS_WITHOUT_PORTIONS = 100.0

    fun step(unit: AmountUnit): Double = if (unit is AmountUnit.Grams) GRAM_STEP else UNIT_STEP

    fun more(unit: AmountUnit, quantity: Double): Double = tidy(quantity + step(unit))

    fun less(unit: AmountUnit, quantity: Double): Double = max(step(unit), tidy(quantity - step(unit)))

    fun canLess(unit: AmountUnit, quantity: Double): Boolean = quantity > step(unit)

    /** The amount a unit starts at: one portion, or 100 g when grams are all there is. */
    fun initial(unit: AmountUnit): Double = if (unit is AmountUnit.Grams) GRAMS_WITHOUT_PORTIONS else 1.0

    /** [quantity] of [from] in [to]. With no known weight to convert through, [to] starts fresh. */
    fun convert(quantity: Double, from: AmountUnit, to: AmountUnit): Double {
        if (from == to) return quantity
        val grams = from.gramsEach?.times(quantity)
        val toGrams = to.gramsEach
        if (grams == null || toGrams == null || toGrams <= 0.0) return initial(to)
        return if (to is AmountUnit.Grams) {
            max(GRAM_STEP, round(grams))
        } else {
            max(UNIT_STEP, round(grams / toGrams * 2) / 2)
        }
    }

    // One decimal place is all a half step needs, and it keeps 0.1 + 0.2 drift out of the text.
    private fun tidy(value: Double): Double = round(value * 10) / 10
}

/**
 * The unit as a chip on the amount sheet: a portion without its leading count
 * ("1 cup" is "cup", "1 medium" is "medium"), grams spelled out.
 */
val AmountUnit.chipLabel: String
    get() = when (this) {
        AmountUnit.Grams -> "grams"
        is AmountUnit.OfPortion -> label.removePrefix("1 ").trim().ifEmpty { label }
        is AmountUnit.Serving -> unit
    }

/** The amount on the stepper: "150 g", "1 plate", "1.5 cup". An amount, not a nutrient, so no policy. */
fun AmountUnit.amountText(quantity: Double): String = when (this) {
    AmountUnit.Grams -> "${formatNumber(quantity)} g"
    else -> "${formatNumber(quantity)} $chipLabel"
}

/** Units for a catalog food: its portions, then grams. A food with no portions offers grams only. */
fun Food.amountUnits(): List<AmountUnit> = portions.map(AmountUnit::OfPortion) + AmountUnit.Grams

/** A custom food offers its one serving unit; when that unit is grams, grams is it. */
fun CustomFood.amountUnits(): List<AmountUnit> =
    listOf(if (servingUnit.equals("g", ignoreCase = true)) AmountUnit.Grams else AmountUnit.Serving(servingUnit))

/** [quantity] of [unit] of this food as a basket item. */
fun Food.draftFor(
    unit: AmountUnit,
    quantity: Double,
    precision: Precision = Precision.Estimate,
    method: LogMethod? = null,
): LogDraft {
    val amount = when (unit) {
        AmountUnit.Grams -> FoodAmount.Grams(quantity)
        is AmountUnit.OfPortion -> FoodAmount.OfPortion(unit.portion, quantity)
        is AmountUnit.Serving -> FoodAmount.Servings(quantity)
    }
    return toLogDraft(amount, precision, method)
}

/**
 * [quantity] in this food's serving unit ("3 bars", "45 g") as a basket item.
 * The quantity counts the unit, not servings, so a 30 g serving at 45 is one
 * and a half servings.
 */
fun CustomFood.draftFor(
    quantity: Double,
    precision: Precision = Precision.Estimate,
    method: LogMethod? = null,
): LogDraft {
    val servings = if (servingAmount > 0.0) quantity / servingAmount else 1.0
    val draft = toDraft(servings, precision)
    return if (method == null) draft else draft.copy(method = method)
}

/** The quantity a custom food starts at on the amount sheet: one serving, in its own unit. */
val CustomFood.initialQuantity: Double get() = if (servingAmount > 0.0) servingAmount else 1.0
