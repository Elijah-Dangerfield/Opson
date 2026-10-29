package com.opson.libraries.medication

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class MedicationModelTest {

    @Test
    fun theFlagHidesEverythingAndNothingElseDoes() {
        val set = MedicationPlan(on = true, medications = listOf(wegovy))
        assertEquals(MedicationAccess.Hidden, MedicationAccess.of(released = false, plan = set))
        assertEquals(MedicationAccess.Offered, MedicationAccess.of(released = true, plan = MedicationPlan()))
        assertEquals(MedicationAccess.Offered, MedicationAccess.of(released = true, plan = set.copy(on = false)))
        assertEquals(MedicationAccess.Tracking, MedicationAccess.of(released = true, plan = set))
        // On with nothing set up is not tracking: no card, no reminder.
        assertEquals(MedicationAccess.Offered, MedicationAccess.of(released = true, plan = MedicationPlan(on = true)))
    }

    @Test
    fun theNextSiteIsTheEnabledOneUsedLongestAgo() {
        val doses = listOf(
            dose(LocalDate(2026, 10, 1), InjectionSite.AbdomenUpperLeft),
            dose(LocalDate(2026, 10, 8), InjectionSite.AbdomenUpperRight),
            dose(LocalDate(2026, 10, 15), InjectionSite.AbdomenLowerLeft),
        )
        val abdomen = setOf(InjectionSite.AbdomenUpperLeft, InjectionSite.AbdomenUpperRight, InjectionSite.AbdomenLowerLeft)
        assertEquals(InjectionSite.AbdomenUpperLeft, SiteRotation.next(abdomen, doses))
    }

    @Test
    fun aSiteNeverUsedComesFirstAndTiesGoInListOrder() {
        val doses = listOf(dose(LocalDate(2026, 10, 1), InjectionSite.AbdomenUpperLeft))
        assertEquals(InjectionSite.AbdomenUpperRight, SiteRotation.next(InjectionSite.DEFAULT, doses))
        assertEquals(InjectionSite.AbdomenUpperLeft, SiteRotation.next(InjectionSite.DEFAULT, emptyList()))
    }

    @Test
    fun aSiteTurnedOffIsNeverSuggested() {
        val doses = listOf(dose(LocalDate(2026, 10, 1), InjectionSite.ThighLeft))
        assertEquals(InjectionSite.ThighLeft, SiteRotation.next(setOf(InjectionSite.ThighLeft), doses))
        assertNull(SiteRotation.next(emptySet(), doses))
    }

    @Test
    fun theWeightTicksAreTheStartAndEachChangeOfDose() {
        val doses = listOf(
            dose(LocalDate(2026, 9, 3), amount = 0.25),
            dose(LocalDate(2026, 9, 10), amount = 0.25),
            dose(LocalDate(2026, 10, 1), amount = 0.5),
            dose(LocalDate(2026, 10, 8), amount = 0.5),
            dose(LocalDate(2026, 10, 29), amount = 1.0),
        )
        assertEquals(
            listOf(
                DoseMarks.Mark.Started(LocalDate(2026, 9, 3)),
                DoseMarks.Mark.Changed(LocalDate(2026, 10, 1), 0.5, DoseUnit.Mg),
                DoseMarks.Mark.Changed(LocalDate(2026, 10, 29), 1.0, DoseUnit.Mg),
            ),
            DoseMarks.of(wegovy, doses.shuffled()),
        )
        val startedEarlier = wegovy.copy(startedOn = LocalDate(2026, 8, 1))
        assertEquals(DoseMarks.Mark.Started(LocalDate(2026, 8, 1)), DoseMarks.of(startedEarlier, doses).first())
        assertEquals(emptyList(), DoseMarks.of(wegovy, emptyList()))
    }

    @Test
    fun dosesPrintTheirOwnPrecision() {
        assertEquals("2.5", DoseFormat.number(2.5))
        assertEquals("0.25", DoseFormat.number(0.25))
        assertEquals("15", DoseFormat.number(15.0))
        assertEquals("0.125", DoseFormat.number(0.125))
        assertEquals("1.7", DoseFormat.number(1.7))
        assertEquals(2.4, DoseFormat.parse("2,4"))
        assertNull(DoseFormat.parse("two"))
    }

    @Test
    fun aDraftIsRefusedOutsideWhatTheServerWillTake() {
        val draft = DoseDraft("med", 1.0, DoseUnit.Mg, Instant.parse("2026-10-29T19:00:00Z"), InjectionSite.ThighLeft)
        assertNull(draft.problem())
        assertEquals("dose", draft.copy(dose = 0.0).problem())
        assertEquals("dose", draft.copy(dose = 100.5).problem())
        assertEquals("note", draft.copy(note = "x".repeat(DoseDraft.MAX_NOTE + 1)).problem())
    }

    @Test
    fun severeVomitingOrStomachPainCallsForTheCareLine() {
        assertTrue(Symptoms.needCare(mapOf(Symptom.Vomiting to Severity.Severe)))
        assertTrue(Symptoms.needCare(mapOf(Symptom.StomachPain to Severity.Severe, Symptom.Nausea to Severity.Mild)))
        assertEquals(false, Symptoms.needCare(mapOf(Symptom.Nausea to Severity.Severe, Symptom.Vomiting to Severity.Moderate)))
    }

    @Test
    fun thePlanRoundTripsAndAnOldOneDecodesToDefaults() {
        val json = Json { ignoreUnknownKeys = true }
        val plan = MedicationPlan(
            on = true,
            medications = listOf(
                wegovy.copy(schedule = DoseSchedule.Weekly(DayOfWeek.THURSDAY, LocalTime(19, 30)), reminder = true),
            ),
            sites = setOf(InjectionSite.ThighLeft, InjectionSite.ArmRight),
            skippedDue = LocalDate(2026, 10, 22),
        )
        val text = json.encodeToString(MedicationPlan.serializer(), plan)
        assertEquals(plan, json.decodeFromString(MedicationPlan.serializer(), text))
        assertTrue("\"thigh_left\"" in text && "\"weekly\"" in text, text)
        assertEquals(MedicationPlan(), json.decodeFromString(MedicationPlan.serializer(), "{\"aFieldFromLater\":1}"))
    }

    @Test
    fun theNameIsTheListedProductOrThePersonsOwn() {
        assertEquals(MedicationName.Listed(MedicationProduct.Wegovy.label), wegovy.copy(brand = "ignored").displayName())
        val compounded = wegovy.copy(product = MedicationProduct.CompoundedTirzepatide, brand = " Tirz from the clinic ")
        assertEquals(MedicationName.Own("Tirz from the clinic"), compounded.displayName())
        assertEquals(MedicationName.Listed(MedicationProduct.Other.label), wegovy.copy(product = MedicationProduct.Other, brand = " ").displayName())
    }

    private val wegovy = Medication(id = "med", product = MedicationProduct.Wegovy, addedOn = LocalDate(2026, 9, 1))
}
