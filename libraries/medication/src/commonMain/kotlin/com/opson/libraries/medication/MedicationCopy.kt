package com.opson.libraries.medication

import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.medication_amount_mcg
import opson.libraries.resources.generated.resources.medication_amount_mg
import opson.libraries.resources.generated.resources.medication_amount_units
import opson.libraries.resources.generated.resources.medication_drug_dulaglutide
import opson.libraries.resources.generated.resources.medication_drug_liraglutide
import opson.libraries.resources.generated.resources.medication_drug_other
import opson.libraries.resources.generated.resources.medication_drug_semaglutide
import opson.libraries.resources.generated.resources.medication_drug_tirzepatide
import opson.libraries.resources.generated.resources.medication_product_compounded_semaglutide
import opson.libraries.resources.generated.resources.medication_product_compounded_tirzepatide
import opson.libraries.resources.generated.resources.medication_product_mounjaro
import opson.libraries.resources.generated.resources.medication_product_other
import opson.libraries.resources.generated.resources.medication_product_ozempic
import opson.libraries.resources.generated.resources.medication_product_rybelsus
import opson.libraries.resources.generated.resources.medication_product_saxenda
import opson.libraries.resources.generated.resources.medication_product_trulicity
import opson.libraries.resources.generated.resources.medication_product_victoza
import opson.libraries.resources.generated.resources.medication_product_wegovy
import opson.libraries.resources.generated.resources.medication_product_wegovy_pill
import opson.libraries.resources.generated.resources.medication_product_zepbound
import opson.libraries.resources.generated.resources.medication_severity_mild
import opson.libraries.resources.generated.resources.medication_severity_moderate
import opson.libraries.resources.generated.resources.medication_severity_severe
import opson.libraries.resources.generated.resources.medication_site_abdomen_lower_left
import opson.libraries.resources.generated.resources.medication_site_abdomen_lower_left_in
import opson.libraries.resources.generated.resources.medication_site_abdomen_lower_right
import opson.libraries.resources.generated.resources.medication_site_abdomen_lower_right_in
import opson.libraries.resources.generated.resources.medication_site_abdomen_upper_left
import opson.libraries.resources.generated.resources.medication_site_abdomen_upper_left_in
import opson.libraries.resources.generated.resources.medication_site_abdomen_upper_right
import opson.libraries.resources.generated.resources.medication_site_abdomen_upper_right_in
import opson.libraries.resources.generated.resources.medication_site_arm_left
import opson.libraries.resources.generated.resources.medication_site_arm_left_in
import opson.libraries.resources.generated.resources.medication_site_arm_right
import opson.libraries.resources.generated.resources.medication_site_arm_right_in
import opson.libraries.resources.generated.resources.medication_site_thigh_left
import opson.libraries.resources.generated.resources.medication_site_thigh_left_in
import opson.libraries.resources.generated.resources.medication_site_thigh_right
import opson.libraries.resources.generated.resources.medication_site_thigh_right_in
import opson.libraries.resources.generated.resources.medication_symptom_constipation
import opson.libraries.resources.generated.resources.medication_symptom_diarrhea
import opson.libraries.resources.generated.resources.medication_symptom_dizziness
import opson.libraries.resources.generated.resources.medication_symptom_fatigue
import opson.libraries.resources.generated.resources.medication_symptom_headache
import opson.libraries.resources.generated.resources.medication_symptom_heartburn
import opson.libraries.resources.generated.resources.medication_symptom_injection_site
import opson.libraries.resources.generated.resources.medication_symptom_nausea
import opson.libraries.resources.generated.resources.medication_symptom_other
import opson.libraries.resources.generated.resources.medication_symptom_stomach_pain
import opson.libraries.resources.generated.resources.medication_symptom_vomiting
import opson.libraries.resources.generated.resources.medication_unit_mcg
import opson.libraries.resources.generated.resources.medication_unit_mg
import opson.libraries.resources.generated.resources.medication_unit_units
import org.jetbrains.compose.resources.StringResource
import kotlin.math.abs
import kotlin.math.roundToLong

/** Labels for the medication types, shared by the Medication page, Today, Trends and the summary. */
val MedicationProduct.label: StringResource
    get() = when (this) {
        MedicationProduct.Wegovy -> Res.string.medication_product_wegovy
        MedicationProduct.Ozempic -> Res.string.medication_product_ozempic
        MedicationProduct.Rybelsus -> Res.string.medication_product_rybelsus
        MedicationProduct.WegovyPill -> Res.string.medication_product_wegovy_pill
        MedicationProduct.Zepbound -> Res.string.medication_product_zepbound
        MedicationProduct.Mounjaro -> Res.string.medication_product_mounjaro
        MedicationProduct.Saxenda -> Res.string.medication_product_saxenda
        MedicationProduct.Victoza -> Res.string.medication_product_victoza
        MedicationProduct.Trulicity -> Res.string.medication_product_trulicity
        MedicationProduct.CompoundedSemaglutide -> Res.string.medication_product_compounded_semaglutide
        MedicationProduct.CompoundedTirzepatide -> Res.string.medication_product_compounded_tirzepatide
        MedicationProduct.Other -> Res.string.medication_product_other
    }

