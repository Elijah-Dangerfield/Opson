package com.opson.features.trends.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import com.opson.libraries.diary.Fullness
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Mood
import com.opson.libraries.diary.TrendsRange
import com.opson.libraries.diary.ui.label
import com.opson.libraries.diary.ui.tileColors
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ui.NutrientFillBar
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.nutrition.ui.NutrientVisible
import com.opson.libraries.ui.components.ChevronLink
import com.opson.libraries.ui.components.ColorDot
import com.opson.libraries.ui.components.DelayedLoadingIndicator
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.EdgeCardDefaults
import com.opson.libraries.ui.components.EmptyCard
import com.opson.libraries.ui.components.HeroCard
import com.opson.libraries.ui.components.HeroLink
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.IntakeBarChart
import com.opson.libraries.ui.components.ListRowChevron
import com.opson.libraries.ui.components.LockedHeroCard
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.TimelineAxis
import com.opson.libraries.ui.components.TimelineTrack
import com.opson.libraries.ui.components.TrendLine
import com.opson.libraries.ui.components.chip.PillTag
import com.opson.libraries.ui.components.chip.PillTagSize
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.MinTouchTarget
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import com.opson.system.Dimension
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.trends_avg
import opson.libraries.resources.generated.resources.trends_check_body
import opson.libraries.resources.generated.resources.trends_check_on
import opson.libraries.resources.generated.resources.trends_check_tonight
import opson.libraries.resources.generated.resources.trends_eaten
import opson.libraries.resources.generated.resources.trends_eaten_empty
import opson.libraries.resources.generated.resources.trends_expended
import opson.libraries.resources.generated.resources.trends_expended_sub
import opson.libraries.resources.generated.resources.trends_intake_caption_plain
import opson.libraries.resources.generated.resources.trends_intake_caption_plain_goal
import opson.libraries.resources.generated.resources.trends_intake_caption_today
import opson.libraries.resources.generated.resources.trends_intake_caption_today_goal
import opson.libraries.resources.generated.resources.trends_intake_caption_week
import opson.libraries.resources.generated.resources.trends_intake_caption_week_goal
import opson.libraries.resources.generated.resources.trends_last_check
import opson.libraries.resources.generated.resources.trends_last_check_average
import opson.libraries.resources.generated.resources.trends_last_check_weight
import opson.libraries.resources.generated.resources.trends_loading_cd
import opson.libraries.resources.generated.resources.trends_nutrients
import opson.libraries.resources.generated.resources.trends_nutrients_sub
import opson.libraries.resources.generated.resources.trends_patterns
import opson.libraries.resources.generated.resources.trends_range_3m
import opson.libraries.resources.generated.resources.trends_range_4w
import opson.libraries.resources.generated.resources.trends_range_month
import opson.libraries.resources.generated.resources.trends_range_three_months
import opson.libraries.resources.generated.resources.trends_range_week
import opson.libraries.resources.generated.resources.trends_recap
import opson.libraries.resources.generated.resources.trends_recaps_last_month
import opson.libraries.resources.generated.resources.trends_recaps_last_week
import opson.libraries.resources.generated.resources.trends_recaps_title
import opson.libraries.resources.generated.resources.trends_recap_breakfast
import opson.libraries.resources.generated.resources.trends_recap_breakfast_value
import opson.libraries.resources.generated.resources.trends_recap_days
import opson.libraries.resources.generated.resources.trends_recap_days_value
import opson.libraries.resources.generated.resources.trends_recap_meals
import opson.libraries.resources.generated.resources.trends_recap_new_foods
import opson.libraries.resources.generated.resources.trends_settings_cd
import opson.libraries.resources.generated.resources.trends_title
import opson.libraries.resources.generated.resources.trends_weight
import opson.libraries.resources.generated.resources.trends_weight_none
import opson.libraries.resources.generated.resources.trends_weight_trend
import opson.libraries.resources.generated.resources.trends_your_week
import opson.libraries.resources.generated.resources.trends_fullness_full
import opson.libraries.resources.generated.resources.trends_fullness_satisfied
import opson.libraries.resources.generated.resources.trends_fullness_still_hungry
import opson.libraries.resources.generated.resources.trends_fullness_very_full
import opson.libraries.resources.generated.resources.trends_hunger
import opson.libraries.resources.generated.resources.trends_hunger_by_meal
import opson.libraries.resources.generated.resources.trends_hunger_caption_days
import opson.libraries.resources.generated.resources.trends_hunger_caption_weeks
import opson.libraries.resources.generated.resources.trends_hunger_empty
import opson.libraries.resources.generated.resources.trends_hunger_meal_cd
import opson.libraries.resources.generated.resources.trends_hunger_meals
import opson.libraries.resources.generated.resources.trends_hunger_checkins_sub
import opson.libraries.resources.generated.resources.trends_hunger_sub
import opson.libraries.resources.generated.resources.trends_journal
import opson.libraries.resources.generated.resources.trends_journal_all
import opson.libraries.resources.generated.resources.trends_journal_fullness
import opson.libraries.resources.generated.resources.trends_journal_meals
import opson.libraries.resources.generated.resources.trends_journal_mood_count
import opson.libraries.resources.generated.resources.trends_journal_moods
import opson.libraries.resources.generated.resources.trends_journal_noted
import opson.libraries.resources.generated.resources.trends_mood_anxious
import opson.libraries.resources.generated.resources.trends_mood_calm
import opson.libraries.resources.generated.resources.trends_mood_happy
import opson.libraries.resources.generated.resources.trends_mood_low
import opson.libraries.resources.generated.resources.trends_mood_rushed
import opson.libraries.resources.generated.resources.trends_mood_social
import opson.libraries.resources.generated.resources.trends_mood_stressed
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The Trends tab. Stateless: everything on it comes from [state], already resolved by the policy. */
@Composable
fun TrendsScreen(
    state: TrendsState,
    onAction: (TrendsAction) -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
) {
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { padding ->
        TrendsContent(state, onAction, Modifier.padding(padding).verticalScroll(scrollState))
    }
}

