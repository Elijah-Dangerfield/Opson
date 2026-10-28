@file:Suppress("MagicNumber", "VariableNaming")

package com.opson.system.typography

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opson.libraries.ui.PreviewContent
import com.opson.system.AppTheme
import com.opson.system.Dimension
import com.opson.system.LineHeightRatio
import com.opson.system.lineHeight
import com.opson.system.sp
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The legacy role-named type scale (Display, Heading, Body, Label, Caption, each on the
 * `Dimension` sizes). It renders in Atkinson Hyperlegible Next like everything else, and stays
 * so existing screens keep compiling.
 *
 * New code uses `AppTheme.type`, the handoff's em-based scale in [TypeScale], which also grows
 * with the large-text setting. This one does not.
 *
 * ## Modifiers
 * Both scales share [TypographyResource], so the modifiers work on either:
 * ```
 * AppTheme.type.secondarySmall.Tabular    // tabular figures
 * AppTheme.typography.Body.B500.Bold      // legacy, bold
 * ```
 */

@Suppress("ComplexMethod")
data class TypographyResource internal constructor(
    internal val fontFamily: FontFamily,
    internal val fontWeight: FontWeight,
    internal val fontSize: TextUnit,
    internal val lineHeight: TextUnit,
    internal val lineBreak: LineBreak,
    internal val fontStyle: FontStyle = FontStyle.Normal,
    internal val identifier: String,
    internal val letterSpacing: TextUnit = TextUnit.Unspecified,
    internal val fontFeatureSettings: String? = null,
) {

    val style: TextStyle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontStyle = fontStyle,
        lineBreak = lineBreak,
        letterSpacing = letterSpacing,
        fontFeatureSettings = fontFeatureSettings,
    )

    fun style(color: Color) = style.copy(color = color)

    // The variants below are built once per style and kept: they are read in composable bodies
    // (`AppTheme.type.bodyBold.ExtraBold`), and each copy builds a new TextStyle.

    val Italic: TypographyResource
        by lazy(LazyThreadSafetyMode.PUBLICATION) { copy(fontStyle = FontStyle.Italic, identifier = "${identifier}-italic") }

    val Bold: TypographyResource
        by lazy(LazyThreadSafetyMode.PUBLICATION) { copy(fontWeight = FontWeight.Bold, identifier = "${identifier}-bold") }

    val ExtraBold: TypographyResource
        by lazy(LazyThreadSafetyMode.PUBLICATION) { copy(fontWeight = FontWeight.ExtraBold, identifier = "${identifier}-extrabold") }

    val SemiBold: TypographyResource
        by lazy(LazyThreadSafetyMode.PUBLICATION) { copy(fontWeight = FontWeight.SemiBold, identifier = "${identifier}-semibold") }

    val Medium: TypographyResource
        by lazy(LazyThreadSafetyMode.PUBLICATION) { copy(fontWeight = FontWeight.Medium, identifier = "${identifier}-medium") }

    val Normal: TypographyResource
        by lazy(LazyThreadSafetyMode.PUBLICATION) { copy(fontWeight = FontWeight.Normal, identifier = "${identifier}-normal") }

    val Light: TypographyResource
        by lazy(LazyThreadSafetyMode.PUBLICATION) { copy(fontWeight = FontWeight.Light, identifier = "${identifier}-light") }

    /**
     * Tabular figures, so digits share one width and a changing number does not shift the
     * text around it. Every figure in the app uses this; `AppTheme.type.numeric*` already has it.
     */
    val Tabular: TypographyResource
        by lazy(LazyThreadSafetyMode.PUBLICATION) { copy(fontFeatureSettings = TABULAR_FIGURES, identifier = "${identifier}-tnum") }
}

internal const val TABULAR_FIGURES = "tnum"

interface Typography {
    val Brand: BrandTypography
    val Display: DisplayTypography
    val Heading: HeadingTypography
    val Body: BodyTypography
    val Label: LabelTypography
    val Caption: CaptionTypography
    val Default: TypographyResource
}

