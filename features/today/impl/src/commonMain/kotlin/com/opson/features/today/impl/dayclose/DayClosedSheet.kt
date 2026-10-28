package com.opson.features.today.impl.dayclose

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.ui.DayClosedNotice
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.system.AppTheme
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject

/*
 * Where a write a closed day refused lands (`DayClosedRoute`): "Today is closed. Reopen it to add
 * more." with Reopen, which opens the day again exactly as Day close's Reopen does, and Not now.
 */

data class DayClosedState(val isToday: Boolean)

sealed interface DayClosedAction {
    data object ReopenTapped : DayClosedAction
    data object NotNowTapped : DayClosedAction
}

sealed interface DayClosedEvent {
    data object Dismiss : DayClosedEvent
}

@Inject
class DayClosedViewModel(
    @Assisted private val day: LocalDate,
    private val diary: DiaryRepository,
    calendar: LocalCalendar,
) : SEAViewModel<DayClosedState, DayClosedEvent, DayClosedAction>(initialStateArg = DayClosedState(isToday = day == calendar.today())) {

    override suspend fun handleAction(action: DayClosedAction) {
        when (action) {
            DayClosedAction.ReopenTapped -> {
                diary.reopenDay(day)
                sendEvent(DayClosedEvent.Dismiss)
            }
            DayClosedAction.NotNowTapped -> sendEvent(DayClosedEvent.Dismiss)
        }
    }
}

@Composable
fun DayClosedSheetContent(state: DayClosedState, onAction: (DayClosedAction) -> Unit, modifier: Modifier = Modifier) {
    DayClosedNotice(
        isToday = state.isToday,
        onReopen = { onAction(DayClosedAction.ReopenTapped) },
        onNotNow = { onAction(DayClosedAction.NotNowTapped) },
        modifier = modifier.padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
    )
}
