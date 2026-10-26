package com.opson.features.logger.impl

import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.Precision
import com.opson.libraries.nutrition.NutrientAmounts
import org.jetbrains.compose.resources.StringResource

/**
 * A toast for the entry point to show. [args] fill the message's `%1$s`
 * slots; a [StringResource] among them (a meal's sentence name) is resolved
 * first, so a view model never needs a resource loader.
 */
data class Toast(val message: StringResource, val args: List<Any> = emptyList())

/**
 * A food's name cut at the first comma or bracket, for the Favorites tiles,
 * the basket line and the toast: "Greek yogurt, plain" is "Greek yogurt". The
 * prototype's `SHORT`, generalized.
 */
fun shortName(name: String): String {
    val cut = name.indexOfFirst { it == ',' || it == '(' || it == '[' }
    val short = (if (cut > 0) name.substring(0, cut) else name).trim()
    return short.ifEmpty { name.trim() }
}

/**
 * Energy and macros of a draft for the policy. An unknown macro reads as 0 g
 * here, so check [hasMacros] before drawing a macro line: a name-only item
 * must show nothing, not "0P 0C 0F".
 */
internal fun LogDraft.amounts(): NutrientAmounts =
    NutrientAmounts(kcal = kcal, protein = protein ?: 0.0, carbs = carbs ?: 0.0, fat = fat ?: 0.0, fiber = fiber ?: 0.0)

/** Macros worth a macro line: at least one of protein, carbs and fat is known. */
internal val LogDraft.hasMacros: Boolean get() = protein != null || carbs != null || fat != null

/**
 * The same food and amount again, for a pick from Favorites, Usual or Recent.
 * Logging it again is an estimate of the same amount, not a fresh weighing; a
 * name-only entry stays name-only.
 */
internal fun LogItem.againDraft(): LogDraft {
    val draft = toDraft()
    return if (draft.precision == Precision.Weighed) draft.copy(precision = Precision.Estimate) else draft
}

/** Just the name: no amount and no nutrients, which everywhere else reads as "we do not know". */
internal fun LogDraft.nameOnly(): LogDraft =
    LogDraft(name = name, precision = Precision.Named, foodId = foodId, customFoodId = customFoodId, method = method)

/** The sum of the drafts' energy, or null when none of them has any. */
internal fun List<LogDraft>.totalKcal(): Double? =
    mapNotNull { it.kcal }.takeIf { it.isNotEmpty() }?.sum()
