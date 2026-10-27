package com.opson.features.logger.impl

import com.opson.libraries.diary.HouseholdAmount
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.Precision
import com.opson.libraries.foods.AmountUnit
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.ParsedItem
import com.opson.libraries.foods.PortionAmount
import com.opson.libraries.foods.amountUnits
import com.opson.libraries.foods.draftFor
import com.opson.libraries.foods.toLogDraft
import kotlin.math.max
import kotlin.math.round

/**
 * What Describe read, as lines the person can fix before anything is logged
 * (docs/plans/screens/describe.md): change the amount with a stepper or the
 * unit menu, swap the food for one of the alternatives the server sent or one
 * searched for, take a line out, or add one it missed. Add logs exactly these
 * lines.
 */
data class DescribeReview(
    val rows: List<DescribeRow> = emptyList(),
    /** The search for a missed food, or for a line's replacement; null when closed. */
    val find: DescribeFind? = null,
    /**
     * Phrases the person took out ("bacon"), by their text, so reading the
     * same words again after an edit to the description doesn't bring them back.
     */
    val dismissed: Set<String> = emptySet(),
    /** The next line's key number. Keys are never reused while the pane is open. */
    val nextKey: Int = 0,
)

/**
 * One line. [food] and [unit] are set when the catalog matched it, and the
 * amount is then [quantity] of [unit] of the food; otherwise [parsed] is all
 * there is and the line logs as the server read it. [said] is the phrase it
 * came from, how a later read of the same words recognises it. [edited] lines
 * keep what the person did when the description is read again; [added] lines
 * came from search and stay whatever the description says.
 */
data class DescribeRow(
    val key: String,
    val said: String,
    val food: Food?,
    val unit: AmountUnit?,
    val quantity: Double,
    val parsed: ParsedItem? = null,
    val alternatives: List<Food> = emptyList(),
    val edited: Boolean = false,
    val added: Boolean = false,
) {
    val name: String get() = food?.name ?: parsed?.name.orEmpty()

    /** Always an estimate, logged as Describe whether it was read or searched for. */
    fun draft(): LogDraft = if (food != null && unit != null) {
        food.draftFor(unit, quantity, Precision.Estimate, LogMethod.Describe)
    } else {
        checkNotNull(parsed) { "a line is a food or a parsed item" }.toLogDraft()
    }

    fun toLogPick(): LogPick {
        // A server older than 0.2 names the food by id only, with no food to change the amount on.
        val ref = food?.let { FoodRef.Catalog(it.id) } ?: parsed?.foodId?.let { FoodRef.Catalog(it) }
        return LogPick(key = key, ref = ref, draft = draft().copy(method = PickSource.Describe.methodFor(ref)), source = PickSource.Describe, food = food)
    }
}

/** The find-a-food search inside the pane. [replacing] is the line being swapped, or null when adding one. */
data class DescribeFind(val replacing: String?, val query: String = "", val answer: SwapAnswer? = null)

/** What the person does to the lines. Handled by [LoggerViewModel] through [DescribeRows]. */
sealed interface DescribeReviewAction : LoggerAction {
    data class LessTapped(val key: String) : DescribeReviewAction
    data class MoreTapped(val key: String) : DescribeReviewAction
    data class UnitPicked(val key: String, val index: Int) : DescribeReviewAction

    /** One of the line's alternatives, by its place in [DescribedLine.alternatives]. */
    data class AlternativePicked(val key: String, val index: Int) : DescribeReviewAction
    data class RemoveTapped(val key: String) : DescribeReviewAction

    /** "Add something it missed" with no key; "Search for something else" on a line with its key. */
    data class FindTapped(val replacing: String? = null) : DescribeReviewAction
    data class FindTyped(val text: String) : DescribeReviewAction
    data class FindResultsArrived(val query: String, val foods: List<Food>) : DescribeReviewAction
    data class FindPicked(val id: String) : DescribeReviewAction
    data object FindClosed : DescribeReviewAction
}

/**
 * The line rules, pure so they can be tested on their own. [householdOnly] is
 * true while calories are hidden: a weight is a number too, so a food with a
 * household measure is counted in it.
 */
object DescribeRows {

