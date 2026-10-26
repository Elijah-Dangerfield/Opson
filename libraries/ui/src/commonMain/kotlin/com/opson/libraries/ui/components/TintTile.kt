package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.system.color.MealTone
import com.opson.system.color.ToneColors
import com.opson.system.thenIf
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A meal tile (design v3, 2.4): a big icon, a title and a line under it. [done] tints it with
 * [tone] and adds a check badge; open it is the surface with a dashed outline in the tone and
 * an add icon. Lay them out with [TintTileGrid].
 */
@Composable
fun TintTile(
    icon: IconResource,
    tone: ToneColors,
    title: String,
    subtitle: String,
    done: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = TintTileMinHeight)
            .clip(AppTheme.shapes.card)
            .background(if (done) tone.container else AppTheme.colors.surface)
            .thenIf(!done) { dashedBorder(tone.content.withAlpha(DASH_ALPHA).color, TileRadius) }
            .semantics { role = Role.Button }
            .bounceClick(scaleDown = 0.97f, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Icon(icon = icon, size = 30.dp, color = tone.content)
            if (done) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(tone.content), contentAlignment = Alignment.Center) {
                    Icon(icon = AppIcons.Check, size = 16.dp, color = AppTheme.colors.surface)
                }
            } else {
                Icon(icon = AppIcons.AddCircle, size = 24.dp, color = tone.content)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
            Text(text = subtitle, typography = AppTheme.type.secondary.Tabular, color = AppTheme.colors.muted)
        }
    }
}

/** Two [TintTile]s per row with the handoff's 10 gap, tiles in a row the same height. */
@Composable
fun TintTileGrid(
    count: Int,
    modifier: Modifier = Modifier,
    tile: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TintTileGap)) {
        (0 until count).chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TintTileGap)) {
                pair.forEach { index -> tile(index, Modifier.weight(1f).fillMaxHeight()) }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

private val TintTileMinHeight: Dp = 128.dp
private val TintTileGap: Dp = 12.dp
private val TileRadius: Dp = 22.dp
private const val DASH_ALPHA = 0.5f

@Suppress("VerifyStrings")
@Preview(widthDp = 390)
@Composable
private fun TintTilePreview() {
    PreviewLightDark {
        val meals = listOf(MealTone.Breakfast, MealTone.Lunch, MealTone.Snack, MealTone.Dinner)
        TintTileGrid(count = meals.size) { index, modifier ->
            val meal = meals[index]
            TintTile(
                icon = AppIcons.meal(meal),
                tone = AppTheme.colors.meal(meal),
                title = meal.name,
                subtitle = if (index < 3) "3 foods · 8:10 AM" else "Around 7:00",
                done = index < 3,
                onClick = {},
                modifier = modifier,
            )
        }
    }
}
