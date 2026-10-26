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
 *
 * Sizes are design v3's, measured on the 3x export (ink width of known strings fitted against
 * this font) and noted on each style as the size at the 16 sp base. Weights are the handoff's.
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

    /** The big onboarding titles (the welcome headline, the recommended mode). 2em (32), 800. */
    val display = style("display", 2f, FontWeight.ExtraBold, HEADING_LINE_HEIGHT, TIGHT_TRACKING, lineBreak = LineBreak.Heading)

    /** Tab screen title ("Foods", "Trends"). 1.75em (28), 800. */
    val h1 = style("h1", 1.75f, FontWeight.ExtraBold, HEADING_LINE_HEIGHT, TIGHT_TRACKING, lineBreak = LineBreak.Heading)

    /** A question or section headline (onboarding questions). 1.625em (26), 800. */
    val subH1 = style("sub-h1", 1.625f, FontWeight.ExtraBold, HEADING_LINE_HEIGHT, TIGHT_TRACKING, lineBreak = LineBreak.Heading)

    /**
     * The day label in the Today header ("Today", "Thu, Oct 22") and the title of a pushed page
     * (Nutrients, Weight, a More page). 1.3125em (21), 800.
     */
    val dayLabel = style("day-label", 1.3125f, FontWeight.ExtraBold, 1.1f, TIGHT_TRACKING)

    /** Sheet title. 1.25em (20), 800. */
    val sheetTitle = style("sheet-title", 1.25f, FontWeight.ExtraBold, HEADING_LINE_HEIGHT)

    /** Card title ("Water", "Breakfast"). 1em (16), 800. */
    val cardTitle = style("card-title", 1f, FontWeight.ExtraBold)

    /** A page's lead paragraph and text the user types (search, inputs). 1em (16), 400. */
    val lead = style("lead", 1f, FontWeight.Normal, lineBreak = LineBreak.Paragraph)

    /** Body copy and list row text. 0.9375em (15), 400. */
    val body = style("body", 0.9375f, FontWeight.Normal, lineBreak = LineBreak.Paragraph)

    /** Row and option titles, segmented control labels. 0.9375em (15), 600. */
    val bodyStrong = style("body-strong", 0.9375f, FontWeight.SemiBold)

    /** Emphasised row titles (food names, list rows, menu items). 0.9375em (15), 700. */
    val bodyBold = style("body-bold", 0.9375f, FontWeight.Bold)

    /** Menu items and list actions. 0.9375em (15), 600. */
    val menuItem = style("menu-item", 0.9375f, FontWeight.SemiBold)

    /** Secondary copy: row subtitles, notes under a chart, axis labels. 0.75em (12), 400. */
    val secondary = style("secondary", 0.75f, FontWeight.Normal, lineBreak = LineBreak.Paragraph)

    /** Labels on the hero and above a figure ("Eaten", "Trend"), chips. 0.8125em (13), 600. */
    val secondaryStrong = style("secondary-strong", 0.8125f, FontWeight.SemiBold)

    /** Subtitles under a row title. 0.75em (12), 400. */
    val secondarySmall = style("secondary-small", 0.75f, FontWeight.Normal)

    /** Small labels: the date under a page title, "Add to" over the meal. 0.75em (12), 600. */
    val secondarySmallStrong = style("secondary-small-strong", 0.75f, FontWeight.SemiBold)

    /** Section labels ("Saved meals", "How sure are you?"). 0.8125em (13), 700. */
    val sectionLabel = style("section-label", 0.8125f, FontWeight.Bold)

    /** Captions. 0.75em (12), 400. */
    val caption = style("caption", 0.75f, FontWeight.Normal)

    /** Ring labels and favorite names. 0.75em (12), 700. */
    val captionStrong = style("caption-strong", 0.75f, FontWeight.Bold)

    /**
     * Summary card headings ("ENERGY SUMMARY"). 0.75em, 800, 0.08em tracking. Pass
     * `allCaps = true` to `Text`; the style does not upper-case by itself.
     */
    val overline = style("overline", 0.75f, FontWeight.ExtraBold, letterSpacingEm = 0.08f)

    /** Bottom navigation labels. 0.75em (12), 600 (800 when selected, use `.ExtraBold`). */
    val navLabel = style("nav-label", 0.75f, FontWeight.SemiBold)

    /** Primary call to action. 1em (16), 700. */
    val button = style("button", 1f, FontWeight.Bold)

    /** Half-width, pill and compact buttons. 0.9375em (15), 700. */
    val buttonSmall = style("button-small", 0.9375f, FontWeight.Bold)

    /** A figure at row size ("6 of 7 days"). 1em (16), 700, tabular. */
    val numeric = style("numeric", 1f, FontWeight.Bold, tabular = true)

    /** The amount in a stepper ("1 plate"). 1.375em (22), 700, tabular. */
    val numericLarge = style("numeric-large", 1.375f, FontWeight.Bold, tabular = true)

    /** A trailing figure on a row. 0.8125em (13), 600, tabular. */
    val numericSmall = style("numeric-small", 0.8125f, FontWeight.SemiBold, tabular = true)

    /** The big figure on Today and Weight ("1,245", "73.4"). 2.25em (36), 700, tabular, tight. */
    val figureDisplay = style("figure-display", 2.25f, FontWeight.Bold, 1f, TIGHT_TRACKING, tabular = true)

    /** The word or count on Today's soft card ("Partway through", "2 of 4"). 1.375em, 800, tabular. */
    val figureHero = style("figure-hero", 1.375f, FontWeight.ExtraBold, tabular = true)

    /** The locked card's title ("Open at 9:00 pm"). 1.1875em, 800. */
    val heroTitle = style("hero-title", 1.1875f, FontWeight.ExtraBold)

    /** A soft line on a card standing in for a figure (Day close's "Partway through"). 1.25em, 700. */
    val figureLarge = style("figure-large", 1.25f, FontWeight.Bold, tabular = true)

    /** Day close's meals line ("3 of 4 meals"). 1.125em, 700. */
    val figureMedium = style("figure-medium", 1.125f, FontWeight.Bold, tabular = true)

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
