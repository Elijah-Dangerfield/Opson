package com.opson.features.settings.impl.summary

import com.opson.features.settings.impl.FakePolicySource
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.integrations.FakeCalendar
import com.opson.features.settings.impl.integrations.FakeDiary
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.medication.MedicationRecord
import com.opson.libraries.medication.MedicationRecords
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.pdf.PdfDocument
import com.opson.libraries.pdf.PdfWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The provider summary's Medication section (`release.medication`). */
class ProviderSummaryMedicationTest : CoroutineTest() {

    private val record = ProviderSummaryFixtures.medication
    private val policy = ProviderSummaryFixtures.policy(PresetId.NoNumbers)

    @Test
    fun withNoMedicationThereIsNoSwitch() {
        val state = ProviderSummaryStateBuilder.build(ProviderSummaryFixtures.input(policy), policy)
        assertTrue(state.parts.none { it.part == SummaryPart.Medication })
    }

    @Test
    fun theSectionListsTheDosesTheChangeAndTheSideEffectDays() {
        val week = SummaryRange.last(7, ProviderSummaryFixtures.today)
        val content = SummaryContentBuilder.build(week, SummaryChoices(medication = true), emptyList(), emptyList(), TimeZone.UTC, record)
        val medication = checkNotNull(content.medication)
        assertEquals(listOf(0.5), medication.doses.map { it.dose }, "only the dose in the range")
        assertEquals(listOf(0.5), medication.changes.map { it.dose }, "the step up from 0.25, read across the range's edge")
        assertEquals(1, medication.sideEffects.size)
        assertNull(SummaryContentBuilder.build(week, SummaryChoices(), emptyList(), emptyList(), TimeZone.UTC, record).medication)
    }

    @Test
    fun theLinesNameTheDoseTheSiteAndHowBadEvenInNoNumbers() = runUnitTest {
        val keys = SummaryStrings { resource, args -> "${resource.key}(${args.joinToString(", ")})" }
        val lines = ProviderSummaryFixtures.medicationState(policy).preview.flatMap { line -> line.parts.map { it.text.resolve(keys) } }.joinToString("\n")
        assertTrue("summary_doc_medication" in lines, lines)
        assertTrue("medication_site_abdomen_lower_right_in" in lines, lines)
        assertTrue("summary_doc_medication_changed" in lines, lines)
        assertTrue("medication_severity_moderate" in lines, lines)
    }

    @Test
    fun medicationStartsInWhenItIsTracked_andStaysAsThePersonSwitchedIt() = runUnitTest {
        val records = MutableStateFlow<MedicationRecord?>(record)
        val vm = vm(object : MedicationRecords {
            override fun observe(from: LocalDate, to: LocalDate): Flow<MedicationRecord?> = records
        })
        assertTrue(vm.state.parts.single { it.part == SummaryPart.Medication }.on)

        vm.takeAction(ProviderSummaryAction.PartToggled(SummaryPart.Medication))
        assertFalse(vm.state.parts.single { it.part == SummaryPart.Medication }.on)
        vm.takeAction(ProviderSummaryAction.RangePicked(SummaryRangeChoice.Week))
        assertFalse(vm.state.parts.single { it.part == SummaryPart.Medication }.on, "a new range does not switch it back on")
    }

    @Test
    fun aStoppedMedicationIsOfferedButStartsOut() = runUnitTest {
        val vm = vm(object : MedicationRecords {
            override fun observe(from: LocalDate, to: LocalDate): Flow<MedicationRecord?> = flowOf(record.copy(tracking = false))
        })
        assertFalse(vm.state.parts.single { it.part == SummaryPart.Medication }.on)
    }

    private fun vm(records: MedicationRecords) = ProviderSummaryViewModel(
        diary = FakeDiary(ProviderSummaryFixtures.days),
        weights = NoWeights,
        settings = FakeSettingsRepository(SettingsFixtures.settings(numbers = NumbersSettings(preset = PresetId.NoNumbers))),
        policies = FakePolicySource(policy),
        calendar = FakeCalendar(ProviderSummaryFixtures.today),
        clock = SettingsFixtures.clock,
        pdfWriter = NoPdf,
        strings = SummaryStrings { resource, _ -> resource.key },
        dispatchers = dispatchers,
        medication = records,
    )

    private object NoWeights : WeightRepository {
        override fun observeWeighIns(since: Instant?): Flow<List<WeighIn>> = flowOf(emptyList())
        override suspend fun addWeighIn(kg: Double, measuredAt: Instant?): WeighIn = error("not written here")
        override suspend fun removeWeighIn(id: String) = error("not written here")
    }

    private object NoPdf : PdfWriter {
        override fun write(document: PdfDocument): ByteArray = ByteArray(0)
    }
}
