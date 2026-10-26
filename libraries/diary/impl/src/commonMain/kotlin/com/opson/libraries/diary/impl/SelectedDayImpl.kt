package com.opson.libraries.diary.impl

import com.opson.libraries.diary.SelectedDay
import com.opson.libraries.flowroutines.AppCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class SelectedDayImpl(
    private val calendar: LocalCalendar,
    private val clock: Clock,
    appScope: AppCoroutineScope,
) : SelectedDay {

    private val state = MutableStateFlow(calendar.today())

    override val date: StateFlow<LocalDate> = state.asStateFlow()

    override fun select(day: LocalDate) {
        state.value = day
    }

    init {
        appScope.launch {
            var today = calendar.today()
            while (true) {
                val midnight = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(calendar.timeZone())
                // Waits in steps of at most a minute: a coroutine delay does not
                // count time the phone spent asleep, so one long delay to
                // midnight could wake hours late.
                delay((midnight - clock.now()).coerceIn(1.milliseconds, 1.minutes))
                val now = calendar.today()
                if (now != today) {
                    state.compareAndSet(today, now)
                    today = now
                }
            }
        }
    }
}
