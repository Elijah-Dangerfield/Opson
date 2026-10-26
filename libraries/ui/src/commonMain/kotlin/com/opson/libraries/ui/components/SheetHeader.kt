package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.ds_close
import opson.libraries.resources.generated.resources.Res as SharedRes
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/** The 40 by 5 grab handle at the top of every sheet. */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 40.dp, height = 5.dp)
            .background(AppTheme.colors.line, AppTheme.shapes.bar)
    )
}

/**
 * A sheet's title row: title (and an optional subtitle such as a brand) on the left, a 44dp round
 * close button on the right. Pass `onClose = null` for a sheet that closes some other way.
 */
@Composable
fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClose: (() -> Unit)? = null,
    closeButtonColor: ColorResource = AppTheme.colors.surface,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        verticalAlignment = if (subtitle == null) Alignment.CenterVertically else Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, typography = AppTheme.type.sheetTitle, color = AppTheme.colors.ink)
            if (subtitle != null) {
                Text(text = subtitle, typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted)
            }
        }
        if (onClose != null) {
            RoundIconButton(
                icon = AppIcons.Close.described(stringResource(SharedRes.string.ds_close)),
                onClick = onClose,
                size = AppTheme.spacing.touchTarget,
                iconSize = 22.dp,
                containerColor = closeButtonColor,
                contentColor = AppTheme.colors.ink,
            )
        }
    }
}

@Preview(widthDp = 390)
@Composable
private fun SheetHeaderPreview() {
    PreviewLightDark {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SheetHandle()
            SheetHeader(title = "Trends settings", onClose = {})
            SheetHeader(title = "Greek yogurt", subtitle = "Fage, 2%", onClose = {}, closeButtonColor = AppTheme.colors.soft)
        }
    }
}
