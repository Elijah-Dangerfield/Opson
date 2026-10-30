package com.opson.features.trends.impl

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opson.features.settings.EnergyExplainerRoute
import com.opson.libraries.diary.ui.label
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.nutrition.ui.NutrientPhrase
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.nutrition.ui.NutrientVisible
import com.opson.libraries.ui.components.ChevronLink
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.RangeDotAxisLabel
import com.opson.libraries.ui.components.RangeDotChart
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.SheetHandle
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.SwipePages
import com.opson.libraries.ui.components.button.ButtonSecondary
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.IconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.MinTouchTarget
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import com.opson.system.Dimension
import com.opson.system.background
import com.opson.system.thenIf
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.goal_calendar_note
import opson.libraries.resources.generated.resources.goal_calendar_note_week
import opson.libraries.resources.generated.resources.today_cal_initial_fri
import opson.libraries.resources.generated.resources.today_cal_initial_mon
import opson.libraries.resources.generated.resources.today_cal_initial_sat
import opson.libraries.resources.generated.resources.today_cal_initial_sun
import opson.libraries.resources.generated.resources.today_cal_initial_thu
import opson.libraries.resources.generated.resources.today_cal_initial_tue
import opson.libraries.resources.generated.resources.today_cal_initial_wed
import opson.libraries.resources.generated.resources.today_cal_next_cd
import opson.libraries.resources.generated.resources.today_cal_prev_cd
import opson.libraries.resources.generated.resources.trends_calendar_counts
import opson.libraries.resources.generated.resources.trends_calendar_counts_off
import opson.libraries.resources.generated.resources.trends_calendar_counts_over
import opson.libraries.resources.generated.resources.trends_calendar_counts_under
import opson.libraries.resources.generated.resources.trends_calendar_day_cd
import opson.libraries.resources.generated.resources.trends_calendar_help_cd
import opson.libraries.resources.generated.resources.trends_calendar_help_gain
import opson.libraries.resources.generated.resources.trends_calendar_help_gray
import opson.libraries.resources.generated.resources.trends_calendar_help_hold
import opson.libraries.resources.generated.resources.trends_calendar_help_logged
import opson.libraries.resources.generated.resources.trends_calendar_help_lose
import opson.libraries.resources.generated.resources.trends_calendar_logged_count
import opson.libraries.resources.generated.resources.trends_calendar_sub
import opson.libraries.resources.generated.resources.trends_calendar_sub_logged
import opson.libraries.resources.generated.resources.trends_calendar_title
import opson.libraries.resources.generated.resources.trends_calendar_title_logged
import opson.libraries.resources.generated.resources.trends_day_burned
import opson.libraries.resources.generated.resources.trends_day_difference
import opson.libraries.resources.generated.resources.trends_day_eaten
import opson.libraries.resources.generated.resources.trends_day_note_in_progress
import opson.libraries.resources.generated.resources.trends_day_note_logged
import opson.libraries.resources.generated.resources.trends_day_note_not_logged
import opson.libraries.resources.generated.resources.trends_day_note_not_yet
import opson.libraries.resources.generated.resources.trends_day_note_partial
import opson.libraries.resources.generated.resources.trends_day_note_untracked
import opson.libraries.resources.generated.resources.trends_day_open
import opson.libraries.resources.generated.resources.trends_day_plan
import opson.libraries.resources.generated.resources.trends_day_plan_about
import opson.libraries.resources.generated.resources.trends_energy_link_burned
import opson.libraries.resources.generated.resources.trends_energy_link_deficit
import opson.libraries.resources.generated.resources.trends_energy_link_surplus
import opson.libraries.resources.generated.resources.trends_hunger_checkins
import opson.libraries.resources.generated.resources.trends_hunger_checkins_caption_days
import opson.libraries.resources.generated.resources.trends_hunger_checkins_caption_weeks
import opson.libraries.resources.generated.resources.trends_plan_a_bit_over
import opson.libraries.resources.generated.resources.trends_plan_a_bit_under
import opson.libraries.resources.generated.resources.trends_plan_future
import opson.libraries.resources.generated.resources.trends_plan_in_progress
import opson.libraries.resources.generated.resources.trends_plan_logged
import opson.libraries.resources.generated.resources.trends_plan_near
import opson.libraries.resources.generated.resources.trends_plan_none
import opson.libraries.resources.generated.resources.trends_plan_not_yet
import opson.libraries.resources.generated.resources.trends_plan_off
import opson.libraries.resources.generated.resources.trends_plan_on
import opson.libraries.resources.generated.resources.trends_plan_over
import opson.libraries.resources.generated.resources.trends_plan_partial
import opson.libraries.resources.generated.resources.trends_plan_under
import opson.libraries.resources.generated.resources.trends_plan_untracked
import opson.libraries.resources.generated.resources.trends_running_days
import opson.libraries.resources.generated.resources.trends_running_empty
import opson.libraries.resources.generated.resources.trends_running_even
import opson.libraries.resources.generated.resources.trends_running_gained_kg
import opson.libraries.resources.generated.resources.trends_running_gained_lb
import opson.libraries.resources.generated.resources.trends_running_help
import opson.libraries.resources.generated.resources.trends_running_help_cd
import opson.libraries.resources.generated.resources.trends_running_lost_kg
import opson.libraries.resources.generated.resources.trends_running_lost_lb
import opson.libraries.resources.generated.resources.trends_running_month
import opson.libraries.resources.generated.resources.trends_running_over
import opson.libraries.resources.generated.resources.trends_running_scale
import opson.libraries.resources.generated.resources.trends_running_scale_down
import opson.libraries.resources.generated.resources.trends_running_scale_steady
import opson.libraries.resources.generated.resources.trends_running_scale_up
import opson.libraries.resources.generated.resources.trends_running_since
import opson.libraries.resources.generated.resources.trends_running_sub
import opson.libraries.resources.generated.resources.trends_running_title
import opson.libraries.resources.generated.resources.trends_running_under
import opson.libraries.resources.generated.resources.trends_running_week
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The on-track calendar: a month at a time, swiped or stepped with the arrows, each day filled by
 * how it went against the plan, today ringed. A day opens its sheet. The (i) says how to read it.
 */
