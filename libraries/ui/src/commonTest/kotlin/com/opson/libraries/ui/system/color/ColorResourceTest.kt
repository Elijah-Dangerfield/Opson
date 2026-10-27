package com.opson.libraries.ui.system.color

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Compose skips a composable only when its arguments are equal to last time's. A token made on
 * every recomposition (`onHero.withAlpha(0.7f)`) has to be equal by value for that to happen.
 */
class ColorResourceTest {

    private val ink = ColorResource.FromColor(Color(0xFF1E1E24), "light-ink")

    @Test
    fun theSameTintMadeTwiceIsEqual() {
        assertEquals(ink.withAlpha(0.7f), ink.withAlpha(0.7f))
        assertEquals(ink.withAlpha(0.7f).hashCode(), ink.withAlpha(0.7f).hashCode())
    }

    @Test
    fun aDifferentTintIsNot() {
        assertNotEquals(ink.withAlpha(0.7f), ink.withAlpha(0.5f))
        assertNotEquals<ColorResource>(ink, ColorResource.FromColor(Color(0xFF1E1E24), "dark-ink"))
    }
}
