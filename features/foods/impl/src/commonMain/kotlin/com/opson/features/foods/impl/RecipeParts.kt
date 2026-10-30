package com.opson.features.foods.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.MacroLineStyle
import com.opson.libraries.nutrition.ui.MacroLine
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.nutrition.ui.separator
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconResource
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.recipes_checked_cd
import org.jetbrains.compose.resources.stringResource

/** A row with a label on the left and an amount's calories and macros on the right: "Per serving  312 · 13P 40C 9F". */
@Composable
internal fun AmountRow(label: String, numbers: AmountNumbersUi, strong: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = label, typography = if (strong) AppTheme.type.cardTitle else AppTheme.type.body, color = AppTheme.colors.ink)
            MacroLine(parts = numbers.macros, separator = MacroLineStyle.Compact.separator, typography = AppTheme.type.secondary.Tabular)
        }
        NutrientText(
            metric = Metric.Calories,
            display = numbers.calories,
            typography = (if (strong) AppTheme.type.cardTitle else AppTheme.type.body).Tabular,
            color = AppTheme.colors.ink,
            maxLines = 1,
        )
    }
}

/**
 * An ingredient on a recipe's page: name, amount and its bare calories, with a small check when it
 * came from a food Opson checked (someone's shared recipe only).
 */
@Composable
internal fun IngredientRow(ingredient: IngredientUi, showDivider: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = ingredient.name,
                        typography = AppTheme.type.body,
                        color = AppTheme.colors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (ingredient.checked) {
                        Icon(
                            icon = AppIcons.Check.described(stringResource(Res.string.recipes_checked_cd)),
                            size = 14.dp,
                            color = AppTheme.colors.accent,
                        )
                    }
                }
                if (ingredient.amount.isNotBlank()) {
                    Text(text = ingredient.amount, typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted)
                }
            }
            NutrientText(
                metric = Metric.Calories,
                display = ingredient.calories,
                typography = AppTheme.type.secondary.Tabular,
                color = AppTheme.colors.muted,
                maxLines = 1,
            )
        }
        if (showDivider) HorizontalDivider(color = AppTheme.colors.line)
    }
}

/** "+ Add an ingredient", "Scan a recipe code": a card that is one accent action. */
@Composable
internal fun ActionCard(icon: IconResource, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    EdgeCard(modifier = modifier, edgeToEdge = false, contentPadding = PaddingValues(0.dp)) {
        ActionLine(icon, label, onClick, Modifier.padding(horizontal = FoodsDefaults.CardPadding))
    }
}

/** One accent action inside a card. */
@Composable
internal fun ActionLine(icon: IconResource, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .semantics { role = Role.Button }
            .bounceClick(scaleDown = 0.98f, onClick = onClick)
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Icon(icon = icon, size = 24.dp, color = AppTheme.colors.accent)
        Text(text = label, typography = AppTheme.type.cardTitle, color = AppTheme.colors.accent)
    }
}