@Composable
internal fun CalendarCardView(calendar: OnTrackCalendar, onAction: (TrendsAction) -> Unit) {
    val plan = calendar.mode == CalendarMode.Plan
    TrendsCard(verticalArrangement = Arrangement.spacedBy(Dimension.D500)) {
        TitleWithHelp(
            title = stringResource(if (plan) Res.string.trends_calendar_title else Res.string.trends_calendar_title_logged),
            subtitle = stringResource(if (plan) Res.string.trends_calendar_sub else Res.string.trends_calendar_sub_logged),
            helpDescription = stringResource(Res.string.trends_calendar_help_cd),
            onHelp = { onAction(TrendsAction.CalendarHelpTapped) },
        )
        if (calendar.explained) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimension.D300)) {
                if (plan) Help(stringResource(calendar.direction.help))
                Help(stringResource(if (plan) Res.string.trends_calendar_help_gray else Res.string.trends_calendar_help_logged))
                // A plan aimed at Burned: the same (i) sheets Targets and Energy today open.
                if (plan && calendar.direction != GoalDirection.Hold) EnergyLinks(calendar.direction == GoalDirection.Gain, onAction)
            }
        }
        MonthStepper(calendar, onAction)
        SwipePages(
            page = calendar.page,
            pageCount = calendar.months.size,
            onPageChange = { onAction(TrendsAction.CalendarMonthShown(it)) },
        ) { index ->
            MonthDays(calendar.months[index], calendar.weekStart, calendar.mode, onDay = { onAction(TrendsAction.CalendarDayTapped(it)) })
        }
        val month = calendar.shown
        Text(
            text = if (plan) {
                stringResource(Res.string.trends_calendar_counts, month.on, month.near, month.off, stringResource(calendar.direction.offCount))
            } else {
                pluralStringResource(Res.plurals.trends_calendar_logged_count, month.logged, month.logged)
            },
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.muted,
        )
        calendar.goalSince?.let { since ->
            Text(
                text = stringResource(if (calendar.goalWeekly) Res.string.goal_calendar_note_week else Res.string.goal_calendar_note, DateCopy.monthDay(since)),
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.muted,
            )
        }
        Legend(calendar)
    }
}

