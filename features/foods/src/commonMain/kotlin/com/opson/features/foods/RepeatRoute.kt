package com.opson.features.foods

import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * A repeat, new or [ruleId]'s (`foods.md`, Repeating). From "Repeat this" on a food's sheet on
 * Today it starts from [fromEntryId] on [fromDayIso] (`LocalDate.toString()`): that food and
 * amount, in its meal. Plain strings only, so it needs no typeMap. Foods registers it.
 */
@Serializable
data class RepeatRoute(
    val ruleId: String? = null,
    val fromDayIso: String? = null,
    val fromEntryId: String? = null,
) : Route()
