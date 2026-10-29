package com.opson.libraries.medication

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class DoseDaysTest {

    // Thursday, October 29 2026.
    private val thursday = LocalDate(2026, 10, 29)
    private val weekly = medication(DoseSchedule.Weekly(DayOfWeek.THURSDAY, LocalTime(19, 0)))
    private val plan = MedicationPlan(on = true, medications = listOf(weekly))

    @Test
    fun aWeeklyDoseDayWithNothingLoggedIsDueWithTheNextSite() {
        val status = DoseDays.status(weekly, plan, emptyList(), thursday)
        assertEquals(DoseStatus.Due(InjectionSite.AbdomenUpperLeft), status)
    }

    @Test
    fun aDoseLoggedTodayIsLogged() {
        val dose = dose(thursday, site = InjectionSite.ThighRight)
        assertEquals(DoseStatus.Logged(dose), DoseDays.status(weekly, plan, listOf(dose), thursday))
    }

    @Test
    fun aDoseTakenAFewDaysEarlyCoversTheDoseDay() {
        val tuesday = dose(LocalDate(2026, 10, 27))
        val status = DoseDays.status(weekly, plan, listOf(tuesday), thursday)
        assertEquals(DoseStatus.Between(tuesday, next = LocalDate(2026, 11, 5)), status)
    }

    @Test
    fun aDoseTooEarlyToCountLeavesTheDayDue() {
        val sunday = dose(LocalDate(2026, 10, 25))
        assertIs<DoseStatus.Due>(DoseDays.status(weekly, plan, listOf(sunday), thursday))
    }

    @Test
    fun thePassedDoseDayWithNoDoseIsSaidPlainly() {
        val saturday = LocalDate(2026, 10, 31)
        assertEquals(DoseStatus.NotLogged(thursday, InjectionSite.AbdomenUpperLeft), DoseDays.status(weekly, plan, emptyList(), saturday))
    }

    @Test
    fun aLateDoseCoversTheDayItWasFor() {
        val saturday = LocalDate(2026, 10, 31)
        val friday = dose(LocalDate(2026, 10, 30))
        assertEquals(DoseStatus.Between(friday, next = LocalDate(2026, 11, 5)), DoseDays.status(weekly, plan, listOf(friday), saturday))
    }

    @Test
    fun skippedHidesThePromptUntilTheNextDoseDay() {
        val saturday = LocalDate(2026, 10, 31)
        val skipped = plan.copy(skippedDue = thursday)
        assertEquals(DoseStatus.Between(null, next = LocalDate(2026, 11, 5)), DoseDays.status(weekly, skipped, emptyList(), saturday))
        val nextThursday = LocalDate(2026, 11, 5)
        assertIs<DoseStatus.Due>(DoseDays.status(weekly, skipped, emptyList(), nextThursday))
    }

    @Test
    fun aDoseDayBeforeSetUpIsNeverNotLogged() {
        val setUpSaturday = weekly.copy(addedOn = LocalDate(2026, 10, 31))
        val status = DoseDays.status(setUpSaturday, plan.copy(medications = listOf(setUpSaturday)), emptyList(), LocalDate(2026, 11, 2))
        assertEquals(DoseStatus.Between(null, next = LocalDate(2026, 11, 5)), status)
    }

    @Test
    fun aDailyMedicationIsDueUntilLogged() {
        val daily = medication(DoseSchedule.Daily(LocalTime(8, 0)), product = MedicationProduct.Rybelsus)
        val dailyPlan = plan.copy(medications = listOf(daily))
        assertEquals(DoseStatus.Due(nextSite = null), DoseDays.status(daily, dailyPlan, emptyList(), thursday))
        val dose = dose(thursday, site = null)
        assertEquals(DoseStatus.Logged(dose), DoseDays.status(daily, dailyPlan, listOf(dose), thursday))
    }

    @Test
    fun whenNeededHasNoDoseDays() {
        val asNeeded = medication(DoseSchedule.AsNeeded)
        val last = dose(LocalDate(2026, 10, 20))
        assertEquals(DoseStatus.Between(last, next = null), DoseDays.status(asNeeded, plan.copy(medications = listOf(asNeeded)), listOf(last), thursday))
        assertEquals(emptyList(), DoseDays.upcoming(asNeeded, emptyList(), thursday, days = 7))
    }

    @Test
    fun anotherMedicationsDoseCoversNothing() {
        val other = dose(thursday).copy(medicationId = "other")
        assertIs<DoseStatus.Due>(DoseDays.status(weekly, plan, listOf(other), thursday))
    }

    @Test
    fun upcomingListsDoseDaysWithWhetherADoseCoversThem() {
        val upcoming = DoseDays.upcoming(weekly, listOf(dose(LocalDate(2026, 10, 28))), LocalDate(2026, 10, 26), days = 14)
        assertEquals(
            listOf(
                DoseDays.DueDay(thursday, LocalTime(19, 0), covered = true),
                DoseDays.DueDay(LocalDate(2026, 11, 5), LocalTime(19, 0), covered = false),
            ),
            upcoming,
        )
    }

    @Test
    fun upcomingSkipsDaysBeforeSetUp() {
        val daily = medication(DoseSchedule.Daily(LocalTime(8, 0))).copy(addedOn = thursday)
        val days = DoseDays.upcoming(daily, emptyList(), LocalDate(2026, 10, 27), days = 4).map { it.date }
        assertEquals(listOf(thursday, LocalDate(2026, 10, 30)), days)
    }

    private fun medication(schedule: DoseSchedule, product: MedicationProduct = MedicationProduct.Wegovy) = Medication(
        id = "med",
        product = product,
        schedule = schedule,
        currentDose = 1.0,
        addedOn = LocalDate(2026, 9, 1),
    )
}

/** A dose of `med` at 7 PM UTC on [day]. */
internal fun dose(
    day: LocalDate,
    site: InjectionSite? = InjectionSite.AbdomenLowerLeft,
    amount: Double = 1.0,
    hour: Int = 19,
) = MedicationDose(
    id = "dose-$day-$hour-$site",
    medicationId = "med",
    product = MedicationProduct.Wegovy,
    drug = MedicationDrug.Semaglutide,
    brand = null,
    dose = amount,
    unit = DoseUnit.Mg,
    takenAt = Instant.parse("${day}T${hour.toString().padStart(2, '0')}:00:00Z"),
    day = day,
    site = site,
    note = null,
)
