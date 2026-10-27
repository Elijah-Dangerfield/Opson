package com.opson.libraries.ui.components.icon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.system.color.MealTone
import com.opson.system.color.ToneColors
import com.opson.system.thenIf
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Sizes for a [ToneIcon]: [box] is the space it takes in a layout (what the handoff's tinted
 * square took, so rows keep their alignment) and [icon] the glyph drawn in it. [Medium] is the
 * one on cards and rows.
 */
enum class ToneIconSize(val box: Dp, val icon: Dp) {
    /** Preset rows and compact lists. */
    Small(box = 36.dp, icon = 26.dp),

    /** Meal cards, food rows, Foods create rows. */
    Medium(box = 38.dp, icon = 28.dp),

    /** Touch-size icons. */
    Large(box = 44.dp, icon = 30.dp),

    /** The glyph over an empty state ("No match for ... yet"). */
    Huge(box = 64.dp, icon = 44.dp),
}

/**
 * A colored icon in a fixed box, e.g. a meal's or water's icon. Take [tone] from
 * `AppTheme.colors.meal(...)` or `AppTheme.colors.nutrients`; only its content color is drawn.
 * The handoff put these on tinted rounded squares; the owner removed the squares everywhere
 * on 2026-10-27, so nothing draws behind the icon.
 */
@Composable
fun ToneIcon(
    icon: IconResource,
    tone: ToneColors,
    modifier: Modifier = Modifier,
    size: ToneIconSize = ToneIconSize.Medium,
) {
    ToneIcon(icon = icon, color = tone.content, modifier = modifier, size = size)
}

@Composable
fun ToneIcon(
    icon: IconResource,
    color: ColorResource,
    modifier: Modifier = Modifier,
    size: ToneIconSize = ToneIconSize.Medium,
) {
    Box(modifier = modifier.size(size.box), contentAlignment = Alignment.Center) {
        Icon(icon = icon, size = size.icon, color = color)
    }
}

/**
 * A row's icon on its own, in its [color] with no tile behind it: Today's water, meal, fasting,
 * weight and note rows (2026-10-27, the owner found the tinted squares heavy). Sized so its
 * glyph reads as large as a tile's whole square did.
 */
@Composable
fun RowIcon(
    icon: IconResource,
    color: ColorResource,
    modifier: Modifier = Modifier,
    size: Dp = RowIconSize,
) {
    Icon(icon = icon, size = size, color = color, modifier = modifier)
}

/** A [RowIcon]'s size: between a Medium tile's glyph and its square, so a row keeps its weight. */
val RowIconSize: Dp = 28.dp

/**
 * The round 40dp button the handoff puts on cards and rows (the + on a meal, the + on water):
 * accent icon on the accent tint. [selected] swaps to the solid accent, as the logger's + does
 * once a food is picked. It lays out at [touchTarget], 48 by default, whatever [size] it draws.
 */
@Composable
fun RoundIconButton(
    icon: IconResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    size: Dp = AppTheme.spacing.compactTouchTarget,
    iconSize: Dp = 24.dp,
    containerColor: ColorResource = if (selected) AppTheme.colors.accent else AppTheme.colors.accentTint,
    contentColor: ColorResource = if (selected) AppTheme.colors.onAccent else AppTheme.colors.accent,
    enabled: Boolean = true,
    touchTarget: Dp = AppTheme.spacing.touchTarget,
) {
    // The tappable area is larger than the drawn circle: v3 draws the row + at 40 and asks for 48 to tap.
    Box(
        modifier = modifier
            .size(maxOf(size, touchTarget))
            .thenIf(enabled) { bounceClick(scaleDown = 0.92f, onClick = onClick) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(AppTheme.shapes.pill)
                .background(containerColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon = icon, size = iconSize, color = contentColor)
        }
    }
}

@Preview
@Composable
private fun ToneIconPreview() {
    PreviewLightDark {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MealTone.entries.forEach { meal ->
                ToneIcon(icon = AppIcons.meal(meal), tone = AppTheme.colors.meal(meal))
            }
            ToneIcon(icon = AppIcons.WaterDrop, tone = AppTheme.colors.nutrients.water, size = ToneIconSize.Small)
            ToneIcon(icon = AppIcons.WaterDrop, tone = AppTheme.colors.nutrients.water, size = ToneIconSize.Large)
            RowIcon(icon = AppIcons.WaterDrop, color = AppTheme.colors.nutrients.water.content)
            RoundIconButton(icon = AppIcons.Add, onClick = {})
            RoundIconButton(icon = AppIcons.Check, onClick = {}, selected = true)
        }
    }
}
