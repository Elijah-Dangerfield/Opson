package com.opson.features.settings.impl.summary

import com.opson.features.settings.impl.SettingsFixtures
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a provider summary holds: each choice puts in its part and nothing else, and the mode
 * decides what the preview shows, never what the PDF holds.
 */
class ProviderSummaryContentTest {

    private val fixtures = ProviderSummaryFixtures
    private val range = SummaryRange.last(7, fixtures.today)
    private val everything = SummaryChoices(weight = true, numbers = true)

    private fun content(choices: SummaryChoices) =
        SummaryContentBuilder.build(range, choices, fixtures.days, fixtures.weighIns, TimeZone.UTC)

    private val today get() = content(everything).days.single { it.date == fixtures.today }

    @Test
    fun everyDayInTheRangeIsListed_oldestFirst() {
        assertEquals(7, content(everything).days.size)
        assertEquals(range.start, content(everything).days.first().date)
    }

    @Test
    fun mealsAndTimes_putInTheFoodsAndTheFirstFoodsTime() {
        val breakfast = today.meals.single { it.slot == MealSlot.Breakfast }
        assertEquals(LocalTime(8, 5), breakfast.time)
        assertEquals(listOf("Porridge, 1 bowl", "Coffee with milk, 1 mug"), breakfast.foods)

        val off = content(everything.copy(mealsAndTimes = false)).days.single { it.date == fixtures.today }
        assertTrue(off.meals.all { it.foods.isEmpty() && it.time == null }, "no foods or times without meals and times")
        assertEquals(Hunger.Hungry, off.meals.single { it.slot == MealSlot.Breakfast }.hunger, "hunger still goes in on its own")
    }

    @Test
    fun ateNoDetails_isAMealWithoutFoods() {
        val saturday = content(everything).days.single { it.date == LocalDate(2026, 10, 24) }
        assertTrue(saturday.meals.single { it.slot == MealSlot.Lunch }.ateNoDetails)
    }

    @Test
    fun eachPart_goesInOnlyWhenPicked() {
        val nothing = SummaryChoices(mealsAndTimes = false, notes = false, hunger = false, checkIns = false)
        val bare = content(nothing)
        assertTrue(bare.days.all { it.isEmpty }, "with nothing picked a day holds nothing")
        assertNull(bare.weighIns)

        assertEquals("A steadier day than yesterday", content(nothing.copy(notes = true)).days.last().note)
        assertEquals("Before the train", content(nothing.copy(notes = true)).days.last().meals.single().note)
        assertEquals(null, content(nothing.copy(notes = true)).days.last().checkIn)
        assertTrue(content(nothing.copy(checkIns = true)).days.last().checkIn != null)
        assertEquals(1500, content(nothing.copy(numbers = true)).days.last().waterMl)
        assertEquals(575.0, content(nothing.copy(numbers = true)).days.last().totals?.kcal?.value)
        assertEquals(listOf(61.8, 62.1), content(nothing.copy(weight = true)).weighIns?.map { it.kg })
    }

    @Test
    fun theStartingChoices_followTheMode() {
        assertEquals(SummaryChoices(weight = true, numbers = true), SummaryChoices.defaultsFor(NumbersSettings(preset = PresetId.Full)))
        assertEquals(SummaryChoices(), SummaryChoices.defaultsFor(NumbersSettings(preset = PresetId.NoNumbers)))
        assertEquals(SummaryChoices(weight = true), SummaryChoices.defaultsFor(NumbersSettings(preset = PresetId.MacrosWithGrams)))
        assertEquals(SummaryChoices(), SummaryChoices.defaultsFor(NumbersSettings(preset = PresetId.MacrosOnly)))
        assertEquals(
            SummaryChoices(weight = true),
            SummaryChoices.defaultsFor(NumbersSettings(preset = PresetId.Full, overrides = mapOf(Metric.Water to Visibility.Delay))),
            "a number on Later is not shown outright",
        )
    }

