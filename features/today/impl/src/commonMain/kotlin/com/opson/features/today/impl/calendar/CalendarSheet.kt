package com.opson.features.today.impl.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.opson.features.today.impl.TodayStateBuilder
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.SelectedDay
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.ui.components.MonthGrid
import com.opson.libraries.ui.components.MonthGridCell
import com.opson.libraries.ui.components.MonthGridLegend
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.RoundIconButton
import com.opson.libraries.ui.components.icon.described
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
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
import opson.libraries.resources.generated.resources.today_cal_done
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
import opson.libraries.resources.generated.resources.today_cal_today
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The calendar sheet: one month, Sunday first as the prototype, with a dot on
 * days that have something logged. Up to six days ahead can be picked.
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
    data object TodayTapped : CalendarAction
    data object DoneTapped : CalendarAction
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

/** The calendar sheet, opened on the selected day's month. */
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
            CalendarAction.DoneTapped -> sendEvent(CalendarEvent.Dismiss)
        }
    }

    /** Shows [next] at once with no dots, then the dots as they arrive. */
    private suspend fun CalendarAction.moveTo(next: LocalDate) {
        month.value = next
        updateState { CalendarStateBuilder.build(next, selected, calendar.today(), emptySet()) }
    }
}

@Composable
fun CalendarSheetContent(
    state: CalendarState,
    onAction: (CalendarAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RoundIconButton(
                icon = AppIcons.ChevronLeft.described(stringResource(Res.string.today_cal_prev_cd)),
                onClick = { onAction(CalendarAction.PrevMonthTapped) },
                size = AppTheme.spacing.touchTarget,
                containerColor = AppTheme.colors.surface,
                contentColor = if (state.canGoPrev) AppTheme.colors.ink else AppTheme.colors.line,
                enabled = state.canGoPrev,
            )
            Text(text = state.title, typography = AppTheme.type.figureMedium.ExtraBold, color = AppTheme.colors.ink)
            RoundIconButton(
                icon = AppIcons.ChevronRight.described(stringResource(Res.string.today_cal_next_cd)),
                onClick = { onAction(CalendarAction.NextMonthTapped) },
                size = AppTheme.spacing.touchTarget,
                containerColor = AppTheme.colors.surface,
                contentColor = if (state.canGoNext) AppTheme.colors.ink else AppTheme.colors.line,
                enabled = state.canGoNext,
            )
        }
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
        ) {
            MonthGridLegend(text = stringResource(Res.string.today_cal_legend), modifier = Modifier.weight(1f))
            FlatButton(
                text = stringResource(Res.string.today_cal_today),
                onClick = { onAction(CalendarAction.TodayTapped) },
                shape = AppTheme.shapes.pill,
                minHeight = AppTheme.spacing.touchTarget,
                typography = AppTheme.type.buttonSmall,
                centered = true,
            )
            FlatButton(
                text = stringResource(Res.string.today_cal_done),
                onClick = { onAction(CalendarAction.DoneTapped) },
                tone = FlatButtonTone.Accent,
                shape = AppTheme.shapes.pill,
                minHeight = AppTheme.spacing.touchTarget,
                typography = AppTheme.type.buttonSmall,
                centered = true,
            )
        }
    }
}
