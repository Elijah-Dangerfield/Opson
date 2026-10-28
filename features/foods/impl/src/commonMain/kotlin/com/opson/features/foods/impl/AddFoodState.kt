package com.opson.features.foods.impl

import com.opson.libraries.diary.CustomFoodDraft
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy

/** Add a food (design v3, 11.3 and 11.4). Built by [AddFoodStateBuilder]. */
data class AddFoodState(
    val name: String = "",
    val brand: String = "",
    val servingAmount: String = "1",
    val servingUnit: ServingUnit = ServingUnit.Piece,
    val units: List<ServingUnit> = ServingUnit.entries,
    /** Only the metrics the mode does not hide, in label order. */
    val fields: List<NutritionField> = emptyList(),
    /** A name is all a food needs. */
    val canSave: Boolean = false,
) {
    /** No numbers and any other mode that hides every number: no number fields at all (11.4). */
    val labelOnly: Boolean get() = fields.isEmpty()
}

/**
 * What one serving is counted in, v3's three chips. [stored] is the custom food's `servingUnit`;
 * grams store as "g", so a logged amount carries its grams.
 */
enum class ServingUnit(val stored: String) {
    Piece("piece"),
    Grams("g"),
    Cup("cup"),
}

/** One nutrition field. [text] is what the person typed, never a number the policy formatted. */
data class NutritionField(val metric: Metric, val text: String)

/** What the person has typed so far. The view model holds it; [AddFoodStateBuilder] draws it. */
data class FoodForm(
    val name: String = "",
    val brand: String = "",
    val servingAmount: String = "1",
    val servingUnit: ServingUnit = ServingUnit.Piece,
    val nutrition: Map<Metric, String> = emptyMap(),
)

sealed interface AddFoodAction {
    data class PolicyArrived(val policy: VisibilityPolicy) : AddFoodAction
    data class NameTyped(val text: String) : AddFoodAction
    data class BrandTyped(val text: String) : AddFoodAction
    data class ServingAmountTyped(val text: String) : AddFoodAction
    data class ServingUnitPicked(val unit: ServingUnit) : AddFoodAction
    data class NutritionTyped(val metric: Metric, val text: String) : AddFoodAction
    data object SaveTapped : AddFoodAction
    data object CloseTapped : AddFoodAction
}

sealed interface AddFoodEvent {
    data class ShowToast(val toast: Toast) : AddFoodEvent
    data object Close : AddFoodEvent
}

/**
 * Which nutrition fields Add a food asks for, and the food it saves. Every field is optional.
 * A field is asked when the mode does not hide its metric (the configured level, as the
 * prototype: a Later user still fills them in). So All numbers asks all five, the two macro modes
 * protein, carbs, fat and fiber, and No numbers asks none.
 */
object AddFoodStateBuilder {
    val FIELD_ORDER: List<Metric> = listOf(Metric.Calories, Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber)

    fun asked(policy: VisibilityPolicy): List<Metric> = FIELD_ORDER.filter { policy.level(it) != Visibility.Hide }

    fun build(form: FoodForm, policy: VisibilityPolicy): AddFoodState = AddFoodState(
        name = form.name,
        brand = form.brand,
        servingAmount = form.servingAmount,
        servingUnit = form.servingUnit,
        fields = asked(policy).map { NutritionField(it, form.nutrition[it].orEmpty()) },
        canSave = form.name.isNotBlank(),
    )

    /**
     * The food to save. A blank field, or one this mode does not ask for, is unknown (null),
     * never 0: a blank energy must not read as "0 kcal" later. A blank amount is one serving.
     */
    fun draft(form: FoodForm, policy: VisibilityPolicy): CustomFoodDraft {
        val asked = asked(policy)
        fun value(metric: Metric): Double? = if (metric in asked) form.nutrition[metric]?.toDoubleOrNull() else null
        return CustomFoodDraft(
            name = form.name.trim(),
            servingAmount = form.servingAmount.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 1.0,
            servingUnit = form.servingUnit.stored,
            brand = form.brand.trim().ifEmpty { null },
            kcal = value(Metric.Calories),
            protein = value(Metric.Protein),
            carbs = value(Metric.Carbs),
            fat = value(Metric.Fat),
            fiber = value(Metric.Fiber),
        )
    }
}
