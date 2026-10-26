package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The top of a pushed page: an accent back link (`‹ More`, `‹ Today`) over the page title, as
 * every More page and the Nutrients, Weight and Water pages draw it. [backLabel] arrives whole,
 * chevron included, so the copy owns it.
 */
@Composable
fun PageHeader(
    title: String,
    backLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = backLabel,
            typography = AppTheme.type.menuItem,
            color = AppTheme.colors.accent,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.Start)
                .semantics { role = Role.Button }
                .bounceClick(scaleDown = 0.96f, onClick = onBack)
                .defaultMinSize(minHeight = AppTheme.spacing.touchTarget)
                .padding(vertical = 12.dp),
        )
        Text(text = title, typography = AppTheme.type.subH1, color = AppTheme.colors.ink)
    }
}

@Suppress("VerifyStrings")
@Preview(widthDp = 390)
@Composable
private fun PageHeaderPreview() {
    PreviewLightDark {
        PageHeader(title = "Modes and numbers", backLabel = "‹ More", onBack = {})
    }
}
