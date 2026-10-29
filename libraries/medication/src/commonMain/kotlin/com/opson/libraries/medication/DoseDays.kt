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
 * Each weekly dose is for exactly one dose day. A dose late for a dose day nothing covered yet is
 * for that day, however near the next one it falls. Otherwise a dose up to [EARLY_DAYS] days
 * before a dose day is an early dose for it. People shift a weekly shot by a day or two all the
 * time, and a card still saying "Dose day" the morning after an early dose would be wrong. But a
 * Friday dose after a missed Monday is that Monday's, so the next Monday is still a dose day and
 * keeps its reminder. A dose day before the medication was set up here is never waiting for a
 * late dose. A daily dose is for its own day.
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

    /** Whether a dose of [medication] logged on or before [upTo] is for the dose day [due]. */
    fun covered(medication: Medication, due: LocalDate, doses: List<MedicationDose>, upTo: LocalDate = due): Boolean =
        due in coveredDays(medication, doses, upTo)

    /** The dose days [medication]'s doses up to [upTo] are for, each dose for one day at most. */
    private fun coveredDays(medication: Medication, doses: List<MedicationDose>, upTo: LocalDate): Set<LocalDate> {
        val mine = doses.filter { it.medicationId == medication.id && it.day <= upTo }
        val schedule = medication.schedule as? DoseSchedule.Weekly ?: return mine.mapTo(mutableSetOf()) { it.day }
        val covered = mutableSetOf<LocalDate>()
        mine.sortedWith(compareBy({ it.day }, { it.takenAt })).forEach { dose ->
            val before = checkNotNull(lastDue(schedule, dose.day))
            val after = before.plus(DatePeriod(days = WEEK))
            when {
                before >= medication.addedOn && before !in covered -> covered += before
                dose.day >= after.minus(DatePeriod(days = EARLY_DAYS)) -> covered += after
                // A second dose for a day already covered: it covers nothing more.
            }
        }
        return covered
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
                    covered(medication, due, mine, upTo = today) -> DoseStatus.Between(last, next)
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
            .map { date -> DueDay(date, time, covered(medication, date, doses)) }
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
