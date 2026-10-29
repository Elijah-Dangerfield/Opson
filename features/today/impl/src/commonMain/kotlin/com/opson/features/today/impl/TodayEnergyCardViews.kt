package com.opson.features.today.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.SummaryCardPreset
import com.opson.libraries.diary.ui.SummaryLabel
import com.opson.libraries.diary.ui.SummaryLink
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ui.NutrientPhrase
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.nutrition.ui.NutrientVisible
import com.opson.libraries.ui.components.FlipContent
import com.opson.libraries.ui.components.FlipControl
import com.opson.libraries.ui.components.FlipIcon
import com.opson.libraries.ui.components.flipToggle
import com.opson.libraries.ui.components.RangeBar
import com.opson.libraries.ui.components.RangeBarDefaults
import com.opson.libraries.ui.components.RangeBarSegment
import com.opson.libraries.ui.components.RingSegment
import com.opson.libraries.ui.components.ProgressRing
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.MinTouchTarget
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_card_active
import opson.libraries.resources.generated.resources.today_card_burned
import opson.libraries.resources.generated.resources.today_card_deficit
import opson.libraries.resources.generated.resources.today_card_eaten
import opson.libraries.resources.generated.resources.today_card_energy
import opson.libraries.resources.generated.resources.today_card_goal_deficit
import opson.libraries.resources.generated.resources.today_card_goal_surplus
import opson.libraries.resources.generated.resources.today_card_kcal
import opson.libraries.resources.generated.resources.today_card_left
import opson.libraries.resources.generated.resources.today_card_left_line
import opson.libraries.resources.generated.resources.today_card_legend_range
import opson.libraries.resources.generated.resources.today_card_legend_target
import opson.libraries.resources.generated.resources.today_card_meals
import opson.libraries.resources.generated.resources.today_card_range
import opson.libraries.resources.generated.resources.today_card_resting
import opson.libraries.resources.generated.resources.today_card_ring_left
import opson.libraries.resources.generated.resources.today_card_ring_to_range
import opson.libraries.resources.generated.resources.today_card_ring_to_surplus
import opson.libraries.resources.generated.resources.today_card_surplus
import opson.libraries.resources.generated.resources.today_card_target
import opson.libraries.resources.generated.resources.today_card_target_follows
import opson.libraries.resources.generated.resources.today_card_target_left
import opson.libraries.resources.generated.resources.today_card_target_moves
import opson.libraries.resources.generated.resources.today_card_to_range
import opson.libraries.resources.generated.resources.today_card_to_range_line
import opson.libraries.resources.generated.resources.today_card_to_surplus
import opson.libraries.resources.generated.resources.today_card_to_surplus_line
import opson.libraries.resources.generated.resources.today_flip_show_deficit
import opson.libraries.resources.generated.resources.today_flip_show_eaten
import opson.libraries.resources.generated.resources.today_flip_show_left
import opson.libraries.resources.generated.resources.today_flip_show_surplus
import opson.libraries.resources.generated.resources.today_flip_show_to_range
import opson.libraries.resources.generated.resources.today_flip_show_to_surplus
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * Design sync 4's summary card, page 1 (13.1, 13.2a, 13.2b, 13.6c), in each of its presets.
 * Stateless: the card arrives built in [EnergyCard]; a tap on the main number and "Energy ›" go
 * back as [TodayAction]s.
 */

/** [macros] is the macro row, drawn under the eaten bar (or the rings, or the sum). */
@Composable
internal fun EnergyCardContent(card: EnergyCard, onAction: (TodayAction) -> Unit, macros: @Composable () -> Unit = {}) {
    when (card.preset) {
        SummaryCardPreset.InOut -> InOut(card, onAction, macros)
        SummaryCardPreset.RangeBar -> RangeBarCard(card, onAction, macros)
        SummaryCardPreset.Rings -> Rings(card, onAction, macros)
        SummaryCardPreset.Equation -> Equation(card, onAction, macros)
    }
}

