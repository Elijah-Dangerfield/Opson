package com.opson.libraries.diary.ui

import com.opson.libraries.nutrition.MicroGroup
import com.opson.libraries.nutrition.Micronutrient
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.trends_micro_calcium
import opson.libraries.resources.generated.resources.trends_micro_choline
import opson.libraries.resources.generated.resources.trends_micro_copper
import opson.libraries.resources.generated.resources.trends_micro_folate
import opson.libraries.resources.generated.resources.trends_micro_iron
import opson.libraries.resources.generated.resources.trends_micro_magnesium
import opson.libraries.resources.generated.resources.trends_micro_manganese
import opson.libraries.resources.generated.resources.trends_micro_niacin
import opson.libraries.resources.generated.resources.trends_micro_phosphorus
import opson.libraries.resources.generated.resources.trends_micro_potassium
import opson.libraries.resources.generated.resources.trends_micro_riboflavin
import opson.libraries.resources.generated.resources.trends_micro_selenium
import opson.libraries.resources.generated.resources.trends_micro_sodium
import opson.libraries.resources.generated.resources.trends_micro_thiamin
import opson.libraries.resources.generated.resources.trends_micro_vitamin_a
import opson.libraries.resources.generated.resources.trends_micro_vitamin_b12
import opson.libraries.resources.generated.resources.trends_micro_vitamin_b6
import opson.libraries.resources.generated.resources.trends_micro_vitamin_c
import opson.libraries.resources.generated.resources.trends_micro_vitamin_d
import opson.libraries.resources.generated.resources.trends_micro_vitamin_e
import opson.libraries.resources.generated.resources.trends_micro_vitamin_k
import opson.libraries.resources.generated.resources.trends_micro_zinc
import opson.libraries.resources.generated.resources.trends_micros_minerals
import opson.libraries.resources.generated.resources.trends_micros_vitamins
import org.jetbrains.compose.resources.StringResource

/** A vitamin or mineral's name as a row title: "Vitamin D", "Iron". Shared by Today, Nutrients and settings. */
val Micronutrient.label: StringResource
    get() = when (this) {
        Micronutrient.VitaminA -> Res.string.trends_micro_vitamin_a
        Micronutrient.VitaminB12 -> Res.string.trends_micro_vitamin_b12
        Micronutrient.VitaminC -> Res.string.trends_micro_vitamin_c
        Micronutrient.VitaminD -> Res.string.trends_micro_vitamin_d
        Micronutrient.VitaminE -> Res.string.trends_micro_vitamin_e
        Micronutrient.VitaminK -> Res.string.trends_micro_vitamin_k
        Micronutrient.Folate -> Res.string.trends_micro_folate
        Micronutrient.Calcium -> Res.string.trends_micro_calcium
        Micronutrient.Iron -> Res.string.trends_micro_iron
        Micronutrient.Magnesium -> Res.string.trends_micro_magnesium
        Micronutrient.Potassium -> Res.string.trends_micro_potassium
        Micronutrient.Sodium -> Res.string.trends_micro_sodium
        Micronutrient.Zinc -> Res.string.trends_micro_zinc
        Micronutrient.Thiamin -> Res.string.trends_micro_thiamin
        Micronutrient.Riboflavin -> Res.string.trends_micro_riboflavin
        Micronutrient.Niacin -> Res.string.trends_micro_niacin
        Micronutrient.VitaminB6 -> Res.string.trends_micro_vitamin_b6
        Micronutrient.Choline -> Res.string.trends_micro_choline
        Micronutrient.Copper -> Res.string.trends_micro_copper
        Micronutrient.Manganese -> Res.string.trends_micro_manganese
        Micronutrient.Phosphorus -> Res.string.trends_micro_phosphorus
        Micronutrient.Selenium -> Res.string.trends_micro_selenium
    }

val MicroGroup.label: StringResource
    get() = when (this) {
        MicroGroup.Vitamins -> Res.string.trends_micros_vitamins
        MicroGroup.Minerals -> Res.string.trends_micros_minerals
    }
