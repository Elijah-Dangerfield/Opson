package com.opson.libraries.ui.components

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceAgainstAnchorTest {

    private val container = IntSize(width = 400, height = 800)

    private fun place(
        anchor: IntRect,
        contentSize: IntSize = IntSize(100, 50),
        preferredSide: AnchorSide = AnchorSide.Below,
        gap: Int = 10,
        margin: Int = 8,
        containerSize: IntSize = container,
    ) = placeAgainstAnchor(
        anchor = anchor,
        contentSize = contentSize,
        containerSize = containerSize,
        preferredSide = preferredSide,
        gap = gap,
        margin = margin,
    )

    @Test
    fun `content sits below the anchor, centred on it`() {
        val offset = place(anchor = IntRect(left = 150, top = 200, right = 250, bottom = 240))

        assertEquals(IntOffset(x = 150, y = 250), offset)
    }

    @Test
    fun `content sits above the anchor when that side is asked for`() {
        val offset = place(
            anchor = IntRect(left = 150, top = 200, right = 250, bottom = 240),
            preferredSide = AnchorSide.Above,
        )

        assertEquals(IntOffset(x = 150, y = 140), offset)
    }

    @Test
    fun `content wider than the anchor is centred on it, not left-aligned`() {
        val offset = place(
            anchor = IntRect(left = 180, top = 200, right = 220, bottom = 240),
            contentSize = IntSize(100, 50),
        )

        assertEquals(150, offset.x)
    }

    @Test
    fun `content flips above when there is no room below`() {
        val offset = place(anchor = IntRect(left = 150, top = 700, right = 250, bottom = 760))

        assertEquals(640, offset.y)
    }

    @Test
    fun `content flips below when there is no room above`() {
        val offset = place(
            anchor = IntRect(left = 150, top = 20, right = 250, bottom = 60),
            preferredSide = AnchorSide.Above,
        )

        assertEquals(70, offset.y)
    }

    @Test
    fun `content stays inside the left margin when the anchor hugs the edge`() {
        val offset = place(anchor = IntRect(left = 0, top = 200, right = 30, bottom = 240))

        assertEquals(8, offset.x)
    }

    @Test
    fun `content stays inside the right margin when the anchor hugs the edge`() {
        val offset = place(anchor = IntRect(left = 370, top = 200, right = 400, bottom = 240))

        assertEquals(292, offset.x)
    }

    @Test
    fun `content overlaps the anchor rather than leaving the container when neither side fits`() {
        val offset = place(
            anchor = IntRect(left = 0, top = 0, right = 400, bottom = 800),
            contentSize = IntSize(100, 700),
        )

        assertEquals(IntOffset(x = 150, y = 92), offset)
    }

    @Test
    fun `content taller than the container is pinned to the top margin`() {
        val offset = place(
            anchor = IntRect(left = 150, top = 200, right = 250, bottom = 240),
            contentSize = IntSize(100, 900),
        )

        assertEquals(8, offset.y)
    }
}
