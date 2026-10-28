package com.opson.libraries.diary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.opson.libraries.nutrition.HeightInput
import com.opson.libraries.nutrition.HeightUnit
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.text.NumberField
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_resting_cm
import opson.libraries.resources.generated.resources.settings_resting_ft
import opson.libraries.resources.generated.resources.settings_resting_ft_in
import opson.libraries.resources.generated.resources.settings_resting_height
import opson.libraries.resources.generated.resources.settings_resting_in
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Height, in centimetres or in feet and inches, with the switch between them. Onboarding's body
 * step and Resting energy both use it. It draws [input] as given; the caller converts and
 * validates through [HeightInput]. [imeAction] is the key on its last field: Next when the form
 * goes on past height, so the keyboard walks to the field after it.
 */
@Composable
fun HeightField(
    input: HeightInput,
    onUnitPicked: (HeightUnit) -> Unit,
    onCmTyped: (String) -> Unit,
    onFeetTyped: (String) -> Unit,
    onInchesTyped: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(Res.string.settings_resting_height),
            typography = AppTheme.type.secondarySmallStrong,
            color = AppTheme.colors.muted,
        )
        SegmentedControl(
            options = HeightUnit.entries,
            selected = input.unit,
            onSelect = { if (enabled) onUnitPicked(it) },
            label = { stringResource(if (it == HeightUnit.Cm) Res.string.settings_resting_cm else Res.string.settings_resting_ft_in) },
        )
        when (input.unit) {
            HeightUnit.Cm -> NumberField(
                value = input.cm,
                onValueChange = onCmTyped,
                unit = stringResource(Res.string.settings_resting_cm),
                enabled = enabled,
                imeAction = imeAction,
            )
            HeightUnit.FtIn -> Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
                NumberField(
                    value = input.feet,
                    onValueChange = onFeetTyped,
                    unit = stringResource(Res.string.settings_resting_ft),
                    allowDecimal = false,
                    enabled = enabled,
                    imeAction = ImeAction.Next,
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    value = input.inches,
                    onValueChange = onInchesTyped,
                    unit = stringResource(Res.string.settings_resting_in),
                    enabled = enabled,
                    imeAction = imeAction,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Preview(widthDp = 390)
@Composable
private fun HeightFieldPreview() {
    PreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            HeightField(HeightInput(HeightUnit.Cm, cm = "168"), {}, {}, {}, {})
            HeightField(HeightInput(HeightUnit.FtIn, feet = "5", inches = "11.5"), {}, {}, {}, {})
        }
    }
}
