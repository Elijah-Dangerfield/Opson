package com.opson.features.logger.impl

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opson.libraries.nutrition.MacroLineStyle
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ui.MacroLine
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.nutrition.ui.separator
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.button.ButtonGhost
import com.opson.libraries.ui.components.button.ButtonPrimary
import com.opson.libraries.ui.components.button.ButtonStyle
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconSize
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.barcode_code
import opson.libraries.resources.generated.resources.barcode_match_corrected
import opson.libraries.resources.generated.resources.barcode_match_numbers_off
import opson.libraries.resources.generated.resources.barcode_match_suggested
import opson.libraries.resources.generated.resources.barcode_match_verified
import opson.libraries.resources.generated.resources.barcode_matches_body
import opson.libraries.resources.generated.resources.barcode_matches_title
import opson.libraries.resources.generated.resources.barcode_scan_again
import opson.libraries.resources.generated.resources.barcode_search_instead
import opson.libraries.resources.generated.resources.search_off_credit
import org.jetbrains.compose.resources.stringResource

/**
 * "Pick a match": every food the code matched, the suggested one first and outlined, each with its
 * database, a serving and its numbers as the person's mode shows them. A tap opens the food, as a
 * scan's single hit does. Search and another scan stay below, the ways out a miss offers too.
 */
@Composable
internal fun MatchList(stage: ScanStage.Matches, onAction: (BarcodeScanAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        Column {
            Text(text = stringResource(Res.string.barcode_matches_title), typography = AppTheme.type.sheetTitle, color = AppTheme.colors.ink)
            Text(text = stringResource(Res.string.barcode_matches_body), typography = AppTheme.type.body, color = AppTheme.colors.inkSoft)
        }
        stage.rows.forEach { row ->
            MatchRow(row, onClick = { onAction(BarcodeScanAction.MatchTapped(row.foodId)) })
        }
        // Open Food Facts' licence asks for its credit wherever its foods appear.
        if (stage.openFoodFacts) {
            Text(text = stringResource(Res.string.search_off_credit), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
        Text(text = stringResource(Res.string.barcode_code, stage.code), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        ButtonPrimary(
            onClick = { onAction(BarcodeScanAction.SearchInsteadTapped) },
            style = ButtonStyle.Tonal,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.barcode_search_instead))
        }
        ButtonGhost(onClick = { onAction(BarcodeScanAction.ScanAgainTapped) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.barcode_scan_again))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchRow(row: MatchRowUi, onClick: () -> Unit) {
    val shape = AppTheme.shapes.groupedList
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (row.suggested) AppTheme.colors.accentTint else AppTheme.colors.surface)
            .then(if (row.suggested) Modifier.border(SuggestedBorder, AppTheme.colors.accent.color, shape) else Modifier)
            .bounceClick(scaleDown = 0.98f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (row.suggested) {
                Text(text = stringResource(Res.string.barcode_match_suggested), typography = AppTheme.type.captionStrong, color = AppTheme.colors.accent)
            }
            Text(text = row.name, typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = AppTheme.type.secondary.Tabular
            val dot = MacroLineStyle.Long.separator
            FlowRow {
                val details = listOfNotNull(row.brand, row.amount.takeIf { it.isNotEmpty() })
                details.forEachIndexed { index, word ->
                    if (index > 0) Text(text = dot, typography = sub, color = AppTheme.colors.muted)
                    Text(text = word, typography = sub, color = AppTheme.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (details.isNotEmpty() && row.macros.isNotEmpty()) Text(text = dot, typography = sub, color = AppTheme.colors.muted)
                MacroLine(parts = row.macros, separator = MacroLineStyle.Compact.separator, typography = sub)
            }
            Marks(row)
        }
        NutrientText(metric = Metric.Calories, display = row.calories, typography = AppTheme.type.numeric, color = AppTheme.colors.ink, maxLines = 1)
        Icon(icon = AppIcons.ChevronRight, size = IconSize.Medium, color = AppTheme.colors.muted)
    }
}

/** The databases the row stands for ("USDA branded · Open Food Facts"), then what is known about its numbers. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Marks(row: MatchRowUi) {
    val mark = AppTheme.type.captionStrong
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val sources = row.sources.map { stringResource(it.label) }
        if (sources.isNotEmpty()) {
            Text(text = sources.joinToString(MacroLineStyle.Long.separator), typography = mark, color = AppTheme.colors.muted, maxLines = 2)
        }
        when {
            row.unverified -> UnverifiedMark()
            row.verified -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(icon = AppIcons.CheckCircle, size = 12.dp, color = AppTheme.colors.muted)
                Text(text = stringResource(Res.string.barcode_match_verified), typography = mark, color = AppTheme.colors.muted, maxLines = 1)
            }
        }
        if (row.corrected) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(icon = AppIcons.EditNote, size = 12.dp, color = AppTheme.colors.muted)
                Text(text = stringResource(Res.string.barcode_match_corrected), typography = mark, color = AppTheme.colors.muted, maxLines = 1)
            }
        }
        if (row.numbersLookOff) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(icon = AppIcons.Info, size = 12.dp, color = AppTheme.colors.inkSoft)
                Text(text = stringResource(Res.string.barcode_match_numbers_off), typography = mark, color = AppTheme.colors.inkSoft, maxLines = 1)
            }
        }
    }
}

private val SuggestedBorder = 1.5.dp
