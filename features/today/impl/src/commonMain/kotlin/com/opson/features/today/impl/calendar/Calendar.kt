package com.opson.features.today.impl.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.opson.features.today.impl.TodayStateBuilder
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.SelectedDay
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.ui.components.DayHeader
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.MonthGrid
import com.opson.libraries.ui.components.MonthGridCell
import com.opson.libraries.ui.components.MonthGridLegend
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import com.opson.system.background
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_cal_close_cd
import opson.libraries.resources.generated.resources.today_cal_jump_today
import opson.libraries.resources.generated.resources.today_cal_initial_fri
import opson.libraries.resources.generated.resources.today_cal_initial_mon
import opson.libraries.resources.generated.resources.today_cal_initial_sat
import opson.libraries.resources.generated.resources.today_cal_initial_sun
import opson.libraries.resources.generated.resources.today_cal_initial_thu
import opson.libraries.resources.generated.resources.today_cal_initial_tue
import opson.libraries.resources.generated.resources.today_cal_initial_wed
import opson.libraries.resources.generated.resources.today_cal_legend
import opson.libraries.resources.generated.resources.today_cal_next_cd
import opson.libraries.resources.generated.resources.today_cal_prev_cd
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The calendar that drops from Today's title (design sync 4, 13.3a): one month, Sunday first,
 * with a dot on days that have something logged. Up to [TodayStateBuilder.MAX_DAYS_AHEAD] days
 * ahead can be picked.
 */
data class CalendarState(
    /** The first of the month shown. */
    val month: LocalDate,
    val title: String,
    val weekdayInitials: List<StringResource>,
    /** A null is a blank before the 1st. */
    val cells: List<CalendarCell?>,
    val canGoPrev: Boolean,
    val canGoNext: Boolean,
)

data class CalendarCell(
    val date: LocalDate,
    val selected: Boolean,
    val isToday: Boolean,
    val enabled: Boolean,
    val logged: Boolean,
)

sealed interface CalendarAction {
    data class LoggedDaysArrived(val month: LocalDate, val days: Set<LocalDate>) : CalendarAction
    data object PrevMonthTapped : CalendarAction
    data object NextMonthTapped : CalendarAction
    data class DayTapped(val date: LocalDate) : CalendarAction
    /** Jump to today. */
    data object TodayTapped : CalendarAction

    /** The month's title with its ▴: the calendar folds back up. */
    data object CloseTapped : CalendarAction
}

sealed interface CalendarEvent {
    data object Dismiss : CalendarEvent
}

object CalendarStateBuilder {
    /** A year back is as far as the arrows go. */
    const val MONTHS_BACK = 12

    private const val DAYS_IN_WEEK = 7

    val weekdayInitials: List<StringResource> = listOf(
        Res.string.today_cal_initial_sun,
        Res.string.today_cal_initial_mon,
        Res.string.today_cal_initial_tue,
        Res.string.today_cal_initial_wed,
        Res.string.today_cal_initial_thu,
        Res.string.today_cal_initial_fri,
        Res.string.today_cal_initial_sat,
    )

    fun monthOf(date: LocalDate): LocalDate = LocalDate(date.year, date.month, 1)

    fun build(month: LocalDate, selected: LocalDate, today: LocalDate, logged: Set<LocalDate>): CalendarState {
        val lastPickable = lastPickable(today)
        val next = month.plus(DatePeriod(months = 1))
        val length = month.daysUntilNext(next)
        // Sunday first: Sunday is ISO 7, so it leads with no blanks.
        val blanks = month.dayOfWeek.isoDayNumber % DAYS_IN_WEEK
        val days = (0 until length).map { offset ->
            val date = month.plus(DatePeriod(days = offset))
            CalendarCell(
                date = date,
                selected = date == selected,
                isToday = date == today,
                enabled = date <= lastPickable,
                logged = date in logged,
            )
        }
        return CalendarState(
            month = month,
            title = DateCopy.monthYear(month),
            weekdayInitials = weekdayInitials,
            cells = List(blanks) { null } + days,
            canGoPrev = month > monthOf(today).minus(DatePeriod(months = MONTHS_BACK)),
            canGoNext = month < monthOf(lastPickable),
        )
    }

    fun lastPickable(today: LocalDate): LocalDate = today.plus(DatePeriod(days = TodayStateBuilder.MAX_DAYS_AHEAD))

    private fun LocalDate.daysUntilNext(next: LocalDate): Int = next.minus(DatePeriod(days = 1)).day
}

