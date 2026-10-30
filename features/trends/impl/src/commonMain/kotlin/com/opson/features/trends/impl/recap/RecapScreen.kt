package com.opson.features.trends.impl.recap

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.features.settings.EnergyExplainerRoute
import com.opson.features.trends.impl.Balance
import com.opson.features.trends.impl.CardTitle
import com.opson.features.trends.impl.FactRow
import com.opson.features.trends.impl.FoodChips
import com.opson.features.trends.impl.TrendsCard
import com.opson.features.trends.impl.balanceWeightPhrase
import com.opson.features.trends.impl.word
import com.opson.libraries.diary.Milestone
import com.opson.libraries.diary.RecapKind
import com.opson.libraries.diary.RecapPeriod
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.GoalType
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ui.NutrientPhrase
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.nutrition.ui.NutrientVisible
import com.opson.libraries.ui.components.ChevronLink
import com.opson.libraries.ui.components.DelayedLoadingIndicator
import com.opson.libraries.ui.components.EmptyCard
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.IntakeBarChart
import com.opson.libraries.ui.components.PageHeader
import com.opson.libraries.ui.components.PromptAction
import com.opson.libraries.ui.components.PromptCard
import com.opson.libraries.ui.components.PromptCardFooterLink
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.MinTouchTarget
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import com.opson.system.Dimension
import com.opson.system.background
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.color.ToneColors
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.celebrate_check_ins_30
import opson.libraries.resources.generated.resources.celebrate_check_ins_7
import opson.libraries.resources.generated.resources.celebrate_days_100
import opson.libraries.resources.generated.resources.celebrate_days_3
import opson.libraries.resources.generated.resources.celebrate_days_30
import opson.libraries.resources.generated.resources.celebrate_first_check_in
import opson.libraries.resources.generated.resources.celebrate_first_wrap_up
import opson.libraries.resources.generated.resources.celebrate_gentle_run
import opson.libraries.resources.generated.resources.celebrate_months_6
import opson.libraries.resources.generated.resources.celebrate_run
import opson.libraries.resources.generated.resources.celebrate_week_1
import opson.libraries.resources.generated.resources.celebrate_weeks_2
import opson.libraries.resources.generated.resources.celebrate_year_1
import opson.libraries.resources.generated.resources.prompt_not_now
import opson.libraries.resources.generated.resources.prompt_rather_not
import opson.libraries.resources.generated.resources.recap_balance_title
import opson.libraries.resources.generated.resources.recap_banner_body
import opson.libraries.resources.generated.resources.recap_banner_month_title
import opson.libraries.resources.generated.resources.recap_banner_open
import opson.libraries.resources.generated.resources.recap_banner_week_title
import opson.libraries.resources.generated.resources.recap_burned_activity
import opson.libraries.resources.generated.resources.recap_burned_digesting
import opson.libraries.resources.generated.resources.recap_burned_exercise
import opson.libraries.resources.generated.resources.recap_burned_resting
import opson.libraries.resources.generated.resources.recap_burned_sub
import opson.libraries.resources.generated.resources.recap_burned_title
import opson.libraries.resources.generated.resources.recap_checkins_sub
import opson.libraries.resources.generated.resources.recap_checkins_title
import opson.libraries.resources.generated.resources.recap_counting_note
import opson.libraries.resources.generated.resources.recap_daily_average
import opson.libraries.resources.generated.resources.recap_day_ahead_cd
import opson.libraries.resources.generated.resources.recap_day_logged_cd
import opson.libraries.resources.generated.resources.recap_day_not_logged_cd
import opson.libraries.resources.generated.resources.recap_day_skipped_cd
import opson.libraries.resources.generated.resources.recap_days_ahead
import opson.libraries.resources.generated.resources.recap_days_logged
import opson.libraries.resources.generated.resources.recap_days_logged_value
import opson.libraries.resources.generated.resources.recap_days_not_logged
import opson.libraries.resources.generated.resources.recap_days_skipped
import opson.libraries.resources.generated.resources.recap_days_title
import opson.libraries.resources.generated.resources.recap_eaten_caption
import opson.libraries.resources.generated.resources.recap_eaten_caption_goal
import opson.libraries.resources.generated.resources.recap_eaten_title
import opson.libraries.resources.generated.resources.recap_goal_burn
import opson.libraries.resources.generated.resources.recap_goal_close
import opson.libraries.resources.generated.resources.recap_goal_days
import opson.libraries.resources.generated.resources.recap_goal_deficit
import opson.libraries.resources.generated.resources.recap_goal_even
import opson.libraries.resources.generated.resources.recap_goal_every_day
import opson.libraries.resources.generated.resources.recap_goal_gain
import opson.libraries.resources.generated.resources.recap_goal_keep
import opson.libraries.resources.generated.resources.recap_goal_lose
import opson.libraries.resources.generated.resources.recap_goal_meals
import opson.libraries.resources.generated.resources.recap_goal_on_track
import opson.libraries.resources.generated.resources.recap_goal_steps
import opson.libraries.resources.generated.resources.recap_goal_surplus
import opson.libraries.resources.generated.resources.recap_goals_title
import opson.libraries.resources.generated.resources.recap_empty
import opson.libraries.resources.generated.resources.recap_kind_month
import opson.libraries.resources.generated.resources.recap_kind_week
import opson.libraries.resources.generated.resources.recap_loading_cd
import opson.libraries.resources.generated.resources.recap_new_foods
import opson.libraries.resources.generated.resources.recap_next_cd
import opson.libraries.resources.generated.resources.recap_plan_close
import opson.libraries.resources.generated.resources.recap_plan_of
import opson.libraries.resources.generated.resources.recap_plan_on
import opson.libraries.resources.generated.resources.recap_plan_title
import opson.libraries.resources.generated.resources.recap_previous_cd
import opson.libraries.resources.generated.resources.recap_reached
import opson.libraries.resources.generated.resources.recap_showing_up
import opson.libraries.resources.generated.resources.recap_so_far
import opson.libraries.resources.generated.resources.recap_title_month
import opson.libraries.resources.generated.resources.recap_title_week
import opson.libraries.resources.generated.resources.recap_top_food_days
import opson.libraries.resources.generated.resources.recap_top_foods
import opson.libraries.resources.generated.resources.recap_total
import opson.libraries.resources.generated.resources.recap_weight_title
import opson.libraries.resources.generated.resources.recap_your_goal
import opson.libraries.resources.generated.resources.recap_your_range
import opson.libraries.resources.generated.resources.today_checkin_energy
import opson.libraries.resources.generated.resources.today_checkin_hunger
import opson.libraries.resources.generated.resources.today_checkin_mood_level
import opson.libraries.resources.generated.resources.today_checkin_sleep
import opson.libraries.resources.generated.resources.trends_energy_link_burned
import opson.libraries.resources.generated.resources.trends_energy_link_deficit
import opson.libraries.resources.generated.resources.trends_energy_link_surplus
import opson.libraries.resources.generated.resources.trends_running_days
import opson.libraries.resources.generated.resources.trends_running_even
import opson.libraries.resources.generated.resources.trends_running_over
import opson.libraries.resources.generated.resources.trends_running_scale_steady
import opson.libraries.resources.generated.resources.trends_running_under
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** A recap. Stateless: everything on it comes from [state], already resolved by the policy. */
@Composable
fun RecapScreen(state: RecapState, onAction: (RecapAction) -> Unit, modifier: Modifier = Modifier) {
    Screen(modifier = modifier, containerColor = AppTheme.colors.bg.color) { padding ->
        RecapContent(state, onAction, Modifier.padding(padding).verticalScroll(rememberScrollState()))
    }
}

