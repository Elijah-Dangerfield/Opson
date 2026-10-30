package com.opson.features.today.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ui.NutrientPhrase
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.system.minTouchTarget
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.goal_burn_goal
import opson.libraries.resources.generated.resources.goal_burn_usual
import opson.libraries.resources.generated.resources.goal_chip_balance
import opson.libraries.resources.generated.resources.goal_chip_burn
import opson.libraries.resources.generated.resources.goal_chip_deficit
import opson.libraries.resources.generated.resources.goal_chip_gain
import opson.libraries.resources.generated.resources.goal_chip_keep
import opson.libraries.resources.generated.resources.goal_chip_lose
import opson.libraries.resources.generated.resources.goal_chip_meals
import opson.libraries.resources.generated.resources.goal_chip_open
import opson.libraries.resources.generated.resources.goal_chip_steps
import opson.libraries.resources.generated.resources.goal_chip_surplus
import opson.libraries.resources.generated.resources.goal_chip_track
import opson.libraries.resources.generated.resources.goal_week_deficit
import opson.libraries.resources.generated.resources.goal_week_eat
import opson.libraries.resources.generated.resources.goal_week_on_track
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * The goal parts of Today's card (TodayGoals): the week line and the chip at the card's foot, and
 * the burned bar's marks with their legend.
 */

/** The week line, then the chip naming the goal. Nothing without a goal. */
@Composable
internal fun GoalFooter(card: EnergyCard, onAction: (TodayAction) -> Unit) {
    if (card.goalChip == null && card.weekLine == null) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        card.weekLine?.let { WeekLineText(it) }
        card.goalChip?.let { chip -> GoalChipButton(chip) { onAction(TodayAction.GoalChipTapped) } }
    }
}

@Composable
private fun WeekLineText(line: WeekLine) {
    val typography = AppTheme.type.secondaryStrong.Tabular
    val color = AppTheme.colors.inkSoft
    when (line) {
        WeekLine.OnTrack -> Text(text = stringResource(Res.string.goal_week_on_track), typography = typography, color = color)
        is WeekLine.Eat -> NutrientPhrase(Metric.Calories, line.kcal, phrase = { stringResource(Res.string.goal_week_eat, it) }, typography = typography, color = color)
        is WeekLine.Deficit -> NutrientPhrase(Metric.EnergyOut, line.kcal, phrase = { stringResource(Res.string.goal_week_deficit, it) }, typography = typography, color = color)
    }
}

/** A small tinted pill with the goal's name; a tap opens the goal. */
@Composable
internal fun GoalChipButton(chip: GoalChip, onClick: () -> Unit) {
    val open = stringResource(Res.string.goal_chip_open)
    val label = stringResource(chip.label.text)
    Box(
        modifier = Modifier
            .minTouchTarget()
            .semantics {
                role = Role.Button
                contentDescription = "$label. $open"
            }
            .bounceClick(scaleDown = 0.96f, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 30.dp)
                .clip(AppTheme.shapes.pill)
                .background(AppTheme.colors.accentTint)
                .padding(start = 10.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon = AppIcons.TrackChanges, size = 16.dp, color = AppTheme.colors.accent)
            Text(text = label, typography = AppTheme.type.secondarySmallStrong, color = AppTheme.colors.ink, maxLines = 1)
            Icon(icon = AppIcons.ChevronRight, size = 16.dp, color = AppTheme.colors.muted)
        }
    }
}

internal val GoalLabel.text: StringResource
    get() = when (this) {
        GoalLabel.Lose -> Res.string.goal_chip_lose
        GoalLabel.Keep -> Res.string.goal_chip_keep
        GoalLabel.Gain -> Res.string.goal_chip_gain
        GoalLabel.Deficit -> Res.string.goal_chip_deficit
        GoalLabel.Surplus -> Res.string.goal_chip_surplus
        GoalLabel.Balance -> Res.string.goal_chip_balance
        GoalLabel.Burn -> Res.string.goal_chip_burn
        GoalLabel.Steps -> Res.string.goal_chip_steps
        GoalLabel.TrackEveryDay -> Res.string.goal_chip_track
        GoalLabel.Meals -> Res.string.goal_chip_meals
    }

/**
 * The usual day's burn as a dashed mark and the burn goal as a solid one, drawn over whatever the
 * rest of the chain draws (the bar), on the card's scale.
 */
internal fun Modifier.burnMarks(burned: BurnedBars, usualColor: Color, goalColor: Color): Modifier =
    drawWithContent {
        drawContent()
        val overhang = size.height * MARK_OVERHANG
        burned.usual?.let { mark ->
            val x = size.width * mark.fraction
            drawLine(
                usualColor,
                start = Offset(x, -overhang),
                end = Offset(x, size.height + overhang),
                strokeWidth = MARK_WIDTH.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON, DASH_OFF)),
            )
        }
        burned.goal?.let { mark ->
            val x = size.width * mark.fraction
            drawLine(goalColor, start = Offset(x, -overhang), end = Offset(x, size.height + overhang), strokeWidth = MARK_WIDTH.dp.toPx())
        }
    }

/** "Usual day 2,450" and "Burn goal 2,600", each after a mark like the bar's. */
@Composable
internal fun BurnMarksLegend(burned: BurnedBars) {
    if (burned.usual == null && burned.goal == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        burned.usual?.let { mark ->
            MarkLegend(dashed = true) {
                NutrientPhrase(Metric.EnergyOut, mark.kcal, phrase = { stringResource(Res.string.goal_burn_usual, it) }, typography = AppTheme.type.secondary.Tabular, color = AppTheme.colors.inkSoft)
            }
        }
        burned.goal?.let { mark ->
            MarkLegend(dashed = false) {
                NutrientPhrase(Metric.EnergyOut, mark.kcal, phrase = { stringResource(Res.string.goal_burn_goal, it) }, typography = AppTheme.type.secondary.Tabular, color = AppTheme.colors.inkSoft)
            }
        }
    }
}

@Composable
private fun MarkLegend(dashed: Boolean, content: @Composable () -> Unit) {
    val color = if (dashed) AppTheme.colors.inkSoft.color else AppTheme.colors.accent.color
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(width = 4.dp, height = 16.dp)
                .drawBehind {
                    drawLine(
                        color,
                        start = Offset(size.width / 2, 0f),
                        end = Offset(size.width / 2, size.height),
                        strokeWidth = MARK_WIDTH.dp.toPx(),
                        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(DASH_ON, DASH_OFF)) else null,
                    )
                },
        )
        content()
    }
}

private const val MARK_WIDTH = 2
private const val MARK_OVERHANG = 0.35f
private const val DASH_ON = 6f
private const val DASH_OFF = 4f