/** 13.1 1d: the main number and the goal, then Eaten and Burned, each on its own bar. */
@Composable
private fun InOut(card: EnergyCard, onAction: (TodayAction) -> Unit, macros: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        Row(verticalAlignment = Alignment.Bottom) {
            MainFigure(card, onAction, Modifier.weight(1f)) { main ->
                MainLabel(main.kind, switchable = card.next != null)
                BigNumber(main.metric, main.display)
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 8.dp)) {
                SummaryLink(text = stringResource(Res.string.today_card_energy), onClick = { onAction(TodayAction.EnergyTapped) })
                GoalText(card.goal)
            }
        }
        FigureRow(stringResource(Res.string.today_card_eaten), Metric.Calories, card.eaten)
        EatenBar(card)
        macros()
        card.burned?.let { burned ->
            FigureRow(stringResource(Res.string.today_card_burned), Metric.EnergyOut, burned.total)
            BurnedBar(burned)
            if (burned.breakdown) Breakdown(burned)
        }
        card.target?.let { TargetText(it) }
    }
}

/** 13.1 1c: the main number over one bar, Burned as a tick on it, and what each mark is. */
@Composable
private fun RangeBarCard(card: EnergyCard, onAction: (TodayAction) -> Unit, macros: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MainFigure(card, onAction, Modifier.weight(1f)) { main ->
                MainLabel(main.kind, switchable = card.next != null)
                BigNumber(main.metric, main.display)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PairRow(stringResource(Res.string.today_card_eaten), Metric.Calories, card.eaten)
                card.burned?.let { PairRow(stringResource(Res.string.today_card_burned), Metric.EnergyOut, it.total) }
            }
        }
        EatenBar(card, tick = card.burned?.tick)
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row), verticalAlignment = Alignment.CenterVertically) {
            Legend(AppTheme.colors.nutrients.protein.content, stringResource(Res.string.today_card_eaten))
            if (card.burned != null) Legend(AppTheme.colors.activeEnergy, stringResource(Res.string.today_card_burned), line = true)
            Legend(
                AppTheme.colors.inkSoft,
                stringResource(if (card.rangeStart == card.rangeEnd) Res.string.today_card_legend_target else Res.string.today_card_legend_range),
                box = true,
            )
        }
        macros()
        SummaryLink(text = stringResource(Res.string.today_card_energy), onClick = { onAction(TodayAction.EnergyTapped) })
    }
}

/** 13.6c: Eaten, Burned and the main number as rings. Only with a single target. */
@Composable
private fun Rings(card: EnergyCard, onAction: (TodayAction) -> Unit, macros: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SummaryLink(text = stringResource(Res.string.today_card_energy), onClick = { onAction(TodayAction.EnergyTapped) })
            Box(Modifier.weight(1f))
            GoalText(card.goal)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            val eatenSegments = card.eatenParts.let { parts ->
                val total = parts.sumOf { it.weight.toDouble() }.toFloat().takeIf { it > 0f } ?: 1f
                parts.map { part ->
                    RingSegment(card.eatenFraction * part.weight / total, part.metric?.let { macroColor(it) } ?: AppTheme.colors.inkSoft)
                }
            }
            RingColumn(eatenSegments, Metric.Calories, card.eaten, stringResource(Res.string.today_card_eaten))
            card.burned?.let { burned ->
                val total = (burned.restingFraction + burned.activeFraction).takeIf { it > 0f } ?: 1f
                RingColumn(
                    listOf(
                        RingSegment(burned.activeFraction / total, AppTheme.colors.activeEnergy),
                        RingSegment(burned.restingFraction / total, AppTheme.colors.restingEnergy),
                    ),
                    Metric.EnergyOut,
                    burned.total,
                    stringResource(Res.string.today_card_burned),
                )
            }
            Column(
                modifier = Modifier.flipToggle(card.flip(onAction)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProgressRing(listOf(RingSegment(card.mainFraction, AppTheme.colors.inkSoft)), size = RING, trackColor = AppTheme.colors.track) {
                    FlipContent(card.main) { RingFigure(it.metric, it.display) }
                }
                FlipContent(card.main.kind) { MainLabel(it, switchable = card.next != null, short = true) }
            }
        }
        macros()
    }
}

