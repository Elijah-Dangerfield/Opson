package com.opson.features.medication.impl.today

import com.opson.libraries.medication.DoseDays
import com.opson.libraries.medication.DoseSchedule
import com.opson.libraries.medication.DoseStatus
import com.opson.libraries.medication.InjectionSite
import com.opson.libraries.medication.MedicationAccess
import com.opson.libraries.medication.MedicationDose
import com.opson.libraries.medication.MedicationPlan
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

/**
 * Today's Medication card (spec, Screens 3). No dose amount anywhere on it, no count of anything,
 * and no red: a dose not logged is said plainly, with Skipped beside Log dose.
 */
sealed interface MedicationCard {
    /** "Dose day. Next site: lower left abdomen", or "Dose day" for a pill. Log dose. */
    data class Due(val site: InjectionSite?) : MedicationCard

    /** "Today's dose", for a daily medication. Log dose. */
    data object DailyDue : MedicationCard

    /** "Logged 7:40 PM, upper right thigh". The card opens the dose. */
    data class Logged(val doseId: String, val time: LocalTime, val site: InjectionSite?) : MedicationCard

    /** "Thursday's dose isn't logged". Log dose and Skipped. */
    data class NotLogged(val due: LocalDate) : MedicationCard

    /**
     * "Last dose Thursday. Next on Thursday", or the half there is. Log dose stays as a quiet
     * action. [last] and [next] are days, said as a weekday within the week and a date beyond it.
     */
    data class Between(val last: LocalDate?, val next: LocalDate?, val today: LocalDate) : MedicationCard
}

object MedicationCardBuilder {

    /** The card for [today], or null: flag off, tracking off, or "Show on Today" off. */
    fun build(released: Boolean, plan: MedicationPlan, doses: List<MedicationDose>, today: LocalDate, zone: TimeZone): MedicationCard? {
        if (MedicationAccess.of(released, plan) != MedicationAccess.Tracking || !plan.todayCard) return null
        val medication = plan.medication ?: return null
        return when (val status = DoseDays.status(medication, plan, doses, today)) {
            is DoseStatus.Due -> if (medication.schedule is DoseSchedule.Daily) MedicationCard.DailyDue else MedicationCard.Due(status.nextSite)
            is DoseStatus.Logged -> {
                val local = status.dose.takenAt.toLocalDateTime(zone)
                MedicationCard.Logged(status.dose.id, LocalTime(local.hour, local.minute), status.dose.site)
            }
            is DoseStatus.NotLogged -> MedicationCard.NotLogged(status.due)
            is DoseStatus.Between -> MedicationCard.Between(status.last?.day, status.next, today)
        }
    }

    /** A weekday within the week either side of [today], a date beyond it. */
    fun saysWeekday(day: LocalDate, today: LocalDate): Boolean = kotlin.math.abs(day.daysUntil(today)) < WEEK

    private const val WEEK = 7
}
