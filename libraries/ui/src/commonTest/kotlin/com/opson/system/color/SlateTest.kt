package com.opson.system.color

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.opson.libraries.ui.system.color.ColorResource
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * WCAG contrast for every pair of Slate tokens that puts text or an icon on a background, in light
 * and dark, plus the one rule behind a value the design sheet does not give ([SchemeColors.inkSoft]).
 */
class SlateTest {

    private val schemes = listOf(false, true).map { appColors(it) }

    @Test
    fun bodyText_meetsAa_onEveryBackgroundItSitsOn() = schemes.forEach { c ->
        listOf(c.bg, c.surface, c.soft).forEach { background ->
            listOf(c.ink, c.inkSoft, c.muted, c.danger).forEach { text -> assertContrast(text, background, AA, c) }
        }
    }

    /** Accent text belongs on a card. On the page and on raised it is icons, fills and large text. */
    @Test
    fun accent_meetsAaAsText_onEveryBackgroundItSitsOn() = schemes.forEach { c ->
        listOf(c.surface, c.bg, c.soft, c.accentTint).forEach { assertContrast(c.accent, it, AA, c) }
    }

    @Test
    fun textOnFilledBlocks_meetsAa() = schemes.forEach { c ->
        assertContrast(c.onAccent, c.accent, AA, c)
        assertContrast(c.onHero, c.hero, AA, c)
        assertContrast(c.onHeroMuted, c.hero, AA, c)
        // The undo toast: the sheet's Toast is ink, its message is bg and its action the accent tint.
        assertContrast(c.bg, c.ink, AA, c)
        assertContrast(c.accentTint, c.ink, AA, c)
    }

    @Test
    fun energyOut_isVisibleOnACardAndItsTrack() = schemes.forEach { c ->
        listOf(c.restingEnergy, c.activeEnergy).forEach { fill ->
            assertContrast(fill, c.surface, NON_TEXT, c)
        }
    }

    @Test
    fun inkSoft_isInkToMutedInOklabAtTheEarlierPalettesFraction() {
        listOf(SlateLight, SlateDark).forEach { scheme ->
            val expected = mix(scheme.ink, scheme.muted, INK_SOFT_FRACTION)
            val actual = scheme.inkSoft.channels()
            assertTrue(
                actual.zip(expected).all { (a, e) -> abs(a - e) <= 1 },
                "inkSoft is ${actual.hex()}, the rule gives ${expected.hex()}",
            )
        }
    }

    private fun assertContrast(fg: ColorResource, bg: ColorResource, minimum: Double, c: Colors) {
        val ratio = contrast(fg.color.compositeOver(bg.color), bg.color)
        val theme = if (c.isDark) "dark" else "light"
        assertTrue(ratio >= minimum, "$theme ${fg.designSystemName} on ${bg.designSystemName} is $ratio, needs $minimum")
    }

    private fun contrast(a: Color, b: Color): Double {
        val (dim, bright) = listOf(luminance(a), luminance(b)).sorted()
        return (bright + 0.05) / (dim + 0.05)
    }

    private fun luminance(color: Color): Double {
        val (r, g, b) = listOf(color.red, color.green, color.blue).map { linear(it.toDouble()) }
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun linear(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    /** [a] to [b] at [t], in OKLab (Björn Ottosson's matrices), back to 8-bit sRGB. */
    private fun mix(a: Color, b: Color, t: Double): List<Int> {
        val (la, aa, ba) = oklab(a)
        val (lb, ab, bb) = oklab(b)
        val l = la + (lb - la) * t
        val m = aa + (ab - aa) * t
        val s = ba + (bb - ba) * t
        val l3 = (l + 0.3963377774 * m + 0.2158037573 * s).pow(3)
        val m3 = (l - 0.1055613458 * m - 0.0638541728 * s).pow(3)
        val s3 = (l - 0.0894841775 * m - 1.2914855480 * s).pow(3)
        return listOf(
            4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3,
            -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3,
            -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3,
        ).map { v ->
            val x = min(1.0, max(0.0, v))
            val srgb = if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1 / 2.4) - 0.055
            (srgb * 255).roundToInt()
        }
    }

    private fun oklab(color: Color): List<Double> {
        val (r, g, b) = listOf(color.red, color.green, color.blue).map { linear(it.toDouble()) }
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return listOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    private fun Color.channels() = listOf(red, green, blue).map { (it * 255).roundToInt() }

    private fun List<Int>.hex() = joinToString("") { it.toString(16).padStart(2, '0') }

    private companion object {
        const val AA = 4.5

        /** WCAG 1.4.11 for icons and fills, and 1.4.3's minimum for large text. */
        const val NON_TEXT = 3.0

        /** Where the earlier palettes put inkSoft between ink and muted, by lightness. */
        const val INK_SOFT_FRACTION = 0.61
    }
}
