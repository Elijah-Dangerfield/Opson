package com.opson.features.trends.impl

import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.WeightUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The weight page at each level (set in Modes, not here), a Delay override
 * either side of 9 pm, masking and range slicing, over the fixtures' ninety
 * mornings. The EMA itself
 * is `WeightTrend` in `:libraries:nutrition`.
 */
class WeightStateBuilderTest {

    private fun build(
        preset: PresetId = PresetId.Full,
        overrides: Map<Metric, Visibility> = emptyMap(),
        ui: WeightUi = WeightUi(),
        settings: Settings = Settings(),
        at: LocalTime = LocalTime(12, 0),
    ) = WeightStateBuilder.build(
        TrendsFixtures.weightInputs(settings),
        ui,
        TrendsFixtures.policy(preset, overrides, TrendsFixtures.fixedClock(LocalDateTime(TrendsFixtures.today, at))),
    )

    @Test
    fun numbers_theTrendIsTheLargeNumber_todaysReadingTheSmallOne() {
        val state = build()
        val trend = assertIs<Display.Shown>(state.trend)
        val latest = assertIs<Display.Shown>(state.latest)
        // The trend is bare; the screen sets "kg" small beside it. Today's reading carries its unit.
        assertTrue(trend.text.none { it.isLetter() } && latest.text.endsWith("kg"))
        assertEquals(WeighInWhen.ThisMorning, state.latestWhen)
        val chart = assertNotNull(state.chart)
        assertEquals(28, chart.dots?.size)
        assertEquals(28, chart.trend.size)
        assertEquals("Sep 28", chart.from)
        assertEquals("Oct 25", chart.to)
        assertEquals(true, chart.ranges)
        assertEquals(false, state.inputMasked)
        assertEquals(false, state.hiddenCard)
        assertEquals(false, state.trendOnlyNote)
    }

    @Test
    fun noChangeReadout_anywhere() {
        val state = build()
        val texts = listOfNotNull(state.trend, state.latest).filterIsInstance<Display.Shown>().map { it.text }
        assertTrue(texts.none { it.startsWith("-") || it.startsWith("\u2212") || it.startsWith("+") }, texts.toString())
    }

    @Test
    fun trendOnly_aSlopeWordAndTheLine_noNumbersNoDotsNoRanges() {
        val state = build(PresetId.Fuel)
        val word = assertIs<Display.Soft>(state.trend).text
        assertTrue(word in setOf("Steady", "Rising slowly", "Falling slowly"), word)
        assertNull(state.latest)
        val chart = assertNotNull(state.chart)
        assertNull(chart.dots)
        assertNull(chart.from)
        assertEquals(false, chart.ranges)
        assertEquals(28, chart.trend.size)
        assertEquals(true, state.inputMasked)
        assertEquals(true, state.trendOnlyNote)
    }

    @Test
    fun hidden_noChart_hiddenCard_maskedInput() {
        val state = build(PresetId.Blind)
        assertNull(state.chart)
        assertNull(state.trend)
        assertNull(state.latest)
        assertEquals(true, state.hiddenCard)
        assertEquals(true, state.inputMasked)
    }

    @Test
    fun delayOverride_hidesUntilNinePm_thenShows() {
        val delayed = mapOf(Metric.Weight to Visibility.Delay)
        val noon = build(overrides = delayed)
        assertNull(noon.chart)
        assertEquals(true, noon.hiddenCard)
        val evening = build(overrides = delayed, at = LocalTime(21, 30))
        assertNotNull(evening.chart?.dots)
        assertIs<Display.Shown>(evening.trend)
    }

    @Test
    fun rangeSlicesTheReadings() {
        assertEquals(7, build(ui = WeightUi(range = WeightRange.W1)).chart?.dots?.size)
        assertEquals(90, build(ui = WeightUi(range = WeightRange.M3)).chart?.dots?.size)
        assertEquals(WeightRange.M3, build(PresetId.Blind, ui = WeightUi(range = WeightRange.M3)).range)
    }

    @Test
    fun chartHeights_stayInsideTheBox() {
        val chart = assertNotNull(build(ui = WeightUi(range = WeightRange.M3)).chart)
        assertTrue((chart.trend + chart.dots.orEmpty()).all { it in 0f..1f })
    }

    @Test
    fun savedNote_saysWhereTheWeighInWillShow() {
        assertEquals(SavedNote.Plain, build(ui = WeightUi(saved = true)).saved)
        assertEquals(SavedNote.TrendOnly, build(PresetId.Fuel, ui = WeightUi(saved = true)).saved)
        assertEquals(SavedNote.OutOfView, build(PresetId.Blind, ui = WeightUi(saved = true)).saved)
        assertNull(build().saved)
    }

    @Test
    fun pounds_readInPounds() {
        val state = build(settings = Settings(weightUnit = WeightUnit.Lb))
        assertEquals(WeightUnit.Lb, state.unit)
        // The trend is bare, in pounds: about 73 kg reads as about 161, the screen adds "lb".
        assertTrue(assertIs<Display.Shown>(state.trend).text.toDouble() > 150.0)
        assertTrue(assertIs<Display.Shown>(state.latest).text.endsWith("lb"))
    }

    @Test
    fun noWeighIns_emptyChartNoValue() {
        val state = WeightStateBuilder.build(
            TrendsFixtures.weightInputs(weighIns = emptyList()),
            WeightUi(),
            TrendsFixtures.policy(),
        )
        assertNull(state.trend)
        assertNull(state.latest)
        assertEquals(emptyList(), state.chart?.trend)
    }
}