/** The page without its scaffold, so previews and the leak test render it at full height. */
@Composable
internal fun RecapContent(state: RecapState, onAction: (RecapAction) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.section),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        val period = state.period
        PageHeader(
            title = period?.let { stringResource(it.title, *it.titleArgs()) } ?: stringResource(Res.string.recap_title_week),
            subtitle = period?.let { dates ->
                if (state.inProgress) stringResource(Res.string.recap_so_far, dates.label()) else dates.label()
            },
            backLabel = "",
            onBack = { onAction(RecapAction.BackTapped) },
        )
        if (state.loading || period == null) {
            DelayedLoadingIndicator(contentDescription = stringResource(Res.string.recap_loading_cd))
            return@Column
        }
        PeriodBar(state, period, onAction)
        if (state.empty) {
            EmptyCard(message = stringResource(Res.string.recap_empty), icon = AppIcons.EventAvailable)
            return@Column
        }
        state.showingUp?.let { ShowingUpCard(it) }
        state.milestones?.let { MilestonesCard(it) }
        state.checkIns?.let { CheckInsCard(it) }
        state.eaten?.let { EatenCard(it) }
        state.plan?.let { PlanCard(it) }
        if (state.goals.isNotEmpty()) GoalsCard(state.goals)
        state.burned?.let { BurnedCard(it, onAction) }
        state.balance?.let { BalanceCard(it, onAction) }
        state.weight?.let { WeightCard(it) }
        if (state.topFoods.isNotEmpty()) TopFoodsCard(state.topFoods)
        if (state.newFoods.isNotEmpty()) NewFoodsCard(state.newFoods)
    }
}

