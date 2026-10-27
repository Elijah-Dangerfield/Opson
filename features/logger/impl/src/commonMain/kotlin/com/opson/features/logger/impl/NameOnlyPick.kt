package com.opson.features.logger.impl

import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.CustomFoodDraft
import com.opson.libraries.diary.CustomFoodRepository
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.Precision
import com.opson.libraries.foods.FoodRef

/**
 * "Save the name", from search's miss, Describe and the scanner's miss: a custom food with
 * nothing but [name], so it shows up in search next time, as a name-only pick with no numbers.
 * An existing food by that name in [known] is reused rather than duplicated.
 */
internal suspend fun nameOnlyPick(name: String, known: List<CustomFood>, customFoods: CustomFoodRepository): LogPick {
    val food = known.firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?: customFoods.create(CustomFoodDraft(name = name, servingAmount = 1.0, servingUnit = NAME_ONLY_UNIT))
    val ref = FoodRef.Custom(food.id)
    val draft = LogDraft(name = food.name, precision = Precision.Named, customFoodId = food.id, method = PickSource.Name.methodFor(ref))
    return LogPick(key = ref.key, ref = ref, draft = draft, source = PickSource.Name)
}

/** A saved name's serving: one of whatever it is. Data, not copy. */
internal const val NAME_ONLY_UNIT = "serving"
