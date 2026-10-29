package com.opson.libraries.medication

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Who sees medication tracking. A dose is not a food number, so it is offered in every mode,
 * No numbers and "numbers have been hard" included; what doses are drawn next to (the weight
 * chart) follows that surface's own rules.
 */
enum class MedicationAccess {
    /** `release.medication` is off: nothing anywhere. */
    Hidden,

    /** The page is offered; tracking is off or not set up, so no card, reminder or ticks. */
    Offered,

    /** Set up and on: the Today card (if wanted), the reminder (if wanted) and the weight ticks. */
    Tracking,
    ;

    companion object {
        fun of(released: Boolean, plan: MedicationPlan): MedicationAccess = when {
            !released -> Hidden
            plan.tracking -> Tracking
            else -> Offered
        }
    }
}

/** Where a medication stands today, for the Today card. Nothing here counts or scores anything. */
sealed interface DoseStatus {
    /** Today is a dose day and nothing is logged yet. [nextSite] is null for pills. */
    data class Due(val nextSite: InjectionSite?) : DoseStatus

    /** A dose was logged today. */
    data class Logged(val dose: MedicationDose) : DoseStatus

    /** A weekly dose day has passed with no dose near it. Until the next dose day, or Skipped. */
    data class NotLogged(val due: LocalDate, val nextSite: InjectionSite?) : DoseStatus

    /** Between weekly doses, or any day for a medication taken when needed ([next] null). */
    data class Between(val last: MedicationDose?, val next: LocalDate?) : DoseStatus
}

/**
 * Dose days from a schedule, and which of them a logged dose covers. Pure, so the Today card,
 * the reminder plan and their tests read the same rules.
 *
 * A weekly dose logged up to [EARLY_DAYS] days before its day, or any day after it until the next,
 * counts for that dose day. People shift a weekly shot by a day or two all the time, and a card
 * still saying "Dose day" the morning after an early dose would be wrong.
 */
object DoseDays {
    const val EARLY_DAYS = 3
    private const val WEEK = 7

    fun isDue(schedule: DoseSchedule, date: LocalDate): Boolean = when (schedule) {
        is DoseSchedule.Weekly -> date.dayOfWeek.isoDayNumber == schedule.isoDay
        is DoseSchedule.Daily -> true
        DoseSchedule.AsNeeded -> false
    }

    /** The latest dose day on or before [date], or null for a medication taken when needed. */
    fun lastDue(schedule: DoseSchedule, date: LocalDate): LocalDate? = when (schedule) {
        is DoseSchedule.Weekly -> date.minus(DatePeriod(days = (date.dayOfWeek.isoDayNumber - schedule.isoDay + WEEK) % WEEK))
        is DoseSchedule.Daily -> date
        DoseSchedule.AsNeeded -> null
    }

    /** The first dose day after [date]. */
    fun nextDue(schedule: DoseSchedule, date: LocalDate): LocalDate? = when (schedule) {
        is DoseSchedule.Weekly -> lastDue(schedule, date)?.plus(DatePeriod(days = WEEK))
        is DoseSchedule.Daily -> date.plus(DatePeriod(days = 1))
        DoseSchedule.AsNeeded -> null
    }

    /** Whether a dose of [medicationId] logged between [due]'s early window and [upTo] covers [due]. */
    fun covered(schedule: DoseSchedule, due: LocalDate, upTo: LocalDate, medicationId: String, doses: List<MedicationDose>): Boolean {
        val from = if (schedule is DoseSchedule.Weekly) due.minus(DatePeriod(days = EARLY_DAYS)) else due
        return doses.any { it.medicationId == medicationId && it.day >= from && it.day <= upTo }
    }

    /** Today's status for [medication]. [doses] may be in any order and hold other medications'. */
    fun status(medication: Medication, plan: MedicationPlan, doses: List<MedicationDose>, today: LocalDate): DoseStatus {
        val mine = doses.filter { it.medicationId == medication.id }
        mine.filter { it.day == today }.maxByOrNull { it.takenAt }?.let { return DoseStatus.Logged(it) }
        val last = mine.filter { it.day <= today }.maxByOrNull { it.takenAt }
        val site = if (medication.injected) SiteRotation.next(plan.sites, doses) else null
        return when (val schedule = medication.schedule) {
            is DoseSchedule.Daily -> DoseStatus.Due(site)
            DoseSchedule.AsNeeded -> DoseStatus.Between(last, next = null)
            is DoseSchedule.Weekly -> {
                val due = checkNotNull(lastDue(schedule, today))
                val next = nextDue(schedule, today)
                when {
                    covered(schedule, due, today, medication.id, mine) -> DoseStatus.Between(last, next)
                    due == today -> DoseStatus.Due(site)
                    due < medication.addedOn || plan.skippedDue == due -> DoseStatus.Between(last, next)
                    else -> DoseStatus.NotLogged(due, site)
                }
            }
        }
    }

    /** What a reminder plans from: the dose days from [from] for [days] days, each with whether it is covered. */
    fun upcoming(medication: Medication, doses: List<MedicationDose>, from: LocalDate, days: Int): List<DueDay> {
        val time = medication.schedule.reminderTime ?: return emptyList()
        return (0 until days).map { from.plus(DatePeriod(days = it)) }
            .filter { it >= medication.addedOn && isDue(medication.schedule, it) }
            .map { date -> DueDay(date, time, covered(medication.schedule, date, date, medication.id, doses)) }
    }

    data class DueDay(val date: LocalDate, val time: LocalTime, val covered: Boolean)
}

/**
 * The next injection site: the enabled site used longest ago, a site never used counting as
 * oldest. Ties go in [InjectionSite]'s order. No "too soon" warning, ever.
 */
object SiteRotation {
    fun next(enabled: Set<InjectionSite>, doses: List<MedicationDose>): InjectionSite? {
        val lastUsed = doses.filter { it.site != null }.groupBy { it.site }.mapValues { (_, used) -> used.maxOf { it.takenAt } }
        return InjectionSite.entries.filter { it in enabled }.minWithOrNull(
            compareBy<InjectionSite, kotlin.time.Instant?>(nullsFirst()) { lastUsed[it] }.thenBy { it.ordinal },
        )
    }
}

/**
 * The weight chart's ticks: "Started" on the day the medication started (or its first dose), then
 * one at each dose whose amount differs from the dose before it. No projection, nothing about
 * weight.
 */
object DoseMarks {
    sealed interface Mark {
        val day: LocalDate

        data class Started(override val day: LocalDate) : Mark

        data class Changed(override val day: LocalDate, val dose: Double, val unit: DoseUnit) : Mark
    }

    fun of(medication: Medication?, doses: List<MedicationDose>): List<Mark> {
        val mine = doses.filter { medication == null || it.medicationId == medication.id }.sortedBy { it.takenAt }
        val start = medication?.startedOn ?: mine.firstOrNull()?.day ?: return emptyList()
        val changes = mine.zipWithNext().filter { (before, after) -> before.dose != after.dose || before.unit != after.unit }
            .map { (_, after) -> Mark.Changed(after.day, after.dose, after.unit) }
        return listOf(Mark.Started(start)) + changes
    }
}
