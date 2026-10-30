package com.opson.features.today.impl.activity

import com.opson.features.today.impl.BurnedGap
import com.opson.features.today.impl.burnedUnavailableText
import com.opson.features.today.impl.label

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.ui.TodayCard
import com.opson.libraries.diary.ui.exerciseActivityLabel
import com.opson.libraries.diary.ui.label
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.WorkoutKind
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.ExerciseActivities
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.RestingFrom
import com.opson.libraries.nutrition.ui.NutrientPhrase
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.nutrition.ui.NutrientVisible
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.ListRow
import com.opson.libraries.ui.components.ListRowChevron
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.chip.StatusPill
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.IconButton
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.ToneIcon
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.MinTouchTarget
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.system.color.ToneColors
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.energy_info_cd
import opson.libraries.resources.generated.resources.exercise_minutes_chip
import opson.libraries.resources.generated.resources.integrations_apple_health
import opson.libraries.resources.generated.resources.integrations_health_connect
import opson.libraries.resources.generated.resources.settings_energy_logged
import opson.libraries.resources.generated.resources.settings_energy_resting_calculated
import opson.libraries.resources.generated.resources.settings_energy_resting_health
import opson.libraries.resources.generated.resources.settings_energy_resting_own
import opson.libraries.resources.generated.resources.today_activity_active
import opson.libraries.resources.generated.resources.today_activity_active_line
import opson.libraries.resources.generated.resources.today_activity_add_missed
import opson.libraries.resources.generated.resources.energy_digesting
import opson.libraries.resources.generated.resources.energy_digesting_line
import opson.libraries.resources.generated.resources.today_activity_exercise_missed
import opson.libraries.resources.generated.resources.today_activity_from
import opson.libraries.resources.generated.resources.today_activity_health_note
import opson.libraries.resources.generated.resources.today_activity_resting
import opson.libraries.resources.generated.resources.today_activity_title
import opson.libraries.resources.generated.resources.today_activity_total
import opson.libraries.resources.generated.resources.today_activity_watch_has_it
import opson.libraries.resources.generated.resources.today_activity_workouts_included
import opson.libraries.resources.generated.resources.today_burned_add
import opson.libraries.resources.generated.resources.today_burned_everyday
import opson.libraries.resources.generated.resources.today_burned_exercise
import opson.libraries.resources.generated.resources.today_burned_none
import opson.libraries.resources.generated.resources.today_workout_running
import opson.libraries.resources.generated.resources.today_workout_walking
import opson.libraries.resources.generated.resources.today_workout_hiking
import opson.libraries.resources.generated.resources.today_workout_cycling
import opson.libraries.resources.generated.resources.today_workout_swimming
import opson.libraries.resources.generated.resources.today_workout_strength
import opson.libraries.resources.generated.resources.today_workout_hiit
import opson.libraries.resources.generated.resources.today_workout_yoga
import opson.libraries.resources.generated.resources.today_workout_pilates
import opson.libraries.resources.generated.resources.today_workout_rowing
import opson.libraries.resources.generated.resources.today_workout_elliptical
import opson.libraries.resources.generated.resources.today_workout_stairs
import opson.libraries.resources.generated.resources.today_workout_dance
import opson.libraries.resources.generated.resources.today_workout_martial_arts
import opson.libraries.resources.generated.resources.today_workout_sports
import opson.libraries.resources.generated.resources.today_workout_snow
import opson.libraries.resources.generated.resources.today_workout_water
import opson.libraries.resources.generated.resources.today_workout_other
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Today's Activity card: the title (with "From Apple Health" when the health app counts the day's
 * activity), the lines that make up what was burned with the total under them, then each exercise
 * logged, which opens its sheet. With no health app the + logs exercise; with one, the watch's
 * workouts are already in its active energy, so adding is a quieter button under a line saying
 * so, for what the watch missed. Every amount is a resolved [Display] drawn through the tagged
 * texts, so a hidden one takes its label with it.
 */
