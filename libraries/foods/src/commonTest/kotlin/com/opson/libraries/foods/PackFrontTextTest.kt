package com.opson.libraries.foods

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PackFrontTextTest {

    private fun line(text: String, top: Double, height: Double) = LabelTextLine(text, 0.9, 0.1, top, 0.8, height)

    @Test
    fun theBiggestLineIsTheName() {
        val lines = listOf(
            line("Nature Valley", 0.05, 0.04),
            line("Crunchy Oats & Honey", 0.2, 0.08),
            line("12 bars", 0.8, 0.03),
        )
        assertEquals("Crunchy Oats & Honey", PackFrontText.guessName(lines))
    }

    @Test
    fun aSecondLineOfTheSameSizeJustBelowJoinsIt() {
        val lines = listOf(
            line("OATS & HONEY", 0.2, 0.08),
            line("GRANOLA BAR", 0.29, 0.075),
            line("WITH WHOLE GRAIN OATS", 0.4, 0.03),
        )
        assertEquals("Oats & Honey Granola Bar", PackFrontText.guessName(lines))
    }

    @Test
    fun weightsClaimsAndNumbersAreNotNames() {
        val lines = listOf(
            line("NET WT 12 OZ (340g)", 0.85, 0.12),
            line("GLUTEN FREE", 0.1, 0.11),
            line("100%", 0.5, 0.2),
            line("Peanut butter", 0.3, 0.06),
        )
        assertEquals("Peanut butter", PackFrontText.guessName(lines))
    }

    @Test
    fun aNutritionPanelHasNoName() {
        val panel = listOf(
            line("Nutrition Facts", 0.05, 0.06),
            line("Serving size 2/3 cup (55g)", 0.15, 0.03),
            line("Calories 230", 0.25, 0.05),
        )
        assertNull(PackFrontText.guessName(panel))
    }

    @Test
    fun nothingNameLikeGivesNoGuess() {
        assertNull(PackFrontText.guessName(listOf(line("250 g", 0.1, 0.1), line("ok", 0.3, 0.2))))
        assertNull(PackFrontText.guessName(emptyList()))
    }
}
