package com.opson.libraries.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A full-width answer to a question, as in onboarding: a surface card whose border turns ink
 * and gains a check mark when selected. Use [Role.Checkbox] where several answers can be picked
 * (or the one answer toggles) and [Role.RadioButton] where picking one replaces another.
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
            .defaultMinSize(minHeight = 52.dp)
            .clip(shape)
            .background(colors.surface)
            .border(1.5.dp, (if (selected) colors.ink else colors.line).color, shape)
            .selectable(selected = selected, enabled = enabled, role = role, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Text(
            text = label,
            typography = AppTheme.type.body,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(icon = AppIcons.Check, size = 20.dp, color = colors.accent)
        } else {
            // Keeps the label from reflowing when the mark appears.
            Spacer(Modifier.size(20.dp))
        }
    }
}

@Preview(widthDp = 390)
@Composable
private fun ChoiceRowPreview() {
    PreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceRow(label = "Curious", selected = true, onClick = {})
            ChoiceRow(label = "Eating regularly", selected = false, onClick = {})
            ChoiceRow(
                label = "Numbers or tracking have been hard for me before",
                selected = false,
                onClick = {},
            )
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