    /** The server's read, as lines. A line the person already changed keeps their change. */
    fun read(review: DescribeReview, items: List<ParsedItem>, householdOnly: Boolean): DescribeReview {
        val kept = review.rows.filter { it.edited && !it.added }.associateBy { it.said }
        var next = review.nextKey
        val fresh = items.filter { it.text !in review.dismissed }.map { item ->
            kept[item.text] ?: fromItem("describe:${next++}", item, householdOnly)
        }
        return review.copy(rows = fresh + review.rows.filter { it.added }, nextKey = next)
    }

    /** Nothing to read (the text went, or the read failed): only what was added by hand stays. */
    fun cleared(review: DescribeReview): DescribeReview = review.copy(rows = review.rows.filter { it.added }, find = null)

    fun fromItem(key: String, item: ParsedItem, householdOnly: Boolean): DescribeRow {
        val food = item.food ?: return DescribeRow(key, item.text, food = null, unit = null, quantity = item.quantity, parsed = item)
        val (unit, quantity) = initialAmount(food, item, householdOnly)
        return DescribeRow(key, item.text, food, unit, quantity, parsed = item, alternatives = item.alternatives)
    }

    /** A food picked from search, as a new line: one of its first household measure. */
    fun added(key: String, said: String, food: Food, householdOnly: Boolean): DescribeRow {
        val unit = units(food, householdOnly).first()
        return DescribeRow(key, said, food, unit, initial(food, unit), added = true)
    }

    /**
     * [row] with [food] in its place, keeping the amount where the new food
     * has the same measure ("1 egg" stays one egg) and the weight otherwise.
     * The food it replaces becomes the first alternative, so a swap is one
     * tap to undo.
     */
    fun swapped(row: DescribeRow, food: Food, householdOnly: Boolean): DescribeRow {
        val units = units(food, householdOnly)
        val sameMeasure = (row.unit as? AmountUnit.OfPortion)?.let { current ->
            units.firstOrNull { it is AmountUnit.OfPortion && it.portion.label.equals(current.portion.label, ignoreCase = true) }
        }
        val (unit, quantity) = when {
            sameMeasure != null -> sameMeasure to row.quantity
            else -> {
                val grams = row.draft().grams?.takeIf { it > 0 }
                val to = units.first()
                to to if (grams == null || to.gramsEach == null) initial(food, to) else fit(food, to, grams / checkNotNull(to.gramsEach))
            }
        }
        val alternatives = (listOfNotNull(row.food) + row.alternatives).filter { it.id != food.id }.distinctBy { it.id }
        return row.copy(food = food, unit = unit, quantity = quantity, alternatives = alternatives, edited = true)
    }

    fun less(row: DescribeRow): DescribeRow {
        val food = row.food ?: return row
        val unit = row.unit ?: return row
        val step = step(food, unit)
        return row.copy(quantity = max(step, tidy(row.quantity - step)), edited = true)
    }

    fun more(row: DescribeRow): DescribeRow {
        val food = row.food ?: return row
        val unit = row.unit ?: return row
        return row.copy(quantity = tidy(row.quantity + step(food, unit)), edited = true)
    }

    fun canLess(row: DescribeRow): Boolean {
        val food = row.food ?: return false
        val unit = row.unit ?: return false
        return row.quantity > step(food, unit)
    }

    /** The unit menu's pick: the count stays and the weight follows, as on the amount sheet; to or from grams converts. */
    fun unitPicked(row: DescribeRow, index: Int, householdOnly: Boolean): DescribeRow {
        val food = row.food ?: return row
        val from = row.unit ?: return row
        val to = units(food, householdOnly).getOrNull(index) ?: return row
        if (to == from) return row
        return row.copy(unit = to, quantity = fit(food, to, PortionAmount.switch(row.quantity, from, to)), edited = true)
    }

    /**
     * The measures the unit menu offers: the food's portions without the
     * bookkeeping ones ("Guideline amount per...", school cartons), then grams,
     * which are left out while calories are hidden and the food has a household measure.
     */
    fun units(food: Food, householdOnly: Boolean): List<AmountUnit> {
        val all = food.amountUnits().filterNot { it is AmountUnit.OfPortion && it.portion.label.isBookkeeping() }
        val usable = all.ifEmpty { listOf(AmountUnit.Grams) }
        return if (householdOnly && usable.any { it !is AmountUnit.Grams }) usable.filterNot { it == AmountUnit.Grams } else usable
    }