    @Test
    fun thePreview_showsOnlyTheNumbersTheModeShowsOutright() {
        assertEquals(everything.metrics, ProviderSummaryStateBuilder.previewMetrics(everything, NumbersSettings(preset = PresetId.Full)))
        assertEquals(emptySet(), ProviderSummaryStateBuilder.previewMetrics(everything, NumbersSettings(preset = PresetId.NoNumbers)))
        assertEquals(
            setOf(Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber, Metric.Water, Metric.Weight),
            ProviderSummaryStateBuilder.previewMetrics(everything, NumbersSettings(preset = PresetId.MacrosWithGrams)),
        )
        assertEquals(emptySet(), ProviderSummaryStateBuilder.previewMetrics(everything, NumbersSettings.later()))
    }

    @Test
    fun inNoNumbers_thePreviewHasNoAmount_butThePdfHasEvery() {
        val state = fixtures.hiddenState()
        assertTrue(state.previewHidesNumbers)
        assertTrue(state.preview.flatMap { it.parts }.none { it.metric != null }, "no number in the preview")
        val preview = state.preview.joinToString("\n") { it.text() }
        assertFalse(Regex("""\d\s*(kcal|g\b|kg|ml)""").containsMatchIn(preview), preview)

        val pdf = ProviderSummaryStateBuilder.pdfLines(fixtures.input(fixtures.policy(PresetId.NoNumbers)), SettingsFixtures.clock)
        val numbers = pdf.flatMap { it.parts }.mapNotNull { part -> part.metric?.let { it to part.text.text() } }
        assertTrue(numbers.any { it == Metric.Calories to "summary_doc_calories(575 kcal)" }, "$numbers")
        assertTrue(numbers.any { it.first == Metric.Weight && it.second.endsWith("62.1 kg") }, "$numbers")
        assertTrue(numbers.any { it.first == Metric.Water && "1,500" in it.second }, "$numbers")
    }

    @Test
    fun inAllNumbers_thePreviewIsThePdf() {
        val input = fixtures.input(fixtures.policy(PresetId.Full))
        val state = ProviderSummaryStateBuilder.build(input, fixtures.policy(PresetId.Full))
        assertFalse(state.previewHidesNumbers)
        assertEquals(ProviderSummaryStateBuilder.pdfLines(input, SettingsFixtures.clock), state.preview)
    }

    @Test
    fun theLinesReadAsAProviderWouldReadThem() {
        val lines = fixtures.state().preview.map { it.text() }
        assertEquals("summary_doc_title()", lines[0])
        assertEquals("summary_doc_range(Oct 19 to 25, 2026)", lines[1])
        assertTrue("Sunday, Oct 25" in lines)
        assertTrue("summary_doc_meal_time(diary_slot_breakfast(), 8:05 AM)" in lines, "$lines")
        assertTrue("Porridge, 1 bowl; Coffee with milk, 1 mug" in lines)
        assertTrue("summary_doc_hunger(diary_hunger_hungry())" in lines)
        assertTrue("summary_doc_ate_no_details(diary_slot_lunch())" in lines)
        assertTrue("summary_doc_untracked()" in lines, "Friday was left out of tracking")
        assertTrue("summary_doc_nothing()" in lines, "an empty day says so")
        assertTrue(
            "summary_doc_pair(today_checkin_hunger(), today_checkin_hunger_5()). summary_doc_pair(today_checkin_energy(), " +
                "today_checkin_energy_good()). summary_doc_pair(today_checkin_mood_level(), today_checkin_mood_good()). " +
                "summary_doc_pair(today_checkin_sleep(), today_checkin_sleep_ok()). summary_doc_moods(today_checkin_mood_calm())" in lines,
            "$lines",
        )
    }

    @Test
    fun theFileIsNamedForItsRange() {
        assertEquals("opson-summary-2026-10-19-to-2026-10-25.pdf", ProviderSummaryStateBuilder.fileName(range))
    }
}

/** A line as the tests read it: copy as its resource key with its arguments, numbers joined. */
internal fun SummaryLine.text(): String = parts.joinToString(SummaryLines.NUMBERS_SEPARATOR) { it.text.text() }

internal fun SummaryText.text(): String = when (this) {
    is SummaryText.Plain -> text
    is SummaryText.Res -> "${resource.key}(${args.joinToString(", ") { it.text() }})"
    is SummaryText.Joined -> parts.joinToString(separator) { it.text() }
}