/** 13.1 1b: Eaten − Burned = the main number, the bar, and the gap to the goal in words. */
@Composable
private fun Equation(card: EnergyCard, onAction: (TodayAction) -> Unit, macros: @Composable () -> Unit) {
    val burned = card.burned ?: return InOut(card, onAction, macros)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SummaryLink(text = stringResource(Res.string.today_card_energy), onClick = { onAction(TodayAction.EnergyTapped) })
            Box(Modifier.weight(1f))
            GoalText(card.goal)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Term(stringResource(Res.string.today_card_eaten), Metric.Calories, card.eaten)
            Operator("−")
            Column {
                Term(stringResource(Res.string.today_card_burned), Metric.EnergyOut, burned.total)
                Row {
                    NutrientText(Metric.EnergyOut, burned.resting, typography = AppTheme.type.secondarySmall.Tabular, color = AppTheme.colors.muted)
                    NutrientVisible(burned.active) {
                        Text(" + ", typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted)
                        NutrientText(Metric.EnergyOut, burned.active, typography = AppTheme.type.secondarySmall.Tabular, color = AppTheme.colors.muted)
                    }
                }
            }
            Operator("=")
            MainFigure(card, onAction) { main ->
                MainLabel(main.kind, switchable = card.next != null)
                NutrientText(main.metric, main.display, typography = AppTheme.type.figureLarge, color = AppTheme.colors.ink, maxLines = 1)
            }
        }
        EatenBar(card)
        NutrientPhrase(
            metric = card.toGoal.metric,
            display = card.toGoal.display,
            phrase = { stringResource(card.toGoalPhrase(), it) },
            typography = AppTheme.type.secondaryStrong,
            color = AppTheme.colors.inkSoft,
        )
        macros()
    }
}

private fun EnergyCard.toGoalPhrase(): StringResource = when (toGoal.kind) {
    MainKind.ToSurplus -> Res.string.today_card_to_surplus_line
    MainKind.LeftToTarget -> Res.string.today_card_left_line
    else -> Res.string.today_card_to_range_line
}

/**
 * The main number and its label as one button that flips it (owner feedback, 2026-10-29), the two
 * cross-fading to the next reading. [content] draws the reading it is handed, so the outgoing one
 * keeps its own words while it fades.
 */
@Composable
private fun MainFigure(card: EnergyCard, onAction: (TodayAction) -> Unit, modifier: Modifier = Modifier, content: @Composable (MainNumber) -> Unit) {
    Column(modifier.flipToggle(card.flip(onAction))) {
        FlipContent(card.main) { main -> Column { content(main) } }
    }
}

/** What a tap on the main number does, and what a screen reader offers; null when it reads one way. */
@Composable
private fun EnergyCard.flip(onAction: (TodayAction) -> Unit): FlipControl? =
    nextKind?.let { kind -> FlipControl(stringResource(kind.showLabel)) { onAction(TodayAction.MainNumberToggled) } }

/** The accent label over the main number, with ⇄ when a tap flips it; muted when it can't. */
@Composable
private fun MainLabel(kind: MainKind, switchable: Boolean, short: Boolean = false) {
    Row(
        modifier = Modifier.defaultMinSize(minHeight = if (switchable) MinTouchTarget else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(if (short) kind.shortLabel else kind.label),
            typography = AppTheme.type.bodyBold.ExtraBold,
            color = if (switchable) AppTheme.colors.accent else AppTheme.colors.muted,
        )
        if (switchable) FlipIcon(size = 22.dp)
    }
}

private val MainKind.showLabel: StringResource
    get() = when (this) {
        MainKind.ToRange -> Res.string.today_flip_show_to_range
        MainKind.LeftToTarget -> Res.string.today_flip_show_left
        MainKind.ToSurplus -> Res.string.today_flip_show_to_surplus
        MainKind.Deficit -> Res.string.today_flip_show_deficit
        MainKind.Surplus -> Res.string.today_flip_show_surplus
        MainKind.Eaten -> Res.string.today_flip_show_eaten
    }

private val MainKind.label: StringResource
    get() = when (this) {
        MainKind.ToRange -> Res.string.today_card_to_range
        MainKind.LeftToTarget -> Res.string.today_card_left
        MainKind.ToSurplus -> Res.string.today_card_to_surplus
        MainKind.Deficit -> Res.string.today_card_deficit
        MainKind.Surplus -> Res.string.today_card_surplus
        MainKind.Eaten -> Res.string.today_card_eaten
    }

