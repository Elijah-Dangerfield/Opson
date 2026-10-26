package com.opson.server.routes

import com.opson.server.domain.DescribedItem
import com.opson.server.domain.Food
import com.opson.server.domain.Nutrients
import kotlinx.serialization.Serializable

/**
 * Wire DTOs for `/v1/foods/...` and `/v1/describe`. camelCase like the rest of
 * the API.
 *
 * `source` is one of `usda_foundation`, `usda_sr`, `usda_fndds`,
 * `usda_branded`, `nutritionix`, `off`. An `off` food comes from Open Food
 * Facts (ODbL), which the Foods screen credits. `micros` keys carry their
 * unit (`sodium_mg`, `vitamin_d_ug`).
 *
 * `tier` is 1 reference (USDA generic), 2 label (USDA Branded), 3 licensed,
 * 4 community (Open Food Facts); `verified` is true for tiers 1 to 3.
 */
@Serializable
data class FoodResponse(
    val id: String,
    val source: String,
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val per100g: NutrientsDto,
    val micros: Map<String, Double> = emptyMap(),
    val portions: List<PortionDto> = emptyList(),
    val tier: Int,
    val verified: Boolean,
)

@Serializable
data class NutrientsDto(
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
)

/** `grams` is absent for a serving the label gives only in ml; the client then asks for a weight. */
@Serializable
data class PortionDto(val label: String, val grams: Double? = null)

@Serializable
data class FoodSearchResponse(
    /** The normalized query the results are for. */
    val query: String,
    val foods: List<FoodResponse>,
)

@Serializable
data class DescribeRequest(val text: String? = null)

@Serializable
data class DescribeResponse(val items: List<DescribedItemDto>)

/**
 * One parsed phrase. Always an estimate: the client shows "Estimate" and logs
 * it with precision `estimate`. `confidence` is `high`, `medium` or `low`.
 * `foodId` (and the nutrient fields) are null when nothing in the catalog
 * matched; `grams` is then only set for a mass unit. Nutrients are for the
 * whole item, not per 100 g. `unit` is null or one of `g`, `kg`, `oz`, `cup`,
 * `tbsp`, `tsp`, `slice`, `piece`, `plate`, `bowl`, `serving`.
 */
@Serializable
data class DescribedItemDto(
    val text: String,
    val name: String,
    val foodId: String? = null,
    val quantity: Double,
    val unit: String? = null,
    val amountText: String,
    val portionLabel: String? = null,
    val grams: Double? = null,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
    val micros: Map<String, Double>? = null,
    val confidence: String,
)

fun Nutrients.toDto(): NutrientsDto = NutrientsDto(kcal, protein, carbs, fat, fiber)

fun Food.toResponse(): FoodResponse = FoodResponse(
    id = id.toString(),
    source = source.wire,
    name = name,
    brand = brand,
    barcode = barcode,
    per100g = per100g.toDto(),
    micros = micros,
    portions = portions.map { PortionDto(it.label, it.grams) },
    tier = tier.wire,
    verified = verified,
)

fun DescribedItem.toDto(): DescribedItemDto = DescribedItemDto(
    text = text,
    name = name,
    foodId = foodId?.toString(),
    quantity = quantity,
    unit = unit?.wire,
    amountText = amountText,
    portionLabel = portionLabel,
    grams = grams,
    kcal = nutrients?.kcal,
    protein = nutrients?.protein,
    carbs = nutrients?.carbs,
    fat = nutrients?.fat,
    fiber = nutrients?.fiber,
    micros = micros,
    confidence = confidence.wire,
)
