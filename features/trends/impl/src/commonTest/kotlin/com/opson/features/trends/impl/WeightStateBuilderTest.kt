package com.opson.features.trends.impl

import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.WeightUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.trends_weight_caption_dots
import opson.libraries.resources.generated.resources.trends_weight_caption_trend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The weight page in its three views, a Delay override either side of 9 pm,
 * masking and range slicing, over the fixtures' ninety mornings. The EMA itself
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
    fun numbers_valueAverageDotsAndPlainInput() {
        val state = build()
        assertEquals(WeightView.Numbers, state.selected)
        assertTrue(assertIs<Display.Shown>(state.now).text.endsWith("kg"))
        assertEquals(WeighInWhen.ThisMorning, state.nowWhen)
        assertIs<Display.Shown>(state.average)
        val chart = assertNotNull(state.chart)
        assertEquals(28, chart.dots?.size)
        assertEquals(28, chart.trend.size)
        assertEquals(Res.string.trends_weight_caption_dots, chart.caption)
        assertEquals(false, state.inputMasked)
        assertEquals(false, state.hiddenCard)
    }

    @Test
    fun trendOnly_lineWithoutDotsOrValues_maskedInput() {
        val state = build(PresetId.Fuel)
        assertEquals(WeightView.TrendOnly, state.selected)
        assertNull(state.now)
        assertNull(state.average)
        val chart = assertNotNull(state.chart)
        assertNull(chart.dots)
        assertEquals(28, chart.trend.size)
        assertEquals(Res.string.trends_weight_caption_trend, chart.caption)
        assertEquals(true, state.inputMasked)
    }

    @Test
    fun hidden_noChart_hiddenCard_maskedInput() {
        val state = build(PresetId.Blind)
        assertEquals(WeightView.Hidden, state.selected)
        assertNull(state.chart)
        assertNull(state.now)
        assertEquals(true, state.hiddenCard)
        assertEquals(true, state.inputMasked)
    }

    @Test
    fun delayOverride_picksTrendOnly_andHidesTheChartUntilNinePm() {
        val delayed = mapOf(Metric.Weight to Visibility.Delay)
        val noon = build(overrides = delayed)
        assertEquals(WeightView.TrendOnly, noon.selected)
        assertNull(noon.chart)
        assertEquals(true, noon.hiddenCard)
        val evening = build(overrides = delayed, at = LocalTime(21, 30))
        assertEquals(WeightView.TrendOnly, evening.selected)
        assertNotNull(evening.chart?.dots)
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
    fun savedNote_plainOnlyWhenTheWeightShows() {
        assertEquals(SavedNote.Plain, build(ui = WeightUi(saved = true)).saved)
        assertEquals(SavedNote.OutOfView, build(PresetId.Blind, ui = WeightUi(saved = true)).saved)
        assertNull(build().saved)
    }

    @Test
    fun pounds_readInPounds() {
        val state = build(settings = Settings(weightUnit = WeightUnit.Lb))
        assertEquals(WeightUnit.Lb, state.unit)
        assertTrue(assertIs<Display.Shown>(state.now).text.endsWith("lb"))
    }

    @Test
    fun noWeighIns_emptyChartNoValue() {
        val state = WeightStateBuilder.build(
            TrendsFixtures.weightInputs(weighIns = emptyList()),
            WeightUi(),
            TrendsFixtures.policy(),
        )
        assertNull(state.now)
        assertEquals(emptyList(), state.chart?.trend)
    }
}
