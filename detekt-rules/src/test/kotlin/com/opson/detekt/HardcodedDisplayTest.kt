package com.opson.detekt

import dev.detekt.api.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The fixture that would make `CaloriesHiddenLeakTest` test itself instead of the screen: a resolved
 * value written out by hand. Path scoping (`:libraries:nutrition` and test source sets are
 * allowed) lives in `detekt.yml`, so this file covers what the rule matches in a file it is
 * pointed at.
 */
class HardcodedDisplayTest {

    private val rule get() = HardcodedDisplay(Config.empty)

    @Test
    fun `reports Display Shown and Soft`() {
        val findings = rule.findingsOn(
            """
            import com.opson.libraries.nutrition.Display

            object TodayFixtures {
                val eaten = Display.Shown("1,245 kcal", fraction = 0.6f)
                val protein = Display.Soft("Getting there", 0.5f)
            }
            """
        )

        assertEquals(2, findings.size)
    }

    @Test
    fun `reports the fully qualified form`() {
        val findings = rule.findingsOn(
            """
            val eaten = com.opson.libraries.nutrition.Display.Shown("1,245 kcal")
            """
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `reports bare Shown when the file imports it`() {
        val findings = rule.findingsOn(
            """
            import com.opson.libraries.nutrition.Display.Shown
            import com.opson.libraries.nutrition.Display.*

            val eaten = Shown("1,245 kcal")
            val soft = Soft("In your range", 1f)
            """
        )

        assertEquals(2, findings.size)
    }

    @Test
    fun `reports hand-built ring values and macro parts`() {
        val findings = rule.findingsOn(
            """
            val ring = RingValue("1,245", "kcal")
            val macros = listOf(MacroPart(Metric.Protein, "47 g protein"))
            """
        )

        assertEquals(2, findings.size)
    }

    @Test
    fun `ignores Hidden, type checks and values from the policy`() {
        val findings = rule.findingsOn(
            """
            import com.opson.libraries.nutrition.Display

            fun render(display: Display, policy: VisibilityPolicy) {
                val none = Display.Hidden
                val shown = policy.resolve(Metric.Calories, 1245.0, Surface.Today)
                val ring = policy.ringValue(Metric.Calories, 1245.0, Surface.Today)
                when (display) {
                    is Display.Shown -> Text(display.text)
                    is Display.Soft -> Text(display.text.orEmpty())
                    Display.Hidden -> Unit
                }
            }
            """
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun `ignores an unrelated Shown`() {
        // No import of Display's members, and a receiver that is not Display.
        val findings = rule.findingsOn(
            """
            val state = SheetState.Shown("x")
            val other = Shown()
            """
        )

        assertEquals(0, findings.size)
    }
}