/** The month's name between the arrows that step to the month before and after. */
@Composable
private fun MonthStepper(calendar: OnTrackCalendar, onAction: (TrendsAction) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            icon = AppIcons.ChevronLeft.described(stringResource(Res.string.today_cal_prev_cd)),
            onClick = { onAction(TrendsAction.CalendarMonthShown(calendar.page - 1)) },
            enabled = calendar.page > 0,
            iconColor = if (calendar.page > 0) AppTheme.colors.ink else AppTheme.colors.line,
        )
        Text(
            text = DateCopy.monthYear(calendar.shown.first),
            typography = AppTheme.type.bodyBold,
            color = AppTheme.colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            icon = AppIcons.ChevronRight.described(stringResource(Res.string.today_cal_next_cd)),
            onClick = { onAction(TrendsAction.CalendarMonthShown(calendar.page + 1)) },
            enabled = calendar.page < calendar.months.lastIndex,
            iconColor = if (calendar.page < calendar.months.lastIndex) AppTheme.colors.ink else AppTheme.colors.line,
        )
    }
}

/** Weekday initials from the person's week start, then the month in weeks of seven. */
@Composable
private fun MonthDays(month: CalendarMonth, weekStart: DayOfWeek, mode: CalendarMode, onDay: (kotlinx.datetime.LocalDate) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimension.D100)) {
        Row(Modifier.fillMaxWidth()) {
            (0 until DAYS_IN_WEEK).forEach { offset ->
                val day = DayOfWeek((weekStart.isoDayNumber - 1 + offset) % DAYS_IN_WEEK + 1)
                Text(
                    text = stringResource(day.initial),
                    typography = AppTheme.type.captionStrong,
                    color = AppTheme.colors.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(bottom = Dimension.D200),
                )
            }
        }
        month.cells.chunked(DAYS_IN_WEEK).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (0 until DAYS_IN_WEEK).forEach { index ->
                    Box(Modifier.weight(1f).height(CELL_HEIGHT), contentAlignment = Alignment.Center) {
                        week.getOrNull(index)?.let { DayCell(it, mode, onDay) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: CalendarDay, mode: CalendarMode, onDay: (kotlinx.datetime.LocalDate) -> Unit) {
    val description = stringResource(Res.string.trends_calendar_day_cd, DateCopy.longMonthDay(day.date), stringResource(day.standing.word(mode)))
    val fill = day.fill()
    Box(
        modifier = Modifier
            .size(CELL_HEIGHT)
            .clip(CircleShape)
            .thenIf(day.tappable) { clickable(role = Role.Button) { onDay(day.date) } }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(DAY_SIZE)
                .clip(CircleShape)
                .thenIf(fill != null) { background(requireNotNull(fill)) }
                .thenIf(day.isToday) { border(TODAY_RING, AppTheme.colors.ink.color, CircleShape) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.date.day.toString(),
                typography = if (day.isToday) AppTheme.type.numeric.Bold else AppTheme.type.numeric,
                color = if (day.standing == PlanStanding.Future) AppTheme.colors.muted else AppTheme.colors.ink,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/** The fill a day's tone draws; gray for a past day with nothing to show, none for a day to come. */
@Composable
private fun CalendarDay.fill(): ColorResource? = when (tone) {
    PlanTone.On -> AppTheme.colors.plan.on
    PlanTone.Near -> AppTheme.colors.plan.near
    PlanTone.Off -> AppTheme.colors.plan.off
    PlanTone.Logged -> AppTheme.colors.plan.logged
    PlanTone.None -> if (standing == PlanStanding.Future) null else AppTheme.colors.soft
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(calendar: OnTrackCalendar) {
    val keys = if (calendar.mode == CalendarMode.Plan) {
        listOf(
            AppTheme.colors.plan.on to Res.string.trends_plan_on,
            AppTheme.colors.plan.near to Res.string.trends_plan_near,
            AppTheme.colors.plan.off to calendar.direction.offWord,
            AppTheme.colors.soft to Res.string.trends_plan_none,
        )
    } else {
        listOf(AppTheme.colors.plan.logged to Res.string.trends_plan_logged, AppTheme.colors.soft to Res.string.trends_plan_none)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.section), verticalArrangement = Arrangement.spacedBy(Dimension.D200)) {
        keys.forEach { (color, word) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimension.D200)) {
                Box(Modifier.size(LEGEND_DOT).clip(CircleShape).background(color))
                Text(text = stringResource(word), typography = AppTheme.type.secondary, color = AppTheme.colors.inkSoft)
            }
        }
    }
}

/**
 * The running total: the span picker, the total in plain words, what it might mean on the scale
 * and the weight trend beside it, and how many days went in. The (i) says what it can't know.
 */
@Composable
internal fun RunningTotalCardView(card: RunningTotalCard, onAction: (TrendsAction) -> Unit) {
    TrendsCard(verticalArrangement = Arrangement.spacedBy(Dimension.D500)) {
        TitleWithHelp(
            title = stringResource(Res.string.trends_running_title),
            subtitle = stringResource(Res.string.trends_running_sub),
            helpDescription = stringResource(Res.string.trends_running_help_cd),
            onHelp = { onAction(TrendsAction.RunningHelpTapped) },
        )
        if (card.explained) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimension.D300)) {
                Help(stringResource(Res.string.trends_running_help))
                EnergyLinks(surplus = card.side == Balance.Over, onAction)
            }
        }
        if (card.spans.size > 1) {
            SegmentedControl(
                options = card.spans,
                selected = card.span,
                onSelect = { onAction(TrendsAction.RunningSpanPicked(it)) },
                label = { stringResource(it.label) },
                minHeight = MinTouchTarget,
            )
        }
        if (card.empty) {
            Text(text = stringResource(Res.string.trends_running_empty), typography = AppTheme.type.body, color = AppTheme.colors.inkSoft)
            return@TrendsCard
        }
        when (card.side) {
            Balance.Even -> NutrientVisible(card.balance) {
                Text(text = stringResource(Res.string.trends_running_even), typography = AppTheme.type.figureMedium.ExtraBold, color = AppTheme.colors.ink)
            }
            Balance.Under, Balance.Over -> {
                val phrase = if (card.side == Balance.Under) Res.string.trends_running_under else Res.string.trends_running_over
                NutrientPhrase(
                    metric = Metric.Calories,
                    display = card.balance,
                    phrase = { stringResource(phrase, it) },
                    typography = AppTheme.type.cardTitle,
                    color = AppTheme.colors.ink,
                    numberWeight = FontWeight.ExtraBold,
                )
            }
        }
        card.weight?.let { weight ->
            NutrientPhrase(
                metric = Metric.Weight,
                display = weight,
                phrase = { stringResource(card.weightPhrase, it) },
                typography = AppTheme.type.body,
                color = AppTheme.colors.inkSoft,
                numberWeight = FontWeight.Bold,
            )
        }
        // Beside the estimate, what the scale's smoothed trend did over the same days.
        card.scale?.let { scale ->
            Column {
                HorizontalDivider(color = AppTheme.colors.line)
                CompareRow(stringResource(Res.string.trends_running_scale), Metric.Weight, scale.change, scale.direction.word)
            }
        }
        Text(
            text = stringResource(Res.string.trends_running_days, card.days, card.of),
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.muted,
        )
    }
}

/** "Your weight trend · Down 0.8 lb", under the estimate so the two read together. */
@Composable
private fun CompareRow(label: String, metric: Metric, display: Display, word: StringResource?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimension.D300),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Text(text = label, typography = AppTheme.type.body, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
        if (word == null) {
            Text(text = stringResource(Res.string.trends_running_scale_steady), typography = AppTheme.type.body.ExtraBold, color = AppTheme.colors.ink)
        } else {
            NutrientPhrase(
                metric = metric,
                display = display,
                phrase = { stringResource(word, it) },
                typography = AppTheme.type.body,
                color = AppTheme.colors.ink,
                numberWeight = FontWeight.ExtraBold,
            )
        }
    }
}