/** The page without its scaffold, so previews and the leak test render it at full height. */
@Composable
internal fun TrendsContent(
    state: TrendsState,
    onAction: (TrendsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AppTheme.spacing.page, vertical = AppTheme.spacing.section),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        Header(state.rangeLabel, onSettings = { onAction(TrendsAction.SettingsTapped) })
        if (state.loading) {
            DelayedLoadingIndicator(contentDescription = stringResource(Res.string.trends_loading_cd))
            return@Column
        }
        state.range?.let { range ->
            SegmentedControl(
                options = TrendsRange.entries,
                selected = range,
                onSelect = { onAction(TrendsAction.RangePicked(it)) },
                label = { stringResource(it.label) },
                minHeight = MinTouchTarget,
            )
        }
        state.weeklyCheck?.let { WeeklyCheckCard(it) }
        state.recaps?.let { RecapLinksCard(it, onAction) }
        state.intake?.let { IntakeCardView(it) }
        state.week?.let { WeekDotsCard(it, onPatterns = { onAction(TrendsAction.PatternsTapped) }) }
        state.calendar?.let { CalendarCardView(it, onAction) }
        state.recap?.let { RecapCardView(it, onPatterns = { onAction(TrendsAction.PatternsTapped) }) }
        state.lastCheck?.let { LastCheckCard(it) }
        state.nutrients?.let { NutrientsCardView(it, onClick = { onAction(TrendsAction.NutrientsCardTapped) }) }
        state.expended?.let { ExpendedCardView(it) }
        // The running total sits on top of the weight card, so the estimate and the scale read together.
        state.runningTotal?.let { RunningTotalCardView(it, onAction) }
        state.weight?.let { WeightCardView(it, onClick = { onAction(TrendsAction.WeightCardTapped) }) }
        if (state.hunger != null || state.checkInHunger != null) HungerCardView(state.hunger, state.checkInHunger)
        state.journal?.let { JournalCardView(it, onAllNotes = { onAction(TrendsAction.AllNotesTapped) }) }
    }
}

