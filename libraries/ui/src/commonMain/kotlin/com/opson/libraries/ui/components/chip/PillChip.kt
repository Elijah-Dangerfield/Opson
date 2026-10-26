package com.opson.libraries.ui.components.chip

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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

/** Chip density. The handoff uses [Regular] for choices on a page and [Compact] in sheets. */
enum class PillChipSize(internal val padding: PaddingValues, internal val minHeight: Dp) {
    Regular(PaddingValues(horizontal = 14.dp, vertical = 8.dp), 40.dp),
    Compact(PaddingValues(horizontal = 13.dp, vertical = 7.dp), 40.dp),
}

/**
 * A selectable pill (meal chips, unit chips, hunger and mood chips). Selected is solid accent
 * with on-accent text; unselected is surface with a 1dp line border and ink text. Pass
 * `role = Role.Checkbox` for a multi-select group such as mood.
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
) {
    val colors = AppTheme.colors
    val container = if (selected) colors.accent else colors.surface
    val border = if (selected) colors.accent else colors.line
    val content = if (selected) colors.onAccent else colors.ink
    val typography = when (size) {
        PillChipSize.Regular -> AppTheme.type.secondaryStrong
        PillChipSize.Compact -> AppTheme.type.secondarySmallStrong
    }
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = size.minHeight)
            .clip(AppTheme.shapes.pill)
            .background(container)
            .border(1.dp, border.color, AppTheme.shapes.pill)
            .selectable(selected = selected, enabled = enabled, role = role, onClick = onClick)
            .padding(size.padding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, typography = typography, color = content, maxLines = 1)
    }
}

/**
 * A pill with a colored icon on a tinted fill, used for the logger's method chips (Photo,
 * Describe it, Water...). Not a selection: it runs an action.
 */
@Composable
fun ActionChip(
    label: String,
    icon: IconResource,
    tone: ToneColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(AppTheme.shapes.pill)
            .background(tone.container)
            .bounceClick(scaleDown = 0.95f, onClick = onClick)
            .padding(start = 9.dp, end = 13.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon = icon, size = 20.dp, color = tone.content)
        Text(text = label, typography = AppTheme.type.sectionLabel, color = AppTheme.colors.ink, maxLines = 1)
    }
}

/** [PillTag] sizes: [Small] for how-it-felt chips on a meal card, [Regular] for Explorer's safe foods. */
enum class PillTagSize(internal val padding: PaddingValues) {
    Small(PaddingValues(horizontal = 10.dp, vertical = 5.dp)),
    Regular(PaddingValues(horizontal = 12.dp, vertical = 7.dp)),
}

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
                PillTag(label = "Calm")
                PillTag(label = "Hungry")
            }
        }
    }
}
