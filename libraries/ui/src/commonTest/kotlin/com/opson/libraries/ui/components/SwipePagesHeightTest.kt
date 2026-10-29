package com.opson.libraries.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/** Today's card is as tall as the page showing, and mid-swipe it runs between the two pages' heights. */
class SwipePagesHeightTest {
    private val heights = listOf(300, 200, 400)
    private val width = 100

    @Test
    fun atRestTheRowIsThePageShowing() {
        assertEquals(300, settledHeight(heights, current = 0, drag = 0f, pageWidth = width))
        assertEquals(200, settledHeight(heights, current = 1, drag = 0f, pageWidth = width))
        assertEquals(400, settledHeight(heights, current = 2, drag = 0f, pageWidth = width))
    }

    @Test
    fun halfwayToTheNextPageIsHalfwayBetweenTheirHeights() {
        assertEquals(250, settledHeight(heights, current = 0, drag = -50f, pageWidth = width))
        assertEquals(300, settledHeight(heights, current = 1, drag = -50f, pageWidth = width))
    }

    @Test
    fun draggingBackTowardsTheLastPageMixesWithIt() {
        assertEquals(275, settledHeight(heights, current = 1, drag = 75f, pageWidth = width))
    }

    @Test
    fun aDotTappedTwoPagesAwayPassesThroughTheOneBetween() {
        // From page 0 sliding to page 2: at one page's width it is page 1's height.
        assertEquals(200, settledHeight(heights, current = 0, drag = -100f, pageWidth = width))
        assertEquals(400, settledHeight(heights, current = 0, drag = -200f, pageWidth = width))
    }

    @Test
    fun beforeTheFirstMeasureTheCurrentPageAnswers() {
        assertEquals(200, settledHeight(heights, current = 1, drag = -40f, pageWidth = 0))
        assertEquals(0, settledHeight(emptyList(), current = 0, drag = 0f, pageWidth = width))
    }
}
