package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import opson.libraries.resources.generated.resources.ds_close
import opson.libraries.resources.generated.resources.Res as SharedRes
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The top of a pushed page, as an Android top app bar (design v3): a back arrow and the title on
 * one row, and optional [actions] at the end. Every More page and the Nutrients, Weight and
 * Water pages use it.
 *
 * Place it inside the page padding. The arrow's 48 dp target reaches [bleed] into that padding,
 * so the arrow sits where a Material top app bar puts it while the title lines up with the page.
 *
 * [backLabel] names where back goes ("‹ More", "‹ Trends"). It is no longer drawn; it becomes
 * the arrow's spoken label ("Back to More"), so callers keep passing the copy they already have.
 *
 * A page that is a task to finish or abandon (Create a meal, Add a food) passes
 * [PageHeaderNavigation.Close]: an X labelled "Close" in place of the arrow, as v3 draws them.
 */
@Composable
fun PageHeader(
    title: String,
    backLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bleed: Dp = PageHeaderDefaults.Bleed,
    navigation: PageHeaderNavigation = PageHeaderNavigation.Back,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val destination = backLabel.removePrefix("‹").trim()
    val description = when {
        navigation == PageHeaderNavigation.Close -> stringResource(SharedRes.string.ds_close)
        destination.isEmpty() -> stringResource(SharedRes.string.ds_back)
        else -> stringResource(SharedRes.string.ds_back_to, destination)
    }
    val icon = when (navigation) {
        PageHeaderNavigation.Back -> AppIcons.ArrowBack
        PageHeaderNavigation.Close -> AppIcons.Close
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .bleedHorizontal(bleed)
            .defaultMinSize(minHeight = PageHeaderDefaults.Height),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PageHeaderDefaults.TitleGap),
    ) {
        Box(
            modifier = Modifier
                .size(MinTouchTarget)
                .clip(CircleShape)
                .semantics { role = Role.Button }
                .bounceClick(scaleDown = 0.9f, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon = icon.described(description), size = 24.dp, color = AppTheme.colors.ink)
        }
        Text(
            text = title,
            typography = AppTheme.type.dayLabel,
            color = AppTheme.colors.ink,
            maxLines = 2,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
                .semantics { heading() },
        )
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** What the top app bar's leading button looks like. Both leave the page. */
enum class PageHeaderNavigation { Back, Close }

object PageHeaderDefaults {
    /** Material's small top app bar. */
    val Height: Dp = 64.dp

    /**
     * How far the back target reaches into the 16 dp page padding: the arrow's 24 dp glyph then
     * starts 16 dp from the screen edge, and the title 56 dp, as a Material top app bar.
     */
    val Bleed: Dp = 12.dp

    internal val TitleGap: Dp = 4.dp
}

@Suppress("VerifyStrings")
@Preview(widthDp = 390)
@Composable
private fun PageHeaderPreview() {
    PreviewLightDark {
        Column {
            PageHeader(title = "Modes and numbers", backLabel = "‹ More", onBack = {})
            PageHeader(title = "Create a meal", backLabel = "‹ Foods", onBack = {}, navigation = PageHeaderNavigation.Close)
            PageHeader(title = "Targets", backLabel = "‹ More", onBack = {}) {
                Box(
                    modifier = Modifier.size(MinTouchTarget),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon = AppIcons.Edit, size = 24.dp, color = AppTheme.colors.ink)
                }
            }
        }
    }
}
