package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconSize
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A quiet line of context on the soft fill, like the mode banners on Today
 * ("Your weekly check is on Sunday evening."). Information only: no icon, no
 * colour that reads as a warning, nothing to dismiss.
 */
@Composable
fun Banner(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        typography = AppTheme.type.secondary,
        color = AppTheme.colors.ink,
        modifier = modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.inputLarge)
            .background(AppTheme.colors.soft)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

/**
 * A note that explains how a number was reached, as design sync 2 draws the photo estimate's
 * (3.7): an outlined "i" on the warm tint. It explains rather than warns, so the tint is the carbs
 * container, never a warning colour, and there is nothing to dismiss.
 */
@Composable
fun NoteBanner(
    text: String,
    modifier: Modifier = Modifier,
) {
    val warm = AppTheme.colors.nutrients.carbs
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.inputLarge)
            .background(warm.container)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon = AppIcons.Info, size = IconSize.Medium, color = AppTheme.colors.ink)
        Text(text = text, typography = AppTheme.type.body, color = AppTheme.colors.ink)
    }
}

@Preview(widthDp = 390)
@Composable
private fun BannerPreview() {
    PreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Banner("Your weekly check is on Sunday evening.")
            Banner("Tag how meals felt when you add them. No nutrition data in this mode.")
            NoteBanner("Photos miss oil and cooking method, so your note was used. This is saved as an estimate.")
        }
    }
}
