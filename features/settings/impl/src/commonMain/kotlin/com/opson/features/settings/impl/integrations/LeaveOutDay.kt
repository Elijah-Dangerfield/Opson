package com.opson.features.settings.impl.integrations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.opson.features.settings.impl.SettingsPreview
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.TrendsRange
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.ui.components.MonthGrid
import com.opson.libraries.ui.components.MonthGridCell
import com.opson.libraries.ui.components.SheetHandle
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonDefaults
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
import kotlinx.datetime.plus
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.integrations_leave_out_body
import opson.libraries.resources.generated.resources.integrations_leave_out_day_left_out
import opson.libraries.resources.generated.resources.integrations_leave_out_done
import opson.libraries.resources.generated.resources.integrations_leave_out_next
import opson.libraries.resources.generated.resources.integrations_leave_out_prev
import opson.libraries.resources.generated.resources.integrations_leave_out_title
import opson.libraries.resources.generated.resources.today_cal_initial_fri
import opson.libraries.resources.generated.resources.today_cal_initial_mon
import opson.libraries.resources.generated.resources.today_cal_initial_sat
import opson.libraries.resources.generated.resources.today_cal_initial_sun
import opson.libraries.resources.generated.resources.today_cal_initial_thu
import opson.libraries.resources.generated.resources.today_cal_initial_tue
import opson.libraries.resources.generated.resources.today_cal_initial_wed
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Which days can be left out of the math: Trends' longest span back from today. An older day is
 * in no average, and a day ahead has nothing in it to leave out.
 */
object LeaveOutWindow {
    val DAYS: Int = TrendsRange.ThreeMonths.days

    fun start(today: LocalDate): LocalDate = today.minus(DatePeriod(days = DAYS - 1))

    fun contains(date: LocalDate, today: LocalDate): Boolean = date >= start(today) && date <= today
}

/**
 * The Leave out a day sheet: one month, Sunday first like Today's calendar, with the days left
 * out drawn selected. Tapping a day leaves it out, tapping it again counts it, and the sheet
 * stays open. A day left out is a day marked untracked, so Today's day menu and this sheet are
 * two doors to the same thing.
 */
data class LeaveOutState(
    /** The first of the month shown. */
    val month: LocalDate,
    val title: String,
    val weekdayInitials: List<StringResource>,
    /** A null is a blank before the 1st. */
    val cells: List<LeaveOutCell?>,
    val canGoPrev: Boolean,
    val canGoNext: Boolean,
)

data class LeaveOutCell(val date: LocalDate, val leftOut: Boolean, val isToday: Boolean, val enabled: Boolean)

sealed interface LeaveOutAction {
    data class DaysArrived(val month: LocalDate, val leftOut: Set<LocalDate>) : LeaveOutAction
    data object PrevMonthTapped : LeaveOutAction
    data object NextMonthTapped : LeaveOutAction
    data class DayTapped(val date: LocalDate) : LeaveOutAction
    data object DoneTapped : LeaveOutAction
}

sealed interface LeaveOutEvent {
    data object Dismiss : LeaveOutEvent
}

object LeaveOutStateBuilder {
    private const val DAYS_IN_WEEK = 7

    private val weekdayInitials: List<StringResource> = listOf(
        Res.string.today_cal_initial_sun,
        Res.string.today_cal_initial_mon,
        Res.string.today_cal_initial_tue,
        Res.string.today_cal_initial_wed,
        Res.string.today_cal_initial_thu,
        Res.string.today_cal_initial_fri,
        Res.string.today_cal_initial_sat,
    )

    fun monthOf(date: LocalDate): LocalDate = LocalDate(date.year, date.month, 1)

