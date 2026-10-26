package com.opson.libraries.foods

import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.FoodUnit
import com.opson.libraries.diary.HouseholdAmount
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

    /** A size the person saved for this food with "Custom size…": "my bowl" is 420 g. */
    data class Saved(val unit: FoodUnit) : AmountUnit {
        override val label: String get() = unit.label
        override val gramsEach: Double get() = unit.grams

        /** As a portion of one, so a logged amount reads "1 my bowl" like any other portion. */
        val portion: Portion get() = Portion("1 ${unit.label}", unit.grams)
    }
}

/** What one of this unit weighs, for the unit menu: "350 g". Null for grams themselves and unknown weights. */
val AmountUnit.gramsLabel: String?
    get() = when (this) {
        AmountUnit.Grams -> null
        else -> gramsEach?.let { HouseholdAmount.of(round(it), "g") }
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

    /**
     * The count after switching units in the unit menu (design sync 2, 3.3b): the count stays and
     * the gram total follows, so a plate to a cup is still "1". Grams are a count of their own, so
     * a switch to or from grams converts through the weight instead.
     */
    fun switch(quantity: Double, from: AmountUnit, to: AmountUnit): Double =
        if (from is AmountUnit.Grams || to is AmountUnit.Grams) convert(quantity, from, to) else quantity

    // One decimal place is all a half step needs, and it keeps 0.1 + 0.2 drift out of the text.
    private fun tidy(value: Double): Double = round(value * 10) / 10
}

/**
 * The unit as a chip on the amount sheet: a portion of one without its count
 * ("1 cup" is "cup", "1 medium" is "medium"), any other portion as people say
 * it ("0.25 cup" is "¼ cup"), grams spelled out.
 */
val AmountUnit.chipLabel: String
    get() = when (this) {
        AmountUnit.Grams -> "grams"
        is AmountUnit.OfPortion ->
            if (HouseholdAmount.amountOf(label) == 1.0) {
                HouseholdAmount.unitOf(label).ifEmpty { label }
            } else {
                HouseholdAmount.ofPortion(label)
            }
        is AmountUnit.Serving -> unit
        is AmountUnit.Saved -> unit.label
    }

/** The amount on the stepper: "150 g", "1 plate", "1½ cups". An amount, not a nutrient, so no policy. */
fun AmountUnit.amountText(quantity: Double): String = when (this) {
    AmountUnit.Grams -> HouseholdAmount.of(quantity, "g")
    is AmountUnit.OfPortion -> HouseholdAmount.ofPortion(label, quantity)
    is AmountUnit.Serving -> HouseholdAmount.of(quantity, unit)
    is AmountUnit.Saved -> savedText(quantity)
}

/** "1½ my bowl": the person's own words, never pluralised (the plural rules are for catalog portions). */
internal fun AmountUnit.Saved.savedText(quantity: Double): String = "${HouseholdAmount.number(quantity)} ${unit.label}"

/**
 * Units for a catalog food: its portions, the person's [saved] sizes, then grams. A food with no
 * portions offers grams only, or the saved sizes and grams.
 */
fun Food.amountUnits(saved: List<FoodUnit> = emptyList()): List<AmountUnit> =
    portions.map(AmountUnit::OfPortion) + saved.map(AmountUnit::Saved) + AmountUnit.Grams

/**
 * A custom food offers its one serving unit; when that unit is grams, grams is it, and then the
 * person's [saved] sizes too. A serving of unknown weight cannot be converted, so it takes none.
 */
fun CustomFood.amountUnits(saved: List<FoodUnit> = emptyList()): List<AmountUnit> =
    if (isByWeight) saved.map(AmountUnit::Saved) + AmountUnit.Grams else listOf(AmountUnit.Serving(servingUnit))

/** Counted in grams, so a size in grams converts to it. */
val CustomFood.isByWeight: Boolean get() = servingUnit.equals("g", ignoreCase = true)

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
        is AmountUnit.Saved -> FoodAmount.OfPortion(unit.portion, quantity)
    }
    val draft = toLogDraft(amount, precision, method)
    return if (unit is AmountUnit.Saved) draft.copy(amountText = unit.savedText(quantity)) else draft
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

/** [quantity] of [unit] of this custom food: its own unit, or a saved size converted through grams. */
fun CustomFood.draftFor(
    unit: AmountUnit,
    quantity: Double,
    precision: Precision = Precision.Estimate,
    method: LogMethod? = null,
): LogDraft = when (unit) {
    is AmountUnit.Saved -> draftFor(quantity * unit.gramsEach, precision, method)
        .copy(amountText = unit.savedText(quantity))
    else -> draftFor(quantity, precision, method)
}

/** The quantity a custom food starts at on the amount sheet: one serving, in its own unit. */
val CustomFood.initialQuantity: Double get() = if (servingAmount > 0.0) servingAmount else 1.0
