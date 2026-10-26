package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.MinTouchTarget
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.ds_back
import opson.libraries.resources.generated.resources.ds_back_to
import opson.libraries.resources.generated.resources.Res as SharedRes
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * [PageHeader] with a muted line under the title (design v3: Nutrients' `Sunday, Oct 25`,
 * Patterns' `Last 4 weeks`). The same top app bar: back arrow, title, and the arrow centred on the
 * two lines together. [subtitle] `null` draws the title alone, like [PageHeader].
 */
@Composable
fun PageHeader(
    title: String,
    subtitle: String?,
    backLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bleed: Dp = PageHeaderDefaults.Bleed,
) {
    val destination = backLabel.removePrefix("‹").trim()
    val description = if (destination.isEmpty()) {
        stringResource(SharedRes.string.ds_back)
    } else {
        stringResource(SharedRes.string.ds_back_to, destination)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .bleedHorizontal(bleed)
            .defaultMinSize(minHeight = PageHeaderDefaults.Height),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SubtitleTitleGap),
    ) {
        Box(
            modifier = Modifier
                .size(MinTouchTarget)
                .clip(CircleShape)
                .semantics { role = Role.Button }
                .bounceClick(scaleDown = 0.9f, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon = AppIcons.ArrowBack.described(description), size = 24.dp, color = AppTheme.colors.ink)
        }
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                text = title,
                typography = AppTheme.type.dayLabel,
                color = AppTheme.colors.ink,
                maxLines = 2,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(text = subtitle, typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.muted, maxLines = 1)
            }
        }
    }
}

/** The same gap [PageHeader] leaves between the arrow's target and the title. */
private val SubtitleTitleGap: Dp = 4.dp

@Suppress("VerifyStrings")
@Preview(widthDp = 390)
@Composable
private fun PageHeaderSubtitlePreview() {
    PreviewLightDark {
        PageHeader(title = "Nutrients", subtitle = "Sunday, Oct 25", backLabel = "‹ Today", onBack = {})
    }
}
