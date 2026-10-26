package com.opson.libraries.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.radio.RadioDot
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A full-width answer to a question, as in onboarding (design v3, 1.2 and 1.3): a card with a
 * leading mark and a bold label. Selected rows take the accent tint and an accent border.
 *
 * [role] picks the mark: [Role.Checkbox] draws a check box, for questions where several answers
 * can be picked; [Role.RadioButton] draws a radio dot, for questions where picking one replaces
 * another.
 */
@Composable
fun ChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.Checkbox,
    enabled: Boolean = true,
) {
    val colors = AppTheme.colors
    val shape = AppTheme.shapes.inputLarge
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clip(shape)
            .background(if (selected) colors.accentTint else colors.surface)
            .border(if (selected) 1.5.dp else 1.dp, (if (selected) colors.accent else colors.line).color, shape)
            .selectable(selected = selected, enabled = enabled, role = role, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        if (role == Role.RadioButton) RadioDot(selected = selected) else CheckMark(selected = selected)
        Text(
            text = label,
            typography = AppTheme.type.bodyBold,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The check box ChoiceRow leads with: a line-bordered square, accent-filled with a white check when picked. */
@Composable
private fun CheckMark(selected: Boolean) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(shape)
            .background(if (selected) colors.accent else colors.surface)
            .border(1.5.dp, (if (selected) colors.accent else colors.line).color, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(icon = AppIcons.Check, size = 16.dp, color = colors.onAccent)
    }
}

@Preview(widthDp = 390)
@Composable
private fun ChoiceRowPreview() {
    PreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceRow(label = "Curious what I eat", selected = false, onClick = {})
            ChoiceRow(label = "Eating enough", selected = true, onClick = {})
            ChoiceRow(label = "Some are fine", selected = true, onClick = {}, role = Role.RadioButton)
            ChoiceRow(label = "Not sure yet", selected = false, onClick = {}, role = Role.RadioButton)
        }
    }
}

@Preview(widthDp = 390)
@Composable
private fun ChoiceRowLargeTextPreview() {
    PreviewLightDark(largeText = true) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceRow(label = "Some are fine", selected = true, onClick = {}, role = Role.RadioButton)
            ChoiceRow(label = "Working with a provider", selected = false, onClick = {})
        }
    }
}