@Composable
private fun Header(rangeLabel: RangeLabel?, onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Column(Modifier.weight(1f)) {
            val range = when (rangeLabel) {
                null -> null
                is RangeLabel.Week -> rangeLabel.text
                RangeLabel.LastFourWeeks -> stringResource(Res.string.trends_range_4w)
                RangeLabel.LastThreeMonths -> stringResource(Res.string.trends_range_3m)
            }
            if (range != null) Text(text = range, typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.muted)
            Text(text = stringResource(Res.string.trends_title), typography = AppTheme.type.h1, color = AppTheme.colors.ink)
        }
        RoundIconButton(
            icon = AppIcons.Tune.described(stringResource(Res.string.trends_settings_cd)),
            onClick = onSettings,
            size = Dimension.D1300,
            containerColor = AppTheme.colors.surface,
            contentColor = AppTheme.colors.ink,
        )
    }
}

@Composable
private fun WeeklyCheckCard(check: WeeklyCheck) {
    val weekday = DateCopy.longWeekday(check.day)
    LockedHeroCard(
        title = if (check.tonight) {
            stringResource(Res.string.trends_check_tonight)
        } else {
            stringResource(Res.string.trends_check_on, weekday)
        },
        body = stringResource(Res.string.trends_check_body, weekday, DateCopy.time(check.at.hour, check.at.minute)),
        icon = AppIcons.EventAvailable,
    )
}

@Composable
private fun IntakeCardView(card: IntakeCard) {
    TrendsCard(verticalArrangement = Arrangement.spacedBy(Dimension.D800)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CardTitle(stringResource(Res.string.trends_eaten), Modifier.weight(1f))
            // "avg" is a label beside the number, so the metric tag carries only the amount.
            NutrientVisible(card.average) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D100)) {
                    NutrientText(
                        metric = Metric.Calories,
                        display = card.average,
                        typography = AppTheme.type.bodyBold.Tabular,
                        color = AppTheme.colors.ink,
                    )
                    Text(text = stringResource(Res.string.trends_avg), typography = AppTheme.type.body, color = AppTheme.colors.ink)
                }
            }
        }
        if (card.empty) {
            Text(text = stringResource(Res.string.trends_eaten_empty), typography = AppTheme.type.body, color = AppTheme.colors.inkSoft)
            return@TrendsCard
        }
        IntakeBarChart(bars = card.bars, band = card.band, readouts = card.readouts, bandColor = AppTheme.colors.accentTint)
        Text(
            text = stringResource(
                when (card.caption) {
                    IntakeCaption.Plain -> if (card.singleGoal) Res.string.trends_intake_caption_plain_goal else Res.string.trends_intake_caption_plain
                    IntakeCaption.TodayInProgress -> if (card.singleGoal) Res.string.trends_intake_caption_today_goal else Res.string.trends_intake_caption_today
                    IntakeCaption.WeekInProgress -> if (card.singleGoal) Res.string.trends_intake_caption_week_goal else Res.string.trends_intake_caption_week
                },
            ),
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.muted,
        )
    }
}

