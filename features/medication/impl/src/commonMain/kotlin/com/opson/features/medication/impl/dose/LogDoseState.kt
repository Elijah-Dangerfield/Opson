package com.opson.features.medication.impl.dose

import com.opson.libraries.medication.DoseDraft
import com.opson.libraries.medication.DoseFormat
import com.opson.libraries.medication.DoseUnit
import com.opson.libraries.medication.InjectionSite
import com.opson.libraries.medication.MedicationName
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** Which day the dose was on, as the sheet offers it: today, yesterday, or the day an edited dose already had. */
sealed interface DoseDay {
    data object Today : DoseDay

    data object Yesterday : DoseDay

    data class Other(val date: LocalDate) : DoseDay
}

/**
 * The dose sheet. [step] is the form, or the one question after a save whose amount differs from
 * the current dose.
 *
 * @property suggested the site used longest ago, which the sheet starts on for a new dose.
 * @property sites the sites the person uses; null for pills.
 */
data class LogDoseState(
    val loading: Boolean = true,
    val editing: Boolean = false,
    val name: MedicationName? = null,
    val doseText: String = "",
    val unit: DoseUnit = DoseUnit.Mg,
    val chips: List<Double> = emptyList(),
    val day: DoseDay = DoseDay.Today,
    val time: LocalTime = LocalTime(0, 0),
    val site: InjectionSite? = null,
    val suggested: InjectionSite? = null,
    val sites: Set<InjectionSite>? = null,
    val note: String = "",
    val notADose: Boolean = false,
    val failed: Boolean = false,
    val saving: Boolean = false,
    val pickingTime: Boolean = false,
    val step: DoseStep = DoseStep.Form,
) {
    val dose: Double? get() = DoseFormat.parse(doseText)?.takeIf { it > 0.0 && it <= DoseDraft.MAX_DOSE }

    val canSave: Boolean get() = !loading && !saving && dose != null

    /** The yesterday choice only for a new dose or one already on those two days. */
    val dayChoices: List<DoseDay> get() = if (day is DoseDay.Other) listOf(day) else listOf(DoseDay.Today, DoseDay.Yesterday)
}

sealed interface DoseStep {
    data object Form : DoseStep

    /** "Make 5 mg your current dose?" */
    data class AskCurrent(val dose: Double, val unit: DoseUnit) : DoseStep
}
