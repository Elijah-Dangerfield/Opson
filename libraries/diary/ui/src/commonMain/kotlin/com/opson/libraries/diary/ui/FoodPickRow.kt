package com.opson.libraries.diary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.opson.libraries.nutrition.MacroLineStyle
import com.opson.libraries.nutrition.MacroPart
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NutrientAmounts
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.macroLine
import com.opson.libraries.nutrition.ui.LocalVisibilityPolicy
import com.opson.libraries.nutrition.ui.MacroLine
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.nutrition.ui.currentVisibilityPolicy
import com.opson.libraries.nutrition.ui.previewVisibilityPolicy
import com.opson.libraries.nutrition.ui.separator
import com.opson.libraries.ui.PreviewLightDark
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.diary_pick_cd
import opson.libraries.resources.generated.resources.diary_remove_cd
import opson.libraries.resources.generated.resources.search_badge_verified_cd
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A food you can pick: its tile, the name, `subtitle · amount · 1P 27C 0F`, the
 * energy, and a + that turns into a check once picked. The logger's Usual,
 * Recent and search rows, and the Foods tab. [subtitle] is the brand or cuisine
 * ("Filipino"); pass energy in a bare context so it reads "288", as v3 draws it.
 *
 * [macros] and [calories] come resolved from the policy, so a hidden macro is
 * simply not in the list and hidden energy renders nothing. Tapping the name
 * runs [onOpen] (the amount sheet); the round button runs [onToggle].
 *
 * [source] is a short badge right-aligned under the energy ("USDA", "Your
 * food"), in the accent when [sourceAccent]; [verified] adds a small check
 * before it for a source someone accountable stands behind. It carries no
 * number, so it stays when the energy is hidden.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoodPickRow(
    name: String,
    amount: String,
    macros: List<MacroPart>,
    calories: Display,
    picked: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    iconSeed: String = name,
    showDivider: Boolean = true,
    subtitle: String? = null,
    source: String? = null,
    sourceAccent: Boolean = false,
    verified: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 60.dp)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            FoodIconTile(seed = iconSeed)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .bounceClick(scaleDown = 0.98f, onClick = onOpen),
            ) {
                Text(
                    text = name,
                    typography = AppTheme.type.bodyBold,
                    color = AppTheme.colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow {
                    val sub = AppTheme.type.secondary.Tabular
                    val dot = MacroLineStyle.Long.separator
                    val words = listOfNotNull(subtitle?.takeIf { it.isNotBlank() }, amount.takeIf { it.isNotEmpty() })
                    words.forEachIndexed { index, word ->
                        if (index > 0) Text(text = dot, typography = sub, color = AppTheme.colors.muted)
                        Text(text = word, typography = sub, color = AppTheme.colors.muted)
                    }
                    if (words.isNotEmpty() && macros.isNotEmpty()) Text(text = dot, typography = sub, color = AppTheme.colors.muted)
                    MacroLine(parts = macros, separator = MacroLineStyle.Compact.separator, typography = sub)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NutrientText(
                    metric = Metric.Calories,
                    display = calories,
                    typography = AppTheme.type.secondary.Tabular,
                    color = AppTheme.colors.muted,
                    maxLines = 1,
                )
                if (source != null) SourceBadge(source, sourceAccent, verified)
            }
            val description = stringResource(if (picked) Res.string.diary_remove_cd else Res.string.diary_pick_cd, name)
            RoundIconButton(
                icon = (if (picked) AppIcons.Check else AppIcons.Add).described(description),
                onClick = onToggle,
                selected = picked,
                touchTarget = AppTheme.spacing.touchTarget,
            )
        }
        if (showDivider) HorizontalDivider(color = AppTheme.colors.line)
    }
}

/** Where a food came from, as the logger's search options show it. */
@Composable
private fun SourceBadge(label: String, accent: Boolean, verified: Boolean) {
    val color = if (accent) AppTheme.colors.accent else AppTheme.colors.muted
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (verified) {
            Icon(
                icon = AppIcons.CheckCircle.described(stringResource(Res.string.search_badge_verified_cd)),
                size = 12.dp,
                color = color,
            )
        }
        Text(text = label, typography = AppTheme.type.captionStrong, color = color, maxLines = 1)
    }
}

@Preview(widthDp = 390)
@Composable
private fun FoodPickRowPreview() {
    PreviewLightDark {
        val policy = currentVisibilityPolicy()
        EdgeCard(edgeToEdge = false, verticalArrangement = Arrangement.Top) {
            listOf(
                Triple("Banana", "1 medium", NutrientAmounts(kcal = 105.0, protein = 1.3, carbs = 27.0, fat = 0.4)),
                Triple("Greek yogurt, plain", "1 package", NutrientAmounts(kcal = 100.0, protein = 17.0, carbs = 6.0, fat = 0.7)),
                Triple("Chicken adobo with a very long name that has to cut off", "1 cup", NutrientAmounts(kcal = 410.0, protein = 34.0, carbs = 6.0, fat = 27.0)),
            ).forEachIndexed { index, (name, amount, nutrients) ->
                FoodPickRow(
                    name = name,
                    amount = amount,
                    macros = policy.macroLine(nutrients, Surface.Logger, style = MacroLineStyle.Compact),
                    calories = policy.resolve(Metric.Calories, nutrients.kcal, Surface.Logger),
                    picked = index == 1,
                    onOpen = {},
                    onToggle = {},
                    showDivider = index < 2,
                    source = listOf("USDA", "USDA branded", "Your food")[index],
                    sourceAccent = index == 2,
                    verified = index < 2,
                )
            }
        }
    }
}

@Preview(widthDp = 390)
@Composable
private fun FoodPickRowBlindPreview() {
    CompositionLocalProvider(LocalVisibilityPolicy provides previewVisibilityPolicy(PresetId.Blind)) {
        FoodPickRowPreview()
    }
}