/** The check-ins' 1 to 10 hunger, inside the Hunger card: dots over the span with the scale's words down the side. */
@Composable
internal fun CheckInHungerSection(hunger: CheckInHunger) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        Text(
            text = stringResource(Res.string.trends_hunger_checkins),
            typography = AppTheme.type.secondaryStrong.Bold,
            color = AppTheme.colors.muted,
        )
        RangeDotChart(
            dots = hunger.dots,
            axis = hunger.axis.map { (position, level) -> RangeDotAxisLabel(position, stringResource(level.label)) },
            readouts = hunger.readouts,
        )
        Text(
            text = stringResource(if (hunger.perWeek) Res.string.trends_hunger_checkins_caption_weeks else Res.string.trends_hunger_checkins_caption_days),
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.muted,
        )
    }
}

/** The calendar's day sheet: how the day went, its numbers where the mode shows them, and the way to it on Today. */
@Composable
fun TrendsDaySheetContent(
    state: TrendsDayState,
    onAction: (TrendsDayAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, top = Dimension.D400, bottom = Dimension.D900),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        SheetHandle(Modifier.align(Alignment.CenterHorizontally))
        SheetHeader(
            title = state.date?.let(DateCopy::longWeekdayMonthDay).orEmpty(),
            onClose = { onAction(TrendsDayAction.CloseTapped) },
        )
        if (state.loading) return@Column
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimension.D300)) {
            Box(Modifier.size(STANDING_DOT).clip(CircleShape).background(state.toneColor()))
            Text(text = stringResource(state.standing.word(state.mode, state.direction)), typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
        }
        state.standing.note?.let { Help(stringResource(it)) }
        val rows = listOfNotNull(
            DayRow.Eaten.takeIf { state.eaten !is Display.Hidden },
            DayRow.Burned.takeIf { state.burned !is Display.Hidden },
            DayRow.Difference.takeIf { state.difference !is Display.Hidden },
            DayRow.Plan.takeIf { state.goal !is Display.Hidden },
        )
        if (rows.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(AppTheme.shapes.groupedList)
                    .background(AppTheme.colors.surface)
                    .padding(horizontal = AppTheme.spacing.cardHorizontal),
            ) {
                rows.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(color = AppTheme.colors.line)
                    DayRowView(row, state)
                }
            }
        }
        if (state.canOpen) {
            ButtonSecondary(onClick = { onAction(TrendsDayAction.OpenDayTapped) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.trends_day_open))
            }
        }
    }
}

