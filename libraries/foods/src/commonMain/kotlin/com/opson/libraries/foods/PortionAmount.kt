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

    /**
     * A custom food's serving unit ("bar", "tbsp"). [gramsEach] is one of it once the person has
     * weighed a serving, and null until then.
     */
    data class Serving(val unit: String, override val gramsEach: Double? = null) : AmountUnit {
        override val label: String get() = unit
    }

    /**
     * A size with its weight: one the person saved for this food with "Custom size…" ("my bowl"
     * is 420 g), or one of the serving sizes of a food of their own ("tbsp" is 15 g).
     */
    data class Saved(val unit: FoodUnit) : AmountUnit {
        override val label: String get() = unit.label
        override val gramsEach: Double get() = unit.grams

        /** As a portion of one, so a logged amount reads "1 my bowl" like any other portion. */
        val portion: Portion get() = Portion("1 ${unit.label}", unit.grams)
    }

    /**
     * A weight or a volume that converts through grams. Ounces and pounds need only the food's
     * weight; millilitres and fluid ounces need [gramsPerMl] too, which a portion stated in a
     * volume gives ("1 cup (240 ml)" at 244 g).
     */
    data class Measure(val kind: MeasureKind, val gramsPerMl: Double = 1.0) : AmountUnit {
        override val label: String get() = kind.label
        override val gramsEach: Double get() = if (kind.isVolume) kind.base * gramsPerMl else kind.base
    }
}

/** Weights and volumes besides grams. [base] is grams in one, or millilitres for a volume. */
enum class MeasureKind(val label: String, val base: Double, val isVolume: Boolean) {
    Ounces("oz", GRAMS_PER_OUNCE, isVolume = false),
    Pounds("lb", GRAMS_PER_POUND, isVolume = false),
    Millilitres("ml", 1.0, isVolume = true),
    FluidOunces("fl oz", ML_PER_FLUID_OUNCE, isVolume = true),
}

private const val GRAMS_PER_OUNCE = 28.349523125
private const val GRAMS_PER_POUND = 453.59237
private const val ML_PER_FLUID_OUNCE = 29.5735295625

/** Counted by weight or volume rather than by a household count: switching to or from it converts. */
val AmountUnit.isMeasure: Boolean get() = this is AmountUnit.Grams || this is AmountUnit.Measure

/** What one of this unit weighs, for the unit menu: "350 g". Null for weights and volumes themselves and unknown weights. */
val AmountUnit.gramsLabel: String?
    get() = when (this) {
        AmountUnit.Grams, is AmountUnit.Measure -> null
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
    private const val TENTHS = 10.0
    private const val HUNDREDTHS = 100.0

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
        val count = grams / toGrams
        return when {
            to is AmountUnit.Grams -> max(GRAM_STEP, round(grams))
            // Millilitres read like grams; ounces and pounds need their decimals or a spoonful is 0.
            to is AmountUnit.Measure && to.kind == MeasureKind.Millilitres -> max(1.0, round(count))
            to is AmountUnit.Measure && to.kind == MeasureKind.Pounds -> max(1 / HUNDREDTHS, round(count * HUNDREDTHS) / HUNDREDTHS)
            to is AmountUnit.Measure -> max(1 / TENTHS, round(count * TENTHS) / TENTHS)
            else -> max(UNIT_STEP, round(count * 2) / 2)
        }
    }

    /**
     * The count after switching units in the unit menu (design sync 2, 3.3b): the count stays and
     * the gram total follows, so a plate to a cup is still "1". Weights and volumes are a count of
     * their own, so a switch to or from one converts through the weight instead.
     */
    fun switch(quantity: Double, from: AmountUnit, to: AmountUnit): Double =
        if (from.isMeasure || to.isMeasure) convert(quantity, from, to) else quantity

    // One decimal place is all a half step needs, and it keeps 0.1 + 0.2 drift out of the text.
    private fun tidy(value: Double): Double = round(value * 10) / 10
}

/**
 * The unit as a chip on the amount sheet: a portion of one without its count
 * ("1 cup" is "cup", "1 medium" is "medium"), any other portion as people say
 * it ("0.25 cup" is "¼ cup"), grams spelled out. A weight a label printed in
 * the portion's name ("2 Tbsp (31g)") is left off: the menu gives the weight once.
 */
