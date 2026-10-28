package com.opson.features.today.impl

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.ui.SummaryCard
import com.opson.libraries.diary.ui.SummaryFooter
import com.opson.libraries.diary.ui.SummaryMacro
import com.opson.libraries.diary.ui.TodayCard
import com.opson.libraries.diary.ui.icon
import com.opson.libraries.diary.ui.label
import com.opson.libraries.diary.ui.tileColors
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ui.NutrientBar
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.nutrition.ui.NutrientVisible
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.Banner
import com.opson.libraries.ui.components.StripedBar
import com.opson.libraries.ui.components.PillAction
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.MinTouchTarget
import com.opson.system.AppTheme
import com.opson.system.background
import kotlinx.datetime.LocalTime
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.modes_foods
import opson.libraries.resources.generated.resources.modes_locked_counts
import opson.libraries.resources.generated.resources.modes_locked_title
import opson.libraries.resources.generated.resources.modes_preset_full
import opson.libraries.resources.generated.resources.modes_preset_no_numbers
import opson.libraries.resources.generated.resources.modes_preset_macros_grams
import opson.libraries.resources.generated.resources.modes_preset_macros_only
import opson.libraries.resources.generated.resources.modes_soft_label
import opson.libraries.resources.generated.resources.modes_timeline_done_around_cd
import opson.libraries.resources.generated.resources.modes_timeline_done_at_cd
import opson.libraries.resources.generated.resources.modes_timeline_open_cd
import opson.libraries.resources.generated.resources.modes_timeline_title
import opson.libraries.resources.generated.resources.modes_timeline_usual
import opson.libraries.resources.generated.resources.modes_timer_ended
import opson.libraries.resources.generated.resources.modes_timer_switch
import opson.libraries.resources.generated.resources.modes_timer_waiting
import opson.libraries.resources.generated.resources.today_collapse_cd
import opson.libraries.resources.generated.resources.today_compact_meals
import opson.libraries.resources.generated.resources.today_counts_meals_one
import opson.libraries.resources.generated.resources.today_counts_meals_other
import opson.libraries.resources.generated.resources.today_locked_week_title
import opson.libraries.resources.generated.resources.today_soft_energy
import opson.libraries.resources.generated.resources.today_summary_nutrients
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/*
 * Today's cards for the gentler modes (design sync 4, 13.5b to 13.5d) and the End after a set
 * time banner. The All numbers card is TodaySummaryViews' own.
 */

/**
 * Calories set to Soft (once Nutrients first, 13.5b): "Today" and the meal count, each visible nutrient as its word over a
 * bar in its own colour, then energy as a word over a neutral bar. Words and bars, no amounts.
 */
@Composable
internal fun NutrientsFirstCard(summary: TodaySummary.Soft, state: TodayState, onAction: (TodayAction) -> Unit) {
    SummaryCard {
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.modes_soft_label),
                    typography = AppTheme.type.secondaryStrong,
                    color = AppTheme.colors.muted,
                    modifier = Modifier.weight(1f),
                )
                summary.mealCount?.let {
                    Text(
                        text = stringResource(Res.string.today_compact_meals, it.done, it.total),
                        typography = AppTheme.type.secondaryStrong.Bold.Tabular,
                        color = AppTheme.colors.muted,
                    )
                }
            }
            summary.rows.forEach { row -> WordBar(row.toSummaryMacro(), dot = true) }
            WordBar(
                SummaryMacro(Metric.Calories, stringResource(Res.string.today_soft_energy), summary.energy, AppTheme.colors.inkSoft),
                dot = false,
            )
        }
        SummaryFooter(
            nutrientsLabel = stringResource(Res.string.today_summary_nutrients),
            onNutrients = { onAction(TodayAction.NutrientsTapped) },
            collapseIcon = if (state.summaryTools) AppIcons.ExpandLess.described(stringResource(Res.string.today_collapse_cd)) else null,
            onCollapse = { onAction(TodayAction.CollapseTapped) },
            modifier = Modifier.padding(top = AppTheme.spacing.row),
        )
    }
}

/** A name, its word (or its amount, where the person shows it) on the right, and a bar under both. */
@Composable
private fun WordBar(macro: SummaryMacro, dot: Boolean) {
    NutrientVisible(macro.display) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (dot) Box(Modifier.size(10.dp).background(macro.color, CircleShape))
                Text(text = macro.label, typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
                NutrientText(macro.metric, macro.display, typography = AppTheme.type.secondaryStrong.Bold.Tabular, color = AppTheme.colors.muted, maxLines = 1)
            }
            NutrientBar(
                metric = macro.metric,
                display = macro.display,
                color = macro.color.color,
                trackColor = AppTheme.colors.track.color,
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
        }
    }
}

/**
 * Later before the reveal (13.5c): a lock, when totals open, and how much is logged as counts,
 * over a striped bar that holds the place of the totals. Says when, never what.
 */