private enum class DayRow { Eaten, Burned, Difference, Plan }

@Composable
private fun DayRowView(row: DayRow, state: TrendsDayState) {
    when (row) {
        DayRow.Eaten -> SheetRow(stringResource(Res.string.trends_day_eaten)) { Figure(Metric.Calories, state.eaten) }
        DayRow.Burned -> SheetRow(stringResource(Res.string.trends_day_burned)) { Figure(Metric.EnergyOut, state.burned) }
        DayRow.Difference -> SheetRow(stringResource(Res.string.trends_day_difference)) {
            if (state.side == Balance.Even) {
                Text(text = stringResource(Res.string.trends_running_even), typography = AppTheme.type.body.ExtraBold, color = AppTheme.colors.ink, textAlign = TextAlign.End)
            } else {
                EndPhrase(state.difference, if (state.side == Balance.Under) Res.string.trends_running_under else Res.string.trends_running_over)
            }
        }
        DayRow.Plan -> SheetRow(stringResource(Res.string.trends_day_plan)) {
            if (state.goalAbout) EndPhrase(state.goal, Res.string.trends_day_plan_about) else Figure(Metric.Calories, state.goal)
        }
    }
}

/** A calorie amount inside a few words, the amount bold, at the row's end. */
@Composable
private fun EndPhrase(display: Display, phrase: StringResource) {
    NutrientPhrase(
        metric = Metric.Calories,
        display = display,
        phrase = { stringResource(phrase, it) },
        typography = AppTheme.type.body,
        color = AppTheme.colors.ink,
        textAlign = TextAlign.End,
        numberWeight = FontWeight.ExtraBold,
    )
}

