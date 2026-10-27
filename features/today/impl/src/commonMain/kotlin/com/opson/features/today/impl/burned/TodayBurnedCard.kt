package com.opson.features.today.impl.burned

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.opson.libraries.diary.ui.TodayCard
import com.opson.libraries.diary.ui.exerciseActivityLabel
import com.opson.libraries.nutrition.ExerciseActivities
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.nutrition.ui.NutrientVisible
import com.opson.libraries.ui.components.ListRow
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.IconTile
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.MinTouchTarget
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import com.opson.system.color.ToneColors
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.exercise_minutes_chip
import opson.libraries.resources.generated.resources.today_burned_add
import opson.libraries.resources.generated.resources.today_burned_everyday
import opson.libraries.resources.generated.resources.today_burned_exercise
import opson.libraries.resources.generated.resources.today_burned_health
import opson.libraries.resources.generated.resources.today_burned_none
import opson.libraries.resources.generated.resources.today_burned_resting
import opson.libraries.resources.generated.resources.today_burned_title
import opson.libraries.resources.generated.resources.today_burned_unknown
import opson.libraries.resources.generated.resources.today_burned_unknown_action
import opson.libraries.resources.generated.resources.today_burned_watch_has_it
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Today's Burned section: the total with its +, where it came from, and each exercise logged,
 * which opens its sheet. Every amount is a resolved [com.opson.libraries.nutrition.Display] drawn
 * through the tagged texts, so a hidden one takes its label with it.
 */
@Composable
fun TodayBurnedSection(card: TodayBurnedCard, onAction: (TodayBurnedAction) -> Unit, modifier: Modifier = Modifier) {
    val tone = ToneColors(AppTheme.colors.nutrients.fat.content, AppTheme.colors.nutrients.fat.container)
    TodayCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            IconTile(icon = AppIcons.FitnessCenter, tone = tone)
            Text(
                text = stringResource(Res.string.today_burned_title),
                typography = AppTheme.type.cardTitle,
                color = AppTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            NutrientText(Metric.EnergyOut, card.total, typography = AppTheme.type.cardTitle.Tabular, color = AppTheme.colors.ink, maxLines = 1)
            RoundIconButton(
                icon = AppIcons.Add.described(stringResource(Res.string.today_burned_add)),
                onClick = { onAction(TodayBurnedAction.AddTapped) },
                size = MinTouchTarget,
            )
        }
        if (!card.known) {
            Text(text = stringResource(Res.string.today_burned_unknown), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
            FlatButton(
                text = stringResource(Res.string.today_burned_unknown_action),
                onClick = { onAction(TodayBurnedAction.RestingEnergyTapped) },
                tone = FlatButtonTone.Soft,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        card.parts.forEach { part ->
            NutrientVisible(part.amount) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = stringResource(part.kind.label), typography = AppTheme.type.secondary, color = AppTheme.colors.muted, modifier = Modifier.weight(1f))
                    NutrientText(Metric.EnergyOut, part.amount, typography = AppTheme.type.secondary.Tabular, color = AppTheme.colors.inkSoft, maxLines = 1)
                }
            }
        }
        Column {
            if (card.entries.isEmpty()) {
                Text(text = stringResource(Res.string.today_burned_none), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
            }
            card.entries.forEachIndexed { index, row ->
                val label = row.name ?: stringResource(exerciseActivityLabel(row.activity) ?: checkNotNull(exerciseActivityLabel(ExerciseActivities.OTHER)))
                val subtitle = buildList {
                    add(stringResource(Res.string.exercise_minutes_chip, row.minutes))
                    add(DateCopy.time(row.startedAt.hour, row.startedAt.minute))
                    if (row.watchHasIt) add(stringResource(Res.string.today_burned_watch_has_it))
                }.joinToString(" · ")
                ListRow(
                    title = label,
                    subtitle = subtitle,
                    onClick = { onAction(TodayBurnedAction.EntryTapped(row.id)) },
                    showDivider = index < card.entries.lastIndex,
                ) {
                    NutrientText(Metric.EnergyOut, row.amount, typography = AppTheme.type.bodyBold.Tabular, color = AppTheme.colors.ink, maxLines = 1)
                }
            }
        }
    }
}

private val BurnedPartKind.label: StringResource
    get() = when (this) {
        BurnedPartKind.Resting -> Res.string.today_burned_resting
        BurnedPartKind.Everyday -> Res.string.today_burned_everyday
        BurnedPartKind.Health -> Res.string.today_burned_health
        BurnedPartKind.Exercise -> Res.string.today_burned_exercise
    }
