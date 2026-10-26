package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.system.dropShadow
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The card the Add and Day menus are drawn on: 268dp wide, surface, radius 18, the popover
 * shadow. Fill it with [MenuItem]s. Use [PopoverMenu] to show it anchored to a button.
 */
@Composable
fun MenuPopover(
    modifier: Modifier = Modifier,
    width: Dp = 268.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = AppTheme.shapes.popover
    Column(
        modifier = modifier
            .width(width)
            .dropShadow(AppTheme.shadows.popover, shape)
            .clip(shape)
            .background(AppTheme.colors.surface)
            .padding(vertical = 6.dp),
        content = content,
    )
}

/**
 * One menu row: label on the left, icon on the right, a line under it. [showDivider] should be
 * false on the last row. [labelColor] lets a two-step action ("Tap again to clear") stand out.
 */
@Composable
fun MenuItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: IconResource? = null,
    iconColor: ColorResource = AppTheme.colors.accent,
    labelColor: ColorResource = AppTheme.colors.ink,
    showDivider: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .bounceClick(scaleDown = 0.98f, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Text(text = label, typography = AppTheme.type.menuItem, color = labelColor, modifier = Modifier.weight(1f))
            if (icon != null) Icon(icon = icon, size = 22.dp, color = iconColor)
        }
        if (showDivider) HorizontalDivider(color = AppTheme.colors.line)
    }
}

object MenuPopoverDefaults {
    /** Today's ⋮ menu, whose longest row is "Copy this day to tomorrow". */
    val WideWidth: Dp = 320.dp
}

/**
 * A menu row with the icon first (design v3, 2.11): the icon in [iconColor], then the label, 56
 * tall, no line. Groups are split with [MenuGroupDivider].
 */
@Composable
fun MenuIconItem(
    label: String,
    icon: IconResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconColor: ColorResource = AppTheme.colors.accent,
    labelColor: ColorResource = AppTheme.colors.ink,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .bounceClick(scaleDown = 0.98f, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(icon = icon, size = 24.dp, color = iconColor)
        Text(text = label, typography = AppTheme.type.bodyBold, color = labelColor, modifier = Modifier.weight(1f))
    }
}

/** The full-width line between groups of [MenuIconItem]s. */
@Composable
fun MenuGroupDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier.padding(vertical = 6.dp), color = AppTheme.colors.line)
}

/**
 * Shows a [MenuPopover] below and end-aligned to the composable that hosts it, so place this
 * inside the same `Box` as the button that opens it. Dismisses on an outside tap or back.
 */
@Composable
fun PopoverMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: IntOffset = IntOffset.Zero,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    Popup(
        alignment = Alignment.TopEnd,
        offset = offset,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        // Room for the shadow, which a popup window would otherwise clip.
        MenuPopover(modifier = modifier.padding(start = 24.dp, end = 12.dp, bottom = 40.dp), content = content)
    }
}

@Preview(widthDp = 390)
@Composable
private fun MenuPopoverPreview() {
    PreviewLightDark {
        MenuPopover(modifier = Modifier.padding(bottom = 24.dp)) {
            MenuItem(label = "Food", icon = AppIcons.Restaurant, onClick = {})
            MenuItem(label = "Scan a barcode", icon = AppIcons.BarcodeScanner, onClick = {})
            MenuItem(label = "Glass of water", icon = AppIcons.WaterDrop, onClick = {})
            MenuItem(label = "Note", icon = AppIcons.EditNote, onClick = {}, showDivider = false)
        }
    }
}