/** "Your week": each day a column of four meal dots, coloured by meal, dim for a meal not logged. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekDotsCard(week: WeekDots, onPatterns: () -> Unit) {
    HeroCard(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.trends_your_week),
                typography = AppTheme.type.sheetTitle,
                color = AppTheme.colors.onHero,
                modifier = Modifier.weight(1f),
            )
            HeroLink(text = stringResource(Res.string.trends_patterns), onClick = onPatterns)
        }
        Row(Modifier.fillMaxWidth()) {
            week.days.forEach { day ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimension.D400),
                ) {
                    day.meals.forEach { slot ->
                        ColorDot(slot?.tileColors()?.content ?: AppTheme.colors.onHero.withAlpha(MISSED_DOT_ALPHA), size = Dimension.D500)
                    }
                    Text(
                        text = day.label,
                        typography = if (day.isToday) AppTheme.type.secondaryStrong.Bold else AppTheme.type.secondary,
                        color = if (day.isToday) AppTheme.colors.onHero else AppTheme.colors.onHero.withAlpha(LABEL_ALPHA),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.padding(top = Dimension.D200),
                    )
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            MealSlot.meals.forEach { slot ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimension.D200)) {
                    ColorDot(slot.tileColors().content)
                    Text(text = stringResource(slot.label), typography = AppTheme.type.secondary, color = AppTheme.colors.onHero.withAlpha(LABEL_ALPHA))
                }
            }
        }
    }
}

@Composable
private fun RecapCardView(card: RecapCard, onPatterns: () -> Unit) {
    EdgeCard(
        edgeToEdge = false,
        contentPadding = PaddingValues(horizontal = AppTheme.spacing.cardHorizontal, vertical = Dimension.D200),
        verticalArrangement = Arrangement.Top,
    ) {
        if (card.titled) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CardTitle(stringResource(Res.string.trends_recap), Modifier.weight(1f))
                ChevronLink(text = stringResource(Res.string.trends_patterns), onClick = onPatterns)
            }
        }
        card.rows.forEachIndexed { index, row ->
            if (index > 0 || card.titled) HorizontalDivider(color = AppTheme.colors.line)
            when (row) {
                is RecapRow.DaysLogged -> FactRow(
                    stringResource(Res.string.trends_recap_days),
                    stringResource(Res.string.trends_recap_days_value, row.days, row.of),
                )
                is RecapRow.Breakfast -> FactRow(
                    stringResource(Res.string.trends_recap_breakfast),
                    stringResource(Res.string.trends_recap_breakfast_value, row.days, row.of),
                )
                is RecapRow.MealsLogged -> FactRow(stringResource(Res.string.trends_recap_meals), row.meals.toString())
                is RecapRow.NewFoods -> Column {
                    FactRow(stringResource(Res.string.trends_recap_new_foods), row.count.toString())
                    if (!row.names.isNullOrEmpty()) FoodChips(row.names, Modifier.padding(bottom = Dimension.D500))
                }
            }
        }
    }
}

/** Last week's and last month's recaps, each a link. */
@Composable
private fun RecapLinksCard(links: RecapLinks, onAction: (TrendsAction) -> Unit) {
    EdgeCard(
        edgeToEdge = false,
        contentPadding = PaddingValues(horizontal = AppTheme.spacing.cardHorizontal, vertical = Dimension.D400),
        verticalArrangement = Arrangement.spacedBy(Dimension.D200),
    ) {
        CardTitle(stringResource(Res.string.trends_recaps_title))
        ChevronLink(
            text = stringResource(Res.string.trends_recaps_last_week, DateCopy.range(links.week.start, links.week.end)),
            onClick = { onAction(TrendsAction.RecapTapped(links.week)) },
        )
        ChevronLink(
            text = stringResource(Res.string.trends_recaps_last_month, DateCopy.longMonth(links.month.start.month)),
            onClick = { onAction(TrendsAction.RecapTapped(links.month)) },
        )
    }
}

/** Foods as soft chips: Trends' new foods, and Patterns'. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FoodChips(names: List<String>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        names.forEach { PillTag(label = it, typography = AppTheme.type.secondaryStrong.Bold, size = PillTagSize.Regular) }
    }
}

/** A label and its value on one line, the value bold at the end. Read-only, so 40 tall as v3 draws it. */
@Composable
internal fun FactRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = Dimension.D1200).padding(vertical = Dimension.D300),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Text(text = label, typography = AppTheme.type.body, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
        Text(text = value, typography = AppTheme.type.body.ExtraBold, color = AppTheme.colors.ink, textAlign = TextAlign.End)
    }
}

