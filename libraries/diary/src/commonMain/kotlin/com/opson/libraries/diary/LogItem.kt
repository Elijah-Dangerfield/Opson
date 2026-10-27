package com.opson.libraries.diary

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * One thing the user logged. The nutrient fields are a snapshot taken when it
 * was logged, so editing or removing a catalog food never rewrites history.
 *
 * Every nutrient is nullable on purpose: a name-only entry ("Named") has no
 * energy, and a null is "we do not know", never zero. Use [Totals] to add
 * items up.
 */
data class LogItem(
    val id: String,
    val day: LocalDate,
    val slot: MealSlot,
    val loggedAt: Instant,
    val name: String,
    val precision: Precision,
    val method: LogMethod,
    val foodId: String? = null,
    val customFoodId: String? = null,
    val amountText: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val grams: Double? = null,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
    /** Micronutrient key (unit in the key, `sodium_mg`) to amount for the whole item. */
    val micros: Map<String, Double>? = null,
) {
    /**
     * The amount as a row shows it. Every screen reads this, not [amountText], so entries an
     * older build saved as "0.25 cup" or "1.5 × 1 cup" read "¼ cup" and "1½ cups" too.
     */
    val amountLabel: String get() = HouseholdAmount.tidy(amountText.orEmpty())

    /**
     * This entry at [count] of its own unit ("Change amount"): the nutrients, grams and micros
     * scale with it, so 2 tbsp changed to 3 is half as much again of everything. Null when there
     * is no amount to scale from (a name only, a quick number). The precision stays as logged.
     */
    fun withQuantity(count: Double): LogItem? {
        val from = quantity?.takeIf { it > 0.0 } ?: return null
        val unit = unit ?: return null
        if (count <= 0.0 || !count.isFinite()) return null
        val factor = count / from
        return copy(
            quantity = count,
            amountText = HouseholdAmount.ofPortion(unit, count),
            grams = grams?.times(factor),
            kcal = kcal?.times(factor),
            protein = protein?.times(factor),
            carbs = carbs?.times(factor),
            fat = fat?.times(factor),
            fiber = fiber?.times(factor),
            micros = micros?.mapValues { it.value * factor },
        )
    }

    /** The same food and amount, for copying into another day or a saved meal. */
    fun toDraft(): LogDraft = LogDraft(
        name = name,
        precision = precision,
        foodId = foodId,
        customFoodId = customFoodId,
        amountText = amountText,
        quantity = quantity,
        unit = unit,
        grams = grams,
        kcal = kcal,
        protein = protein,
        carbs = carbs,
        fat = fat,
        fiber = fiber,
        micros = micros,
    )
}

/**
 * An item before it is logged: what the logger's basket, Describe, a saved
 * meal and "copy yesterday" hand to [DiaryRepository.addItems]. The repository
 * gives it an id, a day, a slot and a time.
 *
 * Also the snapshot a [SavedMeal] stores, which is why it is serializable.
 * [method] overrides the method passed to `addItems` for this one item, for a
 * basket that mixes a search pick with a favorite.
 */
@Serializable
data class LogDraft(
    val name: String,
    val precision: Precision,
    val foodId: String? = null,
    val customFoodId: String? = null,
    val amountText: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val grams: Double? = null,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
    val micros: Map<String, Double>? = null,
    val method: LogMethod? = null,
) {
    /** The amount as a row shows it, read the same way as [LogItem.amountLabel]. Not serialized: no backing field. */
    val amountLabel: String get() = HouseholdAmount.tidy(amountText.orEmpty())
}

/**
 * A sum that knows how much of it is known.
 *
 * [value] is null when there was something to add but none of it had this
 * nutrient (three name-only items), so a screen can say nothing rather than
 * "0 kcal". When some items are known and some are not, [value] is the sum of
 * the known ones and [isPartial] is true: it is a floor, not the day's total.
 * An empty list sums to a real 0.
 */
data class Total(
    val known: Double = 0.0,
    val knownCount: Int = 0,
    val unknownCount: Int = 0,
) {
    val value: Double? get() = if (knownCount == 0 && unknownCount > 0) null else known

    val isPartial: Boolean get() = knownCount > 0 && unknownCount > 0

    /** Nothing was added up at all. */
    val isEmpty: Boolean get() = knownCount == 0 && unknownCount == 0

    fun plus(amount: Double?): Total =
        if (amount == null) copy(unknownCount = unknownCount + 1)
        else copy(known = known + amount, knownCount = knownCount + 1)

    operator fun plus(other: Total): Total = Total(
        known = known + other.known,
        knownCount = knownCount + other.knownCount,
        unknownCount = unknownCount + other.unknownCount,
    )

    /** Something eaten with no details at all, a meal marked "ate, no details". */
    fun plusUnknown(count: Int = 1): Total = copy(unknownCount = unknownCount + count)
}

data class Totals(
    val kcal: Total = Total(),
    val protein: Total = Total(),
    val carbs: Total = Total(),
    val fat: Total = Total(),
    val fiber: Total = Total(),
) {
    operator fun plus(other: Totals): Totals = Totals(
        kcal = kcal + other.kcal,
        protein = protein + other.protein,
        carbs = carbs + other.carbs,
        fat = fat + other.fat,
        fiber = fiber + other.fiber,
    )

    fun plusUnknown(count: Int = 1): Totals = if (count == 0) this else Totals(
        kcal = kcal.plusUnknown(count),
        protein = protein.plusUnknown(count),
        carbs = carbs.plusUnknown(count),
        fat = fat.plusUnknown(count),
        fiber = fiber.plusUnknown(count),
    )

    companion object {
        val Empty = Totals()
    }
}

fun Iterable<LogItem>.totals(): Totals = fold(Totals.Empty) { acc, item ->
    Totals(
        kcal = acc.kcal.plus(item.kcal),
        protein = acc.protein.plus(item.protein),
        carbs = acc.carbs.plus(item.carbs),
        fat = acc.fat.plus(item.fat),
        fiber = acc.fiber.plus(item.fiber),
    )
}

/** Micronutrient sums over the items that carry them. Items without micros add nothing. */
fun Iterable<LogItem>.microTotals(): Map<String, Double> {
    val out = mutableMapOf<String, Double>()
    forEach { item -> item.micros?.forEach { (key, amount) -> out[key] = (out[key] ?: 0.0) + amount } }
    return out
}