val AmountUnit.chipLabel: String
    get() = when (this) {
        AmountUnit.Grams -> "grams"
        is AmountUnit.OfPortion -> {
            val bare = withoutPrintedWeight(label)
            if (HouseholdAmount.amountOf(bare) == 1.0) {
                HouseholdAmount.unitOf(bare).ifEmpty { bare }
            } else {
                HouseholdAmount.ofPortion(bare)
            }
        }
        is AmountUnit.Serving -> unit
        is AmountUnit.Saved -> unit.label
        is AmountUnit.Measure -> kind.label
    }

/** The amount on the stepper: "150 g", "1 plate", "1½ cups". An amount, not a nutrient, so no policy. */
fun AmountUnit.amountText(quantity: Double): String = when (this) {
    AmountUnit.Grams -> HouseholdAmount.of(quantity, "g")
    is AmountUnit.OfPortion -> HouseholdAmount.ofPortion(withoutPrintedWeight(label), quantity)
    is AmountUnit.Serving -> HouseholdAmount.of(quantity, unit)
    is AmountUnit.Saved -> savedText(quantity)
    is AmountUnit.Measure -> HouseholdAmount.of(quantity, kind.label)
}

/** "1½ my bowl": the person's own words, never pluralised (the plural rules are for catalog portions). */
internal fun AmountUnit.Saved.savedText(quantity: Double): String = "${HouseholdAmount.number(quantity)} ${unit.label}"

private val PRINTED_WEIGHT = Regex("""\s*\(\s*[\d.,]+\s*(g|ml|oz|fl\.?\s*oz)\s*\)\s*$""", RegexOption.IGNORE_CASE)

/** "2 Tbsp (31g)" is "2 Tbsp": the label's own copy of the weight, which the detail shows once elsewhere. */
fun withoutPrintedWeight(label: String): String = label.replace(PRINTED_WEIGHT, "").ifBlank { label }

private val MILLILITRES = Regex("""(\d+(?:\.\d+)?)\s*(ml|milliliters?|millilitres?)\b""", RegexOption.IGNORE_CASE)
private val FLUID_OUNCES = Regex("""(\d+(?:\.\d+)?)\s*fl\.?\s*oz\b""", RegexOption.IGNORE_CASE)

/** The volume a label states, in millilitres: "1 cup (240 ml)" is 240, "1 cup (8 fl oz)" is about 237. */
fun volumeInLabel(label: String): Double? {
    MILLILITRES.find(label)?.let { return it.groupValues[1].toDoubleOrNull() }
    return FLUID_OUNCES.find(label)?.groupValues?.get(1)?.toDoubleOrNull()?.times(ML_PER_FLUID_OUNCE)
}

/** Grams in a millilitre of this food, from the first portion that states its volume. Null when none does. */
val Food.gramsPerMl: Double?
    get() = portions.firstNotNullOfOrNull { portion ->
        volumeInLabel(portion.label)?.takeIf { it > 0.0 && portion.grams > 0.0 }?.let { portion.grams / it }
    }

/**
 * Weights for a food whose weight is known: grams, ounces, pounds, with ounces first for someone
 * who weighs themselves in pounds. Then millilitres and fluid ounces when [gramsPerMl] is known.
 */
fun measureUnits(gramsPerMl: Double?, prefersOunces: Boolean): List<AmountUnit> {
    val ounces = AmountUnit.Measure(MeasureKind.Ounces)
    val weights = if (prefersOunces) {
        listOf(ounces, AmountUnit.Grams, AmountUnit.Measure(MeasureKind.Pounds))
    } else {
        listOf(AmountUnit.Grams, ounces, AmountUnit.Measure(MeasureKind.Pounds))
    }
    val volumes = gramsPerMl?.takeIf { it > 0.0 }?.let { density ->
        listOf(AmountUnit.Measure(MeasureKind.Millilitres, density), AmountUnit.Measure(MeasureKind.FluidOunces, density))
    }.orEmpty()
    return weights + volumes
}

/**
 * Units for a catalog food: its portions, the person's [saved] sizes, then grams. A food with no
 * portions offers grams only, or the saved sizes and grams.
 */
fun Food.amountUnits(saved: List<FoodUnit> = emptyList()): List<AmountUnit> =
    portions.map(AmountUnit::OfPortion) + saved.map(AmountUnit::Saved) + AmountUnit.Grams