@Composable
private fun LastCheckCard(check: LastCheck) {
    TrendsCard(verticalArrangement = Arrangement.spacedBy(Dimension.D500)) {
        Text(
            text = stringResource(Res.string.trends_last_check, check.weekEnd),
            typography = AppTheme.type.secondaryStrong.Bold,
            color = AppTheme.colors.muted,
        )
        NutrientVisible(check.average) {
            CheckRow(stringResource(Res.string.trends_last_check_average), Metric.Calories, check.average)
        }
        NutrientVisible(check.weight) {
            CheckRow(stringResource(Res.string.trends_last_check_weight), Metric.Weight, check.weight)
        }
    }
}

@Composable
private fun CheckRow(label: String, metric: Metric, display: Display) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, typography = AppTheme.type.body, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
        NutrientText(metric = metric, display = display, typography = AppTheme.type.numeric.ExtraBold, color = AppTheme.colors.ink)
    }
}

@Composable
private fun NutrientsCardView(card: NutrientsCard, onClick: () -> Unit) {
    TrendsCard(onClick = onClick) {
        TitleWithChevron(title = stringResource(Res.string.trends_nutrients), subtitle = stringResource(Res.string.trends_nutrients_sub))
        card.macros.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimension.D700)) {
                pair.forEach { tile -> MacroTileView(tile, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MacroTileView(tile: MacroTile, modifier: Modifier = Modifier) {
    val color = AppTheme.colors.of(tile.metric)
    Column(modifier.padding(top = Dimension.D200), verticalArrangement = Arrangement.spacedBy(Dimension.D300)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimension.D200),
        ) {
            ColorDot(color, size = Dimension.D400)
            Text(
                text = stringResource(tile.label),
                typography = AppTheme.type.secondaryStrong.Bold,
                color = AppTheme.colors.ink,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            NutrientText(
                metric = tile.metric,
                display = tile.display,
                typography = AppTheme.type.secondary.Tabular,
                color = AppTheme.colors.muted,
                maxLines = 1,
            )
        }
        NutrientFillBar(metric = tile.metric, display = tile.display, color = color)
    }
}

/** Not a link: there is no Expended page. The line is neutral, as the design draws it. */
@Composable
private fun ExpendedCardView(card: ExpendedCard) {
    TrendsCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Column(Modifier.weight(1f)) {
                CardTitle(stringResource(Res.string.trends_expended))
                Text(text = stringResource(Res.string.trends_expended_sub), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
            }
            NutrientText(Metric.EnergyOut, card.average, typography = AppTheme.type.figureMedium.ExtraBold, color = AppTheme.colors.ink)
        }
        if (card.trend.isNotEmpty()) {
            TrendLine(
                points = card.trend,
                dots = null,
                modifier = Modifier.fillMaxWidth().height(Dimension.D1600),
                readouts = card.readouts,
                lineColor = AppTheme.colors.inkSoft,
            )
        }
    }
}

@Composable
internal fun WeightCardView(card: WeightCard, onClick: () -> Unit) {
    TrendsCard(onClick = onClick) {
        val subtitle = when (card.subtitle) {
            WeightSubtitle.Trend -> stringResource(Res.string.trends_weight_trend)
            WeightSubtitle.NoWeighIns -> stringResource(Res.string.trends_weight_none)
        }
        TitleWithChevron(title = stringResource(Res.string.trends_weight), subtitle = subtitle) {
            card.value?.let {
                NutrientText(Metric.Weight, it, typography = AppTheme.type.figureMedium.ExtraBold, color = AppTheme.colors.ink)
            }
        }
        if (card.trend.isNotEmpty()) {
            TrendLine(
                points = card.trend,
                dots = null,
                modifier = Modifier.fillMaxWidth().height(Dimension.D1600),
                readouts = card.readouts,
            )
        }
    }
}

/**
 * Hunger: the check-ins' 1 to 10 level over the span when there are any ([checkIns]), then the
 * meal notes, how hunger before meals moved and where each meal usually sits from stuffed to
 * starving. One colour throughout, as on the Eaten chart: hungrier is not worse.
 */
@Composable
internal fun HungerCardView(trend: HungerTrend?, checkIns: CheckInHunger? = null) {
    if (checkIns == null) {
        when (trend) {
            null -> Unit
            HungerTrend.Empty -> EmptyCard(message = stringResource(Res.string.trends_hunger_empty), icon = AppIcons.EditNote)
            is HungerTrend.Chart -> TrendsCard(verticalArrangement = Arrangement.spacedBy(Dimension.D800)) {
                Column {
                    CardTitle(stringResource(Res.string.trends_hunger))
                    Text(
                        text = pluralStringResource(Res.plurals.trends_hunger_sub, trend.answers, trend.answers),
                        typography = AppTheme.type.secondary,
                        color = AppTheme.colors.muted,
                    )
                }
                MealHungerSections(trend, titled = false)
            }
        }
        return
    }
    TrendsCard(verticalArrangement = Arrangement.spacedBy(Dimension.D800)) {
        Column {
            CardTitle(stringResource(Res.string.trends_hunger))
            Text(
                text = pluralStringResource(Res.plurals.trends_hunger_checkins_sub, checkIns.answers, checkIns.answers),
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.muted,
            )
        }
        CheckInHungerSection(checkIns)
        if (trend is HungerTrend.Chart) MealHungerSections(trend, titled = true)
    }
}

/** The meal notes' hunger: the bars over the span, then each meal's track. [titled] under the check-ins, which come first. */
@Composable
private fun MealHungerSections(trend: HungerTrend.Chart, titled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        if (titled) {
            Text(
                text = stringResource(Res.string.trends_hunger_meals),
                typography = AppTheme.type.secondaryStrong.Bold,
                color = AppTheme.colors.muted,
            )
        }
        IntakeBarChart(bars = trend.bars, band = null, readouts = trend.readouts)
        Text(
            text = stringResource(if (trend.perWeek) Res.string.trends_hunger_caption_weeks else Res.string.trends_hunger_caption_days),
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.muted,
        )
    }
    if (trend.meals.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            Text(
                text = stringResource(Res.string.trends_hunger_by_meal),
                typography = AppTheme.type.secondaryStrong.Bold,
                color = AppTheme.colors.muted,
            )
            trend.meals.forEach { MealHungerRow(it) }
            Row {
                Spacer(Modifier.width(HUNGER_SLOT_WIDTH))
                TimelineAxis(HUNGER_AXIS.map { stringResource(it.label) }, Modifier.weight(1f))
                Spacer(Modifier.width(HUNGER_WORD_WIDTH))
            }
        }
    }
}