    fun lastOf(month: LocalDate): LocalDate = month.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))

    fun build(month: LocalDate, today: LocalDate, leftOut: Set<LocalDate>): LeaveOutState {
        val length = lastOf(month).day
        // Sunday first: Sunday is ISO 7, so it leads with no blanks.
        val blanks = month.dayOfWeek.isoDayNumber % DAYS_IN_WEEK
        val days = (0 until length).map { offset ->
            val date = month.plus(DatePeriod(days = offset))
            LeaveOutCell(date = date, leftOut = date in leftOut, isToday = date == today, enabled = LeaveOutWindow.contains(date, today))
        }
        return LeaveOutState(
            month = month,
            title = DateCopy.monthYear(month),
            weekdayInitials = weekdayInitials,
            cells = List(blanks) { null } + days,
            canGoPrev = month > monthOf(LeaveOutWindow.start(today)),
            canGoNext = month < monthOf(today),
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Inject
class LeaveOutViewModel(
    private val diary: DiaryRepository,
    private val calendar: LocalCalendar,
) : SEAViewModel<LeaveOutState, LeaveOutEvent, LeaveOutAction>(
    initialStateArg = LeaveOutStateBuilder.build(LeaveOutStateBuilder.monthOf(calendar.today()), calendar.today(), emptySet()),
) {
    private val month = MutableStateFlow(LeaveOutStateBuilder.monthOf(calendar.today()))
    private var leftOut: Set<LocalDate> = emptySet()

    init {
        viewModelScope.launch {
            month.flatMapLatest { m ->
                diary.observeDays(m, LeaveOutStateBuilder.lastOf(m)).map { days -> m to days.filter { it.untracked }.map { it.date }.toSet() }
            }.collect { (m, days) -> takeAction(LeaveOutAction.DaysArrived(m, days)) }
        }
    }

    override suspend fun handleAction(action: LeaveOutAction) {
        when (action) {
            is LeaveOutAction.DaysArrived -> if (action.month == month.value) {
                leftOut = action.leftOut
                action.updateState { LeaveOutStateBuilder.build(action.month, calendar.today(), leftOut) }
            }
            LeaveOutAction.PrevMonthTapped -> if (state.canGoPrev) action.moveTo(month.value.minus(DatePeriod(months = 1)))
            LeaveOutAction.NextMonthTapped -> if (state.canGoNext) action.moveTo(month.value.plus(DatePeriod(months = 1)))
            is LeaveOutAction.DayTapped -> if (LeaveOutWindow.contains(action.date, calendar.today())) {
                diary.setUntracked(action.date, untracked = action.date !in leftOut)
            }
            LeaveOutAction.DoneTapped -> sendEvent(LeaveOutEvent.Dismiss)
        }
    }

    /** Shows [next] at once with nothing selected, then the days left out as they arrive. */
    private suspend fun LeaveOutAction.moveTo(next: LocalDate) {
        month.value = next
        leftOut = emptySet()
        updateState { LeaveOutStateBuilder.build(next, calendar.today(), leftOut) }
    }
}

/** The sheet's body, apart from the `BottomSheet` so previews and the leak test can draw it. */
@Composable
fun LeaveOutSheetContent(
    state: LeaveOutState,
    onAction: (LeaveOutAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, top = 10.dp, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        SheetHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
        Column(modifier = Modifier.padding(start = 4.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = stringResource(Res.string.integrations_leave_out_title), typography = AppTheme.type.dayLabel, color = AppTheme.colors.ink)
            Text(text = stringResource(Res.string.integrations_leave_out_body), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RoundIconButton(
                icon = AppIcons.ChevronLeft.described(stringResource(Res.string.integrations_leave_out_prev)),
                onClick = { onAction(LeaveOutAction.PrevMonthTapped) },
                size = AppTheme.spacing.touchTarget,
                containerColor = AppTheme.colors.surface,
                contentColor = if (state.canGoPrev) AppTheme.colors.ink else AppTheme.colors.line,
                enabled = state.canGoPrev,
            )
            Text(text = state.title, typography = AppTheme.type.figureMedium.ExtraBold, color = AppTheme.colors.ink)
            RoundIconButton(
                icon = AppIcons.ChevronRight.described(stringResource(Res.string.integrations_leave_out_next)),
                onClick = { onAction(LeaveOutAction.NextMonthTapped) },
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
                    val day = DateCopy.longMonthDay(it.date)
                    MonthGridCell(
                        date = it.date,
                        contentDescription = if (it.leftOut) stringResource(Res.string.integrations_leave_out_day_left_out, day) else day,
                        selected = it.leftOut,
                        isToday = it.isToday,
                        enabled = it.enabled,
                    )
                }
            },
            onDayClick = { onAction(LeaveOutAction.DayTapped(it)) },
        )
        FlatButton(
            text = stringResource(Res.string.integrations_leave_out_done),
            onClick = { onAction(LeaveOutAction.DoneTapped) },
            tone = FlatButtonTone.Accent,
            centered = true,
            minHeight = FlatButtonDefaults.TallMinHeight,
            typography = AppTheme.type.button,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Preview
@Composable
private fun IntegrationsLeaveOutPreview() {
    SettingsPreview { LeaveOutSheetContent(IntegrationsFixtures.leaveOutState(), onAction = {}) }
}

@Preview
@Composable
private fun IntegrationsLeaveOutDarkPreview() {
    SettingsPreview(dark = true) { LeaveOutSheetContent(IntegrationsFixtures.leaveOutState(), onAction = {}) }
}