private val MainKind.shortLabel: StringResource
    get() = when (this) {
        MainKind.ToRange -> Res.string.today_card_ring_to_range
        MainKind.LeftToTarget -> Res.string.today_card_ring_left
        MainKind.ToSurplus -> Res.string.today_card_ring_to_surplus
        else -> label
    }

@Composable
private fun BigNumber(metric: Metric, display: Display) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NutrientText(metric, display, typography = AppTheme.type.figureDisplay, color = AppTheme.colors.ink, maxLines = 1)
        if (display is Display.Shown && metric != Metric.Remaining) {
            Text(text = stringResource(Res.string.today_card_kcal), typography = AppTheme.type.cardTitle, color = AppTheme.colors.inkSoft, modifier = Modifier.padding(bottom = 6.dp))
        }
    }
}

@Composable
private fun GoalText(goal: GoalLine) {
    val (display, phrase) = when (goal) {
        is GoalLine.Range -> goal.range to Res.string.today_card_range
        is GoalLine.Target -> goal.kcal to Res.string.today_card_target
        is GoalLine.DeficitGoal -> goal.kcal to Res.string.today_card_goal_deficit
        is GoalLine.SurplusGoal -> goal.kcal to Res.string.today_card_goal_surplus
    }
    NutrientPhrase(
        metric = Metric.Calories,
        display = display,
        phrase = { stringResource(phrase, it) },
        typography = AppTheme.type.secondaryStrong.Tabular,
        color = AppTheme.colors.muted,
    )
}

@Composable
private fun FigureRow(label: String, metric: Metric, display: Display) {
    NutrientVisible(display) {
        Row(verticalAlignment = Alignment.Bottom) {
            SummaryLabel(label, modifier = Modifier.weight(1f))
            NutrientText(metric, display, typography = AppTheme.type.cardTitle.Tabular, color = AppTheme.colors.ink)
            Text(text = " " + stringResource(Res.string.today_card_kcal), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
    }
}

@Composable
private fun PairRow(label: String, metric: Metric, display: Display) {
    NutrientVisible(display) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SummaryLabel(label, modifier = Modifier.width(72.dp))
            NutrientText(metric, display, typography = AppTheme.type.cardTitle.Tabular, color = AppTheme.colors.ink)
        }
    }
}

@Composable
private fun EatenBar(card: EnergyCard, tick: Float? = null) {
    NutrientVisible(card.eaten) {
        RangeBar(
            fill = (card.eaten as? Display.Shown)?.fraction ?: 0f,
            rangeStart = card.rangeStart,
            rangeEnd = card.rangeEnd,
            colors = RangeBarDefaults.summary(),
            segments = card.eatenParts.map { RangeBarSegment(it.weight, it.metric?.let { m -> macroColor(m) } ?: AppTheme.colors.inkSoft) },
            tick = tick,
            tickColor = AppTheme.colors.activeEnergy,
        )
    }
}

/** Resting and active end to end, on the eaten bar's scale. */
@Composable
private fun BurnedBar(burned: BurnedBars) {
    val track = AppTheme.colors.track.color
    val resting = AppTheme.colors.restingEnergy.color
    val active = AppTheme.colors.activeEnergy.color
    Box(
        Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .drawBehind {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(track, size = size, cornerRadius = radius)
                val restingWidth = size.width * burned.restingFraction
                if (restingWidth > 0f) drawRoundRect(resting, size = Size(restingWidth, size.height), cornerRadius = radius)
                val activeWidth = size.width * burned.activeFraction
                if (activeWidth > GAP) {
                    drawRoundRect(active, topLeft = Offset(restingWidth + GAP, 0f), size = Size(activeWidth - GAP, size.height), cornerRadius = radius)
                }
            },
    )
}

@Composable
private fun Breakdown(burned: BurnedBars) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Legend(AppTheme.colors.restingEnergy, null) {
            NutrientPhrase(Metric.EnergyOut, burned.resting, phrase = { stringResource(Res.string.today_card_resting, it) }, typography = AppTheme.type.secondary.Tabular, color = AppTheme.colors.inkSoft)
        }
        Legend(AppTheme.colors.activeEnergy, null) {
            NutrientPhrase(Metric.EnergyOut, burned.active, phrase = { stringResource(Res.string.today_card_active, it) }, typography = AppTheme.type.secondary.Tabular, color = AppTheme.colors.inkSoft)
        }
    }
}

