package com.opson.features.logger.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.ButtonStyle
import com.opson.libraries.ui.components.dialog.Dialog
import com.opson.libraries.ui.components.text.NumberField
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.components.text.TextInput
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.logger_amount_cancel
import opson.libraries.resources.generated.resources.logger_amount_custom_body
import opson.libraries.resources.generated.resources.logger_amount_custom_g
import opson.libraries.resources.generated.resources.logger_amount_custom_grams
import opson.libraries.resources.generated.resources.logger_amount_custom_name
import opson.libraries.resources.generated.resources.logger_amount_custom_name_hint
import opson.libraries.resources.generated.resources.logger_amount_custom_save
import opson.libraries.resources.generated.resources.logger_amount_custom_title
import org.jetbrains.compose.resources.stringResource

/**
 * "Custom size…" (design sync 2, 3.3b): a name and its weight, saved to this food so it shows in
 * its unit menu from now on. The weight is what lets the gram total follow the count.
 */
@Composable
internal fun CustomSizeDialog(size: CustomSizeUi, onAction: (AmountAction) -> Unit) {
    Dialog(onDismissRequest = { onAction(AmountAction.CustomSizeDismissed) }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.bg, AppTheme.shapes.dialog)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Text(text = stringResource(Res.string.logger_amount_custom_title), typography = AppTheme.type.sheetTitle, color = AppTheme.colors.ink)
            Text(text = stringResource(Res.string.logger_amount_custom_body), typography = AppTheme.type.body, color = AppTheme.colors.muted)
            TextInput(
                value = size.name,
                onValueChange = { onAction(AmountAction.CustomSizeTyped(it, size.grams)) },
                label = stringResource(Res.string.logger_amount_custom_name),
                placeholder = stringResource(Res.string.logger_amount_custom_name_hint),
            )
            NumberField(
                value = size.grams,
                onValueChange = { onAction(AmountAction.CustomSizeTyped(size.name, it)) },
                label = stringResource(Res.string.logger_amount_custom_grams),
                unit = stringResource(Res.string.logger_amount_custom_g),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
                ButtonPrimary(
                    onClick = { onAction(AmountAction.CustomSizeDismissed) },
                    style = ButtonStyle.Tonal,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(Res.string.logger_amount_cancel)) }
                ButtonPrimary(
                    onClick = { onAction(AmountAction.CustomSizeSaved) },
                    enabled = size.canSave,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(Res.string.logger_amount_custom_save)) }
            }
        }
    }
}