@Composable
fun TodayActivitySection(card: TodayActivityCard, onAction: (TodayActivityAction) -> Unit, modifier: Modifier = Modifier) {
    val health = card.healthSource
    TodayCard(modifier = modifier) {
        Header(card, onAction)
        if (!card.known) {
            // What Burned is waiting on, and the one place to fix it: never a sum with a line missing.
            val gap = card.gap ?: BurnedGap()
            Text(text = burnedUnavailableText(gap), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
            FlatButton(
                text = stringResource(gap.fix.label),
                onClick = { onAction(TodayActivityAction.BurnedFixTapped) },
                tone = FlatButtonTone.Soft,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (card.lines.isNotEmpty()) Sum(card, onAction)
        Workouts(card.workouts)
        Entries(card, onAction)
        if (health != null && !card.closed) {
            val name = stringResource(health.title)
            Text(text = stringResource(Res.string.today_activity_health_note, name), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
            FlatButton(
                text = stringResource(Res.string.today_activity_add_missed),
                onClick = { onAction(TodayActivityAction.AddMissedTapped) },
                tone = FlatButtonTone.Outlined,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Header(card: TodayActivityCard, onAction: (TodayActivityAction) -> Unit) {
    val tone = ToneColors(AppTheme.colors.nutrients.fat.content, AppTheme.colors.nutrients.fat.container)
    val health = card.healthSource
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        ToneIcon(icon = AppIcons.FitnessCenter, tone = tone)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = stringResource(Res.string.today_activity_title), typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
            if (health != null) StatusPill(label = stringResource(Res.string.today_activity_from, stringResource(health.title)), icon = AppIcons.Check)
        }
        // With a health app the watch logs workouts itself; adding one by hand is the quiet button below.
        if (!card.closed && health == null) {
            RoundIconButton(
                icon = AppIcons.Add.described(stringResource(Res.string.today_burned_add)),
                onClick = { onAction(TodayActivityAction.AddTapped) },
                size = MinTouchTarget,
            )
        }
    }
}

/** The lines, then a rule and the total they add up to. */
@Composable
private fun Sum(card: TodayActivityCard, onAction: (TodayActivityAction) -> Unit) {
    Column {
        card.lines.forEach { line ->
            NutrientVisible(line.amount) { Line(line, onAction) }
        }
        NutrientVisible(card.total) {
            HorizontalDivider(color = AppTheme.colors.line, modifier = Modifier.padding(vertical = 4.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(Res.string.today_activity_total), typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
                // What TDEE means and how today's adds up, in the settings sheet every energy page opens.
                IconButton(
                    icon = AppIcons.Info.described(stringResource(Res.string.energy_info_cd)),
                    onClick = { onAction(TodayActivityAction.BurnedInfoTapped) },
                    iconColor = AppTheme.colors.muted,
                    size = IconButton.Size.Small,
                )
                Spacer(Modifier.weight(1f))
                NutrientText(Metric.EnergyOut, card.total, typography = AppTheme.type.cardTitle.Tabular, color = AppTheme.colors.ink, maxLines = 1)
            }
        }
    }
}

/** One part: its dot in the top card's resting or active colour, what it is, where it came from, and the amount. */
@Composable
private fun Line(line: ActivityLine, onAction: (TodayActivityAction) -> Unit) {
    val resting = line is ActivityLine.Resting
    val tap = if (resting) Modifier.bounceClick(scaleDown = 0.98f) { onAction(TodayActivityAction.RestingEnergyTapped) } else Modifier
    Row(
        modifier = Modifier.fillMaxWidth().then(tap).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Dot(if (resting) AppTheme.colors.restingEnergy else AppTheme.colors.activeEnergy)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = stringResource(line.title), typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink)
            LineDetail(line)
        }
        NutrientText(Metric.EnergyOut, line.amount, typography = AppTheme.type.bodyBold.Tabular, color = AppTheme.colors.ink, maxLines = 1)
        if (resting) ListRowChevron()
    }
}

@Composable
private fun LineDetail(line: ActivityLine) {
    val detail: String = when (line) {
        is ActivityLine.Resting -> when (line.from) {
            RestingFrom.Health -> stringResource(Res.string.settings_energy_resting_health, line.source?.let { stringResource(it.title) }.orEmpty())
            RestingFrom.Own -> stringResource(Res.string.settings_energy_resting_own)
            RestingFrom.Calculated -> stringResource(Res.string.settings_energy_resting_calculated)
        }
        is ActivityLine.Active -> if (line.workouts is Display.Shown) {
            NutrientPhrase(
                metric = Metric.EnergyOut,
                display = line.workouts,
                phrase = { stringResource(Res.string.today_activity_workouts_included, it) },
                typography = AppTheme.type.secondary.Tabular,
                color = AppTheme.colors.muted,
            )
            return
        } else {
            stringResource(Res.string.today_activity_active_line)
        }
        is ActivityLine.Everyday -> stringResource(line.level.label)
        is ActivityLine.Exercise -> stringResource(if (line.missedByWatch) Res.string.today_activity_exercise_missed else Res.string.settings_energy_logged)
        is ActivityLine.Digesting -> stringResource(Res.string.energy_digesting_line)
    }
    Text(text = detail, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
}

@Composable
private fun Entries(card: TodayActivityCard, onAction: (TodayActivityAction) -> Unit) {
    // With a health app an empty list is the ordinary day: the watch's workouts are in Active energy.
    if (card.entries.isEmpty()) {
        if (card.healthSource == null) {
            Text(text = stringResource(Res.string.today_burned_none), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
        return
    }
    val counter = card.healthSource?.let { stringResource(it.title) }
    Column {
        card.entries.forEachIndexed { index, row ->
            val label = row.name ?: stringResource(exerciseActivityLabel(row.activity) ?: checkNotNull(exerciseActivityLabel(ExerciseActivities.OTHER)))
            val subtitle = buildList {
                add(stringResource(Res.string.exercise_minutes_chip, row.minutes))
                add(DateCopy.time(row.startedAt.hour, row.startedAt.minute))
                if (row.watchHasIt && counter != null) add(stringResource(Res.string.today_activity_watch_has_it, counter))
            }.joinToString(" · ")
            ListRow(
                title = label,
                subtitle = subtitle,
                onClick = { onAction(TodayActivityAction.EntryTapped(row.id)) },
                showDivider = index < card.entries.lastIndex,
            ) {
                NutrientText(Metric.EnergyOut, row.amount, typography = AppTheme.type.bodyBold.Tabular, color = AppTheme.colors.ink, maxLines = 1)
            }
        }
    }
}

/**
 * The health app's workouts, each with the app that recorded it: "Morning run", "Garmin Connect ·
 * 7:10 · 35 min", 320 kcal. Not tappable: they live in the health app, and their energy is
 * already in Active energy above.
 */
@Composable
private fun Workouts(workouts: List<WorkoutRow>) {
    if (workouts.isEmpty()) return
    Column {
        workouts.forEachIndexed { index, row ->
            val subtitle = listOfNotNull(
                row.source,
                DateCopy.time(row.startedAt.hour, row.startedAt.minute),
                stringResource(Res.string.exercise_minutes_chip, row.minutes).takeIf { row.minutes > 0 },
            ).joinToString(" · ")
            ListRow(title = row.title ?: stringResource(row.kind.label), subtitle = subtitle, showDivider = index < workouts.lastIndex) {
                NutrientText(Metric.EnergyOut, row.amount, typography = AppTheme.type.bodyBold.Tabular, color = AppTheme.colors.ink, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Dot(color: ColorResource) {
    Box(Modifier.size(10.dp).clip(AppTheme.shapes.pill).background(color))
}

private val ActivityLine.title: StringResource
    get() = when (this) {
        is ActivityLine.Resting -> Res.string.today_activity_resting
        is ActivityLine.Active -> Res.string.today_activity_active
        is ActivityLine.Everyday -> Res.string.today_burned_everyday
        is ActivityLine.Exercise -> Res.string.today_burned_exercise
        is ActivityLine.Digesting -> Res.string.energy_digesting
    }

private val WorkoutKind.label: StringResource
    get() = when (this) {
        WorkoutKind.Running -> Res.string.today_workout_running
        WorkoutKind.Walking -> Res.string.today_workout_walking
        WorkoutKind.Hiking -> Res.string.today_workout_hiking
        WorkoutKind.Cycling -> Res.string.today_workout_cycling
        WorkoutKind.Swimming -> Res.string.today_workout_swimming
        WorkoutKind.Strength -> Res.string.today_workout_strength
        WorkoutKind.Hiit -> Res.string.today_workout_hiit
        WorkoutKind.Yoga -> Res.string.today_workout_yoga
        WorkoutKind.Pilates -> Res.string.today_workout_pilates
        WorkoutKind.Rowing -> Res.string.today_workout_rowing
        WorkoutKind.Elliptical -> Res.string.today_workout_elliptical
        WorkoutKind.Stairs -> Res.string.today_workout_stairs
        WorkoutKind.Dance -> Res.string.today_workout_dance
        WorkoutKind.MartialArts -> Res.string.today_workout_martial_arts
        WorkoutKind.Sports -> Res.string.today_workout_sports
        WorkoutKind.SnowSports -> Res.string.today_workout_snow
        WorkoutKind.WaterSports -> Res.string.today_workout_water
        WorkoutKind.Other -> Res.string.today_workout_other
    }

internal val HealthSource.title: StringResource
    get() = when (this) {
        HealthSource.AppleHealth -> Res.string.integrations_apple_health
        HealthSource.HealthConnect -> Res.string.integrations_health_connect
    }
