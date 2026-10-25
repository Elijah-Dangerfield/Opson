package com.opson.libraries.ui.catalog

import androidx.compose.runtime.Composable
import com.opson.system.AppTheme

/*
 * Color catalog content. Broken into granular section-group composables so the (large) color
 * area can be spread across columns and split across multiple previews — a single combined preview
 * of all of color is past the IDE's max render size. The previews themselves live in
 * DesignSystemPreview.kt (ColorPrimaryPreview / ColorSupportPreview).
 */

/** Surfaces + the text ramp. */
@Composable
internal fun ColorSurfacesContent() {
    val c = AppTheme.colors
    CatalogSection(
        "Surfaces",
        "The neutral elevation ladder. Each step sits visually 'on top of' the one before it.",
    ) {
        SwatchFlow {
            ColorRow("background", c.background, c.onBackground, "App canvas, scaffolds, and the base of full-screen sheets.")
            ColorRow("surfacePrimary", c.surfacePrimary, c.onSurfacePrimary, "The default container — Card, sheets, menus, list rows.")
            ColorRow("surfaceSecondary", c.surfaceSecondary, c.onSurfaceSecondary, "A thing ON a surface — text inputs, nested containers, selected rows.")
            ColorRow("surfaceTertiary", c.surfaceTertiary, c.onSurfaceTertiary, "The highest layer — pressed states, floating menus, tooltips.")
            ColorRow("surfaceDisabled", c.surfaceDisabled, c.onSurfaceDisabled, "Fill for a disabled control (e.g. a disabled filled button).")
            ColorRow("backgroundOverlay", c.backgroundOverlay, null, "Dims the screen behind a modal or bottom sheet.")
            ColorRow("shadow", c.shadow, null, "Drop-shadow color cast by elevated surfaces.")
        }
    }

    CatalogSection(
        "Text",
        "One foreground ramp that works on the background and every surface. Step down for less emphasis.",
    ) {
        SwatchFlow {
            ColorRow("text", c.text, c.background, "Primary text and active icons.")
            ColorRow("textSecondary", c.textSecondary, c.background, "Supporting text, captions, inactive icons, metadata.")
            ColorRow("textDisabled", c.textDisabled, c.background, "Disabled text and icons.")
        }
    }
}

/** The two accent pairs. */
@Composable
internal fun ColorAccents() {
    val c = AppTheme.colors
    CatalogSection(
        "Accent · Primary",
        "The brand. The main CTA, focus rings, selected states.",
    ) {
        SwatchFlow {
            ColorRow("accentPrimary", c.accentPrimary, c.onAccentPrimary, "Primary buttons, focus, selection — the one thing you want tapped.")
            ColorRow("onAccentPrimary", c.onAccentPrimary, c.accentPrimary, "Text and icons rendered on an accentPrimary fill.")
        }
    }

    CatalogSection(
        "Accent · Secondary",
        "The second brand accent — only for the rare screen with two primary-level CTAs in different colors.",
    ) {
        SwatchFlow {
            ColorRow("accentSecondary", c.accentSecondary, c.onAccentSecondary, "A second, distinct primary-level action (e.g. 'Upgrade' beside 'Continue').")
            ColorRow("onAccentSecondary", c.onAccentSecondary, c.accentSecondary, "Text and icons on an accentSecondary fill.")
        }
    }
}

/** Status states + borders. */
@Composable
internal fun ColorStatusBorders() {
    val c = AppTheme.colors
    CatalogSection(
        "Status",
        "Universal state meaning. Reach for a role, never a raw color.",
    ) {
        SwatchFlow {
            ColorRow("status.okay", c.status.okay, null, "Positive confirmation — saved, completed, synced.")
            ColorRow("status.warning", c.status.warning, null, "Caution — expiring soon, degraded, risky action.")
            ColorRow("status.bad", c.status.bad, null, "A failing state — sync broken, service down.")
            ColorRow("danger", c.danger, c.danger.onColor, "Errors and destructive actions (delete, sign out).")
        }
    }

    CatalogSection(
        "Borders",
        "Edges and dividers. Strength signals interaction state.",
    ) {
        SwatchFlow {
            ColorRow("border", c.border, null, "Default edges, dividers, input rest state.")
            ColorRow("borderSecondary", c.borderSecondary, null, "Focused / selected edges, and quiet rest rings (radio, switch).")
            ColorRow("borderDisabled", c.borderDisabled, null, "Edges of disabled controls.")
        }
    }
}
