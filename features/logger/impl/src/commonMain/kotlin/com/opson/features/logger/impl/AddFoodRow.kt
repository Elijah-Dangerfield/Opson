package com.opson.features.logger.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.MacroLineStyle
import com.opson.libraries.nutrition.MacroPart
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ui.MacroLine
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.nutrition.ui.separator
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.confirm
import com.opson.libraries.ui.system.toggle
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.adding_add_cd
import opson.libraries.resources.generated.resources.adding_added_cd
import opson.libraries.resources.generated.resources.adding_reason_favorite
import opson.libraries.resources.generated.resources.adding_reason_logged
import opson.libraries.resources.generated.resources.adding_reason_yours
import opson.libraries.resources.generated.resources.adding_unverified
import opson.libraries.resources.generated.resources.adding_your_usual
import opson.libraries.resources.generated.resources.search_badge_branded
import opson.libraries.resources.generated.resources.search_badge_nevo
import opson.libraries.resources.generated.resources.search_badge_off
import opson.libraries.resources.generated.resources.search_badge_opson
import opson.libraries.resources.generated.resources.search_badge_usda
import opson.libraries.resources.generated.resources.search_badge_verified_cd
import opson.libraries.resources.generated.resources.search_badge_yours
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A food with a + (design sync 4, 13.4b and 13.4c): its name, "Your usual · 1 cup · 4P 45C 0F"
 * (in search results, why it is there in place of "Your usual": "Logged 3 times · 1 cup"), its
 * energy, and a round + that logs it at once, or a ✓ once it is in. The name opens the food.
 */
@Composable
internal fun AddFoodRow(row: FoodRowUi, onOpen: () -> Unit, onAdd: () -> Unit, divider: Boolean, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    Column(modifier = modifier.fillMaxWidth()) {
        if (divider) HorizontalDivider(color = AppTheme.colors.line)
        AddRow(
            name = row.name,
            details = listOfNotNull(
                row.reason?.let { reasonText(it) } ?: stringResource(Res.string.adding_your_usual).takeIf { row.usual },
                row.subtitle?.takeIf { it.isNotBlank() },
                row.amount.takeIf { it.isNotEmpty() },
            ),
            macros = row.macros,
            calories = row.calories,
            added = row.added,
            badge = row.badge?.let { stringResource(it.label) },
            badgeAccent = row.badge == SourceBadge.YourFood,
            verified = row.verified,
            unverified = row.unverified,
            onOpen = {
                // The detail covers search; the keyboard would sit over it.
                focusManager.clearFocus()
                onOpen()
            },
            onAdd = {
                if (row.added) haptics.toggle(on = false) else haptics.confirm()
                onAdd()
            },
        )
    }
}

/** Why a result is where it is: "Favorite", "Logged 3 times", "Your food". */
@Composable
private fun reasonText(reason: RowReason): String = when (reason) {
    RowReason.Favorite -> stringResource(Res.string.adding_reason_favorite)
    is RowReason.Logged -> pluralStringResource(Res.plurals.adding_reason_logged, reason.times, reason.times)
    RowReason.YourFood -> stringResource(Res.string.adding_reason_yours)
}

/** A saved meal with its +: the whole meal goes in at once. */
@Composable
internal fun AddMealRow(meal: SavedMealRowUi, onAdd: () -> Unit, divider: Boolean) {
    val haptics = LocalHapticFeedback.current
    Column(modifier = Modifier.fillMaxWidth()) {
        if (divider) HorizontalDivider(color = AppTheme.colors.line)
        AddRow(
            name = meal.name,
            details = listOf(meal.foods),
            macros = emptyList(),
            calories = meal.calories,
            added = meal.added,
            badge = null,
            badgeAccent = false,
            verified = false,
            unverified = false,
            onOpen = null,
            onAdd = {
                if (!meal.added) haptics.confirm()
                onAdd()
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("LongParameterList")
private fun AddRow(
    name: String,
    details: List<String>,
    macros: List<MacroPart>,
    calories: Display,
    added: Boolean,
    badge: String?,
    badgeAccent: Boolean,
    verified: Boolean,
    unverified: Boolean,
    onOpen: (() -> Unit)?,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = ROW_HEIGHT)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .then(if (onOpen != null) Modifier.bounceClick(scaleDown = 0.98f, onClick = onOpen) else Modifier),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = name, typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            FlowRow {
                val sub = AppTheme.type.secondary.Tabular
                val dot = MacroLineStyle.Long.separator
                details.forEachIndexed { index, word ->
                    if (index > 0) Text(text = dot, typography = sub, color = AppTheme.colors.muted)
                    Text(text = word, typography = sub, color = AppTheme.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (details.isNotEmpty() && macros.isNotEmpty()) Text(text = dot, typography = sub, color = AppTheme.colors.muted)
                MacroLine(parts = macros, separator = MacroLineStyle.Compact.separator, typography = sub)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            NutrientText(metric = Metric.Calories, display = calories, typography = AppTheme.type.numeric, color = AppTheme.colors.ink, maxLines = 1)
            // Waiting for review says so in place of the source: it is what the person needs to know about these numbers.
            if (unverified) {
                UnverifiedMark()
            } else {
                badge?.let { SourceMark(it, verified, badgeAccent) }
            }
        }
        val description = stringResource(if (added) Res.string.adding_added_cd else Res.string.adding_add_cd, name)
        RoundIconButton(
            icon = (if (added) AppIcons.Check else AppIcons.Add).described(description),
            onClick = onAdd,
            selected = added,
            size = AppTheme.spacing.touchTarget,
            touchTarget = AppTheme.spacing.touchTarget,
        )
    }
}

/** The source's short name, with a check when someone accountable stands behind the numbers. */
@Composable
private fun SourceMark(badge: String, verified: Boolean, accent: Boolean) {
    val color = if (accent) AppTheme.colors.accent else AppTheme.colors.muted
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (verified) Icon(icon = AppIcons.CheckCircle.described(stringResource(Res.string.search_badge_verified_cd)), size = 12.dp, color = color)
        Text(text = badge, typography = AppTheme.type.captionStrong, color = color, maxLines = 1)
    }
}

/** "Unverified": a food this person added to Opson's food database that nobody has checked yet. */
@Composable
internal fun UnverifiedMark(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon = AppIcons.Schedule, size = 12.dp, color = AppTheme.colors.muted)
        Text(text = stringResource(Res.string.adding_unverified), typography = AppTheme.type.captionStrong, color = AppTheme.colors.muted, maxLines = 1)
    }
}

internal val SourceBadge.label: StringResource
    get() = when (this) {
        SourceBadge.Usda -> Res.string.search_badge_usda
        SourceBadge.UsdaBranded -> Res.string.search_badge_branded
        SourceBadge.OpenFoodFacts -> Res.string.search_badge_off
        SourceBadge.Nevo -> Res.string.search_badge_nevo
        SourceBadge.YourFood -> Res.string.search_badge_yours
        SourceBadge.Opson -> Res.string.search_badge_opson
    }

private val ROW_HEIGHT = 64.dp
