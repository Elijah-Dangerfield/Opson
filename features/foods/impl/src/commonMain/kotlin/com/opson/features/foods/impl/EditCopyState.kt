package com.opson.features.foods.impl

import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.CustomFoodDraft
import com.opson.libraries.diary.HouseholdAmount
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlin.math.roundToInt

/** Edit a copy (design sync 4, 13.3e). Built by [EditCopyStateBuilder]. */
data class EditCopyState(
    val loading: Boolean = true,
    val name: String = "",
    val brand: String = "",
    /** "2 tbsp = 32 g": the logged amount, which the copy counts as one serving. */
    val serving: String = "",
    /** Only the metrics the mode shows in full; the rest are copied as they were, unseen. */
    val fields: List<CopyField> = emptyList(),
    /** "Use for this entry", on until switched off. */
    val useForEntry: Boolean = true,
    /** The entry's meal, for "Today's snack switches to your copy". */
    val slot: MealSlot = MealSlot.Uncategorized,
    val isToday: Boolean = true,
    /** A name is all a copy needs. */
    val canSave: Boolean = false,
)

/**
 * One number of the copy. [text] is what is typed (it starts as the food's own amount); [was] is
 * that amount through the policy ("190", "8 g") once [text] differs from it, else null.
 */
data class CopyField(val metric: Metric, val text: String, val was: Display?)

/** What the person has typed. The view model holds it; [EditCopyStateBuilder] draws it. */
data class CopyForm(
    val name: String,
    val brand: String,
    val nutrition: Map<Metric, String>,
    val useForEntry: Boolean = true,
)

sealed interface EditCopyAction {
    /** The entry to copy, the brand of the food of the person's own it came from (if one), and the policy. */
    data class EntryArrived(val item: LogItem, val brand: String?, val isToday: Boolean, val policy: VisibilityPolicy) : EditCopyAction
    data class NameTyped(val text: String) : EditCopyAction
    data class BrandTyped(val text: String) : EditCopyAction
    data class NutritionTyped(val metric: Metric, val text: String) : EditCopyAction
    data object UseForEntryToggled : EditCopyAction
    data object SaveTapped : EditCopyAction
    data object CloseTapped : EditCopyAction
}

sealed interface EditCopyEvent {
    data class ShowToast(val toast: Toast) : EditCopyEvent
    data object Close : EditCopyEvent
}

/**
 * The copy as a pure function of the entry, what was typed and the policy, so the previews and
 * the leak test draw what the app does.
 *
 * One serving of the copy is the amount logged, so its numbers start as the entry's. A number
 * shows only where the mode shows it in full on Foods: anything softer would put an amount on
 * screen the mode keeps back. What is not shown is copied as it was.
 */
object EditCopyStateBuilder {
    val FIELD_ORDER: List<Metric> = AddFoodStateBuilder.FIELD_ORDER

    fun shown(policy: VisibilityPolicy): List<Metric> = FIELD_ORDER.filter { policy.effectiveLevel(it, Surface.Foods) == Visibility.Show }

    /** The form as it opens: "Peanut butter (mine)", the brand, the numbers that show. */
    fun form(item: LogItem, brand: String?, mine: String, policy: VisibilityPolicy): CopyForm = CopyForm(
        name = "${item.name} $mine",
        brand = brand.orEmpty(),
        nutrition = shown(policy).associateWith { metric -> original(item, metric)?.let(::typed).orEmpty() },
    )

    fun build(item: LogItem, form: CopyForm, isToday: Boolean, policy: VisibilityPolicy): EditCopyState = EditCopyState(
        loading = false,
        name = form.name,
        brand = form.brand,
        serving = serving(item),
        fields = shown(policy).map { metric ->
            val text = form.nutrition[metric].orEmpty()
            val before = original(item, metric)
            val changed = text.toDoubleOrNull() != before
            CopyField(
                metric = metric,
                text = text,
                was = if (changed) policy.resolve(metric, before, Surface.Foods, FormatContext(bare = true)).takeIf { it is Display.Shown } else null,
            )
        },
        useForEntry = form.useForEntry,
        slot = item.slot,
        isToday = isToday,
        canSave = form.name.isNotBlank(),
    )

    /**
     * The new food: one serving is the logged amount. A shown field left blank is unknown, never
     * 0; a field not shown keeps the entry's own number.
     */
    fun draft(item: LogItem, form: CopyForm, policy: VisibilityPolicy): CustomFoodDraft {
        val shown = shown(policy)
        fun value(metric: Metric): Double? =
            if (metric in shown) form.nutrition[metric]?.toDoubleOrNull() else original(item, metric)
        val unit = item.unit
        return CustomFoodDraft(
            name = form.name.trim(),
            // A portion label carries its own amount: 2 of "1 cup" is 2 cup, so the copy reads "2 cups".
            servingAmount = (item.quantity ?: 1.0) * (unit?.let(HouseholdAmount::amountOf) ?: 1.0),
            servingUnit = unit?.let(HouseholdAmount::unitOf) ?: SERVING,
            brand = form.brand.trim().ifEmpty { null },
            kcal = value(Metric.Calories),
            protein = value(Metric.Protein),
            carbs = value(Metric.Carbs),
            fat = value(Metric.Fat),
            fiber = value(Metric.Fiber),
        )
    }

    /** The entry pointed at [food], its numbers now the copy's. Its amount, time and meal stay. */
    fun switched(item: LogItem, food: CustomFood): LogItem = item.copy(
        name = food.name,
        foodId = null,
        customFoodId = food.id,
        kcal = food.kcal,
        protein = food.protein,
        carbs = food.carbs,
        fat = food.fat,
        fiber = food.fiber,
    )

    private fun original(item: LogItem, metric: Metric): Double? = when (metric) {
        Metric.Calories -> item.kcal
        Metric.Protein -> item.protein
        Metric.Carbs -> item.carbs
        Metric.Fat -> item.fat
        Metric.Fiber -> item.fiber
        else -> null
    }

    /** A number as it would be typed: "190", "7.5". */
    private fun typed(value: Double): String {
        val tenths = (value * TENTHS).roundToInt()
        return if (tenths % TENTHS.toInt() == 0) (tenths / TENTHS.toInt()).toString() else (tenths / TENTHS).toString()
    }

    private fun serving(item: LogItem): String {
        val amount = item.amountLabel.ifBlank { HouseholdAmount.of(1.0, SERVING) }
        val grams = item.grams?.takeIf { item.unit?.equals(GRAMS, ignoreCase = true) != true }
        return if (grams == null) amount else "$amount = ${grams.roundToInt()} $GRAMS"
    }

    private const val SERVING = "serving"
    private const val GRAMS = "g"
    private const val TENTHS = 10.0
}
