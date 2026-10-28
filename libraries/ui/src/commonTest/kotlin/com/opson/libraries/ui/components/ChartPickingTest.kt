package com.opson.libraries.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/** Which point a finger at x picks: the nearest one with data, the ends included, gaps skipped. */
class ChartPickingTest {

    private val point = ChartReadout("Mon")

    /** Seven bars across 700 px with 10 px gaps, as the week chart lays them out. */
    private fun week(readouts: List<ChartReadout?>) = { x: Float ->
        ChartPicking.nearest(x, readouts) { ChartPicking.slotCenter(it, readouts.size, 700f, 10f) }
    }

    @Test
    fun slots_centreEachPointInItsSlot() {
        // (700 - 6 * 10) / 7 = 91.43 wide, so the first centre is half of that.
        assertEquals(45.71f, ChartPicking.slotCenter(0, 7, 700f, 10f), 0.01f)
        assertEquals(350f, ChartPicking.slotCenter(3, 7, 700f, 10f), 0.01f)
        assertEquals(654.29f, ChartPicking.slotCenter(6, 7, 700f, 10f), 0.01f)
    }

    @Test
    fun points_spreadInsideTheInset_aLoneOneCentred() {
        assertEquals(4f, ChartPicking.pointX(0, 5, 408f, 4f))
        assertEquals(404f, ChartPicking.pointX(4, 5, 408f, 4f))
        assertEquals(204f, ChartPicking.pointX(2, 5, 408f, 4f))
        assertEquals(150f, ChartPicking.pointX(0, 1, 300f, 4f))
    }

    @Test
    fun nearest_isThePointUnderTheFinger() {
        val pick = week(List(7) { point })
        assertEquals(3, pick(350f))
        assertEquals(3, pick(390f))
        assertEquals(4, pick(410f))
    }

    @Test
    fun aTie_goesToTheEarlierPoint() {
        val readouts = listOf(point, null, point)
        assertEquals(0, ChartPicking.nearest(50f, readouts) { it * 50f })
    }

    @Test
    fun pastEitherEnd_staysOnTheEndPoint() {
        val pick = week(List(7) { point })
        assertEquals(0, pick(-80f))
        assertEquals(6, pick(900f))
    }

    @Test
    fun aGap_isSkippedForTheNearestDayWithData() {
        val pick = week(listOf(point, point, point, null, point, point, point))
        // Over the untracked Thursday: whichever neighbour is nearer.
        assertEquals(2, pick(349f))
        assertEquals(4, pick(351f))
    }

    @Test
    fun gapsAtTheEnds_pickTheFirstAndLastDaysWithData() {
        val pick = week(listOf(null, null, point, point, point, null, null))
        assertEquals(2, pick(0f))
        assertEquals(4, pick(700f))
    }

    @Test
    fun nothingToPick_isNone() {
        assertEquals(ChartPicking.NONE, week(List(7) { null })(350f))
        assertEquals(ChartPicking.NONE, ChartPicking.nearest(10f, emptyList()) { 0f })
    }

    @Test
    fun emphasis_fadesEverythingButThePick() {
        val selection = ChartSelection(initial = 2)
        assertEquals(1f, selection.emphasis(2))
        assertEquals(0.35f, selection.emphasis(0))
        assertEquals(1f, ChartSelection().emphasis(0))
    }
}