interface BrandTypography {
    val B1500: TypographyResource
    val B1400: TypographyResource
    val B1300: TypographyResource
    val B1200: TypographyResource
    val B1100: TypographyResource
    val B1000: TypographyResource
    val B900: TypographyResource
    val B800: TypographyResource
}

interface DisplayTypography {
    val D1500: TypographyResource
    val D1400: TypographyResource
    val D1300: TypographyResource
    val D1200: TypographyResource
    val D1100: TypographyResource
    val D1000: TypographyResource
    val D900: TypographyResource
    val D800: TypographyResource
}

interface HeadingTypography {

    val H1100: TypographyResource

    val H1000: TypographyResource
    val H900: TypographyResource
    val H800: TypographyResource
    val H700: TypographyResource
    val H600: TypographyResource
    val H500: TypographyResource
    val H400: TypographyResource
}

interface LabelTypography {

    val L700: TypographyResource
    val L600: TypographyResource
    val L500: TypographyResource
    val L400: TypographyResource
    val L300: TypographyResource
}

interface BodyTypography {
    val B700: TypographyResource
    val B600: TypographyResource
    val B500: TypographyResource
    val B400: TypographyResource
}

interface CaptionTypography {
    val C400: TypographyResource
    val C300: TypographyResource
    val C200: TypographyResource
}

/**
 * The legacy role-named scale (`AppTheme.typography.Body.B500`, ...). Every group renders in
 * [AppFontFamily]; only Brand uses the wordmark face, [BrandFontFamily]. New code should use `AppTheme.type`.
 */
@Composable
fun rememberTypography(): Typography {
    val appFontFamily = AppFontFamily
    val brandFontFamily = BrandFontFamily

    return remember(appFontFamily, brandFontFamily) {
        DefaultTypography(
            serifFontFamily = appFontFamily,
            sansSerifFontFamily = appFontFamily,
            brandFontFamily = brandFontFamily
        )
    }
}

class DefaultTypography(
    serifFontFamily: FontFamily,
    sansSerifFontFamily: FontFamily,
    brandFontFamily: FontFamily
) : Typography {
    override val Display: DisplayTypography = DisplayTypographyImpl(serifFontFamily)

    override val Brand: BrandTypography = BrandTypographyImpl(brandFontFamily)

    override val Heading: HeadingTypography = HeadingTypographyImpl(sansSerifFontFamily)

    override val Body: BodyTypography = BodyTypographyImpl(sansSerifFontFamily)
    override val Label: LabelTypography = LabelTypographyImpl(sansSerifFontFamily)
    override val Caption: CaptionTypography = CaptionTypographyImpl(sansSerifFontFamily)

    override val Default: TypographyResource = Body.B600
}

class DisplayTypographyImpl(
    private val fontFamily: FontFamily
) : DisplayTypography {

    // Display uses tight line-height (1.1x) for visual impact
    override val D1500 = TypographyResource(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = Dimension.D1500.sp(),
        lineHeight = Dimension.D1500.lineHeight(com.opson.system.LineHeightRatio.TIGHT),
        lineBreak = LineBreak.Heading,
        identifier = "display-1500"
    )

    override val D1400 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = Dimension.D1400.sp(),
        lineHeight = Dimension.D1400.lineHeight(com.opson.system.LineHeightRatio.TIGHT),
        lineBreak = LineBreak.Heading,
        identifier = "display-1400"
    )

    override val D1300 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = Dimension.D1300.sp(),
        lineHeight = Dimension.D1300.lineHeight(com.opson.system.LineHeightRatio.TIGHT),
        lineBreak = LineBreak.Heading,
        identifier = "display-1300"
    )

    override val D1200 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = Dimension.D1200.sp(),
        lineHeight = Dimension.D1200.lineHeight(com.opson.system.LineHeightRatio.TIGHT),
        lineBreak = LineBreak.Heading,
        identifier = "display-1200"
    )

    override val D1100 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = Dimension.D1100.sp(),
        lineHeight = Dimension.D1100.lineHeight(com.opson.system.LineHeightRatio.TIGHT),
        lineBreak = LineBreak.Heading,
        identifier = "display-1100"
    )

    override val D1000 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = Dimension.D1000.sp(),
        lineHeight = Dimension.D1000.lineHeight(com.opson.system.LineHeightRatio.TIGHT),
        lineBreak = LineBreak.Heading,
        identifier = "display-1000"
    )

    override val D900 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = Dimension.D900.sp(),
        lineHeight = Dimension.D900.lineHeight(com.opson.system.LineHeightRatio.TIGHT),
        lineBreak = LineBreak.Heading,
        identifier = "display-900"
    )

    override val D800 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = Dimension.D800.sp(),
        lineHeight = Dimension.D800.lineHeight(com.opson.system.LineHeightRatio.TIGHT),
        lineBreak = LineBreak.Heading,
        identifier = "display-800"
    )
}

