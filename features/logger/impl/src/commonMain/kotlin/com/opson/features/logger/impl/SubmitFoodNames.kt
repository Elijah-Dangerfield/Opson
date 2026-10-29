package com.opson.features.logger.impl

import androidx.compose.runtime.Composable
import com.opson.libraries.foods.LabelNutrient
import com.opson.libraries.foods.LabelUnit
import com.opson.libraries.foods.ServingUnit
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.submit_n_added_sugars_g
import opson.libraries.resources.generated.resources.submit_n_calcium_mg
import opson.libraries.resources.generated.resources.submit_n_carbs_g
import opson.libraries.resources.generated.resources.submit_n_cholesterol_mg
import opson.libraries.resources.generated.resources.submit_n_choline_mg
import opson.libraries.resources.generated.resources.submit_n_copper_mg
import opson.libraries.resources.generated.resources.submit_n_energy_kcal
import opson.libraries.resources.generated.resources.submit_n_fat_g
import opson.libraries.resources.generated.resources.submit_n_fiber_g
import opson.libraries.resources.generated.resources.submit_n_folate_dfe_ug
import opson.libraries.resources.generated.resources.submit_n_iron_mg
import opson.libraries.resources.generated.resources.submit_n_magnesium_mg
import opson.libraries.resources.generated.resources.submit_n_manganese_mg
import opson.libraries.resources.generated.resources.submit_n_niacin_mg
import opson.libraries.resources.generated.resources.submit_n_phosphorus_mg
import opson.libraries.resources.generated.resources.submit_n_potassium_mg
import opson.libraries.resources.generated.resources.submit_n_protein_g
import opson.libraries.resources.generated.resources.submit_n_riboflavin_mg
import opson.libraries.resources.generated.resources.submit_n_saturated_fat_g
import opson.libraries.resources.generated.resources.submit_n_selenium_ug
import opson.libraries.resources.generated.resources.submit_n_sodium_mg
import opson.libraries.resources.generated.resources.submit_n_sugar_alcohols_g
import opson.libraries.resources.generated.resources.submit_n_sugars_g
import opson.libraries.resources.generated.resources.submit_n_thiamin_mg
import opson.libraries.resources.generated.resources.submit_n_trans_fat_g
import opson.libraries.resources.generated.resources.submit_n_vitamin_a_rae_ug
import opson.libraries.resources.generated.resources.submit_n_vitamin_b12_ug
import opson.libraries.resources.generated.resources.submit_n_vitamin_b6_mg
import opson.libraries.resources.generated.resources.submit_n_vitamin_c_mg
import opson.libraries.resources.generated.resources.submit_n_vitamin_d_ug
import opson.libraries.resources.generated.resources.submit_n_vitamin_e_mg
import opson.libraries.resources.generated.resources.submit_n_vitamin_k_ug
import opson.libraries.resources.generated.resources.submit_n_zinc_mg
import opson.libraries.resources.generated.resources.submit_amount_unit
import opson.libraries.resources.generated.resources.submit_unit_g
import opson.libraries.resources.generated.resources.submit_unit_kcal
import opson.libraries.resources.generated.resources.submit_unit_mcg
import opson.libraries.resources.generated.resources.submit_unit_mg
import opson.libraries.resources.generated.resources.submit_unit_ml
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A label unit as the form writes it beside a figure. [LabelUnit.symbol] is the wire spelling. */
val LabelUnit.label: StringResource
    get() = when (this) {
        LabelUnit.Kcal -> Res.string.submit_unit_kcal
        LabelUnit.Gram -> Res.string.submit_unit_g
        LabelUnit.Milligram -> Res.string.submit_unit_mg
        LabelUnit.Microgram -> Res.string.submit_unit_mcg
    }

/** A serving unit as the form writes it. [ServingUnit.wire] is what the server stores. */
val ServingUnit.label: StringResource
    get() = when (this) {
        ServingUnit.Gram -> Res.string.submit_unit_g
        ServingUnit.Milliliter -> Res.string.submit_unit_ml
    }

/** "234 kcal", or the figure alone when it has no unit. */
@Composable
fun UnitAmount.text(): String = unit?.let { stringResource(Res.string.submit_amount_unit, number, stringResource(it)) } ?: number

/** A nutrient's name as the form labels it. */
val LabelNutrient.title: StringResource
    get() = when (this) {
        LabelNutrient.Energy -> Res.string.submit_n_energy_kcal
        LabelNutrient.Protein -> Res.string.submit_n_protein_g
        LabelNutrient.Carbs -> Res.string.submit_n_carbs_g
        LabelNutrient.Fat -> Res.string.submit_n_fat_g
        LabelNutrient.Fiber -> Res.string.submit_n_fiber_g
        LabelNutrient.Sugars -> Res.string.submit_n_sugars_g
        LabelNutrient.AddedSugars -> Res.string.submit_n_added_sugars_g
        LabelNutrient.SugarAlcohols -> Res.string.submit_n_sugar_alcohols_g
        LabelNutrient.SaturatedFat -> Res.string.submit_n_saturated_fat_g
        LabelNutrient.TransFat -> Res.string.submit_n_trans_fat_g
        LabelNutrient.Cholesterol -> Res.string.submit_n_cholesterol_mg
        LabelNutrient.Sodium -> Res.string.submit_n_sodium_mg
        LabelNutrient.Potassium -> Res.string.submit_n_potassium_mg
        LabelNutrient.Calcium -> Res.string.submit_n_calcium_mg
        LabelNutrient.Iron -> Res.string.submit_n_iron_mg
        LabelNutrient.Magnesium -> Res.string.submit_n_magnesium_mg
        LabelNutrient.Phosphorus -> Res.string.submit_n_phosphorus_mg
        LabelNutrient.Zinc -> Res.string.submit_n_zinc_mg
        LabelNutrient.Copper -> Res.string.submit_n_copper_mg
        LabelNutrient.Manganese -> Res.string.submit_n_manganese_mg
        LabelNutrient.Selenium -> Res.string.submit_n_selenium_ug
        LabelNutrient.VitaminA -> Res.string.submit_n_vitamin_a_rae_ug
        LabelNutrient.VitaminC -> Res.string.submit_n_vitamin_c_mg
        LabelNutrient.VitaminD -> Res.string.submit_n_vitamin_d_ug
        LabelNutrient.VitaminE -> Res.string.submit_n_vitamin_e_mg
        LabelNutrient.VitaminK -> Res.string.submit_n_vitamin_k_ug
        LabelNutrient.Thiamin -> Res.string.submit_n_thiamin_mg
        LabelNutrient.Riboflavin -> Res.string.submit_n_riboflavin_mg
        LabelNutrient.Niacin -> Res.string.submit_n_niacin_mg
        LabelNutrient.VitaminB6 -> Res.string.submit_n_vitamin_b6_mg
        LabelNutrient.Folate -> Res.string.submit_n_folate_dfe_ug
        LabelNutrient.VitaminB12 -> Res.string.submit_n_vitamin_b12_ug
        LabelNutrient.Choline -> Res.string.submit_n_choline_mg
    }
