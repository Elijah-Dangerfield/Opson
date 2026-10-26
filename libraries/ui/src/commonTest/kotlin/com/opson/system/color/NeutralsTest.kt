package com.opson.system.color

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The neutrals in [base] are the output of the rule written on it, not hand-picked hex. This
 * recomputes the rule in OKLCH and checks every stored color against it, then checks WCAG AA for
 * every text token on the two backgrounds text sits on.
 */
class NeutralsTest {

    private val hue = mapOf(
        Palette.Pine to 182.2,
        Palette.Espresso to 48.0,
        Palette.Clay to 36.0,
        Palette.Plum to 310.6,
    )

    /** Lightness and chroma, measured on the v3 Pine light images. */
    private val light = mapOf(
        "bg" to (0.9654 to 0.0025),
        "ink" to (0.2331 to 0.0148),
        "inkSoft" to (0.3920 to 0.0134),
        "muted" to (0.4936 to 0.0132),
        "line" to (0.9243 to 0.0050),
        "track" to (0.9334 to 0.0050),
        "soft" to (0.9455 to 0.0050),
    )

    /** The handoff's dark lightness, with its chroma scaled by v3's light/handoff ratio per token. */
    private val dark = mapOf(
        "bg" to (0.1808 to 0.0082),
        "surface" to (0.2245 to 0.0117),
        "ink" to (0.9520 to 0.0068),
        "inkSoft" to (0.8131 to 0.0101),
        "muted" to (0.7242 to 0.0121),
        "line" to (0.2867 to 0.0108),
        "track" to (0.2867 to 0.0108),
        "soft" to (0.2663 to 0.0100),
    )

    @Test
    fun everyNeutralIsTheRuleRoundedToEightBits() {
        Palette.entries.forEach { palette ->
            listOf(false, true).forEach { isDark ->
                val colors = palette.base(isDark).byName()
                (if (isDark) dark else light).forEach { (name, lc) ->
                    val expected = oklch(lc.first, lc.second, hue.getValue(palette))
                    val actual = colors.getValue(name)
                    assertTrue(
                        actual.channels().zip(expected).all { (a, e) -> abs(a - e) <= 1 },
                        "$palette ${if (isDark) "dark" else "light"} $name is ${actual.hex()}, rule gives ${expected.hex()}",
                    )
                }
            }
        }
    }

    @Test
    fun textMeetsAaOnBgAndSurface() {
        Palette.entries.forEach { palette ->
            listOf(false, true).forEach { isDark ->
                val base = palette.base(isDark)
                listOf(base.bg, base.surface).forEach { background ->
                    listOf("ink" to base.ink, "inkSoft" to base.inkSoft, "muted" to base.muted).forEach { (name, text) ->
                        val ratio = contrast(text, background)
                        assertTrue(ratio >= AA, "$palette dark=$isDark $name on ${background.hex()} is $ratio")
                    }
                }
            }
        }
    }

    private fun BaseColors.byName() = mapOf(
        "bg" to bg, "surface" to surface, "ink" to ink, "inkSoft" to inkSoft, "muted" to muted,
        "line" to line, "track" to track, "soft" to soft,
    )

    private fun Color.channels() = listOf(red, green, blue).map { (it * 255).roundToInt() }

    private fun List<Int>.hex() = joinToString("") { it.toString(16).padStart(2, '0') }

    private fun Color.hex() = channels().hex()

    /** OKLCH to 8-bit sRGB (Björn Ottosson's OKLab matrices). */
    private fun oklch(l: Double, c: Double, h: Double): List<Int> {
        val a = c * cos(h * PI_OVER_180)
        val b = c * sin(h * PI_OVER_180)
        val l3 = (l + 0.3963377774 * a + 0.2158037573 * b).pow(3)
        val m3 = (l - 0.1055613458 * a - 0.0638541728 * b).pow(3)
        val s3 = (l - 0.0894841775 * a - 1.2914855480 * b).pow(3)
        val linear = listOf(
            4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3,
            -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3,
            -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3,
        )
        return linear.map { v ->
            val x = min(1.0, max(0.0, v))
            val srgb = if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1 / 2.4) - 0.055
            (srgb * 255).roundToInt()
        }
    }

    private fun luminance(color: Color): Double {
        val (r, g, b) = listOf(color.red, color.green, color.blue).map {
            val c = it.toDouble()
            if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrast(a: Color, b: Color): Double {
        val (dim, bright) = listOf(luminance(a), luminance(b)).sorted()
        return (bright + 0.05) / (dim + 0.05)
    }

    private companion object {
        const val AA = 4.5
        const val PI_OVER_180 = kotlin.math.PI / 180
    }
}