class BrandTypographyImpl(
    private val fontFamily: FontFamily
) : BrandTypography {

    // Brand typography uses tight line-height for visual impact
    override val B1500 = TypographyResource(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = Dimension.D1500.sp(),
        lineHeight = Dimension.D1500.lineHeight(LineHeightRatio.TIGHT),
        lineBreak = LineBreak.Heading,
        identifier = "brand-1500"
    )

    override val B1400 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = Dimension.D1400.sp(),
        lineHeight = Dimension.D1400.lineHeight(LineHeightRatio.TIGHT),
        lineBreak = LineBreak.Heading,
        identifier = "brand-1400"
    )

    override val B1300 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = Dimension.D1300.sp(),
        lineHeight = Dimension.D1300.lineHeight(LineHeightRatio.TIGHT),
        lineBreak = LineBreak.Heading,
        identifier = "brand-1300"
    )

    override val B1200 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = Dimension.D1200.sp(),
        lineHeight = Dimension.D1200.lineHeight(LineHeightRatio.TIGHT),
        lineBreak = LineBreak.Heading,
        identifier = "brand-1200"
    )

    override val B1100 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = Dimension.D1100.sp(),
        lineHeight = Dimension.D1100.lineHeight(LineHeightRatio.TIGHT),
        lineBreak = LineBreak.Heading,
        identifier = "brand-1100"
    )

    override val B1000 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = Dimension.D1000.sp(),
        lineHeight = Dimension.D1000.lineHeight(LineHeightRatio.TIGHT),
        lineBreak = LineBreak.Heading,
        identifier = "brand-1000"
    )

    override val B900 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = Dimension.D900.sp(),
        lineHeight = Dimension.D900.lineHeight(LineHeightRatio.TIGHT),
        lineBreak = LineBreak.Heading,
        identifier = "brand-900"
    )

    override val B800 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = Dimension.D800.sp(),
        lineHeight = Dimension.D800.lineHeight(LineHeightRatio.TIGHT),
        lineBreak = LineBreak.Heading,
        identifier = "brand-800"
    )
}