/** A dot, a short line or a dashed box, then what it stands for. */
@Composable
private fun Legend(color: ColorResource, text: String?, line: Boolean = false, box: Boolean = false, content: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val paint = color.color
        val dash = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH))
        Box(
            Modifier
                .size(width = if (line) 4.dp else 12.dp, height = if (line) 16.dp else 12.dp)
                .drawBehind {
                    when {
                        box -> drawRoundRect(paint, cornerRadius = CornerRadius(3f), style = Stroke(width = 2f, pathEffect = dash))
                        line -> drawRoundRect(paint, cornerRadius = CornerRadius(size.width / 2))
                        else -> drawCircle(paint)
                    }
                },
        )
        if (text != null) Text(text = text, typography = AppTheme.type.secondary, color = AppTheme.colors.inkSoft)
        content()
    }
}

/** "Target 1,680 · 435 kcal left at your goal", after a short dashed mark like the bar's. */
@Composable
private fun TargetText(target: TargetLine) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(2.dp).height(18.dp).background(AppTheme.colors.muted))
        when {
            target.surplus -> NutrientPhrase(Metric.Calories, target.target, phrase = { stringResource(Res.string.today_card_target_follows, it) }, typography = AppTheme.type.bodyBold.Tabular, color = AppTheme.colors.ink)
            target.left != null -> Row {
                NutrientPhrase(Metric.Calories, target.target, phrase = { stringResource(Res.string.today_card_target, it) }, typography = AppTheme.type.bodyBold.Tabular, color = AppTheme.colors.ink)
                NutrientPhrase(Metric.Calories, target.left, phrase = { stringResource(Res.string.today_card_target_left, it) }, typography = AppTheme.type.bodyBold.Tabular, color = AppTheme.colors.ink)
            }
            else -> NutrientPhrase(Metric.Calories, target.target, phrase = { stringResource(Res.string.today_card_target_moves, it) }, typography = AppTheme.type.bodyBold.Tabular, color = AppTheme.colors.ink)
        }
    }
}

@Composable
private fun Term(label: String, metric: Metric, display: Display) {
    Column {
        SummaryLabel(label)
        NutrientText(metric, display, typography = AppTheme.type.figureLarge, color = AppTheme.colors.ink, maxLines = 1)
    }
}

@Composable
private fun Operator(symbol: String) {
    Text(text = symbol, typography = AppTheme.type.figureLarge, color = AppTheme.colors.muted, modifier = Modifier.padding(bottom = 2.dp))
}

@Composable
private fun RingColumn(segments: List<RingSegment>, metric: Metric, display: Display, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProgressRing(segments, size = RING, trackColor = AppTheme.colors.track) { RingFigure(metric, display) }
        Box(Modifier.defaultMinSize(minHeight = MinTouchTarget), contentAlignment = Alignment.Center) {
            Text(text = label, typography = AppTheme.type.bodyBold.ExtraBold, color = AppTheme.colors.inkSoft)
        }
    }
}

@Composable
private fun RingFigure(metric: Metric, display: Display) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        NutrientText(metric, display, typography = AppTheme.type.cardTitle.Tabular, color = AppTheme.colors.ink, maxLines = 1)
        if (display is Display.Shown && metric != Metric.Remaining) {
            Text(text = stringResource(Res.string.today_card_kcal), typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted)
        }
    }
}

/** "3 of 4 meals", with More › Today screen's Meals count on. */
@Composable
internal fun CardMeals(card: EnergyCard) {
    card.meals?.let { meals ->
        Text(text = stringResource(Res.string.today_card_meals, meals.done, meals.total), typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.inkSoft)
    }
}

@Composable
private fun macroColor(metric: Metric): ColorResource {
    val n = AppTheme.colors.nutrients
    return when (metric) {
        Metric.Protein -> n.protein
        Metric.Carbs -> n.carbs
        Metric.Fat -> n.fat
        else -> n.fiber
    }.content
}

private val RING = 96.dp
private val BAR_HEIGHT = 13.dp
private const val GAP = 4f
private const val DASH = 4f
