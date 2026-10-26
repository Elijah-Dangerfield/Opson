package com.opson.libraries.foods

import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogItem

/**
 * Which food something is: a catalog food or one of the user's own. The
 * logger's basket, the amount sheet and the Foods tab's pre-pick all name a
 * food this way.
 *
 * [key] matches how the diary groups entries for Recent and Usual, so the same
 * food is the same key wherever it shows up.
 */
sealed interface FoodRef {
    val id: String
    val key: String

    data class Catalog(override val id: String) : FoodRef {
        override val key: String get() = "f:$id"
    }

    data class Custom(override val id: String) : FoodRef {
        override val key: String get() = "c:$id"
    }

    companion object {
        fun of(foodId: String?, customFoodId: String?): FoodRef? =
            foodId?.let(::Catalog) ?: customFoodId?.let(::Custom)
    }
}

/** The food this entry was logged from, or null for a name-only entry or a quick number. */
fun LogItem.foodRef(): FoodRef? = FoodRef.of(foodId, customFoodId)

/** The food this draft comes from, or null when it is only a name. */
fun LogDraft.foodRef(): FoodRef? = FoodRef.of(foodId, customFoodId)
