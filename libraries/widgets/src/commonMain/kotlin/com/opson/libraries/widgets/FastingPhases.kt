package com.opson.libraries.widgets

import com.opson.libraries.diary.Fast
import com.opson.libraries.diary.FastingSchedule
import com.opson.libraries.nutrition.Fasting
import com.opson.libraries.nutrition.FastingPlan
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** What the fasting widget is built from: the plan, the latest fast, and the last food logged. */
data class FastingInputs(
    val plan: FastingPlan,
    val latest: Fast?,
    val lastLoggedAt: Instant?,
    val now: Instant,
    val zone: TimeZone,
)

/**
 * The fasting timer as phases, from now to a day and a half ahead, so the widget can step from a
 * fast to the eating window and on to the next fast with the app closed.
 *
 * Now is [FastingSchedule.status]'s answer, with one difference: after the window closes, a fast
 * that is due but not written yet ([FastingSchedule.due]) already shows as running. The app writes
 * it the next time it runs, starting at the same close, so the timer reads the same either way.
 * Later phases assume the plan runs as set: each window close starts a fast of the plan's hours.
 * Food logged after a close (which keeps a fast from starting) is only known once it is logged,
 * and that write rebuilds the snapshot.
 */
object FastingPhases {
    private val HORIZON = 36.hours

    /** A day and a half of phases changes at most about six times; the cap only guards a bad clock. */
    private const val MAX_PHASES = 8

    private sealed interface State {
        val endsAt: Instant

        data class Fasting(val since: Instant, val until: Instant) : State {
            override val endsAt: Instant get() = until
        }

        data class Window(val until: Instant) : State {
            override val endsAt: Instant get() = until
        }

        /** Between fasts. [endsAt] is when the window opens; [nextFast] is when the next fast starts. */
        data class Done(override val endsAt: Instant, val nextFast: Instant) : State
    }

    fun build(inputs: FastingInputs, copy: WidgetCopy.FastingCopy): List<WidgetFastingPhase> {
        val phases = mutableListOf<WidgetFastingPhase>()
        var from = inputs.now
        var state = initial(inputs)
        while (phases.size < MAX_PHASES) {
            // The first phase runs from whenever it began; 0 keeps the snapshot equal from one build to
            // the next until something really moves.
            phases += phase(state, from, copy).let { if (phases.isEmpty()) it.copy(fromMs = 0L) else it }
            if (state.endsAt > inputs.now + HORIZON || state.endsAt <= from) break
            from = state.endsAt
            state = after(state, inputs.plan, inputs.zone)
        }
        return phases
    }

    private fun initial(inputs: FastingInputs): State {
        val (plan, latest, lastLoggedAt, now, zone) = inputs
        if (latest != null && latest.isRunningAt(now)) return State.Fasting(latest.startedAt, latest.endsBy)
        if (FastingSchedule.isWindowOpen(plan, now, zone)) return State.Window(closeOn(now.date(zone), plan, zone))
        val due = FastingSchedule.due(plan, now, zone, latest, lastLoggedAt)
        if (due != null && due.isRunningAt(now)) return State.Fasting(due.startedAt, due.endsBy)
        return done(now, plan, zone)
    }

    private fun after(state: State, plan: FastingPlan, zone: TimeZone): State {
        val at = state.endsAt
        return when (state) {
            is State.Fasting ->
                if (FastingSchedule.isWindowOpen(plan, at, zone)) State.Window(closeOn(at.date(zone), plan, zone)) else done(at, plan, zone)
            is State.Window -> State.Fasting(at, at + plan.fastingHours.coerceIn(1, Fasting.MAX_FASTING_HOURS).hours)
            is State.Done -> State.Window(closeOn(at.date(zone), plan, zone))
        }
    }

    private fun done(at: Instant, plan: FastingPlan, zone: TimeZone): State.Done =
        State.Done(endsAt = next(at, plan.opens, zone), nextFast = next(at, plan.closes, zone))

    private fun phase(state: State, from: Instant, copy: WidgetCopy.FastingCopy): WidgetFastingPhase = when (state) {
        is State.Fasting -> WidgetFastingPhase(
            fromMs = from.toEpochMilliseconds(),
            kind = WidgetFastingPhase.FASTING,
            title = copy.now,
            caption = copy.ends,
            untilMs = state.until.toEpochMilliseconds(),
            sinceMs = state.since.toEpochMilliseconds(),
        )
        is State.Window -> WidgetFastingPhase(
            fromMs = from.toEpochMilliseconds(),
            kind = WidgetFastingPhase.WINDOW,
            title = copy.window,
            caption = copy.until,
            untilMs = state.until.toEpochMilliseconds(),
        )
        is State.Done -> WidgetFastingPhase(
            fromMs = from.toEpochMilliseconds(),
            kind = WidgetFastingPhase.DONE,
            title = copy.done,
            caption = copy.next,
            untilMs = state.nextFast.toEpochMilliseconds(),
        )
    }

    private fun closeOn(date: LocalDate, plan: FastingPlan, zone: TimeZone): Instant = LocalDateTime(date, plan.closes).toInstant(zone)

    /** The first [time] on the clock strictly after [at]. */
    private fun next(at: Instant, time: LocalTime, zone: TimeZone): Instant {
        val date = at.date(zone)
        val today = LocalDateTime(date, time).toInstant(zone)
        return if (today > at) today else LocalDateTime(date.plus(DatePeriod(days = 1)), time).toInstant(zone)
    }

    private fun Instant.date(zone: TimeZone): LocalDate = toLocalDateTime(zone).date
}
