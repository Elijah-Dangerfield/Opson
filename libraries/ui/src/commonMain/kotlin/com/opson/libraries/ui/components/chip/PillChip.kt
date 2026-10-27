package com.opson.libraries.ui.components.chip

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
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
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.system.color.ToneColors
import com.opson.system.typography.TypographyResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import com.opson.libraries.ui.system.minTouchTarget

/** Chip density. The handoff uses [Regular] for choices on a page and [Compact] in sheets. */
enum class PillChipSize(internal val padding: PaddingValues, internal val minHeight: Dp) {
    Regular(PaddingValues(horizontal = 14.dp, vertical = 8.dp), 40.dp),
    Compact(PaddingValues(horizontal = 13.dp, vertical = 7.dp), 40.dp),
}

/** How a selected [PillChip] looks. */
enum class PillChipStyle {
    /** Solid accent, on-accent text. */
    Solid,

    /** Accent tint with a check and accent text, and a transparent, outlined unselected chip (v3 pause sheet). */
    Tint,
}

/**
 * A selectable pill (meal chips, unit chips, hunger and mood chips). Selected is solid accent
 * with on-accent text; unselected is surface with a 1dp line border and ink text. Pass
 * `role = Role.Checkbox` for a multi-select group such as mood. [style] picks the selected look.
 */
@Composable
fun PillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: PillChipSize = PillChipSize.Regular,
    enabled: Boolean = true,
    role: Role = Role.RadioButton,
    style: PillChipStyle = PillChipStyle.Solid,
) {
    val colors = AppTheme.colors
    val tint = style == PillChipStyle.Tint
    val container = when {
        selected -> if (tint) colors.accentTint else colors.accent
        else -> if (tint) colors.bg else colors.surface
    }
    val border = when {
        selected -> if (tint) colors.accentTint else colors.accent
        else -> colors.line
    }
    val content = when {
        selected -> if (tint) colors.accent else colors.onAccent
        else -> colors.ink
    }
    val typography = when (size) {
        PillChipSize.Regular -> AppTheme.type.secondaryStrong
        PillChipSize.Compact -> AppTheme.type.secondarySmallStrong
    }
    Row(
        modifier = modifier
            .minTouchTarget()
            .defaultMinSize(minHeight = size.minHeight)
            .clip(AppTheme.shapes.pill)
            .background(container)
            .border(1.dp, border.color, AppTheme.shapes.pill)
            .selectable(selected = selected, enabled = enabled, role = role, onClick = onClick)
            .padding(size.padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (tint && selected) Icon(icon = AppIcons.Check, size = 18.dp, color = content)
        Text(text = label, typography = typography, color = content, maxLines = 1)
    }
}

/**
 * The logger's method chips (Describe, Yesterday's, Saved...): a tinted icon and a bold label on a
 * surface card, design v3. Not a selection: it runs an action. Drawn 36 dp tall with a 48 dp
 * touch area.
 */
@Composable
fun ActionChip(
    label: String,
    icon: IconResource,
    tone: ToneColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .minTouchTarget()
            .semantics { role = Role.Button }
            .bounceClick(scaleDown = 0.95f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = ChipVisualHeight)
                .clip(AppTheme.shapes.inputLarge)
                .background(AppTheme.colors.surface)
                .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon = icon, size = 20.dp, color = tone.content)
            Text(text = label, typography = AppTheme.type.secondaryStrong.Bold, color = AppTheme.colors.ink, maxLines = 1)
        }
    }
}

/**
 * A state, not a control: a check and "Connected" on Watch and health apps (design v3). An accent icon
 * and a bold label on the accent tint, as tall as an [ActionChip]'s drawn pill.
 */
@Composable
fun StatusPill(
    label: String,
    icon: IconResource,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = ChipVisualHeight)
            .clip(AppTheme.shapes.pill)
            .background(AppTheme.colors.accentTint)
            .padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon = icon, size = 18.dp, color = AppTheme.colors.accent)
        Text(text = label, typography = AppTheme.type.secondaryStrong.Bold, color = AppTheme.colors.ink, maxLines = 1)
    }
}

/** [PillTag] sizes: [Small] for the hunger chip on a meal card, [Regular] for Explorer's safe foods. */
enum class PillTagSize(internal val padding: PaddingValues) {
    Small(PaddingValues(horizontal = 10.dp, vertical = 5.dp)),
    Regular(PaddingValues(horizontal = 12.dp, vertical = 7.dp)),
}

/**
 * A v3 choice: a rounded chip with a line border, or a soft accent fill and a check when
 * selected (amount units, hunger, fullness, mood). Drawn 36 dp tall with a 48 dp touch area.
 * Pass `role = Role.Checkbox` for a multi-select group such as mood. On a surface card, pass
 * [unselectedColor] `surface` so an unselected chip is white with its outline, as v3 draws it.
 */
@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.RadioButton,
    unselectedColor: ColorResource = AppTheme.colors.bg,
) {
    val colors = AppTheme.colors
    Box(
        modifier = modifier
            .minTouchTarget()
            .selectable(selected = selected, role = role, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = ChipVisualHeight)
                .clip(AppTheme.shapes.input)
                .background(if (selected) colors.accentTint else unselectedColor)
                .border(1.dp, if (selected) colors.accentTint.color else colors.line.color, AppTheme.shapes.input)
                .padding(start = if (selected) 10.dp else 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected) Icon(icon = AppIcons.Check, size = 20.dp, color = colors.accent)
            Text(
                text = label,
                typography = AppTheme.type.bodyBold,
                color = if (selected) colors.accent else colors.ink,
                maxLines = 1,
            )
        }
    }
}

private val ChipVisualHeight = 36.dp

/**
 * A read-only soft pill for things the user already chose (how it felt, a source's status).
 */
@Composable
fun PillTag(
    label: String,
    modifier: Modifier = Modifier,
    color: ColorResource = AppTheme.colors.soft,
    typography: TypographyResource = AppTheme.type.secondarySmallStrong,
    size: PillTagSize = PillTagSize.Small,
) {
    Text(
        text = label,
        typography = typography,
        color = AppTheme.colors.ink,
        maxLines = 1,
        modifier = modifier
            .clip(AppTheme.shapes.pill)
            .background(color)
            .padding(size.padding),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Preview
@Composable
private fun PillChipPreview() {
    PreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                PillChip(label = "Breakfast", selected = true, onClick = {})
                PillChip(label = "Lunch", selected = false, onClick = {})
                PillChip(label = "g", selected = false, onClick = {}, size = PillChipSize.Compact)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                ActionChip(label = "Photo", icon = AppIcons.PhotoCamera, tone = AppTheme.colors.nutrients.protein, onClick = {})
                ActionChip(label = "Water", icon = AppIcons.WaterDrop, tone = AppTheme.colors.nutrients.water, onClick = {})
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                ChoiceChip(label = "plate", selected = true, onClick = {})
                ChoiceChip(label = "cup", selected = false, onClick = {})
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
                PillTag(label = "Calm")
                PillTag(label = "Hungry")
            }
            StatusPill(label = "Connected", icon = AppIcons.Check)
        }
    }
}