class HeadingTypographyImpl(
    private val fontFamily: FontFamily
) : HeadingTypography {

    // Headings use moderate line-height (1.25x) for clear hierarchy
    override val H1100 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = Dimension.D1100.sp(),
        lineHeight = Dimension.D1100.lineHeight(LineHeightRatio.MODERATE),
        lineBreak = LineBreak.Heading,
        identifier = "heading-1100"
    )
    override val H1000 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = Dimension.D1000.sp(),
        lineHeight = Dimension.D1000.lineHeight(LineHeightRatio.MODERATE),
        lineBreak = LineBreak.Heading,
        identifier = "heading-1000"
    )
    override val H900 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = Dimension.D900.sp(),
        lineHeight = Dimension.D900.lineHeight(LineHeightRatio.MODERATE),
        lineBreak = LineBreak.Heading,
        identifier = "heading-900"
    )

    override val H800 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = Dimension.D800.sp(),
        lineHeight = Dimension.D800.lineHeight(LineHeightRatio.MODERATE),
        lineBreak = LineBreak.Heading,
        identifier = "heading-800"
    )

    override val H700 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = Dimension.D700.sp(),
        lineHeight = Dimension.D700.lineHeight(LineHeightRatio.MODERATE),
        lineBreak = LineBreak.Heading,
        identifier = "heading-700"
    )

    override val H600 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = Dimension.D600.sp(),
        lineHeight = Dimension.D600.lineHeight(LineHeightRatio.MODERATE),
        lineBreak = LineBreak.Heading,
        identifier = "heading-600"
    )

    override val H500 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = Dimension.D500.sp(),
        lineHeight = Dimension.D500.lineHeight(LineHeightRatio.MODERATE),
        lineBreak = LineBreak.Heading,
        identifier = "heading-500"
    )

    override val H400 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = Dimension.D400.sp(),
        lineHeight = Dimension.D400.lineHeight(LineHeightRatio.MODERATE),
        lineBreak = LineBreak.Heading,
        identifier = "heading-400"
    )
}

class LabelTypographyImpl(
    private val fontFamily: FontFamily
) : LabelTypography {

    // Labels use compact line-height (1.2x) for tight UI elements

    override val L700 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = Dimension.D700.sp(),
        lineHeight = Dimension.D700.lineHeight(LineHeightRatio.COMPACT),
        lineBreak = LineBreak.Simple,
        identifier = "label-700"
    )

    override val L600 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = Dimension.D600.sp(),
        lineHeight = Dimension.D600.lineHeight(LineHeightRatio.COMPACT),
        lineBreak = LineBreak.Simple,
        identifier = "label-600"
    )

    override val L500 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = Dimension.D500.sp(),
        lineHeight = Dimension.D500.lineHeight(LineHeightRatio.COMPACT),
        lineBreak = LineBreak.Simple,
        identifier = "label-500"
    )

    override val L400 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = Dimension.D400.sp(),
        lineHeight = Dimension.D400.lineHeight(LineHeightRatio.COMPACT),
        lineBreak = LineBreak.Simple,
        identifier = "label-400"
    )

    override val L300 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = Dimension.D300.sp(),
        lineHeight = Dimension.D300.lineHeight(LineHeightRatio.COMPACT),
        lineBreak = LineBreak.Simple,
        identifier = "label-300"
    )
}

class BodyTypographyImpl(
    private val fontFamily: FontFamily
) : BodyTypography {

    // Body uses comfortable line-height (1.5x) for optimal reading
    override val B700 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = Dimension.D700.sp(),
        lineHeight = Dimension.D700.lineHeight(LineHeightRatio.COMFORTABLE),
        lineBreak = LineBreak.Paragraph,
        identifier = "body-700"
    )

    override val B600 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = Dimension.D600.sp(),
        lineHeight = Dimension.D600.lineHeight(LineHeightRatio.COMFORTABLE),
        lineBreak = LineBreak.Paragraph,
        identifier = "body-600"
    )

    override val B500 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = Dimension.D500.sp(),
        lineHeight = Dimension.D500.lineHeight(LineHeightRatio.COMFORTABLE),
        lineBreak = LineBreak.Paragraph,
        identifier = "body-500"
    )

    override val B400 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = Dimension.D400.sp(),
        lineHeight = Dimension.D400.lineHeight(LineHeightRatio.COMFORTABLE),
        lineBreak = LineBreak.Paragraph,
        identifier = "body-400"
    )
}