val MedicationDrug.label: StringResource
    get() = when (this) {
        MedicationDrug.Semaglutide -> Res.string.medication_drug_semaglutide
        MedicationDrug.Tirzepatide -> Res.string.medication_drug_tirzepatide
        MedicationDrug.Liraglutide -> Res.string.medication_drug_liraglutide
        MedicationDrug.Dulaglutide -> Res.string.medication_drug_dulaglutide
        MedicationDrug.Other -> Res.string.medication_drug_other
    }

/** "Upper left abdomen", for a label on its own. */
val InjectionSite.label: StringResource
    get() = when (this) {
        InjectionSite.AbdomenUpperLeft -> Res.string.medication_site_abdomen_upper_left
        InjectionSite.AbdomenUpperRight -> Res.string.medication_site_abdomen_upper_right
        InjectionSite.AbdomenLowerLeft -> Res.string.medication_site_abdomen_lower_left
        InjectionSite.AbdomenLowerRight -> Res.string.medication_site_abdomen_lower_right
        InjectionSite.ThighLeft -> Res.string.medication_site_thigh_left
        InjectionSite.ThighRight -> Res.string.medication_site_thigh_right
        InjectionSite.ArmLeft -> Res.string.medication_site_arm_left
        InjectionSite.ArmRight -> Res.string.medication_site_arm_right
    }

/** "upper left abdomen", inside a sentence. */
val InjectionSite.inSentence: StringResource
    get() = when (this) {
        InjectionSite.AbdomenUpperLeft -> Res.string.medication_site_abdomen_upper_left_in
        InjectionSite.AbdomenUpperRight -> Res.string.medication_site_abdomen_upper_right_in
        InjectionSite.AbdomenLowerLeft -> Res.string.medication_site_abdomen_lower_left_in
        InjectionSite.AbdomenLowerRight -> Res.string.medication_site_abdomen_lower_right_in
        InjectionSite.ThighLeft -> Res.string.medication_site_thigh_left_in
        InjectionSite.ThighRight -> Res.string.medication_site_thigh_right_in
        InjectionSite.ArmLeft -> Res.string.medication_site_arm_left_in
        InjectionSite.ArmRight -> Res.string.medication_site_arm_right_in
    }

val Symptom.label: StringResource
    get() = when (this) {
        Symptom.Nausea -> Res.string.medication_symptom_nausea
        Symptom.Vomiting -> Res.string.medication_symptom_vomiting
        Symptom.Constipation -> Res.string.medication_symptom_constipation
        Symptom.Diarrhea -> Res.string.medication_symptom_diarrhea
        Symptom.Heartburn -> Res.string.medication_symptom_heartburn
        Symptom.StomachPain -> Res.string.medication_symptom_stomach_pain
        Symptom.Fatigue -> Res.string.medication_symptom_fatigue
        Symptom.Headache -> Res.string.medication_symptom_headache
        Symptom.Dizziness -> Res.string.medication_symptom_dizziness
        Symptom.InjectionSite -> Res.string.medication_symptom_injection_site
        Symptom.Other -> Res.string.medication_symptom_other
    }

val Severity.label: StringResource
    get() = when (this) {
        Severity.Mild -> Res.string.medication_severity_mild
        Severity.Moderate -> Res.string.medication_severity_moderate
        Severity.Severe -> Res.string.medication_severity_severe
    }

val DoseUnit.label: StringResource
    get() = when (this) {
        DoseUnit.Mg -> Res.string.medication_unit_mg
        DoseUnit.Units -> Res.string.medication_unit_units
        DoseUnit.Mcg -> Res.string.medication_unit_mcg
    }

/** "%1$s mg", for [DoseFormat.number]. */
val DoseUnit.amount: StringResource
    get() = when (this) {
        DoseUnit.Mg -> Res.string.medication_amount_mg
        DoseUnit.Units -> Res.string.medication_amount_units
        DoseUnit.Mcg -> Res.string.medication_amount_mcg
    }

object DoseFormat {
    /**
     * Up to three decimal places, trailing zeros dropped: `2.5`, `0.25`, `15`, `0.125`. By hand,
     * like the nutrition formatter, so Android and iOS print the same whatever the locale.
     */
    fun number(dose: Double): String {
        val thousandths = (dose * THOUSAND).roundToLong()
        val whole = abs(thousandths) / THOUSAND.toLong()
        val fraction = (abs(thousandths) % THOUSAND.toLong()).toString().padStart(3, '0').trimEnd('0')
        val sign = if (thousandths < 0) "-" else ""
        return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole.$fraction"
    }

    /** What someone typed, as a dose: a comma reads as a decimal point. Null when it is not a number. */
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    private const val THOUSAND = 1000.0
}

/** The name a medication goes by: the person's own for compounded and other medicines. */
fun Medication.displayName(): MedicationName = MedicationName.of(product, brand)

fun MedicationDose.displayName(): MedicationName = MedicationName.of(product, brand)

/** A listed product's label, or the person's own words. */
sealed interface MedicationName {
    data class Listed(val label: StringResource) : MedicationName

    data class Own(val text: String) : MedicationName

    companion object {
        fun of(product: MedicationProduct, brand: String?): MedicationName =
            brand?.trim()?.takeIf { product.named && it.isNotEmpty() }?.let(::Own) ?: Listed(product.label)
    }
}
