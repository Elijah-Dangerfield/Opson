package com.opson.features.trends.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.ui.label
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.diary.ui.tileColors
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.ui.components.ColorDot
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.EdgeCardDefaults
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.PageHeader
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.ShareBar
import com.opson.libraries.ui.components.TimelineAxis
import com.opson.libraries.ui.components.TimelineTrack
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import com.opson.system.Dimension
import com.opson.system.background
import kotlinx.datetime.DayOfWeek
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.trends_back
import opson.libraries.resources.generated.resources.trends_patterns_and
import opson.libraries.resources.generated.resources.trends_patterns_axis_12p
import opson.libraries.resources.generated.resources.trends_patterns_axis_3p
import opson.libraries.resources.generated.resources.trends_patterns_axis_6a
import opson.libraries.resources.generated.resources.trends_patterns_axis_6p
import opson.libraries.resources.generated.resources.trends_patterns_axis_9a
import opson.libraries.resources.generated.resources.trends_patterns_axis_9p
import opson.libraries.resources.generated.resources.trends_patterns_gap
import opson.libraries.resources.generated.resources.trends_patterns_gap_value
import opson.libraries.resources.generated.resources.trends_patterns_hours
import opson.libraries.resources.generated.resources.trends_patterns_hours_minutes
import opson.libraries.resources.generated.resources.trends_patterns_hunger
import opson.libraries.resources.generated.resources.trends_patterns_hunger_from
import opson.libraries.resources.generated.resources.trends_patterns_hunger_skipped
import opson.libraries.resources.generated.resources.trends_patterns_later
import opson.libraries.resources.generated.resources.trends_patterns_later_on
import opson.libraries.resources.generated.resources.trends_patterns_minutes
import opson.libraries.resources.generated.resources.trends_patterns_new_foods
import opson.libraries.resources.generated.resources.trends_patterns_often
import opson.libraries.resources.generated.resources.trends_patterns_often_value
import opson.libraries.resources.generated.resources.trends_patterns_size_line
import opson.libraries.resources.generated.resources.trends_patterns_sizes
import opson.libraries.resources.generated.resources.trends_patterns_span
import opson.libraries.resources.generated.resources.trends_patterns_title
import opson.libraries.resources.generated.resources.trends_patterns_usually
import opson.libraries.resources.generated.resources.trends_patterns_when
import opson.libraries.resources.generated.resources.trends_title
import org.jetbrains.compose.resources.stringResource

/** Patterns. Stateless; facts arrive worked out, amounts arrive resolved. */
@Composable
fun PatternsScreen(
    state: PatternsState,
    onAction: (PatternsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { padding ->
        PatternsContent(state, onAction, Modifier.padding(padding).verticalScroll(rememberScrollState()))
    }
}

/** The page without its scaffold, for previews and the leak test. */
@Composable
internal fun PatternsContent(
    state: PatternsState,
    onAction: (PatternsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.section),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        PageHeader(
            title = stringResource(Res.string.trends_patterns_title),
            subtitle = stringResource(Res.string.trends_patterns_span),
            backLabel = stringResource(Res.string.trends_back, stringResource(Res.string.trends_title)),
            onBack = { onAction(PatternsAction.BackTapped) },
        )
        state.timing?.let { TimingCardView(it) }
        state.sizes?.let { SizesCard(it) }
        state.often?.let { OftenCard(it) }
        if (!state.hungerLast) state.hunger?.let { HungerCardView(it) }
        if (state.newFoods.isNotEmpty()) NewFoodsCard(state.newFoods)
        if (state.hungerLast) state.hunger?.let { HungerCardView(it) }
    }
}

@Composable
private fun NewFoodsCard(foods: List<String>) {
    PatternsCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CardTitle(stringResource(Res.string.trends_patterns_new_foods), Modifier.weight(1f))
            Text(text = foods.size.toString(), typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
        }
        FoodChips(foods)
    }
}

@Composable
private fun PatternsCard(content: @Composable ColumnScope.() -> Unit) {
    EdgeCard(
        edgeToEdge = false,
        contentPadding = EdgeCardDefaults.InsetPadding,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        content = content,
    )
}

@Composable
private fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(text = text, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink, modifier = modifier)
}

@Composable
private fun TimingCardView(card: TimingCard) {
    PatternsCard {
        CardTitle(stringResource(Res.string.trends_patterns_when))
        card.rows.forEach { row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                SlotLabel(row.slot)
                TimelineTrack(dots = row.dots, window = row.usual, color = row.slot.tileColors().content, modifier = Modifier.weight(1f))
            }
        }
        Row {
            Spacer(Modifier.width(SLOT_LABEL_WIDTH))
            TimelineAxis(AXIS.map { stringResource(it) }, Modifier.weight(1f))
        }
        Column {
            card.facts.forEach { fact -> TimingFactRow(fact) }
        }
    }
}

