package com.opson.features.medication.impl

import com.opson.libraries.medication.DoseDraft
import com.opson.libraries.medication.DoseFormat
import com.opson.libraries.medication.DoseSchedule
import com.opson.libraries.medication.DoseUnit
import com.opson.libraries.medication.InjectionSite
import com.opson.libraries.medication.MedicationAccess
import com.opson.libraries.medication.MedicationDose
import com.opson.libraries.medication.MedicationForm
import com.opson.libraries.medication.MedicationName
import com.opson.libraries.medication.MedicationPlan
import com.opson.libraries.medication.MedicationProduct
import com.opson.libraries.medication.Severity
import com.opson.libraries.medication.Symptom
import com.opson.libraries.medication.Symptoms
import com.opson.libraries.medication.displayName
import com.opson.libraries.reminders.NotificationPermission
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** More › Medication. [page] is null until the plan and doses have been read. */
data class MedicationState(val page: MedicationPage? = null)

sealed interface MedicationPage {
    /** `release.medication` is off. Nothing links here then; this is for a stale back stack. */
    data object Hidden : MedicationPage

    /** First visit: what this is, and Set up. [picking] once Set up was tapped: which medication. */
    data class Intro(val picking: Boolean) : MedicationPage

    /** Tracking was stopped. The history stays, and so does the way back. */
    data class Off(val name: MedicationName, val history: List<DoseRow>) : MedicationPage

    data class Tracking(
        val name: MedicationName,
        val schedule: ScheduleSummary,
        val feeling: Feeling,
        val history: List<DoseRow>,
        val setup: Setup,
        /** Null for pills, which have no site. */
        val sites: Set<InjectionSite>?,
        val showOnToday: Boolean,
    ) : MedicationPage
}

enum class ScheduleKind { Weekly, Daily, AsNeeded }

/** "Weekly on Thursday at 9:00 AM". */
data class ScheduleSummary(val kind: ScheduleKind, val day: DayOfWeek?, val time: LocalTime?)

/** Today's side effects, and whether to show the line about getting care. */
data class Feeling(val symptoms: Map<Symptom, Severity>, val needCare: Boolean)

/** One dose in History: "Thu, Oct 23 · 7:40 PM" over "1 mg, upper left abdomen". */
data class DoseRow(
    val id: String,
    val day: LocalDate,
    val time: LocalTime,
    val dose: Double,
    val unit: DoseUnit,
    val site: InjectionSite?,
)

/**
 * The medication and its schedule, as the page edits them. [doseText] and [name] are what is in
 * the fields, which can be ahead of the plan while someone is typing.
 *
 * @property reminder null while `release.reminders` is off, or for a medication taken when needed.
 */
data class Setup(
    val product: MedicationProduct,
    val name: String,
    val form: MedicationForm,
    val doseText: String,
    val unit: DoseUnit,
    val schedule: ScheduleSummary,
    val reminder: ReminderRow?,
    val pickingTime: Boolean,
) {
    val chips: List<Double> get() = product.chips
    val units: List<DoseUnit> get() = product.units
    val formChoice: Boolean get() = product.eitherForm

    /** Typed but not a dose: the field says so rather than silently keeping the old one. */
    val doseInvalid: Boolean get() = doseText.isNotBlank() && DoseFormat.parse(doseText)?.let { it > 0.0 && it <= DoseDraft.MAX_DOSE } != true
}

data class ReminderRow(val on: Boolean, val denied: Boolean)

/** Everything the page is built from. */
data class MedicationInputs(
    val released: Boolean,
    val plan: MedicationPlan,
    /** Newest first. */
    val doses: List<MedicationDose>,
    val symptoms: Map<Symptom, Severity>,
    val zone: TimeZone,
    val remindersReleased: Boolean,
    val permission: NotificationPermission = NotificationPermission.Granted,
)

/** The page's own choices, which no repository holds. A null text follows the plan. */
data class MedicationUi(
    val settingUp: Boolean = false,
    val doseText: String? = null,
    val nameText: String? = null,
    val pickingTime: Boolean = false,
)

object MedicationStateBuilder {

    fun build(inputs: MedicationInputs, ui: MedicationUi): MedicationState {
        val plan = inputs.plan
        val medication = plan.medication
        val page = when {
            MedicationAccess.of(inputs.released, plan) == MedicationAccess.Hidden -> MedicationPage.Hidden
            medication == null -> MedicationPage.Intro(picking = ui.settingUp)
            !plan.on -> MedicationPage.Off(medication.displayName(), history(inputs))
            else -> MedicationPage.Tracking(
                name = medication.displayName(),
                schedule = summary(medication.schedule),
                feeling = Feeling(inputs.symptoms, Symptoms.needCare(inputs.symptoms)),
                history = history(inputs),
                setup = Setup(
                    product = medication.product,
                    name = ui.nameText ?: medication.brand.orEmpty(),
                    form = medication.form,
                    doseText = ui.doseText ?: medication.currentDose?.let(DoseFormat::number).orEmpty(),
                    unit = medication.doseUnit,
                    schedule = summary(medication.schedule),
                    reminder = if (inputs.remindersReleased && medication.schedule.reminderTime != null) {
                        ReminderRow(on = medication.reminder, denied = medication.reminder && inputs.permission == NotificationPermission.Denied)
                    } else {
                        null
                    },
                    pickingTime = ui.pickingTime,
                ),
                sites = plan.sites.takeIf { medication.injected },
                showOnToday = plan.todayCard,
            )
        }
        return MedicationState(page)
    }

    fun summary(schedule: DoseSchedule): ScheduleSummary = when (schedule) {
        is DoseSchedule.Weekly -> ScheduleSummary(ScheduleKind.Weekly, schedule.day, schedule.time)
        is DoseSchedule.Daily -> ScheduleSummary(ScheduleKind.Daily, null, schedule.time)
        DoseSchedule.AsNeeded -> ScheduleSummary(ScheduleKind.AsNeeded, null, null)
    }

    private fun history(inputs: MedicationInputs): List<DoseRow> = inputs.doses.map { dose ->
        DoseRow(
            id = dose.id,
            day = dose.day,
            time = dose.takenAt.toLocalDateTime(inputs.zone).time,
            dose = dose.dose,
            unit = dose.unit,
            site = dose.site,
        )
    }
}
