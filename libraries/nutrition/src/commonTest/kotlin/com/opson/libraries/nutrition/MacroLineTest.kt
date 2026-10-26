package com.opson.libraries.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `macroLine` and `macroText` from the prototype, plus the rule they exist to
 * keep: a macro that is not effectively shown never appears in the line, at any
 * level, in any preset, on any surface.
 */
class MacroLineTest {

    // Banana: 1.3 g protein, 27 g carbs, 0.4 g fat, 3.1 g fiber.
    private val banana = NutrientAmounts(kcal = 105.0, protein = 1.3, carbs = 27.0, fat = 0.4, fiber = 3.1)

    @Test
    fun long_spellsEachMacroInPrototypeOrder() {
        val lunch = NutrientAmounts(kcal = 515.0, protein = 30.0, carbs = 50.0, fat = 20.0, fiber = 1.0)
        assertEquals(
            listOf(
                MacroPart(Metric.Protein, "30 g protein"),
                MacroPart(Metric.Carbs, "50 g carbs"),
                MacroPart(Metric.Fat, "20 g fat"),
            ),
            showAll().macroLine(lunch, Surface.Today, style = MacroLineStyle.Long),
        )
    }

    @Test
    fun compact_isTheLetterForm_zeroIncluded() {
        assertEquals(
            listOf("1P", "27C", "0F"),
            showAll().macroLine(banana, Surface.Logger, style = MacroLineStyle.Compact).map { it.text },
        )
    }

    @Test
    fun fiber_isNeverInTheLine() {
        assertTrue(showAll().macroLine(banana, Surface.Today).none { it.metric == Metric.Fiber })
    }

    @Test
    fun onlyShownMacrosAppear() {
        val proteinOnly = policy(
            PresetId.Full,
            overrides = mapOf(Metric.Carbs to Visibility.Hide, Metric.Fat to Visibility.Soften),
        )
        assertEquals(listOf(Metric.Protein), proteinOnly.macroLine(banana, Surface.Today).map { it.metric })
    }

    @Test
    fun hiddenMacrosNeverAppear_anyLevelAnyPresetAnySurface() {
        val levels = Visibility.entries
        PresetId.entries.forEach { preset ->
            levels.forEach { carbsLevel ->
                val p = policy(preset, overrides = mapOf(Metric.Carbs to carbsLevel))
                Surface.entries.forEach { surface ->
                    MacroLineStyle.entries.forEach { style ->
                        val parts = p.macroLine(banana, surface, style = style)
                        parts.forEach { part ->
                            assertEquals(
                                Visibility.Show,
                                p.effectiveLevel(part.metric, surface),
                                "$preset carbs=$carbsLevel $surface $style: ${part.text}",
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun gentleModes_haveNoLineAtAll() {
        listOf(PresetId.Blind, PresetId.Nutrient, PresetId.Rhythm, PresetId.Explorer, PresetId.Mindful, PresetId.Coast)
            .forEach { preset ->
                assertEquals(emptyList(), policy(preset).macroLine(banana, Surface.Today), "$preset")
            }
    }

    @Test
    fun fuel_showsProteinOnly() {
        // Fuel shows protein and fiber; fiber is not in the line.
        assertEquals(listOf("1 g protein"), policy(PresetId.Fuel).macroLine(banana, Surface.Today).map { it.text })
    }

    @Test
    fun delayed_followsTheReveal() {
        assertEquals(emptyList(), policy(PresetId.Delayed).macroLine(banana, Surface.Today))
        assertEquals(3, policy(PresetId.Delayed).macroLine(banana, Surface.DayClose).size)
        assertEquals(
            3,
            policy(PresetId.Delayed).macroLine(banana, Surface.Today, FormatContext(day = YESTERDAY)).size,
        )
    }
}