/** Week or month, and a step back or forward. */
@Composable
private fun PeriodBar(state: RecapState, period: RecapPeriod, onAction: (RecapAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        RoundIconButton(
            icon = AppIcons.ChevronLeft.described(stringResource(Res.string.recap_previous_cd)),
            onClick = { onAction(RecapAction.PreviousTapped) },
            enabled = state.canGoBack,
            containerColor = AppTheme.colors.surface,
            contentColor = AppTheme.colors.ink,
        )
        SegmentedControl(
            options = RecapKind.entries,
            selected = period.kind,
            onSelect = { onAction(RecapAction.KindPicked(it)) },
            label = { stringResource(if (it == RecapKind.Week) Res.string.recap_kind_week else Res.string.recap_kind_month) },
            minHeight = MinTouchTarget,
            modifier = Modifier.weight(1f),
        )
        RoundIconButton(
            icon = AppIcons.ChevronRight.described(stringResource(Res.string.recap_next_cd)),
            onClick = { onAction(RecapAction.NextTapped) },
            enabled = state.canGoForward,
            containerColor = AppTheme.colors.surface,
            contentColor = if (state.canGoForward) AppTheme.colors.ink else AppTheme.colors.muted,
        )
    }
}

/** A dot a day, then how many were logged, skipped and empty, and what the totals count. */
@Composable
private fun ShowingUpCard(section: ShowingUpSection) {
    TrendsCard(verticalArrangement = Arrangement.spacedBy(Dimension.D500)) {
        CardTitle(stringResource(Res.string.recap_days_title))
        section.days.chunked(DAYS_IN_A_ROW).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { DayDotView(it, Modifier.weight(1f)) }
                repeat(DAYS_IN_A_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Column {
            FactRow(
                stringResource(Res.string.recap_days_logged),
                stringResource(Res.string.recap_days_logged_value, section.logged, section.days.size - section.ahead),
            )
            if (section.skipped > 0) FactRow(stringResource(Res.string.recap_days_skipped), section.skipped.toString())
            if (section.notLogged > 0) FactRow(stringResource(Res.string.recap_days_not_logged), section.notLogged.toString())
            if (section.ahead > 0) FactRow(stringResource(Res.string.recap_days_ahead), section.ahead.toString())
        }
        if (section.countingNote) {
            Text(
                text = pluralStringResource(Res.plurals.recap_counting_note, section.logged, section.logged),
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.muted,
            )
        }
    }
}

/** One day: a filled dot when logged, a sun umbrella when skipped, an open ring when empty, faint when still to come. */
@Composable
private fun DayDotView(dot: RecapDayDot, modifier: Modifier = Modifier) {
    val day = DateCopy.longWeekdayMonthDay(dot.date)
    val description = stringResource(
        when (dot.status) {
            RecapDayStatus.Logged -> Res.string.recap_day_logged_cd
            RecapDayStatus.Skipped -> Res.string.recap_day_skipped_cd
            RecapDayStatus.NotLogged -> Res.string.recap_day_not_logged_cd
            RecapDayStatus.Ahead -> Res.string.recap_day_ahead_cd
        },
        day,
    )
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimension.D200),
    ) {
        Box(Modifier.size(DOT), contentAlignment = Alignment.Center) {
            when (dot.status) {
                RecapDayStatus.Logged -> Box(Modifier.size(DOT).clip(CircleShape).background(AppTheme.colors.accent))
                RecapDayStatus.Skipped -> Icon(icon = AppIcons.BeachAccess, size = DOT, color = AppTheme.colors.muted)
                RecapDayStatus.NotLogged -> Box(Modifier.size(DOT).border(2.dp, AppTheme.colors.line.color, CircleShape))
                RecapDayStatus.Ahead -> Box(Modifier.size(DOT).clip(CircleShape).background(AppTheme.colors.track))
            }
        }
        Text(
            text = dot.label,
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.muted,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun MilestonesCard(section: MilestonesSection) {
    TrendsCard(verticalArrangement = Arrangement.spacedBy(Dimension.D500)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            Icon(icon = AppIcons.AutoAwesome, size = 20.dp, color = AppTheme.colors.accent)
            CardTitle(stringResource(Res.string.recap_showing_up))
        }
        if (!section.counts) {
            Text(text = stringResource(Res.string.celebrate_gentle_run), typography = AppTheme.type.body, color = AppTheme.colors.ink)
            return@TrendsCard
        }
        if (section.run > 0) {
            Text(
                text = pluralStringResource(Res.plurals.celebrate_run, section.run, section.run),
                typography = AppTheme.type.body,
                color = AppTheme.colors.ink,
            )
        }
        if (section.reached.isNotEmpty()) {
            Text(text = stringResource(Res.string.recap_reached), typography = AppTheme.type.secondaryStrong.Bold, color = AppTheme.colors.muted)
            FoodChips(section.reached.map { stringResource(it.recapLabel) })
        }
    }
}

@Composable
private fun CheckInsCard(section: CheckInsSection) {
    TrendsCard(verticalArrangement = Arrangement.spacedBy(Dimension.D300)) {
        Column {
            CardTitle(stringResource(Res.string.recap_checkins_title))
            Text(
                text = pluralStringResource(Res.plurals.recap_checkins_sub, section.days, section.days),
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.muted,
            )
        }
        Column {
            section.mood?.let { FactRow(stringResource(Res.string.today_checkin_mood_level), stringResource(it)) }
            section.energy?.let { FactRow(stringResource(Res.string.today_checkin_energy), stringResource(it)) }
            section.hunger?.let { FactRow(stringResource(Res.string.today_checkin_hunger), stringResource(it)) }
            section.sleep?.let { FactRow(stringResource(Res.string.today_checkin_sleep), stringResource(it)) }
        }
    }
}

@Composable
private fun EatenCard(section: EatenSection) {
    TrendsCard(verticalArrangement = Arrangement.spacedBy(Dimension.D500)) {
        CardTitle(stringResource(Res.string.recap_eaten_title))
        Column {
            AmountRow(stringResource(Res.string.recap_total), Metric.Calories, section.total)
            AmountRow(stringResource(Res.string.recap_daily_average), Metric.Calories, section.average)
            AmountRow(stringResource(if (section.singleGoal) Res.string.recap_your_goal else Res.string.recap_your_range), Metric.Calories, section.range)
        }
        IntakeBarChart(bars = section.bars, band = section.band, readouts = section.readouts, bandColor = AppTheme.colors.accentTint)
        Text(
            text = stringResource(if (section.singleGoal) Res.string.recap_eaten_caption_goal else Res.string.recap_eaten_caption),
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.muted,
        )
    }
}

/** On plan or close, never a count of days off. */
@Composable
private fun PlanCard(section: PlanSection) {
    TrendsCard(verticalArrangement = Arrangement.spacedBy(Dimension.D300)) {
        CardTitle(stringResource(Res.string.recap_plan_title))
        Column {
            FactRow(stringResource(Res.string.recap_plan_on), section.onPlan.toString())
            if (section.close > 0) FactRow(stringResource(Res.string.recap_plan_close), section.close.toString())
        }
        Text(text = pluralStringResource(Res.plurals.recap_plan_of, section.of, section.of), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
    }
}

/** Each goal: the days on track and close, out of the days it could judge. Never a count of days off. */
@Composable
private fun GoalsCard(goals: List<GoalWeek>) {
    TrendsCard(verticalArrangement = Arrangement.spacedBy(Dimension.D300)) {
        CardTitle(stringResource(Res.string.recap_goals_title))
        goals.forEachIndexed { index, goal ->
            if (index > 0) HorizontalDivider(color = AppTheme.colors.line)
            Column {
                Text(text = goal.type.recapLabel(), typography = AppTheme.type.bodyStrong, color = AppTheme.colors.ink, modifier = Modifier.padding(top = Dimension.D300))
                FactRow(stringResource(Res.string.recap_goal_on_track), stringResource(Res.string.recap_goal_days, goal.met, goal.counted))
                if (goal.near > 0) FactRow(stringResource(Res.string.recap_goal_close), goal.near.toString())
            }
        }
    }
}

/** How the recap names a goal. */
@Composable
private fun GoalType.recapLabel(): String = when (this) {
    is GoalType.Weight -> stringResource(
        when (direction) {
            GoalDirection.Lose -> Res.string.recap_goal_lose
            GoalDirection.Gain -> Res.string.recap_goal_gain
            else -> Res.string.recap_goal_keep
        },
    )
    is GoalType.EnergyBalance -> stringResource(
        when {
            kcalPerDay < 0 -> Res.string.recap_goal_deficit
            kcalPerDay > 0 -> Res.string.recap_goal_surplus
            else -> Res.string.recap_goal_even
        },
    )
    is GoalType.Burn -> stringResource(Res.string.recap_goal_burn)
    is GoalType.Steps -> stringResource(Res.string.recap_goal_steps)
    GoalType.TrackEveryDay -> stringResource(Res.string.recap_goal_every_day)
    is GoalType.Meals -> pluralStringResource(Res.plurals.recap_goal_meals, count, count)
    is GoalType.Unknown -> stringResource(Res.string.recap_goals_title)
}

@Composable
private fun BurnedCard(section: BurnedSection, onAction: (RecapAction) -> Unit) {
    TrendsCard(verticalArrangement = Arrangement.spacedBy(Dimension.D500)) {
        Column {
            CardTitle(stringResource(Res.string.recap_burned_title))
            Text(
                text = pluralStringResource(Res.plurals.recap_burned_sub, section.days, section.days),
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.muted,
            )
        }
        Column {
            AmountRow(stringResource(Res.string.recap_total), Metric.EnergyOut, section.total)
            AmountRow(stringResource(Res.string.recap_daily_average), Metric.EnergyOut, section.average)
        }
        BurnedSplitBar(section.parts)
        Column {
            section.parts.forEach { share ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimension.D300)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(share.part.color()))
                    AmountRow(stringResource(share.part.label), Metric.EnergyOut, share.amount, Modifier.weight(1f))
                }
            }
        }
        ChevronLink(
            text = stringResource(Res.string.trends_energy_link_burned),
            onClick = { onAction(RecapAction.EnergyExplainerTapped(EnergyExplainerRoute.TOPIC_BURNED)) },
        )
    }
}

