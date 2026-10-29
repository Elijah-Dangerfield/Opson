package com.opson.libraries.nutrition.ui

import com.opson.libraries.nutrition.AmountWords
import com.opson.libraries.nutrition.NutritionWords
import com.opson.libraries.nutrition.SoftWord
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.nutrition_above_range
import opson.libraries.resources.generated.resources.nutrition_amount_grams
import opson.libraries.resources.generated.resources.nutrition_amount_kilograms
import opson.libraries.resources.generated.resources.nutrition_amount_milligrams
import opson.libraries.resources.generated.resources.nutrition_amount_micrograms
import opson.libraries.resources.generated.resources.nutrition_amount_millilitres
import opson.libraries.resources.generated.resources.nutrition_amount_fluid_ounces
import opson.libraries.resources.generated.resources.nutrition_amount_ounces
import opson.libraries.resources.generated.resources.nutrition_amount_pounds
import opson.libraries.resources.generated.resources.nutrition_amount_grams_word
import opson.libraries.resources.generated.resources.nutrition_symbol_kcal
import opson.libraries.resources.generated.resources.nutrition_symbol_grams
import opson.libraries.resources.generated.resources.nutrition_symbol_milligrams
import opson.libraries.resources.generated.resources.nutrition_symbol_micrograms
import opson.libraries.resources.generated.resources.nutrition_symbol_millilitres
import opson.libraries.resources.generated.resources.nutrition_symbol_fluid_ounces
import opson.libraries.resources.generated.resources.nutrition_symbol_ounces
import opson.libraries.resources.generated.resources.nutrition_symbol_pounds
import opson.libraries.resources.generated.resources.nutrition_energy
import opson.libraries.resources.generated.resources.nutrition_energy_of
import opson.libraries.resources.generated.resources.nutrition_energy_of_bare
import opson.libraries.resources.generated.resources.nutrition_energy_slash
import opson.libraries.resources.generated.resources.nutrition_glass_floz
import opson.libraries.resources.generated.resources.nutrition_glass_ml
import opson.libraries.resources.generated.resources.nutrition_grams
import opson.libraries.resources.generated.resources.nutrition_grams_of
import opson.libraries.resources.generated.resources.nutrition_grams_slash
import opson.libraries.resources.generated.resources.nutrition_left
import opson.libraries.resources.generated.resources.nutrition_macro_carbs
import opson.libraries.resources.generated.resources.nutrition_macro_fat
import opson.libraries.resources.generated.resources.nutrition_macro_fiber
import opson.libraries.resources.generated.resources.nutrition_macro_letter_carbs
import opson.libraries.resources.generated.resources.nutrition_macro_letter_fat
import opson.libraries.resources.generated.resources.nutrition_macro_letter_fiber
import opson.libraries.resources.generated.resources.nutrition_macro_letter_protein
import opson.libraries.resources.generated.resources.nutrition_macro_long
import opson.libraries.resources.generated.resources.nutrition_macro_protein
import opson.libraries.resources.generated.resources.nutrition_micro_limit
import opson.libraries.resources.generated.resources.nutrition_micro_mg
import opson.libraries.resources.generated.resources.nutrition_micro_ug
import opson.libraries.resources.generated.resources.nutrition_on_target
import opson.libraries.resources.generated.resources.nutrition_percent
import opson.libraries.resources.generated.resources.nutrition_range
import opson.libraries.resources.generated.resources.nutrition_ring_above_range_unit
import opson.libraries.resources.generated.resources.nutrition_ring_above_range_value
import opson.libraries.resources.generated.resources.nutrition_ring_on_target_unit
import opson.libraries.resources.generated.resources.nutrition_ring_on_target_value
import opson.libraries.resources.generated.resources.nutrition_soft_a_bigger_day
import opson.libraries.resources.generated.resources.nutrition_soft_building
import opson.libraries.resources.generated.resources.nutrition_soft_early_in_the_day
import opson.libraries.resources.generated.resources.nutrition_soft_falling_slowly
import opson.libraries.resources.generated.resources.nutrition_soft_getting_there
import opson.libraries.resources.generated.resources.nutrition_soft_in_your_range
import opson.libraries.resources.generated.resources.nutrition_soft_most
import opson.libraries.resources.generated.resources.nutrition_soft_most_of_the_way
import opson.libraries.resources.generated.resources.nutrition_soft_partway_through
import opson.libraries.resources.generated.resources.nutrition_soft_plenty
import opson.libraries.resources.generated.resources.nutrition_soft_plenty_today
import opson.libraries.resources.generated.resources.nutrition_soft_rising_slowly
import opson.libraries.resources.generated.resources.nutrition_soft_some
import opson.libraries.resources.generated.resources.nutrition_soft_steady
import opson.libraries.resources.generated.resources.nutrition_soft_trend_only
import opson.libraries.resources.generated.resources.nutrition_unit_g
import opson.libraries.resources.generated.resources.nutrition_unit_kcal
import opson.libraries.resources.generated.resources.nutrition_water_floz
import opson.libraries.resources.generated.resources.nutrition_water_ml
import opson.libraries.resources.generated.resources.nutrition_weight_kg
import opson.libraries.resources.generated.resources.nutrition_weight_lb
import org.jetbrains.compose.resources.StringResource

/**
 * `strings_nutrition.xml` as [NutritionWords], each resource read raw through [get] with its
 * placeholders left in: `getString` in the app, the XML file itself in `NutritionWordsDriftTest`.
 */
