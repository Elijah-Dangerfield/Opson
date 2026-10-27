package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.text.NumberInput
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.components.text.rememberTypedText
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.libraries.ui.system.minTouchTarget
import com.opson.system.AppTheme
import com.opson.system.background
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The grouped card a sheet's action rows sit in (the Meal sheet, the Water sheet): surface,
 * radius 20, full width. Fill it with [SheetMenuRow]s.
 */
@Composable
fun SheetMenuGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.groupedList)
            .background(AppTheme.colors.surface)
            .padding(vertical = 2.dp),
        content = content,
    )
}

/**
 * One action in a [SheetMenuGroup]: an accent icon, the label, an optional [trailing] (a
 * chevron for a row that opens a page), and a line under it unless [showDivider] is false.
 * 50 tall, as the handoff's sheet rows.
 */
@Composable
fun SheetMenuRow(
    label: String,
    icon: IconResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconColor: ColorResource = AppTheme.colors.accent,
    showDivider: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = SheetMenuRowHeight)
                .semantics { role = Role.Button }
                .bounceClick(scaleDown = 0.98f, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon = icon, size = 22.dp, color = iconColor)
            Text(text = label, typography = AppTheme.type.menuItem, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
            trailing?.invoke(this)
        }
        if (showDivider) HorizontalDivider(color = AppTheme.colors.line)
    }
}

/**
 * A [SheetMenuGroup] row holding a whole-number input, its [unit] and an [actionLabel] pill:
 * the Water sheet's custom amount. The input keeps digits only and exposes its value as
 * `EditableText` semantics, described by [placeholder].
 */
@Composable
fun SheetMenuNumberRow(
    value: String,
    onValueChange: (String) -> Unit,
    icon: IconResource,
    placeholder: String,
    unit: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = SheetMenuRowHeight)
                .padding(start = 18.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon = icon, size = 22.dp, color = AppTheme.colors.accent)
            Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                val typed = rememberTypedText(value, onValueChange)
                if (typed.text.isEmpty()) {
                    Text(text = placeholder, typography = AppTheme.type.menuItem.Normal, color = AppTheme.colors.muted, maxLines = 1)
                }
                BasicTextField(
                    value = typed.text,
                    onValueChange = { typed.onType(NumberInput.sanitize(it, allowDecimal = false)) },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
                    singleLine = true,
                    textStyle = AppTheme.type.menuItem.Normal.style(AppTheme.colors.ink.color),
                    cursorBrush = SolidColor(AppTheme.colors.accent.color),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                )
            }
            Text(text = unit, typography = AppTheme.type.secondary, color = AppTheme.colors.muted, maxLines = 1)
            Box(
                modifier = Modifier
                    .minTouchTarget()
                    .defaultMinSize(minHeight = AppTheme.spacing.compactTouchTarget)
                    .clip(AppTheme.shapes.pill)
                    .background(AppTheme.colors.soft)
                    .bounceClick(scaleDown = 0.95f, onClick = onAction)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = actionLabel, typography = AppTheme.type.buttonSmall, color = AppTheme.colors.ink, maxLines = 1)
            }
        }
        if (showDivider) HorizontalDivider(color = AppTheme.colors.line)
    }
}

private val SheetMenuRowHeight = 50.dp

@Suppress("VerifyStrings")
@Preview(widthDp = 390)
@Composable
private fun SheetMenuPreview() {
    PreviewLightDark {
        SheetMenuGroup {
            SheetMenuRow(label = "Add a glass, 250 ml", icon = AppIcons.LocalDrink, onClick = {})
            SheetMenuNumberRow(
                value = "",
                onValueChange = {},
                icon = AppIcons.Edit,
                placeholder = "Custom amount",
                unit = "ml",
                actionLabel = "Add",
                onAction = {},
            )
            SheetMenuRow(label = "Water settings", icon = AppIcons.Settings, onClick = {}, showDivider = false) {
                ListRowChevron()
            }
        }
    }
}