class CaptionTypographyImpl(
    private val fontFamily: FontFamily
) : CaptionTypography {

    // Captions use standard line-height (1.4x) for small text legibility
    override val C400 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = Dimension.D400.sp(),
        lineHeight = Dimension.D400.lineHeight(LineHeightRatio.STANDARD),
        lineBreak = LineBreak.Simple,
        identifier = "caption-400"
    )

    override val C300 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = Dimension.D300.sp(),
        lineHeight = Dimension.D300.lineHeight(LineHeightRatio.STANDARD),
        lineBreak = LineBreak.Simple,
        identifier = "caption-300"
    )

    override val C200 = TypographyResource(
        fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = Dimension.D200.sp(),
        lineHeight = Dimension.D200.lineHeight(LineHeightRatio.STANDARD),
        lineBreak = LineBreak.Simple,
        identifier = "caption-200"
    )
}

/**
 * Typography preview composables that render like a professional design specification.
 * Shows font name, size, weight, line height, and example text for each typography style.
 */

@Composable
private fun TypographySpecItem(
    name: String,
    typographyResource: TypographyResource,
    exampleText: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimension.D600)
    ) {
        // Spec details
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Style name
                Text(
                    text = name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF000000)
                )

                // Technical specs
                Text(
                    text = buildString {
                        append("${typographyResource.fontSize} / ")
                        append("${typographyResource.lineHeight} • ")
                        append(typographyResource.fontWeight.toString())
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Identifier tag
            Text(
                text = typographyResource.identifier,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF666666),
                modifier = Modifier
                    .background(
                        Color(0xFFEEEEEE),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // Example text with actual typography
        Text(
            text = exampleText,
            style = typographyResource.style,
            color = Color(0xFF000000),
            modifier = Modifier.padding(top = Dimension.D500)
        )

        HorizontalDivider(
            modifier = Modifier.padding(top = Dimension.D600),
            color = Color(0xFFEEEEEE)
        )
    }
}

@Preview(widthDp = 800, heightDp = 2400)
@Composable
private fun PreviewBrandTypography() {
    PreviewContent {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(Dimension.D800)
        ) {
            item {
                Text(
                    text = "Brand Typography",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF000000),
                    modifier = Modifier.padding(bottom = Dimension.D500)
                )
                Text(
                    text = "The wordmark face: Young Serif",
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(bottom = Dimension.D1000)
                )
            }

            item {
                TypographySpecItem(
                    name = "Brand 1500",
                    typographyResource = AppTheme.typography.Brand.B1500,
                    exampleText = "Opson"
                )
            }

            item {
                TypographySpecItem(
                    name = "Brand 1400",
                    typographyResource = AppTheme.typography.Brand.B1400,
                    exampleText = "Opson"
                )
            }

            item {
                TypographySpecItem(
                    name = "Brand 1300",
                    typographyResource = AppTheme.typography.Brand.B1300,
                    exampleText = "Opson"
                )
            }

            item {
                TypographySpecItem(
                    name = "Brand 1200",
                    typographyResource = AppTheme.typography.Brand.B1200,
                    exampleText = "Opson"
                )
            }

            item {
                TypographySpecItem(
                    name = "Brand 1100",
                    typographyResource = AppTheme.typography.Brand.B1100,
                    exampleText = "Opson"
                )
            }

            item {
                TypographySpecItem(
                    name = "Brand 1000",
                    typographyResource = AppTheme.typography.Brand.B1000,
                    exampleText = "Opson"
                )
            }

            item {
                TypographySpecItem(
                    name = "Brand 900",
                    typographyResource = AppTheme.typography.Brand.B900,
                    exampleText = "Opson"
                )
            }

            item {
                TypographySpecItem(
                    name = "Brand 800",
                    typographyResource = AppTheme.typography.Brand.B800,
                    exampleText = "Opson"
                )
            }
        }
    }
}

