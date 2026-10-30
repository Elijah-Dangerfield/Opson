package com.opson.features.trends.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.TrendsRange
import com.opson.libraries.health.HealthSource
import com.opson.libraries.nutrition.countText
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.IntakeBarChart
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.IconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.Dimension
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.integrations_apple_health
import opson.libraries.resources.generated.resources.integrations_connect
import opson.libraries.resources.generated.resources.integrations_get_health_connect
import opson.libraries.resources.generated.resources.integrations_health_connect
import opson.libraries.resources.generated.resources.steps_not_in_burned
import opson.libraries.resources.generated.resources.trends_avg
import opson.libraries.resources.generated.resources.trends_recap_days_value
import opson.libraries.resources.generated.resources.trends_steps
import opson.libraries.resources.generated.resources.trends_steps_allow
import opson.libraries.resources.generated.resources.trends_steps_allow_button
import opson.libraries.resources.generated.resources.trends_steps_at_goal
import opson.libraries.resources.generated.resources.trends_steps_average
import opson.libraries.resources.generated.resources.trends_steps_best
import opson.libraries.resources.generated.resources.trends_steps_best_value
import opson.libraries.resources.generated.resources.trends_steps_connect
import opson.libraries.resources.generated.resources.trends_steps_empty_apple_health
import opson.libraries.resources.generated.resources.trends_steps_empty_health_connect
import opson.libraries.resources.generated.resources.trends_steps_from
import opson.libraries.resources.generated.resources.trends_steps_goal_line
import opson.libraries.resources.generated.resources.trends_steps_help_apple_health
import opson.libraries.resources.generated.resources.trends_steps_help_cd
import opson.libraries.resources.generated.resources.trends_steps_help_health_connect
import opson.libraries.resources.generated.resources.trends_steps_help_private
import opson.libraries.resources.generated.resources.trends_steps_streak
import opson.libraries.resources.generated.resources.trends_steps_today
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Steps card: a bar a day in the accent colour, a steps goal as a line across them, then the
 * facts under it. Like the Eaten chart, a bar never changes colour with the count: a short day is
 * not a worse one. Not connected, it says what it would show and offers Connect.
 */
@Composable
internal fun StepsCardView(card: StepsCard, onAction: (TrendsAction) -> Unit) {
    TrendsCard(verticalArrangement = Arrangement.spacedBy(Dimension.D500)) {
        Header(card, onAction)
        if (card.explained) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimension.D300)) {
                Help(stringResource(if (card.source == HealthSource.AppleHealth) Res.string.trends_steps_help_apple_health else Res.string.trends_steps_help_health_connect))
                Help(stringResource(Res.string.steps_not_in_burned))
                Help(stringResource(Res.string.trends_steps_help_private))
            }
        }
        val name = stringResource(card.source.title)
        when (card) {
            is StepsCard.Connect -> {
                Body(stringResource(Res.string.trends_steps_connect, name))
                FlatButton(
                    text = stringResource(if (card.install) Res.string.integrations_get_health_connect else Res.string.integrations_connect),
                    onClick = { onAction(TrendsAction.StepsConnectTapped) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is StepsCard.Allow -> {
                Body(stringResource(Res.string.trends_steps_allow, name))
                FlatButton(
                    text = stringResource(Res.string.trends_steps_allow_button),
                    onClick = { onAction(TrendsAction.StepsConnectTapped) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is StepsCard.Empty -> Body(
                stringResource(if (card.source == HealthSource.AppleHealth) Res.string.trends_steps_empty_apple_health else Res.string.trends_steps_empty_health_connect),
            )
            is StepsCard.Chart -> Chart(card)
        }
    }
}

/** "Steps", where they come from, the average beside it once there is one, and the (i). */
@Composable
private fun Header(card: StepsCard, onAction: (TrendsAction) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        Column(Modifier.weight(1f)) {
            CardTitle(stringResource(Res.string.trends_steps))
            Text(text = stringResource(Res.string.trends_steps_from, stringResource(card.source.title)), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
        val average = (card as? StepsCard.Chart)?.average
        if (average != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D100)) {
                Text(text = countText(average), typography = AppTheme.type.bodyBold.Tabular, color = AppTheme.colors.ink)
                Text(text = stringResource(Res.string.trends_avg), typography = AppTheme.type.body, color = AppTheme.colors.ink)
            }
        }
        IconButton(
            icon = AppIcons.Info.described(stringResource(Res.string.trends_steps_help_cd)),
            onClick = { onAction(TrendsAction.StepsHelpTapped) },
            iconColor = AppTheme.colors.muted,
        )
    }
}

@Composable
private fun Chart(card: StepsCard.Chart) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimension.D300)) {
        IntakeBarChart(
            bars = card.bars,
            band = card.band,
            readouts = card.readouts,
            bandColor = AppTheme.colors.accentTint,
            barGap = card.range.barGap,
        )
        card.axis?.let { axis ->
            Row(Modifier.fillMaxWidth()) {
                Text(text = axis.start, typography = AppTheme.type.secondary, color = AppTheme.colors.muted, modifier = Modifier.weight(1f))
                Text(text = axis.end, typography = AppTheme.type.secondaryStrong.Bold, color = AppTheme.colors.ink)
            }
        }
        card.goal?.let { goal ->
            Text(text = stringResource(Res.string.trends_steps_goal_line, countText(goal.steps)), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
    }
    Column {
        val rows = buildList<Pair<String, String>> {
            card.today?.let { add(stringResource(Res.string.trends_steps_today) to countText(it)) }
            card.average?.let { add(stringResource(Res.string.trends_steps_average) to countText(it)) }
            card.best?.let { add(stringResource(Res.string.trends_steps_best) to stringResource(Res.string.trends_steps_best_value, countText(it.steps), it.day)) }
            card.goal?.takeIf { it.counted > 0 }?.let { add(stringResource(Res.string.trends_steps_at_goal) to stringResource(Res.string.trends_recap_days_value, it.met, it.counted)) }
        }
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) HorizontalDivider(color = AppTheme.colors.line)
            FactRow(label, value)
        }
        // Only a run worth naming: a single day isn't a streak.
        card.goal?.streak?.takeIf { it >= MIN_STREAK }?.let { streak ->
            HorizontalDivider(color = AppTheme.colors.line)
            Text(
                text = pluralStringResource(Res.plurals.trends_steps_streak, streak, streak),
                typography = AppTheme.type.bodyBold,
                color = AppTheme.colors.ink,
                modifier = Modifier.fillMaxWidth().padding(vertical = Dimension.D300),
            )
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(text = text, typography = AppTheme.type.body, color = AppTheme.colors.inkSoft)
}

@Composable
private fun Help(text: String) {
    Text(text = text, typography = AppTheme.type.secondary, color = AppTheme.colors.inkSoft)
}

private val HealthSource.title: StringResource
    get() = when (this) {
        HealthSource.AppleHealth -> Res.string.integrations_apple_health
        HealthSource.HealthConnect -> Res.string.integrations_health_connect
    }

/** A week's seven bars keep the Eaten chart's gap; a month's or three months' days need narrower ones to fit. */
private val TrendsRange.barGap: Dp
    get() = when (this) {
        TrendsRange.Week -> 8.dp
        TrendsRange.FourWeeks -> 3.dp
        TrendsRange.ThreeMonths -> 1.dp
    }

private const val MIN_STREAK = 2