    /**
     * One tap's worth: 10 g in grams, one of something nobody halves (an
     * egg, a nugget, a link, an ounce), and a half of everything else
     * (a slice, a cup, a serving).
     */
    fun step(food: Food, unit: AmountUnit): Double = when {
        unit is AmountUnit.Grams -> GRAM_STEP
        unit is AmountUnit.OfPortion && unit.countsWholeItems(food) -> 1.0
        else -> HALF
    }

    /** A line counted in grams moves to the food's household measure once calories are hidden. */
    fun forPolicy(row: DescribeRow, householdOnly: Boolean): DescribeRow {
        val food = row.food ?: return row
        val unit = row.unit ?: return row
        if (!householdOnly || unit != AmountUnit.Grams || food.portions.isEmpty()) return row
        val to = units(food, householdOnly = true).first()
        return row.copy(unit = to, quantity = fit(food, to, PortionAmount.convert(row.quantity, unit, to)))
    }

    /**
     * The portion the server matched, counted from its weight; else the
     * server's weight in grams; else one of the food's first unit.
     */
    private fun initialAmount(food: Food, item: ParsedItem, householdOnly: Boolean): Pair<AmountUnit, Double> {
        val units = units(food, householdOnly)
        val portion = units.firstOrNull { it is AmountUnit.OfPortion && it.portion.label.equals(item.portionLabel, ignoreCase = true) }
        val grams = item.grams?.takeIf { it > 0 }
        return when {
            portion != null && grams != null -> portion to fit(food, portion, grams / checkNotNull(portion.gramsEach))
            portion != null -> portion to fit(food, portion, item.quantity)
            grams != null && !householdOnly -> AmountUnit.Grams to max(1.0, round(grams))
            grams != null -> units.first().let { to -> to to fit(food, to, PortionAmount.convert(grams, AmountUnit.Grams, to)) }
            else -> units.first().let { it to initial(food, it) }
        }
    }

    private fun initial(food: Food, unit: AmountUnit): Double = if (unit is AmountUnit.Grams) PortionAmount.initial(unit) else step(food, unit).coerceAtLeast(1.0)

    /** [quantity] on [unit]'s steps, never below one step. A weight stays to the gram. */
    private fun fit(food: Food, unit: AmountUnit, quantity: Double): Double {
        if (unit is AmountUnit.Grams) return max(1.0, round(quantity))
        val step = step(food, unit)
        return max(step, round(quantity / step) * step).let(::tidy)
    }

    private fun AmountUnit.OfPortion.countsWholeItems(food: Food): Boolean {
        val words = HouseholdAmount.unitOf(portion.label).lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
        val first = words.firstOrNull() ?: return false
        if (first.singular() in WHOLE_ITEMS || first in WHOLE_MEASURES) return true
        // "1 large" on an egg: a size alone counts the food itself.
        val foodWord = food.name.lowercase().split(Regex("[^a-z]+")).firstOrNull()?.singular()
        return words.all { it in SIZES } && foodWord in WHOLE_ITEMS
    }

    private fun String.singular(): String = if (length > 3 && endsWith("s")) dropLast(1) else this

    private fun String.isBookkeeping(): Boolean = BOOKKEEPING.any { contains(it, ignoreCase = true) }

    // One decimal place is all a half step needs, and it keeps 0.1 + 0.2 drift out of the text.
    private fun tidy(value: Double): Double = round(value * 100) / 100

    private const val GRAM_STEP = 10.0
    private const val HALF = 0.5

    /** Things counted whole: half an egg is not something anyone logs. */
    private val WHOLE_ITEMS = setOf(
        "egg", "nugget", "link", "wing", "shrimp", "nut", "almond", "grape", "chip", "fry", "meatball", "olive",
        "cracker", "strip", "drumstick", "pat",
    )

    /** Measures small enough that a half is noise: an ounce, a teaspoon. */
    private val WHOLE_MEASURES = setOf("oz", "fl", "tsp", "teaspoon")

    private val SIZES = setOf("small", "medium", "large", "extra", "jumbo", "regular")

    private val BOOKKEEPING = listOf("guideline", "school", "not specified", "as ingredient", "yields")
}
