package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.icon.IconTile
import com.opson.libraries.ui.components.icon.IconTileSize
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.color.MealTone
import com.opson.system.color.ToneColors
import com.opson.system.thenIf
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The handoff's list row (Foods, the logger's Recent and Usual picks, More pages): an optional
 * icon tile, a title with an optional subtitle, optional trailing text and an optional trailing
 * action such as a [RoundIconButton] or a chevron.
 *
 * Texts arrive formatted. A nutrition figure on the right should come in through [trailing] as
 * a `NutrientValue`, which carries the metric test tag; [trailingText] is for anything else.
 * [onClick] covers the icon and the text, not the trailing action, which handles its own taps.
 * [subtitleContent] replaces [subtitle] when the subtitle carries a number that must render
 * through a tagged nutrient text.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: IconResource? = null,
    iconTone: ToneColors? = null,
    trailingText: String? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = false,
    minHeight: Dp = 58.dp,
    subtitleContent: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = minHeight)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .thenIf(onClick != null) { bounceClick(scaleDown = 0.98f, onClick = onClick!!) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
            ) {
                if (icon != null) {
                    IconTile(
                        icon = icon,
                        tone = iconTone ?: ToneColors(AppTheme.colors.accent, AppTheme.colors.accentTint),
                        size = IconTileSize.Medium,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        typography = AppTheme.type.bodyBold,
                        color = AppTheme.colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitleContent != null) {
                        subtitleContent()
                    } else if (subtitle != null) {
                        Text(
                            text = subtitle,
                            typography = AppTheme.type.secondarySmall.Tabular,
                            color = AppTheme.colors.muted,
                        )
                    }
                }
            }
            if (trailingText != null) {
                Text(text = trailingText, typography = AppTheme.type.numericSmall, color = AppTheme.colors.muted)
            }
            trailing?.invoke(this)
        }
        if (showDivider) HorizontalDivider(color = AppTheme.colors.line)
    }
}

/** The muted chevron at the end of a row that opens a page (More, Foods' "Create a meal"). */
@Composable
fun ListRowChevron(modifier: Modifier = Modifier) {
    Icon(icon = AppIcons.ChevronRight, size = 22.dp, color = AppTheme.colors.muted, modifier = modifier)
}

@Preview(widthDp = 390)
@Composable
private fun ListRowPreview() {
    PreviewLightDark {
        EdgeCard(edgeToEdge = false, verticalArrangement = Arrangement.Top) {
            ListRow(
                title = "Greek yogurt",
                subtitle = "1 cup · Estimate",
                icon = AppIcons.meal(MealTone.Breakfast),
                iconTone = AppTheme.colors.meal(MealTone.Breakfast),
                trailingText = "Weighed",
                showDivider = true,
                onClick = {},
            ) {
                RoundIconButton(icon = AppIcons.Add, onClick = {})
            }
            ListRow(
                title = "Create a meal",
                subtitle = "Group foods you often eat together",
                icon = AppIcons.BookmarkAdd,
                iconTone = AppTheme.colors.nutrients.fat,
                onClick = {},
            ) {
                Icon(icon = AppIcons.ChevronRight, size = 22.dp, color = AppTheme.colors.muted)
            }
        }
    }
}