/** The calendar, opened on the selected day's month. */
@OptIn(ExperimentalCoroutinesApi::class)
@Inject
class CalendarViewModel(
    @Assisted private val selected: LocalDate,
    diary: DiaryRepository,
    private val selectedDay: SelectedDay,
    private val calendar: LocalCalendar,
) : SEAViewModel<CalendarState, CalendarEvent, CalendarAction>(
    initialStateArg = CalendarStateBuilder.build(CalendarStateBuilder.monthOf(selected), selected, calendar.today(), emptySet()),
) {
    private val month = MutableStateFlow(CalendarStateBuilder.monthOf(selected))

    init {
        viewModelScope.launch {
            month.flatMapLatest { m -> diary.observeLoggedDates(m.year, m.month.number).map { m to it } }
                .collect { (m, days) -> takeAction(CalendarAction.LoggedDaysArrived(m, days)) }
        }
    }

    override suspend fun handleAction(action: CalendarAction) {
        when (action) {
            is CalendarAction.LoggedDaysArrived -> if (action.month == month.value) {
                action.updateState { CalendarStateBuilder.build(action.month, selected, calendar.today(), action.days) }
            }
            CalendarAction.PrevMonthTapped -> if (state.canGoPrev) action.moveTo(month.value.minus(DatePeriod(months = 1)))
            CalendarAction.NextMonthTapped -> if (state.canGoNext) action.moveTo(month.value.plus(DatePeriod(months = 1)))
            is CalendarAction.DayTapped -> if (action.date <= CalendarStateBuilder.lastPickable(calendar.today())) {
                selectedDay.select(action.date)
                sendEvent(CalendarEvent.Dismiss)
            }
            CalendarAction.TodayTapped -> {
                selectedDay.select(calendar.today())
                sendEvent(CalendarEvent.Dismiss)
            }
            CalendarAction.CloseTapped -> sendEvent(CalendarEvent.Dismiss)
        }
    }

    /** Shows [next] at once with no dots, then the dots as they arrive. */
    private suspend fun CalendarAction.moveTo(next: LocalDate) {
        month.value = next
        updateState { CalendarStateBuilder.build(next, selected, calendar.today(), emptySet()) }
    }
}

/**
 * The header repeated with the month as its title and a ▴ (‹ › page months, the title folds it
 * up), then the month on a card with Jump to today and the dot's legend under a hairline.
 */
@Composable
fun CalendarDropdownContent(
    state: CalendarState,
    onAction: (CalendarAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.bg)
                .statusBarsPadding()
                .padding(horizontal = AppTheme.spacing.row),
        ) {
            DayHeader(
                title = state.title,
                previousIcon = AppIcons.ChevronLeft.described(stringResource(Res.string.today_cal_prev_cd)),
                nextIcon = AppIcons.ChevronRight.described(stringResource(Res.string.today_cal_next_cd)),
                onPrevious = { onAction(CalendarAction.PrevMonthTapped) },
                onNext = { onAction(CalendarAction.NextMonthTapped) },
                onTitleClick = { onAction(CalendarAction.CloseTapped) },
                titleDescription = stringResource(Res.string.today_cal_close_cd, state.title),
                previousEnabled = state.canGoPrev,
                nextEnabled = state.canGoNext,
                expanded = true,
            )
        }
        Column(
            modifier = Modifier
                .padding(horizontal = AppTheme.spacing.row, vertical = AppTheme.spacing.row)
                .fillMaxWidth()
                .clip(AppTheme.shapes.card)
                .background(AppTheme.colors.surface)
                .padding(bottom = AppTheme.spacing.row),
        ) {
            MonthGrid(
                weekdayLabels = state.weekdayInitials.map { stringResource(it) },
                cells = state.cells.map { cell ->
                    cell?.let {
                        MonthGridCell(
                            date = it.date,
                            contentDescription = DateCopy.longMonthDay(it.date),
                            selected = it.selected,
                            isToday = it.isToday,
                            enabled = it.enabled,
                            marked = it.logged,
                        )
                    }
                },
                onDayClick = { onAction(CalendarAction.DayTapped(it)) },
            )
            HorizontalDivider(color = AppTheme.colors.line, modifier = Modifier.padding(horizontal = AppTheme.spacing.section))
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = AppTheme.spacing.section, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
            ) {
                FlatButton(
                    text = stringResource(Res.string.today_cal_jump_today),
                    onClick = { onAction(CalendarAction.TodayTapped) },
                    tone = FlatButtonTone.AccentText,
                    minHeight = AppTheme.spacing.touchTarget,
                    typography = AppTheme.type.button,
                    modifier = Modifier.weight(1f, fill = false),
                )
                MonthGridLegend(text = stringResource(Res.string.today_cal_legend), modifier = Modifier.weight(1f).wrapContentWidth(Alignment.End))
            }
        }
    }
}
