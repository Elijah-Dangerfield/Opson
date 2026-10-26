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

/** Tile sizes the handoff uses. [Medium] is the one on cards and rows. */
enum class IconTileSize(val tile: Dp, val icon: Dp, val corner: Dp) {
    /** Preset rows and compact lists. */
    Small(tile = 36.dp, icon = 20.dp, corner = 11.dp),

    /** Meal cards, food rows, Foods create rows. */
    Medium(tile = 38.dp, icon = 22.dp, corner = 12.dp),

    /** Touch-size tiles. */
    Large(tile = 44.dp, icon = 24.dp, corner = 12.dp),
}

/**
 * A rounded square with a tinted background and a colored icon, e.g. the meal tiles and the
 * water tile. Take [tone] from `AppTheme.colors.meal(...)` or `AppTheme.colors.nutrients`.
 */
@Composable
fun IconTile(
    icon: IconResource,
    tone: ToneColors,
    modifier: Modifier = Modifier,
    size: IconTileSize = IconTileSize.Medium,
) {
    IconTile(
        icon = icon,
        contentColor = tone.content,
        containerColor = tone.container,
        modifier = modifier,
        size = size,
    )
}

@Composable
fun IconTile(
    icon: IconResource,
    contentColor: ColorResource,
    containerColor: ColorResource,
    modifier: Modifier = Modifier,
    size: IconTileSize = IconTileSize.Medium,
) {
    val shape = RoundedCornerShape(size.corner)
    Box(
        modifier = modifier
            .size(size.tile)
            .clip(shape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon = icon, size = size.icon, color = contentColor)
    }
}

/**
 * The round 40dp button the handoff puts on cards and rows (the + on a meal, the + on water):
 * accent icon on the accent tint. [selected] swaps to the solid accent, as the logger's + does
 * once a food is picked.
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
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(AppTheme.shapes.pill)
            .background(containerColor)
            .thenIf(enabled) { bounceClick(scaleDown = 0.92f, onClick = onClick) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon = icon, size = iconSize, color = contentColor)
    }
}

@Preview
@Composable
private fun IconTilePreview() {
    PreviewLightDark {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MealTone.entries.forEach { meal ->
                IconTile(icon = AppIcons.meal(meal), tone = AppTheme.colors.meal(meal))
            }
            IconTile(icon = AppIcons.WaterDrop, tone = AppTheme.colors.nutrients.water, size = IconTileSize.Small)
            IconTile(icon = AppIcons.WaterDrop, tone = AppTheme.colors.nutrients.water, size = IconTileSize.Large)
            RoundIconButton(icon = AppIcons.Add, onClick = {})
            RoundIconButton(icon = AppIcons.Check, onClick = {}, selected = true)
        }
    }
}