@Composable
private fun TimingFactRow(fact: TimingFact) {
    HorizontalDivider(color = AppTheme.colors.line)
    when (fact) {
        is TimingFact.Usually -> FactRow(
            stringResource(Res.string.trends_patterns_usually, stringResource(fact.slot.label)),
            DateCopy.time(fact.time.hour, fact.time.minute),
        )
        is TimingFact.LaterOn -> FactRow(
            stringResource(Res.string.trends_patterns_later_on, stringResource(fact.slot.label), weekdays(fact.days)),
            stringResource(Res.string.trends_patterns_later, duration(fact.minutes)),
        )
        is TimingFact.LongestGap -> FactRow(
            stringResource(Res.string.trends_patterns_gap),
            stringResource(
                Res.string.trends_patterns_gap_value,
                stringResource(fact.from.label),
                stringResource(fact.to.sentenceName),
                duration(fact.minutes),
            ),
        )
    }
}

@Composable
private fun SlotLabel(slot: MealSlot) {
    Text(
        text = stringResource(slot.label),
        typography = AppTheme.type.secondaryStrong.Bold,
        color = AppTheme.colors.ink,
        maxLines = 1,
        modifier = Modifier.width(SLOT_LABEL_WIDTH),
    )
}

/** "Fri and Sat", "Thu, Fri and Sat". */
@Composable
private fun weekdays(days: List<DayOfWeek>): String {
    val names = days.map { DateCopy.shortWeekday(it) }
    if (names.size < 2) return names.joinToString()
    return stringResource(Res.string.trends_patterns_and, names.dropLast(1).joinToString(", "), names.last())
}

/** "45 min", "1 h", "5 h 30 min", to the nearest quarter hour. */
@Composable
private fun duration(minutes: Int): String {
    val rounded = ((minutes + QUARTER_HOUR / 2) / QUARTER_HOUR) * QUARTER_HOUR
    val hours = rounded / MINUTES_PER_HOUR
    val rest = rounded % MINUTES_PER_HOUR
    return when {
        hours == 0 -> stringResource(Res.string.trends_patterns_minutes, rest)
        rest == 0 -> stringResource(Res.string.trends_patterns_hours, hours)
        else -> stringResource(Res.string.trends_patterns_hours_minutes, hours, rest)
    }
}

@Composable
private fun SizesCard(sizes: List<MealSize>) {
    PatternsCard {
        CardTitle(stringResource(Res.string.trends_patterns_sizes))
        ShareBar(sizes.map { it.fraction to it.slot.tileColors().content })
        sizes.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(top = Dimension.D200), horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
                pair.forEach { size ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimension.D100)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimension.D300)) {
                            ColorDot(size.slot.tileColors().content, size = Dimension.D400)
                            Text(text = stringResource(size.slot.label), typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D100)) {
                            NutrientText(Metric.Calories, size.energy, typography = AppTheme.type.body.Tabular, color = AppTheme.colors.ink)
                            Text(text = stringResource(Res.string.trends_patterns_size_line), typography = AppTheme.type.body, color = AppTheme.colors.ink)
                            NutrientText(Metric.Calories, size.share, typography = AppTheme.type.body.Tabular, color = AppTheme.colors.ink)
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OftenCard(rows: List<MealFrequency>) {
    PatternsCard {
        CardTitle(stringResource(Res.string.trends_patterns_often))
        Column {
            rows.forEach { row ->
                HorizontalDivider(color = AppTheme.colors.line)
                FactRow(stringResource(row.slot.label), stringResource(Res.string.trends_patterns_often_value, row.days, row.of))
            }
        }
    }
}

/** A bar per weekday, as tall as that day's tagged meals were hungry. No amounts, so it stays in Blind. */
@Composable
private fun HungerCardView(card: HungerCard) {
    PatternsCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            CardTitle(stringResource(Res.string.trends_patterns_hunger), Modifier.weight(1f))
            Text(
                text = stringResource(Res.string.trends_patterns_hunger_from),
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.muted,
                modifier = Modifier.weight(HUNGER_NOTE_WEIGHT),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(HUNGER_CHART).padding(top = Dimension.D200),
            horizontalArrangement = Arrangement.spacedBy(Dimension.D300),
            verticalAlignment = Alignment.Bottom,
        ) {
            card.days.forEach { day ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight((day.level ?: 0f).coerceIn(HUNGER_FLOOR, 1f))
                        .clip(AppTheme.shapes.inputLarge)
                        .background(if (day.level == null) AppTheme.colors.track else AppTheme.colors.accentTint),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D300)) {
            card.days.forEach { day ->
                Text(
                    text = DateCopy.shortWeekday(day.day),
                    typography = AppTheme.type.secondary,
                    color = AppTheme.colors.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        card.skippedBreakfast?.let { days ->
            Text(
                text = stringResource(Res.string.trends_patterns_hunger_skipped, days.joinToString(", ") { DateCopy.shortWeekday(it) }),
                typography = AppTheme.type.body,
                color = AppTheme.colors.ink,
            )
        }
    }
}

/** 6 am to 9 pm in three-hour steps, as the track spans. */
private val AXIS = listOf(
    Res.string.trends_patterns_axis_6a,
    Res.string.trends_patterns_axis_9a,
    Res.string.trends_patterns_axis_12p,
    Res.string.trends_patterns_axis_3p,
    Res.string.trends_patterns_axis_6p,
    Res.string.trends_patterns_axis_9p,
)

private val SLOT_LABEL_WIDTH = Dimension.D1600
private val HUNGER_CHART = 96.dp
private const val HUNGER_FLOOR = 0.08f
private const val HUNGER_NOTE_WEIGHT = 0.6f
private const val QUARTER_HOUR = 15
private const val MINUTES_PER_HOUR = 60
