package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.system.thenIf
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The handoff's card: surface color, radius 22, padding 16 by 20, running edge to edge.
 *
 * Pages keep the 20dp page padding, and cards cancel it with a negative margin so they touch
 * the screen edges while everything else stays inset. Compose has no negative padding, so
 * [edgeToEdge] widens the card by [bleed] on each side with [bleedHorizontal] instead. Pass
 * `edgeToEdge = false` for a card that sits inside the padding (inside a sheet, for example).
 */
@Composable
fun EdgeCard(
    modifier: Modifier = Modifier,
    edgeToEdge: Boolean = true,
    bleed: Dp = AppTheme.spacing.page,
    color: ColorResource = AppTheme.colors.surface,
    contentPadding: PaddingValues = EdgeCardDefaults.ContentPadding,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(EdgeCardDefaults.ContentGap),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .thenIf(edgeToEdge) { bleedHorizontal(bleed) }
            .clip(AppTheme.shapes.card)
            .background(color)
            .thenIf(onClick != null) { bounceClick(scaleDown = 0.98f, onClick = onClick!!) }
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

object EdgeCardDefaults {
    val ContentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
    val ContentGap = 10.dp

    /** A card holding a list of divided rows (More, the presets, Support's sharing rows). */
    val ListPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)

    /** A card inside the page padding holding text and buttons (Support, Targets). */
    val InsetPadding = PaddingValues(18.dp)

    /** The gap between a card's sections when [ContentGap] is too tight (Targets, the friction sheet). */
    val SectionGap = 14.dp
}

/**
 * Lays the element out [amount] wider on each side than its parent allows, centred on the same
 * spot, so it reaches past the parent's horizontal padding. The CSS `margin: 0 -20px` trick.
 */
fun Modifier.bleedHorizontal(amount: Dp): Modifier = layout { measurable, constraints ->
    // An unbounded parent (a horizontal scroll) has no padding edge to reach past.
    val side = if (constraints.hasBoundedWidth) amount.roundToPx() else 0
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + side * 2,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + side * 2 else constraints.maxWidth,
        )
    )
    layout((placeable.width - side * 2).coerceAtLeast(0), placeable.height) {
        placeable.place(x = -side, y = 0)
    }
}

@Preview(widthDp = 390)
@Composable
private fun EdgeCardPreview() {
    PreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
            EdgeCard {
                Text("Breakfast", typography = AppTheme.type.cardTitle)
                Text("Edge to edge against the page padding", typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted)
            }
            EdgeCard(edgeToEdge = false) {
                Text("Inset card", typography = AppTheme.type.cardTitle)
            }
        }
    }
}
