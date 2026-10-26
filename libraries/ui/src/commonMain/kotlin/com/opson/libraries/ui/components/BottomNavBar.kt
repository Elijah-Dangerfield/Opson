package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.system.dropShadow
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The 60dp raised + in the middle of the bottom bar: solid accent, on-accent icon, the raised
 * shadow. Give [icon] a description with `described(...)`, since it has no label.
 */
@Composable
fun CenterAddButton(
    onClick: () -> Unit,
    icon: IconResource,
    modifier: Modifier = Modifier,
) {
    val shape = AppTheme.shapes.pill
    Box(
        modifier = modifier
            .size(CenterButtonSize)
            .dropShadow(AppTheme.shadows.raised, shape)
            .clip(shape)
            .background(AppTheme.colors.accent)
            .bounceClick(scaleDown = 0.92f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon = icon, size = 32.dp, color = AppTheme.colors.onAccent)
    }
}

/** One tab in [BottomNavBar]. [label] doubles as the accessibility name. */
@Immutable
data class BottomNavItem(
    val label: String,
    val icon: IconResource,
    val selected: Boolean,
)

/**
 * The five-slot bottom bar: two tabs, the raised [CenterAddButton], two tabs. [items] must be
 * the four tabs in order (Today, Trends, Foods, More). Visual only: the caller maps clicks to
 * tab switches and the logger.
 */
@Composable
fun BottomNavBar(
    items: List<BottomNavItem>,
    onItemClick: (index: Int) -> Unit,
    onAddClick: () -> Unit,
    addIcon: IconResource,
    modifier: Modifier = Modifier,
) {
    require(items.size == TAB_COUNT) { "BottomNavBar takes exactly $TAB_COUNT tabs; the + is the fifth slot" }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface)
    ) {
        HorizontalDivider(color = AppTheme.colors.line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            items.forEachIndexed { index, item ->
                if (index == TAB_COUNT / 2) {
                    Box(
                        modifier = Modifier.weight(1f).height(TabMinHeight),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        // The button rises half its height above the bar, as in the handoff,
                        // so it is measured unbounded rather than squeezed into the tab height.
                        CenterAddButton(
                            onClick = onAddClick,
                            icon = addIcon,
                            modifier = Modifier
                                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                                .offset { IntOffset(0, -CenterButtonLift.roundToPx()) },
                        )
                    }
                }
                NavTab(item = item, onClick = { onItemClick(index) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NavTab(item: BottomNavItem, onClick: () -> Unit, modifier: Modifier) {
    val colors = AppTheme.colors
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = TabMinHeight)
            .selectable(selected = item.selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.Bottom),
    ) {
        Icon(icon = item.icon, size = 25.dp, color = if (item.selected) colors.accent else colors.muted)
        Text(
            text = item.label,
            typography = if (item.selected) AppTheme.type.navLabel.ExtraBold else AppTheme.type.navLabel,
            color = if (item.selected) colors.ink else colors.muted,
            maxLines = 1,
        )
    }
}

private const val TAB_COUNT = 4
private val CenterButtonSize = 60.dp
private val CenterButtonLift = 30.dp
private val TabMinHeight = 52.dp

@Preview(widthDp = 390, heightDp = 260)
@Composable
private fun BottomNavBarPreview() {
    PreviewLightDark(contentPadding = PaddingValues(top = 40.dp)) {
        BottomNavBar(
            items = listOf(
                BottomNavItem("Today", AppIcons.CalendarToday, selected = true),
                BottomNavItem("Trends", AppIcons.Insights, selected = false),
                BottomNavItem("Foods", AppIcons.Restaurant, selected = false),
                BottomNavItem("More", AppIcons.MoreHoriz, selected = false),
            ),
            onItemClick = {},
            onAddClick = {},
            addIcon = AppIcons.Add.described("Add"),
        )
    }
}