@Preview(widthDp = 800, heightDp = 2400, )
@Composable
private fun PreviewDisplayTypography() {
    PreviewContent {


        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(Dimension.D800)
        ) {
            item {
                Text(
                    text = "Display Typography",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF000000),
                    modifier = Modifier.padding(bottom = Dimension.D500)
                )
                Text(
                    text = "Large, impactful text for hero sections and splash screens",
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(bottom = Dimension.D1000)
                )
            }

            item {
                TypographySpecItem(
                    name = "Display 1500",
                    typographyResource = AppTheme.typography.Display.D1500,
                    exampleText = "Hero Text"
                )
            }

            item {
                TypographySpecItem(
                    name = "Display 1400",
                    typographyResource = AppTheme.typography.Display.D1400,
                    exampleText = "Welcome Back"
                )
            }

            item {
                TypographySpecItem(
                    name = "Display 1300",
                    typographyResource = AppTheme.typography.Display.D1300,
                    exampleText = "Start Your Journey"
                )
            }

            item {
                TypographySpecItem(
                    name = "Display 1200",
                    typographyResource = AppTheme.typography.Display.D1200,
                    exampleText = "Discover Something New"
                )
            }

            item {
                TypographySpecItem(
                    name = "Display 1100",
                    typographyResource = AppTheme.typography.Display.D1100,
                    exampleText = "Transform Your Experience"
                )
            }

            item {
                TypographySpecItem(
                    name = "Display 1000",
                    typographyResource = AppTheme.typography.Display.D1000,
                    exampleText = "Elevate Your Workflow Today"
                )
            }

            item {
                TypographySpecItem(
                    name = "Display 900",
                    typographyResource = AppTheme.typography.Display.D900,
                    exampleText = "Building Something Amazing Together"
                )
            }

            item {
                TypographySpecItem(
                    name = "Display 800",
                    typographyResource = AppTheme.typography.Display.D800,
                    exampleText = "Empowering Teams to Achieve More Every Day"
                )
            }
        }
    }
}

@Preview(widthDp = 800, heightDp = 1600, )
@Composable
private fun PreviewHeadingTypography() {
    PreviewContent {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(Dimension.D800)
        ) {
            item {
                Text(
                    text = "Heading Typography",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF000000),
                    modifier = Modifier.padding(bottom = Dimension.D500)
                )
                Text(
                    text = "Bold text for page titles, section headers, and establishing hierarchy",
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(bottom = Dimension.D1000)
                )
            }

            item {
                TypographySpecItem(
                    name = "Heading 1000",
                    typographyResource = AppTheme.typography.Heading.H1100,
                    exampleText = "Page Title"
                )
            }
            item {
                TypographySpecItem(
                    name = "Heading H1000",
                    typographyResource = AppTheme.typography.Heading.H1000,
                    exampleText = "Page Title"
                )
            }

            item {
                TypographySpecItem(
                    name = "Heading 900",
                    typographyResource = AppTheme.typography.Heading.H900,
                    exampleText = "Page Title"
                )
            }

            item {
                TypographySpecItem(
                    name = "Heading 800",
                    typographyResource = AppTheme.typography.Heading.H800,
                    exampleText = "Section Header"
                )
            }

            item {
                TypographySpecItem(
                    name = "Heading 700",
                    typographyResource = AppTheme.typography.Heading.H700,
                    exampleText = "Major Component Title"
                )
            }

            item {
                TypographySpecItem(
                    name = "Heading 600",
                    typographyResource = AppTheme.typography.Heading.H600,
                    exampleText = "Card Title and Subsections"
                )
            }

            item {
                TypographySpecItem(
                    name = "Heading 500",
                    typographyResource = AppTheme.typography.Heading.H500,
                    exampleText = "Smaller Card Headers and Lists"
                )
            }

            item {
                TypographySpecItem(
                    name = "Heading 400",
                    typographyResource = AppTheme.typography.Heading.H400,
                    exampleText = "Minimal Headers and Emphasis Points"
                )
            }
        }
    }
}

