package com.opson.libraries.diary.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.diary_pick_cd
import opson.libraries.resources.generated.resources.diary_remove_cd
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * One of the logger's Favorites: a round tinted tile with a + badge that turns
 * into a check once picked, and the short name under it. The whole tile
 * toggles. [name] is the short name; [fullName] is what a screen reader says.
 */
@Composable
fun FavoriteFoodTile(
    name: String,
    picked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    fullName: String = name,
    iconSeed: String = fullName,
) {
    val tone = foodTone(iconSeed)
    val description = stringResource(if (picked) Res.string.diary_remove_cd else Res.string.diary_pick_cd, fullName)
    Column(
        modifier = modifier
            .bounceClick(scaleDown = 0.95f, onClick = onToggle)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Checkbox
                selected = picked
            }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(tone.container, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon = AppIcons.Restaurant, size = 27.dp, color = tone.content)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 3.dp, y = 3.dp)
                    .size(24.dp)
                    .border(2.dp, AppTheme.colors.bg.color, CircleShape)
                    .background(if (picked) AppTheme.colors.accent else AppTheme.colors.accentTint, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon = if (picked) AppIcons.Check else AppIcons.Add,
                    size = 16.dp,
                    color = if (picked) AppTheme.colors.onAccent else AppTheme.colors.accent,
                )
            }
        }
        Text(
            text = name,
            typography = AppTheme.type.captionStrong,
            color = AppTheme.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(widthDp = 390)
@Composable
private fun FavoriteFoodTilePreview() {
    PreviewLightDark {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Banana", "Yogurt", "Rice", "Adobo", "Oats").forEachIndexed { index, name ->
                FavoriteFoodTile(name = name, picked = index == 1, onToggle = {}, modifier = Modifier.weight(1f))
            }
        }
    }
}
