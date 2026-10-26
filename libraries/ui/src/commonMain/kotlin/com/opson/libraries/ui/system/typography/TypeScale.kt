@file:Suppress("MagicNumber")

package com.opson.system.typography

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The handoff's type scale, read as `AppTheme.type.body`, `AppTheme.type.cardTitle`, and so on.
 *
 * Every size is an em multiple of one base, 16 sp normally and 19 sp when the user turns on large
 * text, so the whole scale grows together and the hierarchy never changes. The `numeric*` styles
 * carry tabular figures; use them, or `.Tabular` on any other style, for every figure on screen.
 *
 * Components take already formatted strings. Nothing here formats a nutrition number.
 */
@Immutable
class TypeScale internal constructor(
    fontFamily: FontFamily,
    /** The size of 1em, in sp. */
    val baseSize: Float,
) {
    private val family = fontFamily

    private fun style(
        name: String,
        em: Float,
        weight: FontWeight,
        lineHeight: Float = BODY_LINE_HEIGHT,
        letterSpacingEm: Float? = null,
        tabular: Boolean = false,
        lineBreak: LineBreak = LineBreak.Simple,
    ) = TypographyResource(
        fontFamily = family,
        fontWeight = weight,
        fontSize = (baseSize * em).sp,
        lineHeight = (baseSize * em * lineHeight).sp,
        lineBreak = lineBreak,
        identifier = "type-$name",
        letterSpacing = letterSpacingEm?.em ?: TextUnit.Unspecified,
        fontFeatureSettings = if (tabular) TABULAR_FIGURES else null,
    )

    /** Tab screen title ("Foods", "Trends"). 2em, 800. */
    val h1 = style("h1", 2f, FontWeight.ExtraBold, HEADING_LINE_HEIGHT, TIGHT_TRACKING, lineBreak = LineBreak.Heading)

    /** Title of a pushed screen (Nutrients, Weight, a More page). 1.75em, 800. */
    val subH1 = style("sub-h1", 1.75f, FontWeight.ExtraBold, HEADING_LINE_HEIGHT, TIGHT_TRACKING, lineBreak = LineBreak.Heading)

    /** The day label in the Today header ("Today", "Thu, Oct 22"). 1.5em, 800. */
    val dayLabel = style("day-label", 1.5f, FontWeight.ExtraBold, 1.1f, TIGHT_TRACKING)

    /** Sheet title. 1.25em, 800. */
    val sheetTitle = style("sheet-title", 1.25f, FontWeight.ExtraBold, HEADING_LINE_HEIGHT)

    /** Card title ("Water", "Breakfast"). 1.0625em, 800. */
    val cardTitle = style("card-title", 1.0625f, FontWeight.ExtraBold)

    /** Body copy. 1em, 400. */
    val body = style("body", 1f, FontWeight.Normal, lineBreak = LineBreak.Paragraph)

    /** Row and option titles. 1em, 600. */
    val bodyStrong = style("body-strong", 1f, FontWeight.SemiBold)

    /** Emphasised row titles (food names, list rows). 1em, 700. */
    val bodyBold = style("body-bold", 1f, FontWeight.Bold)

    /** Menu items and list actions. 0.9375em, 600. */
    val menuItem = style("menu-item", 0.9375f, FontWeight.SemiBold)

    /** Secondary copy. 0.875em, 400. */
    val secondary = style("secondary", 0.875f, FontWeight.Normal, lineBreak = LineBreak.Paragraph)

    /** Secondary labels on chips and segments. 0.875em, 600. */
    val secondaryStrong = style("secondary-strong", 0.875f, FontWeight.SemiBold)

    /** Subtitles under a row title, field labels. 0.8125em, 400. */
    val secondarySmall = style("secondary-small", 0.8125f, FontWeight.Normal)

    /** Field labels and small chips. 0.8125em, 600. */
    val secondarySmallStrong = style("secondary-small-strong", 0.8125f, FontWeight.SemiBold)

    /** Section labels inside a card ("Favorites", "Your foods"). 0.8125em, 700. */
    val sectionLabel = style("section-label", 0.8125f, FontWeight.Bold)

    /** Captions. 0.75em, 400. */
    val caption = style("caption", 0.75f, FontWeight.Normal)

    /** Ring labels and favorite names. 0.75em, 700. */
    val captionStrong = style("caption-strong", 0.75f, FontWeight.Bold)

    /**
     * Summary card headings ("ENERGY SUMMARY"). 0.75em, 800, 0.08em tracking. Pass
     * `allCaps = true` to `Text`; the style does not upper-case by itself.
     */
    val overline = style("overline", 0.75f, FontWeight.ExtraBold, letterSpacingEm = 0.08f)

    /** Bottom navigation labels. 0.6875em, 600 (800 when selected, use `.ExtraBold`). */
    val navLabel = style("nav-label", 0.6875f, FontWeight.SemiBold)

    /** Primary call to action. 1em, 700. */
    val button = style("button", 1f, FontWeight.Bold)

    /** Pill and compact buttons. 0.875em, 700. */
    val buttonSmall = style("button-small", 0.875f, FontWeight.Bold)

    /** A figure at body size. 1em, 700, tabular. */
    val numeric = style("numeric", 1f, FontWeight.Bold, tabular = true)

    /** The amount in a stepper. 1.125em, 700, tabular. */
    val numericLarge = style("numeric-large", 1.125f, FontWeight.Bold, tabular = true)

    /** A trailing figure on a row. 0.875em, 600, tabular. */
    val numericSmall = style("numeric-small", 0.875f, FontWeight.SemiBold, tabular = true)

    /** A figure inside a ring. 0.875em, 800, tabular, tight. */
    val ringValue = style("ring-value", 0.875f, FontWeight.ExtraBold, 1.05f, TIGHT_TRACKING, tabular = true)

    /** The unit under a ring figure. 0.625em, 600. */
    val ringUnit = style("ring-unit", 0.625f, FontWeight.SemiBold)

    companion object {
        const val BASE_SIZE = 16f
        const val LARGE_BASE_SIZE = 19f

        private const val BODY_LINE_HEIGHT = 1.35f
        private const val HEADING_LINE_HEIGHT = 1.15f
        private const val TIGHT_TRACKING = -0.02f

        /** System font fallback, so a composable rendered outside `AppTheme` still lays out. */
        internal val Fallback = TypeScale(FontFamily.Default, BASE_SIZE)
    }
}
