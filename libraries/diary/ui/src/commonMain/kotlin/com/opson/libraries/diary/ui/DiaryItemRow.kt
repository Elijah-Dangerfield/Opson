package com.opson.libraries.diary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.ui.LocalVisibilityPolicy
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.nutrition.ui.currentVisibilityPolicy
import com.opson.libraries.nutrition.ui.previewVisibilityPolicy
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.thenIf
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * One logged item: the name, a line under it (`1 cup · Estimate`) and its
 * energy on the right. Today's meal cards and Create a meal. [calories] comes
 * resolved; hidden renders nothing, not a blank where a number was.
 *
 * A list of rows draws a line under each ([showDivider]). Today's meal card
 * draws the line above each row instead, after its header, so it passes
 * [dividerAbove] and [DiaryItemRowDefaults.CardPadding].
 */
@Composable
fun DiaryItemRow(
    name: String,
    sub: String,
    calories: Display,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    dividerAbove: Boolean = false,
    contentPadding: PaddingValues = DiaryItemRowDefaults.ListPadding,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (dividerAbove) HorizontalDivider(color = AppTheme.colors.line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AppTheme.spacing.touchTarget)
                .thenIf(onClick != null) { bounceClick(scaleDown = 0.98f, onClick = onClick!!) }
                .padding(contentPadding),
            verticalAlignment = if (dividerAbove) Alignment.Top else Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    typography = AppTheme.type.body,
                    color = AppTheme.colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (sub.isNotEmpty()) {
                    Text(text = sub, typography = AppTheme.type.secondarySmall.Tabular, color = AppTheme.colors.muted)
                }
            }
            NutrientText(
                metric = Metric.Calories,
                display = calories,
                typography = AppTheme.type.numericSmall,
                color = AppTheme.colors.ink,
                maxLines = 1,
            )
        }
        if (showDivider) HorizontalDivider(color = AppTheme.colors.line)
    }
}

object DiaryItemRowDefaults {
    /** Rows in a list: 10 above and below, a line under each. */
    val ListPadding = PaddingValues(vertical = 10.dp)

    /** Rows on a Today meal card: a line above, 10 under it, and the card's own gap after. */
    val CardPadding = PaddingValues(top = 10.dp)
}

@Preview(widthDp = 390)
@Composable
private fun DiaryItemRowPreview() {
    PreviewLightDark {
        val policy = currentVisibilityPolicy()
        EdgeCard(edgeToEdge = false, verticalArrangement = Arrangement.Top) {
            DiaryItemRow(
                name = "Greek yogurt, plain",
                sub = "1 package · Weighed",
                calories = policy.resolve(Metric.Calories, 100.0, Surface.Today),
                onClick = {},
            )
            DiaryItemRow(
                name = "Lola's pancit",
                sub = "Named",
                calories = policy.resolve(Metric.Calories, null, Surface.Today),
                onClick = {},
                showDivider = false,
            )
        }
    }
}

@Preview(widthDp = 390)
@Composable
private fun DiaryItemRowBlindPreview() {
    CompositionLocalProvider(LocalVisibilityPolicy provides previewVisibilityPolicy(PresetId.Blind)) {
        DiaryItemRowPreview()
    }
}
