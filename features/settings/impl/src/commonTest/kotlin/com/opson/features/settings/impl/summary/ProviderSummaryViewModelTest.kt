package com.opson.features.settings.impl.summary

import com.opson.features.settings.impl.FakePolicySource
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.eventsOf
import com.opson.features.settings.impl.integrations.FakeCalendar
import com.opson.features.settings.impl.integrations.FakeDiary
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.pdf.PdfDocument
import com.opson.libraries.pdf.PdfWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ProviderSummaryViewModelTest : CoroutineTest() {

    private class FakeWeights(weighIns: List<WeighIn>) : WeightRepository {
        val weighIns = MutableStateFlow(weighIns)
        override fun observeWeighIns(since: Instant?): Flow<List<WeighIn>> =
            weighIns.map { all -> all.filter { since == null || it.measuredAt >= since } }
        override suspend fun addWeighIn(kg: Double, measuredAt: Instant?): WeighIn = error("not written here")
        override suspend fun removeWeighIn(id: String) = error("not written here")
    }

    /** Records what it was asked to write, so a test reads the PDF's text. */
    private class FakePdf(var fails: Boolean = false) : PdfWriter {
        val written = mutableListOf<PdfDocument>()
        override fun write(document: PdfDocument): ByteArray {
            if (fails) error("no room")
            written += document
            return "%PDF-".encodeToByteArray()
        }
    }

    private inner class Rig(preset: PresetId) {
        val diary = FakeDiary(ProviderSummaryFixtures.days)
        val pdf = FakePdf()
        val policy = ProviderSummaryFixtures.policy(preset)
        val vm = ProviderSummaryViewModel(
            diary = diary,
            weights = FakeWeights(ProviderSummaryFixtures.weighIns),
            settings = FakeSettingsRepository(SettingsFixtures.settings(numbers = NumbersSettings(preset = preset))),
            policies = FakePolicySource(policy),
            calendar = FakeCalendar(ProviderSummaryFixtures.today),
            clock = SettingsFixtures.clock,
            pdfWriter = pdf,
            strings = SummaryStrings { resource, args -> "${resource.key}(${args.joinToString(", ")})" },
            dispatchers = dispatchers,
        )
    }

    private fun ProviderSummaryState.part(part: SummaryPart) = parts.single { it.part == part }

    @Test
    fun opensOnTheLastTwoWeeks_withTheModesStartingChoices() = runUnitTest {
        val rig = Rig(PresetId.NoNumbers)
        val state = rig.vm.state

        assertEquals(SummaryRangeChoice.TwoWeeks, state.range)
        assertEquals(SummaryRange(LocalDate(2026, 10, 12), LocalDate(2026, 10, 25)), state.dates)
        assertFalse(state.loading, "the days arrived")
        assertFalse(state.part(SummaryPart.Numbers).on)
        assertTrue(state.part(SummaryPart.Numbers).hiddenByMode)
        assertTrue(state.part(SummaryPart.MealsAndTimes).on)
    }

    @Test
    fun switchingInHiddenNumbers_asksFirst_andOnlyOnce() = runUnitTest {
        val rig = Rig(PresetId.NoNumbers)

        rig.vm.takeAction(ProviderSummaryAction.PartToggled(SummaryPart.Numbers))
        assertEquals(SummaryPart.Numbers, rig.vm.state.confirming)
        assertFalse(rig.vm.state.part(SummaryPart.Numbers).on, "not in until the person says so")

        rig.vm.takeAction(ProviderSummaryAction.IncludeHiddenConfirmed)
        assertNull(rig.vm.state.confirming)
        assertTrue(rig.vm.state.part(SummaryPart.Numbers).on)
        assertTrue(rig.vm.state.previewHidesNumbers)
        assertTrue(rig.vm.state.preview.flatMap { it.parts }.none { it.metric != null }, "still nothing on screen")

        rig.vm.takeAction(ProviderSummaryAction.PartToggled(SummaryPart.Weight))
        assertNull(rig.vm.state.confirming, "asked once a visit")
        assertTrue(rig.vm.state.part(SummaryPart.Weight).on)
    }

    @Test
    fun leaveThemOut_keepsThemOut() = runUnitTest {
        val rig = Rig(PresetId.MacrosOnly)

        rig.vm.takeAction(ProviderSummaryAction.PartToggled(SummaryPart.Weight))
        rig.vm.takeAction(ProviderSummaryAction.IncludeHiddenDeclined)

        assertNull(rig.vm.state.confirming)
        assertFalse(rig.vm.state.part(SummaryPart.Weight).on)
    }

    @Test
    fun allNumbers_neverAsks() = runUnitTest {
        val rig = Rig(PresetId.Full)
        rig.vm.takeAction(ProviderSummaryAction.PartToggled(SummaryPart.Numbers))
        rig.vm.takeAction(ProviderSummaryAction.PartToggled(SummaryPart.Numbers))

        assertNull(rig.vm.state.confirming)
        assertTrue(rig.vm.state.part(SummaryPart.Numbers).on, "off and on again")
    }

    @Test
    fun sharing_writesThePdfWithEveryChosenNumber_whateverTheMode() = runUnitTest {
        val rig = Rig(PresetId.NoNumbers)
        val events = eventsOf(rig.vm)
        rig.vm.takeAction(ProviderSummaryAction.RangePicked(SummaryRangeChoice.Week))
        rig.vm.takeAction(ProviderSummaryAction.PartToggled(SummaryPart.Numbers))
        rig.vm.takeAction(ProviderSummaryAction.IncludeHiddenConfirmed)

        rig.vm.takeAction(ProviderSummaryAction.ShareTapped)

        val share = assertIs<ProviderSummaryEvent.SharePdf>(events.single())
        assertEquals("opson-summary-2026-10-19-to-2026-10-25.pdf", share.name)
        val text = rig.pdf.written.single().blocks.joinToString("\n") { it.text }
        assertTrue("summary_doc_calories(575 kcal)" in text, text)
        assertFalse(rig.vm.state.sharing)
    }

    @Test
    fun aFailedPdf_saysSo_andSharesNothing() = runUnitTest {
        val rig = Rig(PresetId.Full)
        val events = eventsOf(rig.vm)
        rig.pdf.fails = true

        rig.vm.takeAction(ProviderSummaryAction.ShareTapped)

        assertTrue(rig.vm.state.failed)
        assertTrue(events.isEmpty())
    }

    @Test
    fun withNothingPicked_thereIsNothingToShare() = runUnitTest {
        val rig = Rig(PresetId.NoNumbers)
        val events = eventsOf(rig.vm)
        listOf(SummaryPart.MealsAndTimes, SummaryPart.Notes, SummaryPart.Hunger, SummaryPart.CheckIns).forEach {
            rig.vm.takeAction(ProviderSummaryAction.PartToggled(it))
        }

        assertTrue(rig.vm.state.nothingPicked)
        assertFalse(rig.vm.state.canShare)
        rig.vm.takeAction(ProviderSummaryAction.ShareTapped)
        assertTrue(events.isEmpty())
    }

    @Test
    fun aRangeChip_readsThoseDays() = runUnitTest {
        val rig = Rig(PresetId.Full)

        rig.vm.takeAction(ProviderSummaryAction.RangePicked(SummaryRangeChoice.FourWeeks))

        assertEquals(SummaryRange(LocalDate(2026, 9, 28), LocalDate(2026, 10, 25)), rig.vm.state.dates)
        assertEquals(LocalDate(2026, 9, 28) to LocalDate(2026, 10, 25), rig.diary.ranges.last())
        assertEquals("Sep 28 to Oct 25, 2026", rig.vm.state.rangeLabel)
    }

    @Test
    fun pickDates_firstTapThenLast_eitherWayRound() = runUnitTest {
        val rig = Rig(PresetId.Full)
        rig.vm.takeAction(ProviderSummaryAction.RangePicked(SummaryRangeChoice.Custom))
        assertEquals(LocalDate(2026, 10, 1), rig.vm.state.calendar?.month)

        rig.vm.takeAction(ProviderSummaryAction.DayTapped(LocalDate(2026, 10, 20)))
        assertEquals(SummaryRange(LocalDate(2026, 10, 20), LocalDate(2026, 10, 20)), rig.vm.state.dates)
        rig.vm.takeAction(ProviderSummaryAction.DayTapped(LocalDate(2026, 10, 6)))
        assertEquals(SummaryRange(LocalDate(2026, 10, 6), LocalDate(2026, 10, 20)), rig.vm.state.dates)

        rig.vm.takeAction(ProviderSummaryAction.DayTapped(LocalDate(2026, 10, 30)))
        assertEquals(SummaryRange(LocalDate(2026, 10, 6), LocalDate(2026, 10, 20)), rig.vm.state.dates, "a day ahead can't be picked")
    }

    @Test
    fun pickDates_keepsToNinetyDays() = runUnitTest {
        val rig = Rig(PresetId.Full)
        rig.vm.takeAction(ProviderSummaryAction.RangePicked(SummaryRangeChoice.Custom))

        rig.vm.takeAction(ProviderSummaryAction.DayTapped(LocalDate(2026, 3, 1)))
        rig.vm.takeAction(ProviderSummaryAction.DayTapped(LocalDate(2026, 10, 25)))

        assertEquals(SummaryRange(LocalDate(2026, 7, 28), LocalDate(2026, 10, 25)), rig.vm.state.dates)
    }

    @Test
    fun backCloses() = runUnitTest {
        val rig = Rig(PresetId.Full)
        val events = eventsOf(rig.vm)

        rig.vm.takeAction(ProviderSummaryAction.BackTapped)

        assertEquals(listOf<ProviderSummaryEvent>(ProviderSummaryEvent.Close), events)
    }
}