@Composable
private fun SheetRow(label: String, value: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimension.D500),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Text(text = label, typography = AppTheme.type.body, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
        value()
    }
}

@Composable
private fun Figure(metric: Metric, display: Display) {
    NutrientText(metric = metric, display = display, typography = AppTheme.type.body.ExtraBold, color = AppTheme.colors.ink, textAlign = TextAlign.End)
}

@Composable
private fun TrendsDayState.toneColor(): ColorResource = when (tone) {
    PlanTone.On -> AppTheme.colors.plan.on
    PlanTone.Near -> AppTheme.colors.plan.near
    PlanTone.Off -> AppTheme.colors.plan.off
    PlanTone.Logged -> AppTheme.colors.plan.logged
    PlanTone.None -> AppTheme.colors.soft
}

/** A card title over its subtitle, with the (i) that opens the card's explanation. */
@Composable
private fun TitleWithHelp(title: String, subtitle: String, helpDescription: String, onHelp: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            CardTitle(title)
            Text(text = subtitle, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
        IconButton(icon = AppIcons.Info.described(helpDescription), onClick = onHelp, iconColor = AppTheme.colors.muted)
    }
}

/** The shared energy (i) sheets: what total burned (TDEE) is, and how a deficit or a surplus works. */
@Composable
private fun EnergyLinks(surplus: Boolean, onAction: (TrendsAction) -> Unit) {
    Column {
        ChevronLink(
            text = stringResource(Res.string.trends_energy_link_burned),
            onClick = { onAction(TrendsAction.EnergyExplainerTapped(EnergyExplainerRoute.TOPIC_BURNED)) },
        )
        ChevronLink(
            text = stringResource(if (surplus) Res.string.trends_energy_link_surplus else Res.string.trends_energy_link_deficit),
            onClick = { onAction(TrendsAction.EnergyExplainerTapped(EnergyExplainerRoute.TOPIC_BALANCE)) },
        )
    }
}

@Composable
private fun Help(text: String) {
    Text(text = text, typography = AppTheme.type.secondary, color = AppTheme.colors.inkSoft)
}

/** The calendar's word for a day. In the logged-only calendar a day is only logged or not. */
internal fun PlanStanding.word(mode: CalendarMode, direction: GoalDirection = GoalDirection.Hold): StringResource = when (this) {
    PlanStanding.OnPlan -> Res.string.trends_plan_on
    PlanStanding.ABitOver -> Res.string.trends_plan_a_bit_over
    PlanStanding.ABitUnder -> Res.string.trends_plan_a_bit_under
    PlanStanding.Over -> Res.string.trends_plan_over
    PlanStanding.Under -> Res.string.trends_plan_under
    PlanStanding.Partial -> if (mode == CalendarMode.Plan) Res.string.trends_plan_partial else Res.string.trends_plan_logged
    PlanStanding.Logged -> Res.string.trends_plan_logged
    PlanStanding.NotLogged -> Res.string.trends_plan_none
    PlanStanding.Untracked -> Res.string.trends_plan_untracked
    PlanStanding.InProgress -> Res.string.trends_plan_in_progress
    PlanStanding.NotYet -> Res.string.trends_plan_not_yet
    PlanStanding.Future -> Res.string.trends_plan_future
}