/** A meal, its stuffed-to-starving track (the dot its average, the tint its spread), and its usual answer. */
@Composable
private fun MealHungerRow(meal: MealHunger) {
    val slot = stringResource(meal.slot.label)
    val usual = stringResource(meal.usual.label)
    val description = stringResource(Res.string.trends_hunger_meal_cd, slot, usual)
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = slot,
            typography = AppTheme.type.secondaryStrong.Bold,
            color = AppTheme.colors.ink,
            maxLines = 1,
            modifier = Modifier.width(HUNGER_SLOT_WIDTH),
        )
        TimelineTrack(
            dots = listOf(meal.average),
            window = meal.spread,
            color = meal.slot.tileColors().content,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = usual,
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.inkSoft,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(HUNGER_WORD_WIDTH),
        )
    }
}

/** How many meals carry a note, and what older notes said about fullness and mood, with the way to all of them. */
@Composable
internal fun JournalCardView(card: JournalCard, onAllNotes: () -> Unit) {
    EdgeCard(
        edgeToEdge = false,
        contentPadding = PaddingValues(horizontal = AppTheme.spacing.cardHorizontal, vertical = Dimension.D200),
        verticalArrangement = Arrangement.Top,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = MinTouchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardTitle(stringResource(Res.string.trends_journal), Modifier.weight(1f))
            if (card.linksToNotes) ChevronLink(text = stringResource(Res.string.trends_journal_all), onClick = onAllNotes)
        }
        HorizontalDivider(color = AppTheme.colors.line)
        FactRow(stringResource(Res.string.trends_journal_noted), stringResource(Res.string.trends_recap_days_value, card.noted, card.meals))
        if (card.fullness.isNotEmpty()) {
            HorizontalDivider(color = AppTheme.colors.line)
            JournalSection(stringResource(Res.string.trends_journal_fullness))
            card.fullness.forEach { tally ->
                FactRow(stringResource(tally.value.label), pluralStringResource(Res.plurals.trends_journal_meals, tally.meals, tally.meals))
            }
        }
        if (card.moods.isNotEmpty()) {
            HorizontalDivider(color = AppTheme.colors.line)
            JournalSection(stringResource(Res.string.trends_journal_moods))
            FoodChips(
                names = card.moods.map { stringResource(Res.string.trends_journal_mood_count, stringResource(it.value.label), it.meals) },
                modifier = Modifier.padding(bottom = Dimension.D500),
            )
        }
    }
}