@Composable
internal fun LaterLockedCard(summary: TodaySummary.Locked) {
    TodayCard(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(AppTheme.colors.soft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon = AppIcons.LockClock, size = 28.dp, color = AppTheme.colors.inkSoft)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (summary.weekly) {
                        stringResource(Res.string.today_locked_week_title)
                    } else {
                        stringResource(Res.string.modes_locked_title, summary.opensAt.clock())
                    },
                    typography = AppTheme.type.cardTitle,
                    color = AppTheme.colors.ink,
                )
                Text(
                    text = stringResource(
                        Res.string.modes_locked_counts,
                        stringResource(if (summary.meals == 1) Res.string.today_counts_meals_one else Res.string.today_counts_meals_other, summary.meals),
                        pluralStringResource(Res.plurals.modes_foods, summary.foods, summary.foods),
                    ),
                    typography = AppTheme.type.secondary.Tabular,
                    color = AppTheme.colors.muted,
                )
            }
        }
        StripedBar(before = 0f, after = 1f, color = AppTheme.colors.track, trackColor = AppTheme.colors.soft, height = 14.dp)
    }
}

/**
 * No numbers' "Meals today" (13.5d): each meal as a circle, ticked once logged, with when it was
 * (or "~7:00 PM", when it usually is). Tapping one adds to that meal.
 */
@Composable
internal fun MealTimelineCard(tiles: List<MealTile>, onAction: (TodayAction) -> Unit) {
    TodayCard(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
        Text(text = stringResource(Res.string.modes_timeline_title), typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.muted)
        Row(Modifier.fillMaxWidth()) {
            tiles.forEach { tile -> TimelineMeal(tile, onAction, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun TimelineMeal(tile: MealTile, onAction: (TodayAction) -> Unit, modifier: Modifier = Modifier) {
    val slot = tile.slot
    val status = tile.status
    val done = status !is TileStatus.Empty
    val name = stringResource(slot.label)
    val time = when (status) {
        is TileStatus.Logged -> status.time.clock()
        is TileStatus.AteNoDetails -> stringResource(Res.string.modes_timeline_usual, status.around.clock())
        is TileStatus.Empty -> stringResource(Res.string.modes_timeline_usual, status.around.clock())
    }
    val spoken = status.spoken()
    val description = stringResource(spoken.sentence, name, spoken.time.clock())
    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
            }
            .bounceClick(scaleDown = 0.96f) { onAction(TodayAction.MealTileTapped(slot)) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MealCircle(slot, done)
        Text(text = name, typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink, maxLines = 1)
        Text(
            text = time,
            typography = AppTheme.type.secondary.Tabular,
            color = AppTheme.colors.muted,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * What a screen reader says for a timeline circle, which stands in for the texts it merges: the meal
 * and when, as one sentence per status so a translator can reorder it ("Breakfast, done at 8:10 AM",
 * not the "~" the screen shows).
 */
internal data class SpokenMeal(val sentence: StringResource, val time: LocalTime)

internal fun TileStatus.spoken(): SpokenMeal = when (this) {
    is TileStatus.Logged -> SpokenMeal(Res.string.modes_timeline_done_at_cd, time)
    is TileStatus.AteNoDetails -> SpokenMeal(Res.string.modes_timeline_done_around_cd, around)
    is TileStatus.Empty -> SpokenMeal(Res.string.modes_timeline_open_cd, around)
}

/** Done: a check on the meal's own tint. Not yet: the meal's icon in an outline. */
@Composable
private fun MealCircle(slot: MealSlot, done: Boolean) {
    val tone = slot.tileColors()
    val circle = Modifier.size(MinTouchTarget + 8.dp).clip(CircleShape)
    if (done) {
        Box(circle.background(tone.container), contentAlignment = Alignment.Center) {
            Icon(icon = AppIcons.Check, size = 26.dp, color = tone.content)
        }
    } else {
        Box(circle.border(2.dp, AppTheme.colors.line.color, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon = slot.icon, size = 24.dp, color = AppTheme.colors.muted)
        }
    }
}

/**
 * End after a set time ran out (13.5): said once, on the day it switched, or, when the switch
 * would show more with the pause on, offered with a button that goes through the pause.
 */
@Composable
internal fun ModeTimerBanner(banner: TodayBanner, onAction: (TodayAction) -> Unit) {
    val from = stringResource(banner.from.label)
    val to = stringResource(banner.to.label)
    if (!banner.waiting) {
        Banner(text = stringResource(Res.string.modes_timer_ended, from, to))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        Banner(text = stringResource(Res.string.modes_timer_waiting, from, to))
        PillAction(
            label = stringResource(Res.string.modes_timer_switch, to),
            onClick = { onAction(TodayAction.TimedSwitchTapped(banner.to)) },
        )
    }
}

private val PresetId.label: StringResource
    get() = when (this) {
        PresetId.Full -> Res.string.modes_preset_full
        PresetId.MacrosWithGrams -> Res.string.modes_preset_macros_grams
        PresetId.MacrosOnly -> Res.string.modes_preset_macros_only
        PresetId.NoNumbers -> Res.string.modes_preset_no_numbers
    }