suspend fun loadNutritionWords(get: suspend (StringResource) -> String): NutritionWords = NutritionWords(
    energy = get(Res.string.nutrition_energy),
    grams = get(Res.string.nutrition_grams),
    gramsOf = get(Res.string.nutrition_grams_of),
    percent = get(Res.string.nutrition_percent),
    weightKg = get(Res.string.nutrition_weight_kg),
    weightLb = get(Res.string.nutrition_weight_lb),
    waterMl = get(Res.string.nutrition_water_ml),
    waterFlOz = get(Res.string.nutrition_water_floz),
    left = get(Res.string.nutrition_left),
    onTarget = get(Res.string.nutrition_on_target),
    aboveRange = get(Res.string.nutrition_above_range),
    energyOf = get(Res.string.nutrition_energy_of),
    energyOfBare = get(Res.string.nutrition_energy_of_bare),
    energySlash = get(Res.string.nutrition_energy_slash),
    gramsSlash = get(Res.string.nutrition_grams_slash),
    range = get(Res.string.nutrition_range),
    macroLong = get(Res.string.nutrition_macro_long),
    protein = get(Res.string.nutrition_macro_protein),
    carbs = get(Res.string.nutrition_macro_carbs),
    fat = get(Res.string.nutrition_macro_fat),
    fiber = get(Res.string.nutrition_macro_fiber),
    proteinLetter = get(Res.string.nutrition_macro_letter_protein),
    carbsLetter = get(Res.string.nutrition_macro_letter_carbs),
    fatLetter = get(Res.string.nutrition_macro_letter_fat),
    fiberLetter = get(Res.string.nutrition_macro_letter_fiber),
    kcalUnit = get(Res.string.nutrition_unit_kcal),
    gramsUnit = get(Res.string.nutrition_unit_g),
    ringOnTargetValue = get(Res.string.nutrition_ring_on_target_value),
    ringOnTargetUnit = get(Res.string.nutrition_ring_on_target_unit),
    ringAboveRangeValue = get(Res.string.nutrition_ring_above_range_value),
    ringAboveRangeUnit = get(Res.string.nutrition_ring_above_range_unit),
    glassMl = get(Res.string.nutrition_glass_ml),
    glassFlOz = get(Res.string.nutrition_glass_floz),
    microLimit = get(Res.string.nutrition_micro_limit),
    microMg = get(Res.string.nutrition_micro_mg),
    microUg = get(Res.string.nutrition_micro_ug),
    soft = SOFT_WORDS.mapValues { (_, resource) -> get(resource) },
)

private val SOFT_WORDS: Map<SoftWord, StringResource> = mapOf(
    SoftWord.EarlyInTheDay to Res.string.nutrition_soft_early_in_the_day,
    SoftWord.PartwayThrough to Res.string.nutrition_soft_partway_through,
    SoftWord.MostOfTheWay to Res.string.nutrition_soft_most_of_the_way,
    SoftWord.InYourRange to Res.string.nutrition_soft_in_your_range,
    SoftWord.ABiggerDay to Res.string.nutrition_soft_a_bigger_day,
    SoftWord.Building to Res.string.nutrition_soft_building,
    SoftWord.GettingThere to Res.string.nutrition_soft_getting_there,
    SoftWord.PlentyToday to Res.string.nutrition_soft_plenty_today,
    SoftWord.Some to Res.string.nutrition_soft_some,
    SoftWord.Most to Res.string.nutrition_soft_most,
    SoftWord.Plenty to Res.string.nutrition_soft_plenty,
    SoftWord.TrendOnly to Res.string.nutrition_soft_trend_only,
    SoftWord.Steady to Res.string.nutrition_soft_steady,
    SoftWord.RisingSlowly to Res.string.nutrition_soft_rising_slowly,
    SoftWord.FallingSlowly to Res.string.nutrition_soft_falling_slowly,
)

/** `strings_nutrition.xml`'s amount units as [AmountWords], read as [loadNutritionWords] reads its words. */
suspend fun loadAmountWords(get: suspend (StringResource) -> String): AmountWords = AmountWords(
    grams = get(Res.string.nutrition_amount_grams),
    kilograms = get(Res.string.nutrition_amount_kilograms),
    milligrams = get(Res.string.nutrition_amount_milligrams),
    micrograms = get(Res.string.nutrition_amount_micrograms),
    millilitres = get(Res.string.nutrition_amount_millilitres),
    fluidOunces = get(Res.string.nutrition_amount_fluid_ounces),
    ounces = get(Res.string.nutrition_amount_ounces),
    pounds = get(Res.string.nutrition_amount_pounds),
    gramsWord = get(Res.string.nutrition_amount_grams_word),
    kcalSymbol = get(Res.string.nutrition_symbol_kcal),
    gramsSymbol = get(Res.string.nutrition_symbol_grams),
    milligramsSymbol = get(Res.string.nutrition_symbol_milligrams),
    microgramsSymbol = get(Res.string.nutrition_symbol_micrograms),
    millilitresSymbol = get(Res.string.nutrition_symbol_millilitres),
    fluidOuncesSymbol = get(Res.string.nutrition_symbol_fluid_ounces),
    ouncesSymbol = get(Res.string.nutrition_symbol_ounces),
    poundsSymbol = get(Res.string.nutrition_symbol_pounds),
)
