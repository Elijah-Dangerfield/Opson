package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
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
import com.opson.system.thenIf
import opson.libraries.resources.generated.resources.ds_less
import opson.libraries.resources.generated.resources.ds_more
import opson.libraries.resources.generated.resources.Res as SharedRes
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The amount stepper: a soft − button, the amount in tabular figures, a soft + button.
 *
 * [amount] arrives formatted. The stepper does not know units or step sizes, so the caller's
 * ViewModel decides what one tap means.
 */
@Composable
fun Stepper(
    amount: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(
            icon = AppIcons.Remove.described(stringResource(SharedRes.string.ds_less)),
            enabled = canDecrease,
            onClick = onDecrease,
        )
        Text(
            text = amount,
            typography = AppTheme.type.numericLarge,
            color = AppTheme.colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        StepperButton(
            icon = AppIcons.Add.described(stringResource(SharedRes.string.ds_more)),
            enabled = canIncrease,
            onClick = onIncrease,
        )
    }
}

@Composable
private fun StepperButton(icon: IconResource, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(AppTheme.shapes.inputLarge)
            .background(AppTheme.colors.soft)
            .thenIf(!enabled) { alpha(DISABLED_ALPHA) }
            .thenIf(enabled) { bounceClick(scaleDown = 0.92f, onClick = onClick) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon = icon, size = 24.dp, color = AppTheme.colors.ink)
    }
}

private const val DISABLED_ALPHA = 0.4f

@Preview(widthDp = 390)
@Composable
private fun StepperPreview() {
    PreviewLightDark {
        Stepper(amount = "1.5 cup", onDecrease = {}, onIncrease = {})
    }
}