/** The food detail's units: [amountUnits] with every weight, and volumes where a portion states one. */
fun Food.detailUnits(saved: List<FoodUnit> = emptyList(), prefersOunces: Boolean = false): List<AmountUnit> =
    (portions.map(AmountUnit::OfPortion) + saved.map(AmountUnit::Saved)).withMeasures(measureUnits(gramsPerMl, prefersOunces))

/** A size someone named "oz" already says it; the menu has one of each name. */
private fun List<AmountUnit>.withMeasures(measures: List<AmountUnit>): List<AmountUnit> {
    val named = map { it.chipLabel.lowercase() }.toSet()
    return this + measures.filter { it == AmountUnit.Grams || it.chipLabel.lowercase() !in named }
}

/**
 * A custom food offers its serving unit, then its own sizes and the person's [saved] ones, then
 * grams, once a serving's weight is known. Counted in grams, grams is its serving unit. A serving
 * of unknown weight cannot be converted, so it is all there is until someone weighs it.
 */
fun CustomFood.amountUnits(saved: List<FoodUnit> = emptyList()): List<AmountUnit> {
    val gramsEach = gramsPerServing?.let { it / servingAmount } ?: return listOf(AmountUnit.Serving(servingUnit))
    val serving = listOf(AmountUnit.Serving(servingUnit, gramsEach)).takeUnless { isCountedInGrams }.orEmpty()
    return serving + (sizes + saved).distinctBy { it.label.lowercase() }.map(AmountUnit::Saved) + AmountUnit.Grams
}

/** The food detail's units: [amountUnits] with every weight once a serving's weight is known. */
fun CustomFood.detailUnits(saved: List<FoodUnit> = emptyList(), prefersOunces: Boolean = false): List<AmountUnit> {
    val units = amountUnits(saved)
    if (!isByWeight) return units
    return units.filterNot { it == AmountUnit.Grams }.withMeasures(measureUnits(gramsPerMl, prefersOunces))
}

/** Its serving is in grams. */
val CustomFood.isCountedInGrams: Boolean get() = servingUnit.equals("g", ignoreCase = true)

/** A serving's weight is known, so a size in grams converts to it. */
val CustomFood.isByWeight: Boolean get() = gramsPerServing != null

/** Grams in a millilitre, from a serving counted in ml with its weight, or a size stated in a volume. */
private val CustomFood.gramsPerMl: Double?
    get() {
        val grams = gramsPerServing ?: return null
        if (servingUnit.equals("ml", ignoreCase = true)) return grams / servingAmount
        return volumeInLabel(HouseholdAmount.of(servingAmount, servingUnit))?.let { grams / it }
            ?: sizes.firstNotNullOfOrNull { size -> volumeInLabel("1 ${size.label}")?.let { size.grams / it } }
    }

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
        is AmountUnit.Measure -> FoodAmount.Grams(quantity * unit.gramsEach)
    }
    val draft = toLogDraft(amount, precision, method)
    return when (unit) {
        is AmountUnit.Saved -> draft.copy(amountText = unit.savedText(quantity))
        is AmountUnit.Measure -> draft.copy(amountText = unit.amountText(quantity), quantity = quantity, unit = unit.label)
        else -> draft
    }
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

/**
 * [quantity] of [unit] of this custom food: its own unit, or a size, weight or volume converted
 * through what a serving weighs.
 */
fun CustomFood.draftFor(
    unit: AmountUnit,
    quantity: Double,
    precision: Precision = Precision.Estimate,
    method: LogMethod? = null,
): LogDraft {
    val perServing = gramsPerServing
    if (unit is AmountUnit.Serving || perServing == null) return draftFor(quantity, precision, method)
    val grams = quantity * (unit.gramsEach ?: return draftFor(quantity, precision, method))
    val draft = draftFor(grams / perServing * servingAmount, precision, method)
    return draft.copy(amountText = (unit as? AmountUnit.Saved)?.savedText(quantity) ?: unit.amountText(quantity), quantity = quantity, unit = unit.label)
}

/** The quantity a custom food starts at on the amount sheet: one serving, in its own unit. */
val CustomFood.initialQuantity: Double get() = if (servingAmount > 0.0) servingAmount else 1.0