@Preview(widthDp = 800, heightDp = 1400, )
@Composable
private fun PreviewBodyTypography() {
    PreviewContent {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(Dimension.D800)
        ) {
            item {
                Text(
                    text = "Body Typography",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF000000),
                    modifier = Modifier.padding(bottom = Dimension.D500)
                )
                Text(
                    text = "Regular weight text optimized for comfortable reading of paragraphs and descriptions",
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(bottom = Dimension.D1000)
                )
            }

            item {
                TypographySpecItem(
                    name = "Body 700",
                    typographyResource = AppTheme.typography.Body.B700,
                    exampleText = "The quick brown fox jumps over the lazy dog. This is the largest body text size, ideal for lead paragraphs and introductory content that needs extra emphasis."
                )
            }

            item {
                TypographySpecItem(
                    name = "Body 600",
                    typographyResource = AppTheme.typography.Body.B600,
                    exampleText = "The quick brown fox jumps over the lazy dog. This size works well for general content and descriptions where readability is important but space is moderate."
                )
            }

            item {
                TypographySpecItem(
                    name = "Body 500",
                    typographyResource = AppTheme.typography.Body.B500,
                    exampleText = "The quick brown fox jumps over the lazy dog. The default body text size, perfect for most paragraph content and detailed descriptions throughout your app."
                )
            }

            item {
                TypographySpecItem(
                    name = "Body 400",
                    typographyResource = AppTheme.typography.Body.B400,
                    exampleText = "The quick brown fox jumps over the lazy dog. The smallest body text, useful for dense information or secondary content where space efficiency matters."
                )
            }
        }
    }
}

@Preview(widthDp = 800, heightDp = 1100, )
@Composable
private fun PreviewLabelTypography() {
    PreviewContent {

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(Dimension.D800)
        ) {
            item {
                Text(
                    text = "Label Typography",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF000000),
                    modifier = Modifier.padding(bottom = Dimension.D500)
                )
                Text(
                    text = "Medium weight text for UI elements like buttons, form labels, and interactive components",
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(bottom = Dimension.D1000)
                )
            }

            item {
                TypographySpecItem(
                    name = "Label 700",
                    typographyResource = AppTheme.typography.Label.L700,
                    exampleText = "Button"
                )
            }

            item {
                TypographySpecItem(
                    name = "Label 600",
                    typographyResource = AppTheme.typography.Label.L600,
                    exampleText = "Primary Button"
                )
            }

            item {
                TypographySpecItem(
                    name = "Label 500",
                    typographyResource = AppTheme.typography.Label.L500,
                    exampleText = "Form Field Label"
                )
            }

            item {
                TypographySpecItem(
                    name = "Label 400",
                    typographyResource = AppTheme.typography.Label.L400,
                    exampleText = "Chip or Tag Text"
                )
            }

            item {
                TypographySpecItem(
                    name = "Label 300",
                    typographyResource = AppTheme.typography.Label.L300,
                    exampleText = "Small Button or Badge"
                )
            }
        }
    }
}

@Preview(widthDp = 800, heightDp = 1000, )
@Composable
private fun PreviewCaptionTypography() {
    PreviewContent {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(Dimension.D800)
        ) {
            item {
                Text(
                    text = "Caption Typography",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF000000),
                    modifier = Modifier.padding(bottom = Dimension.D500)
                )
                Text(
                    text = "Smallest text in the system for timestamps, metadata, legal text, and helper information",
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(bottom = Dimension.D1000)
                )
            }

            item {
                TypographySpecItem(
                    name = "Caption 400",
                    typographyResource = AppTheme.typography.Caption.C400,
                    exampleText = "Posted 2 hours ago"
                )
            }

            item {
                TypographySpecItem(
                    name = "Caption 300",
                    typographyResource = AppTheme.typography.Caption.C300,
                    exampleText = "Updated yesterday at 3:45 PM"
                )
            }

            item {
                TypographySpecItem(
                    name = "Caption 200",
                    typographyResource = AppTheme.typography.Caption.C200,
                    exampleText = "© 2025 All rights reserved. Terms & Conditions apply."
                )
            }
        }
    }
}