/** Burned's parts side by side, by share: no scale, so it says only how they compare. */
@Composable
private fun BurnedSplitBar(parts: List<BurnedShare>) {
    Row(
        modifier = Modifier.fillMaxWidth().height(BAR_HEIGHT).clip(CircleShape),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        parts.filter { it.share > 0f }.forEach { share ->
            Box(Modifier.weight(share.share).height(BAR_HEIGHT).background(share.part.color()))
        }
    }
}

@Composable
private fun BalanceCard(section: BalanceSection, onAction: (RecapAction) -> Unit) {
    TrendsCard(verticalArrangement = Arrangement.spacedBy(Dimension.D500)) {
        CardTitle(stringResource(Res.string.recap_balance_title))
        when (section.side) {
            Balance.Even -> NutrientVisible(section.amount) {
                Text(text = stringResource(Res.string.trends_running_even), typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
            }
            Balance.Under, Balance.Over -> NutrientPhrase(
                metric = Metric.Calories,
                display = section.amount,
                phrase = { stringResource(if (section.side == Balance.Under) Res.string.trends_running_under else Res.string.trends_running_over, it) },
                typography = AppTheme.type.cardTitle,
                color = AppTheme.colors.ink,
                numberWeight = FontWeight.ExtraBold,
            )
        }
        section.weight?.let { weight ->
            NutrientPhrase(
                metric = Metric.Weight,
                display = weight,
                phrase = { stringResource(balanceWeightPhrase(section.side, section.unit), it) },
                typography = AppTheme.type.body,
                color = AppTheme.colors.inkSoft,
                numberWeight = FontWeight.Bold,
            )
        }
        Text(
            text = stringResource(Res.string.trends_running_days, section.days, section.of),
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.muted,
        )
        ChevronLink(
            text = stringResource(if (section.side == Balance.Over) Res.string.trends_energy_link_surplus else Res.string.trends_energy_link_deficit),
            onClick = { onAction(RecapAction.EnergyExplainerTapped(EnergyExplainerRoute.TOPIC_BALANCE)) },
        )
    }
}

@Composable
private fun WeightCard(section: WeightSection) {
    TrendsCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CardTitle(stringResource(Res.string.recap_weight_title), Modifier.weight(1f))
            val word = section.direction.word
            when {
                section.softened -> NutrientText(Metric.Weight, section.change, typography = AppTheme.type.body.ExtraBold, color = AppTheme.colors.ink)
                word == null -> Text(text = stringResource(Res.string.trends_running_scale_steady), typography = AppTheme.type.body.ExtraBold, color = AppTheme.colors.ink)
                else -> NutrientPhrase(
                    metric = Metric.Weight,
                    display = section.change,
                    phrase = { stringResource(word, it) },
                    typography = AppTheme.type.body,
                    color = AppTheme.colors.ink,
                    numberWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
private fun TopFoodsCard(foods: List<TopFood>) {
    TrendsCard(verticalArrangement = Arrangement.spacedBy(Dimension.D300)) {
        CardTitle(stringResource(Res.string.recap_top_foods))
        if (foods.any { it.days == null }) {
            FoodChips(foods.map { it.name })
        } else {
            Column {
                foods.forEachIndexed { index, food ->
                    if (index > 0) HorizontalDivider(color = AppTheme.colors.line)
                    FactRow(food.name, food.days?.let { pluralStringResource(Res.plurals.recap_top_food_days, it, it) }.orEmpty())
                }
            }
        }
    }
}

@Composable
private fun NewFoodsCard(names: List<String>) {
    TrendsCard(verticalArrangement = Arrangement.spacedBy(Dimension.D300)) {
        CardTitle(stringResource(Res.string.recap_new_foods))
        FoodChips(names)
    }
}

/** A label and an amount at its end, the amount tagged with its metric. */
@Composable
private fun AmountRow(label: String, metric: Metric, display: Display, modifier: Modifier = Modifier) {
    NutrientVisible(display) {
        Row(
            modifier = modifier.fillMaxWidth().padding(vertical = Dimension.D200),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            Text(text = label, typography = AppTheme.type.body, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
            NutrientText(metric = metric, display = display, typography = AppTheme.type.body.ExtraBold, color = AppTheme.colors.ink)
        }
    }
}

/**
 * "Your week is ready" in Today's list: Not now first, as on every Today card, so neither reads as
 * the wrong answer, and on its first few showings the way to turn it off.
 */
@Composable
fun RecapBannerCard(
    period: RecapPeriod,
    onOpen: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
    onTurnOff: (() -> Unit)? = null,
) {
    PromptCard(
        icon = AppIcons.EventAvailable,
        tone = ToneColors(AppTheme.colors.accent, AppTheme.colors.accentTint),
        title = when (period.kind) {
            RecapKind.Week -> stringResource(Res.string.recap_banner_week_title)
            RecapKind.Month -> stringResource(Res.string.recap_banner_month_title, DateCopy.longMonth(period.start.month))
        },
        body = stringResource(Res.string.recap_banner_body, period.label()),
        modifier = modifier,
        footer = onTurnOff?.let { turnOff -> { PromptCardFooterLink(label = stringResource(Res.string.prompt_rather_not), onClick = turnOff) } },
    ) {
        PromptAction(label = stringResource(Res.string.prompt_not_now), onClick = onNotNow, modifier = Modifier.weight(1f))
        PromptAction(label = stringResource(Res.string.recap_banner_open), onClick = onOpen, modifier = Modifier.weight(1f), emphasis = true)
    }
}

/** `Oct 19 to 25`, or `October 2026`. */
internal fun RecapPeriod.label(): String = when (kind) {
    RecapKind.Week -> DateCopy.range(start, end)
    RecapKind.Month -> DateCopy.monthYear(start)
}

private val RecapPeriod.title: StringResource
    get() = when (kind) {
        RecapKind.Week -> Res.string.recap_title_week
        RecapKind.Month -> Res.string.recap_title_month
    }

private fun RecapPeriod.titleArgs(): Array<Any> = when (kind) {
    RecapKind.Week -> emptyArray()
    RecapKind.Month -> arrayOf(DateCopy.longMonth(start.month))
}

private val BurnedPart.label: StringResource
    get() = when (this) {
        BurnedPart.Resting -> Res.string.recap_burned_resting
        BurnedPart.Activity -> Res.string.recap_burned_activity
        BurnedPart.Exercise -> Res.string.recap_burned_exercise
        BurnedPart.Digesting -> Res.string.recap_burned_digesting
    }

/** Burned's colours, cool on purpose as everywhere: resting and activity as on Energy today. */
@Composable
private fun BurnedPart.color(): ColorResource = when (this) {
    BurnedPart.Resting -> AppTheme.colors.restingEnergy
    BurnedPart.Activity -> AppTheme.colors.activeEnergy
    BurnedPart.Exercise -> AppTheme.colors.accent
    BurnedPart.Digesting -> AppTheme.colors.inkSoft
}

/** The celebration's own name for a milestone: "1 week", "Your first check-in". */
internal val Milestone.recapLabel: StringResource
    get() = when (this) {
        Milestone.FirstWrapUp -> Res.string.celebrate_first_wrap_up
        Milestone.Days3 -> Res.string.celebrate_days_3
        Milestone.Week1 -> Res.string.celebrate_week_1
        Milestone.Weeks2 -> Res.string.celebrate_weeks_2
        Milestone.Days30 -> Res.string.celebrate_days_30
        Milestone.Days100 -> Res.string.celebrate_days_100
        Milestone.Months6 -> Res.string.celebrate_months_6
        Milestone.Year1 -> Res.string.celebrate_year_1
        Milestone.FirstCheckIn -> Res.string.celebrate_first_check_in
        Milestone.CheckIns7 -> Res.string.celebrate_check_ins_7
        Milestone.CheckIns30 -> Res.string.celebrate_check_ins_30
    }

private const val DAYS_IN_A_ROW = 7
private val DOT = 18.dp
private val BAR_HEIGHT = 10.dp
