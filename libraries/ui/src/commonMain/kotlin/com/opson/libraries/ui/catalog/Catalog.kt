package com.opson.libraries.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.libraries.ui.system.color.toHexString
import com.opson.system.AppTheme
import com.opson.system.Dimension
import com.opson.system.Radii

/*
 * # Design-system catalog
 *
 * A browsable, documentation-style tour of the system — not a screen, a *reference*. Open
 * [DesignSystemPreview] for the grouped token pages, or any single page preview here for one
 * area. Every token / component is shown live next to a short "when to use it" note, so the
 * catalog doubles as the spec.
 *
 * These are intentionally wide (widthDp/heightDp set well past a phone) — read them like a page,
 * not a device. Keep catalog content here, not scattered across component files, so this stays the
 * single source of truth for reviewing the system.
 *
 * The catalog is developer-facing spec copy, never user-facing UI, so its labels are plain
 * constants rather than `:libraries:resources` strings.
 */

/** The "Aa" glyph shown on a swatch in its on-color. */
private const val SampleGlyph = "Aa"

/** Page heading: a big title and a one-line intro. */
@Composable
internal fun CatalogHeader(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimension.D200)) {
        Text(text = title, typography = AppTheme.typography.Display.D1100)
        if (subtitle != null) {
            Text(
                text = subtitle,
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
            )
        }
    }
}

/** Root for a single-area catalog page: themed background, header, scrollable column of sections. */
@Composable
internal fun CatalogPage(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    PreviewContent {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Dimension.D1000),
            verticalArrangement = Arrangement.spacedBy(Dimension.D900),
        ) {
            CatalogHeader(title, subtitle)
            content()
        }
    }
}

/** A titled block within a page, with an optional one-line "what / when" description. */
@Composable
internal fun CatalogSection(
    title: String,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimension.D500)) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimension.D100)) {
            Text(text = title, typography = AppTheme.typography.Heading.H500)
            if (description != null) {
                Text(
                    text = description,
                    typography = AppTheme.typography.Body.B400,
                    color = AppTheme.colors.textSecondary,
                )
            }
        }
        content()
    }
}

/**
 * One color token as a documentation row: a swatch (with an "Aa" in its on-color), the token name +
 * hex, and a "when / where to use it" note. The ladder of these reads like a spec table.
 */
@Composable
internal fun ColorRow(
    role: String,
    resource: ColorResource,
    onColor: ColorResource?,
    usage: String,
) {
    Row(
        // Capped at 500dp so a ladder of these wraps into columns on a wide canvas
        // (see SwatchFlow) instead of one tall single-file stack, but fills the width
        // on a narrow one instead of clipping.
        modifier = Modifier.widthIn(max = 500.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimension.D600),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(width = 104.dp, height = 60.dp)
                .clip(Radii.Card.shape)
                .background(resource.color)
                .border(1.dp, AppTheme.colors.border.color, Radii.Card.shape)
                .padding(Dimension.D300),
        ) {
            if (onColor != null) {
                Text(text = SampleGlyph, typography = AppTheme.typography.Label.L500, color = onColor)
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Dimension.D100),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimension.D300),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = role, typography = AppTheme.typography.Label.L600)
                Text(
                    text = "${resource.designSystemName} · ${resource.toHexString()}",
                    typography = AppTheme.typography.Caption.C200,
                    color = AppTheme.colors.textSecondary,
                )
            }
            Text(
                text = usage,
                typography = AppTheme.typography.Body.B400,
                color = AppTheme.colors.textSecondary,
            )
        }
    }
}

/** A wrapping row of swatches. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SwatchFlow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimension.D500),
        verticalArrangement = Arrangement.spacedBy(Dimension.D500),
    ) { content() }
}