private val PlanStanding.note: StringResource?
    get() = when (this) {
        PlanStanding.Partial -> Res.string.trends_day_note_partial
        PlanStanding.InProgress -> Res.string.trends_day_note_in_progress
        PlanStanding.NotYet -> Res.string.trends_day_note_not_yet
        PlanStanding.Untracked -> Res.string.trends_day_note_untracked
        PlanStanding.NotLogged -> Res.string.trends_day_note_not_logged
        PlanStanding.Logged -> Res.string.trends_day_note_logged
        else -> null
    }

/** The off color's word: over for a deficit, under for a surplus, off for a range. */
private val GoalDirection.offWord: StringResource
    get() = when (this) {
        GoalDirection.Lose -> Res.string.trends_plan_over
        GoalDirection.Gain -> Res.string.trends_plan_under
        GoalDirection.Hold -> Res.string.trends_plan_off
    }

private val GoalDirection.offCount: StringResource
    get() = when (this) {
        GoalDirection.Lose -> Res.string.trends_calendar_counts_over
        GoalDirection.Gain -> Res.string.trends_calendar_counts_under
        GoalDirection.Hold -> Res.string.trends_calendar_counts_off
    }

private val GoalDirection.help: StringResource
    get() = when (this) {
        GoalDirection.Lose -> Res.string.trends_calendar_help_lose
        GoalDirection.Gain -> Res.string.trends_calendar_help_gain
        GoalDirection.Hold -> Res.string.trends_calendar_help_hold
    }

private val RunningSpan.label: StringResource
    get() = when (this) {
        RunningSpan.Week -> Res.string.trends_running_week
        RunningSpan.Month -> Res.string.trends_running_month
        RunningSpan.SinceStart -> Res.string.trends_running_since
    }

private val RunningTotalCard.weightPhrase: StringResource get() = balanceWeightPhrase(side, unit)

/** "Roughly 0.4 kg of body weight lost, using the rule of thumb…": the running total's and a recap's. */
internal fun balanceWeightPhrase(side: Balance, unit: WeightUnit): StringResource = when {
    side == Balance.Over && unit == WeightUnit.Lb -> Res.string.trends_running_gained_lb
    side == Balance.Over -> Res.string.trends_running_gained_kg
    unit == WeightUnit.Lb -> Res.string.trends_running_lost_lb
    else -> Res.string.trends_running_lost_kg
}

/** "Down %s" or "Up %s"; null for about the same. */
internal val ScaleDirection.word: StringResource?
    get() = when (this) {
        ScaleDirection.Down -> Res.string.trends_running_scale_down
        ScaleDirection.Up -> Res.string.trends_running_scale_up
        ScaleDirection.Steady -> null
    }

private val DayOfWeek.initial: StringResource
    get() = when (this) {
        DayOfWeek.MONDAY -> Res.string.today_cal_initial_mon
        DayOfWeek.TUESDAY -> Res.string.today_cal_initial_tue
        DayOfWeek.WEDNESDAY -> Res.string.today_cal_initial_wed
        DayOfWeek.THURSDAY -> Res.string.today_cal_initial_thu
        DayOfWeek.FRIDAY -> Res.string.today_cal_initial_fri
        DayOfWeek.SATURDAY -> Res.string.today_cal_initial_sat
        DayOfWeek.SUNDAY -> Res.string.today_cal_initial_sun
    }

private const val DAYS_IN_WEEK = 7
private val CELL_HEIGHT = 44.dp
private val DAY_SIZE = 36.dp
private val TODAY_RING = 2.dp
private val LEGEND_DOT = 12.dp
private val STANDING_DOT = 14.dp