@Composable
private fun JournalSection(text: String) {
    Text(
        text = text,
        typography = AppTheme.type.secondaryStrong.Bold,
        color = AppTheme.colors.muted,
        modifier = Modifier.padding(top = Dimension.D500, bottom = Dimension.D200),
    )
}

private val Fullness.label: StringResource
    get() = when (this) {
        Fullness.StillHungry -> Res.string.trends_fullness_still_hungry
        Fullness.Satisfied -> Res.string.trends_fullness_satisfied
        Fullness.Full -> Res.string.trends_fullness_full
        Fullness.VeryFull -> Res.string.trends_fullness_very_full
    }

private val Mood.label: StringResource
    get() = when (this) {
        Mood.Calm -> Res.string.trends_mood_calm
        Mood.Happy -> Res.string.trends_mood_happy
        Mood.Social -> Res.string.trends_mood_social
        Mood.Rushed -> Res.string.trends_mood_rushed
        Mood.Stressed -> Res.string.trends_mood_stressed
        Mood.Anxious -> Res.string.trends_mood_anxious
        Mood.Low -> Res.string.trends_mood_low
    }

/** Under the hunger tracks, stuffed at the left and starving at the right. */
private val HUNGER_AXIS = listOf(Hunger.Stuffed, Hunger.Neutral, Hunger.Starving)
private val HUNGER_SLOT_WIDTH = Dimension.D1600
private val HUNGER_WORD_WIDTH = Dimension.D1500

@Composable
internal fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(text = text, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink, modifier = modifier)
}

/** A title over a muted subtitle, then [trailing] and the chevron that says the card opens a page. */
@Composable
private fun TitleWithChevron(title: String, subtitle: String, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Column(Modifier.weight(1f)) {
            CardTitle(title)
            Text(text = subtitle, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
        trailing()
        ListRowChevron()
    }
}

/** The Trends card: inside the page padding, as v3 draws this page, not edge to edge. */
@Composable
internal fun TrendsCard(
    onClick: (() -> Unit)? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(AppTheme.spacing.row),
    content: @Composable ColumnScope.() -> Unit,
) {
    EdgeCard(
        edgeToEdge = false,
        onClick = onClick,
        contentPadding = EdgeCardDefaults.InsetPadding,
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

private val TrendsRange.label
    get() = when (this) {
        TrendsRange.Week -> Res.string.trends_range_week
        TrendsRange.FourWeeks -> Res.string.trends_range_month
        TrendsRange.ThreeMonths -> Res.string.trends_range_three_months
    }

private const val MISSED_DOT_ALPHA = 0.18f
private const val LABEL_ALPHA = 0.78f
