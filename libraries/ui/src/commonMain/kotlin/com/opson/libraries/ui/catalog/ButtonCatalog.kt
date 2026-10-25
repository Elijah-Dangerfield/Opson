package com.opson.libraries.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.opson.libraries.ui.components.button.Button
import com.opson.libraries.ui.components.button.ButtonAccent
import com.opson.libraries.ui.components.button.ButtonSize
import com.opson.libraries.ui.components.button.ButtonStyle
import com.opson.libraries.ui.components.button.ButtonType
import com.opson.libraries.ui.components.text.Text
import com.opson.system.Dimension

internal const val BUTTON_SUBTITLE =
    "type is the only emphasis semantic (Primary > Secondary > Ghost; Danger is destructive). style " +
        "picks the treatment; accent recolors a filled Primary; deep opts a filled button into the 3D lip."

// Catalog sample labels. Named constants (not inline literals in the Text
// calls) because this is developer-facing spec copy, not user-facing UI —
// see the VerifyStrings note in Catalog.kt.
private const val PRIMARY = "Primary"
private const val SECONDARY = "Secondary"
private const val GHOST = "Ghost"
private const val DANGER = "Danger"
private const val OUTLINED = "Outlined"
private const val TEXT = "Text"
private const val LARGE = "Large"
private const val MEDIUM = "Medium"
private const val SMALL = "Small"
private const val EXTRA_SMALL = "ExtraSmall"

/** The button page body. Reused by [DesignSystemPreview]. */
@Composable
internal fun ButtonCatalogBody() {
    CatalogSection(
        "Filled — emphasis ladder",
        "The default treatment. One Primary per screen (the main CTA); Secondary for the supporting action; Danger for destructive.",
    ) {
        ButtonRow {
            Button(type = ButtonType.Primary, onClick = {}) { Text(PRIMARY) }
            Button(type = ButtonType.Secondary, style = ButtonStyle.Filled, onClick = {}) { Text(SECONDARY) }
            Button(type = ButtonType.Ghost, onClick = {}) { Text(GHOST) }
            Button(type = ButtonType.Danger, onClick = {}) { Text(DANGER) }
        }
    }

    CatalogSection(
        "Outlined",
        "One step down in emphasis. Good on busy surfaces, or as the secondary action next to a filled Primary.",
    ) {
        ButtonRow {
            Button(type = ButtonType.Primary, style = ButtonStyle.Outlined, onClick = {}) { Text(PRIMARY) }
            Button(type = ButtonType.Secondary, style = ButtonStyle.Outlined, onClick = {}) { Text(SECONDARY) }
            Button(type = ButtonType.Danger, style = ButtonStyle.Outlined, onClick = {}) { Text(DANGER) }
        }
    }

    CatalogSection(
        "Text",
        "Lowest weight — inline links and dismiss actions (\"Not now\", \"Forgot password?\").",
    ) {
        ButtonRow {
            Button(type = ButtonType.Primary, style = ButtonStyle.Text, onClick = {}) { Text(PRIMARY) }
            Button(type = ButtonType.Secondary, style = ButtonStyle.Text, onClick = {}) { Text(SECONDARY) }
            Button(type = ButtonType.Ghost, style = ButtonStyle.Text, onClick = {}) { Text(GHOST) }
            Button(type = ButtonType.Danger, style = ButtonStyle.Text, onClick = {}) { Text(DANGER) }
        }
    }

    CatalogSection(
        "Accent",
        "A filled Primary recolored — only for the rare screen with two primary-level CTAs in different brand colors.",
    ) {
        ButtonRow {
            Button(type = ButtonType.Primary, accent = ButtonAccent.Primary, onClick = {}) { Text(PRIMARY) }
            Button(type = ButtonType.Primary, accent = ButtonAccent.Secondary, onClick = {}) { Text(SECONDARY) }
        }
    }

    CatalogSection(
        "Deep — the opt-in 3D lip",
        "A hard band under the face that the button drops onto when pressed. Opt-in per call site; the default treatment stays flat.",
    ) {
        ButtonRow {
            Button(type = ButtonType.Primary, deep = true, onClick = {}) { Text(PRIMARY) }
            Button(type = ButtonType.Secondary, style = ButtonStyle.Filled, deep = true, onClick = {}) { Text(SECONDARY) }
            Button(type = ButtonType.Danger, deep = true, onClick = {}) { Text(DANGER) }
        }
    }

    CatalogSection(
        "Disabled",
        "The only non-default interaction state (this is a touch app). Fill flattens, lip drops, color mutes.",
    ) {
        ButtonRow {
            Button(type = ButtonType.Primary, enabled = false, onClick = {}) { Text(PRIMARY) }
            Button(type = ButtonType.Secondary, style = ButtonStyle.Outlined, enabled = false, onClick = {}) { Text(OUTLINED) }
            Button(type = ButtonType.Ghost, style = ButtonStyle.Text, enabled = false, onClick = {}) { Text(TEXT) }
            Button(type = ButtonType.Danger, enabled = false, onClick = {}) { Text(DANGER) }
        }
    }

    CatalogSection(
        "Size ramp",
        "Large for primary CTAs and dialogs; Medium is the workhorse; Small / ExtraSmall for dense rows, chips, and inline actions.",
    ) {
        ButtonRow {
            Button(size = ButtonSize.Large, onClick = {}) { Text(LARGE) }
            Button(size = ButtonSize.Medium, onClick = {}) { Text(MEDIUM) }
            Button(size = ButtonSize.Small, onClick = {}) { Text(SMALL) }
            Button(size = ButtonSize.ExtraSmall, onClick = {}) { Text(EXTRA_SMALL) }
        }
    }
}

@Composable
private fun ButtonRow(content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimension.D500),
    ) { content() }
}
