package com.opson.libraries.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.Dimension
import com.opson.system.typography.TypographyResource
import org.jetbrains.compose.ui.tooling.preview.Preview

private const val TYPE_SUBTITLE =
    "All in Atkinson Hyperlegible Next except Brand, the wordmark face (Young Serif). Higher number = bigger. " +
        "Modifiers chain off any token: .Italic, .Bold, .SemiBold, …"

/** The type page body. Split into two halves so [DesignSystemPreview] can lay it out in two
 *  columns; call this combined version for a single-column page. */
@Composable
internal fun TypographyCatalogBody() {
    TypographyCatalogBodyHeadlines()
    TypographyCatalogBodyText()
}

/** First half, the big styles: Display, Brand and Heading. */
@Composable
internal fun TypographyCatalogBodyHeadlines() {
    val t = AppTheme.typography

    CatalogSection(
        "Display",
        "Large headlines.",
    ) {
        TypeRow("Display.D1500", t.Display.D1500, "Welcome back")
        TypeRow("Display.D1300", t.Display.D1300, "Welcome back")
        TypeRow("Display.D1100", t.Display.D1100, "Welcome back")
        TypeRow("Display.D1000", t.Display.D1000, "Welcome back")
        TypeRow("Display.D900", t.Display.D900, "Welcome back")
        TypeRow("Display.D900.Italic", t.Display.D900.Italic, "Daily summary")
        TypeRow("Display.D800.Italic", t.Display.D800.Italic, "Sign out?")
    }

    CatalogSection(
        "Brand",
        "The brand face — splash, logotype moments. Not for running UI.",
    ) {
        TypeRow("Brand.B1300", t.Brand.B1300, "Opson")
        TypeRow("Brand.B1100", t.Brand.B1100, "Opson")
        TypeRow("Brand.B900", t.Brand.B900, "Opson")
    }

    CatalogSection(
        "Heading",
        "Section and screen titles. Step down the scale as the heading nests deeper.",
    ) {
        TypeRow("Heading.H1100", t.Heading.H1100, "Activity")
        TypeRow("Heading.H900", t.Heading.H900, "Activity")
        TypeRow("Heading.H800", t.Heading.H800, "Activity")
        TypeRow("Heading.H700", t.Heading.H700, "Activity")
        TypeRow("Heading.H600", t.Heading.H600, "Activity")
        TypeRow("Heading.H500", t.Heading.H500, "Activity")
        TypeRow("Heading.H400", t.Heading.H400, "Activity")
    }
}

/** Second half, running text: Body, Label and Caption. */
@Composable
internal fun TypographyCatalogBodyText() {
    val t = AppTheme.typography

    CatalogSection(
        "Body",
        "Running prose — descriptions, dialog bodies, explainers. Optimized for reading, not labels.",
    ) {
        TypeRow("Body.B700", t.Body.B700, "Your changes sync automatically across devices.")
        TypeRow("Body.B600", t.Body.B600, "Your changes sync automatically across devices.")
        TypeRow("Body.B500", t.Body.B500, "Your changes sync automatically across devices.")
        TypeRow("Body.B400", t.Body.B400, "Your changes sync automatically across devices.")
    }

    CatalogSection(
        "Label (UI elements)",
        "Buttons, chips, tabs — tight line height tuned for single-line UI, not paragraphs.",
    ) {
        TypeRow("Label.L700", t.Label.L700, "Continue")
        TypeRow("Label.L600", t.Label.L600, "Continue")
        TypeRow("Label.L500", t.Label.L500, "Continue")
        TypeRow("Label.L400", t.Label.L400, "Continue")
        TypeRow("Label.L300", t.Label.L300, "Continue")
    }

    CatalogSection(
        "Caption (metadata)",
        "The smallest type — timestamps, counts, helper text. Pair with textSecondary.",
    ) {
        TypeRow("Caption.C400", t.Caption.C400, "42m remaining")
        TypeRow("Caption.C300", t.Caption.C300, "42m remaining")
        TypeRow("Caption.C200", t.Caption.C200, "42m remaining")
    }
}

@Preview(widthDp = 1100, heightDp = 2600)
@Composable
private fun TypographyCatalog() {
    CatalogPage(title = "Typography", subtitle = TYPE_SUBTITLE) { TypographyCatalogBody() }
}

@Composable
private fun TypeRow(token: String, typography: TypographyResource, sample: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimension.D700),
    ) {
        Text(
            text = token,
            typography = AppTheme.typography.Caption.C300,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.width(180.dp),
        )
        Column { Text(text = sample, typography = typography) }
    }
}
