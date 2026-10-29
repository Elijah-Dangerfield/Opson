package com.opson.features.medication.impl

import com.opson.features.medication.impl.dose.DoseDay
import com.opson.features.medication.impl.dose.DoseStep
import com.opson.features.medication.impl.dose.LogDoseState
import com.opson.libraries.medication.DoseSchedule
import com.opson.libraries.medication.DoseUnit
import com.opson.libraries.medication.InjectionSite
import com.opson.libraries.medication.Medication
import com.opson.libraries.medication.MedicationDose
import com.opson.libraries.medication.MedicationForm
import com.opson.libraries.medication.MedicationName
import com.opson.libraries.medication.MedicationPlan
import com.opson.libraries.medication.MedicationProduct
import com.opson.libraries.medication.Severity
import com.opson.libraries.medication.Symptom
import com.opson.libraries.medication.label
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant

/**
 * Fixed inputs for previews and tests, built through the real builders. Thursday, October 29
 * 2026, on a weekly Wegovy shot stepped up to 1 mg three weeks ago.
 */
object MedicationFixtures {
    val zone: TimeZone = TimeZone.UTC
    val today: LocalDate = LocalDate(2026, 10, 29)

    val wegovy = Medication(
        id = "med",
        product = MedicationProduct.Wegovy,
        currentDose = 1.0,
        schedule = DoseSchedule.Weekly(DayOfWeek.THURSDAY, LocalTime(19, 0)),
        reminder = true,
        addedOn = LocalDate(2026, 9, 1),
    )

    val rybelsus = Medication(
        id = "pill",
        product = MedicationProduct.Rybelsus,
        form = MedicationForm.Pill,
        currentDose = 7.0,
        schedule = DoseSchedule.Daily(LocalTime(8, 0)),
        addedOn = LocalDate(2026, 9, 1),
    )

    fun plan(medication: Medication? = wegovy, on: Boolean = true, todayCard: Boolean = true) = MedicationPlan(
        on = on,
        medications = listOfNotNull(medication),
        sites = InjectionSite.DEFAULT - InjectionSite.ArmLeft - InjectionSite.ArmRight,
        todayCard = todayCard,
    )

    fun dose(daysAgo: Int, amount: Double, site: InjectionSite?, hour: Int = 19, minute: Int = 40, medication: Medication = wegovy): MedicationDose {
        val day = today.minus(DatePeriod(days = daysAgo))
        return MedicationDose(
            id = "dose-$daysAgo",
            medicationId = medication.id,
            product = medication.product,
            drug = medication.drug,
            brand = null,
            dose = amount,
            unit = DoseUnit.Mg,
            takenAt = LocalDateTime(day, LocalTime(hour, minute)).toInstant(zone),
            day = day,
            site = site,
            note = null,
        )
    }

    /** Newest first: last Thursday's 1 mg back to the 0.5 mg weeks. */
    val history: List<MedicationDose> = listOf(
        dose(7, 1.0, InjectionSite.AbdomenUpperRight),
        dose(14, 1.0, InjectionSite.AbdomenUpperLeft),
        dose(21, 1.0, InjectionSite.ThighRight),
        dose(28, 0.5, InjectionSite.ThighLeft),
    )

    fun inputs(
        plan: MedicationPlan = plan(),
        doses: List<MedicationDose> = history,
        symptoms: Map<Symptom, Severity> = emptyMap(),
        released: Boolean = true,
        remindersReleased: Boolean = true,
    ) = MedicationInputs(released, plan, doses, symptoms, zone, remindersReleased)

    fun state(inputs: MedicationInputs = inputs(), ui: MedicationUi = MedicationUi()): MedicationState = MedicationStateBuilder.build(inputs, ui)

    /** A new dose: the current dose, now, and the site used longest ago. */
    fun newDose(): LogDoseState = LogDoseState(
        loading = false,
        name = MedicationName.Listed(MedicationProduct.Wegovy.label),
        doseText = "1",
        unit = DoseUnit.Mg,
        chips = MedicationProduct.Wegovy.chips,
        day = DoseDay.Today,
        time = LocalTime(19, 40),
        site = InjectionSite.AbdomenLowerLeft,
        suggested = InjectionSite.AbdomenLowerLeft,
        sites = plan().sites,
    )

    fun editDose(): LogDoseState = newDose().copy(
        editing = true,
        day = DoseDay.Other(today.minus(DatePeriod(days = 7))),
        site = InjectionSite.AbdomenUpperRight,
        suggested = null,
        note = "A bit sore after",
    )

    fun askCurrent(): LogDoseState = newDose().copy(doseText = "1.7", step = DoseStep.AskCurrent(1.7, DoseUnit.Mg))
}
