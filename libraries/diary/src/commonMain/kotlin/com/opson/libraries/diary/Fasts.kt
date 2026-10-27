package com.opson.libraries.diary

import com.opson.libraries.nutrition.Fasting
import com.opson.libraries.nutrition.FastingPlan
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/*
 * Fasting (docs/plans/screens/fasting.md). The safety rules live here, as code, so every screen
 * that shows fasting asks the same questions: who may see it ([FastingAccess]), when a fast
 * starts and ends ([FastingSchedule]), and the 16 hour cap ([Fast.endsBy]).
 *
 * There are no streaks and no records on purpose. Nothing here counts or compares fasts; only
 * the latest one is ever read.
 */

/**
 * One fast. [day] is the local date it began, and the key everywhere: a fast starts when the
 * eating window closes, so there is at most one per evening, and two devices that both start it
 * write the same row.
 */
data class Fast(
    val day: LocalDate,
    val startedAt: Instant,
    /** When someone ended it (End fast, logging food, turning the timer off); null until then. */
    val endedAt: Instant? = null,
    /** The plan's fasting hours when it started. Never more than [Fasting.MAX_FASTING_HOURS]. */
    val goalHours: Int? = null,
) {
    /**
     * When the fast is over even if nobody ends it: its goal, and never past 16 hours. Derived,
     * not stored, so reaching the goal never races a real end on another device.
     */
    val endsBy: Instant
        get() = startedAt + (goalHours ?: Fasting.MAX_FASTING_HOURS).coerceIn(1, Fasting.MAX_FASTING_HOURS).hours

    fun isRunningAt(now: Instant): Boolean = endedAt == null && now >= startedAt && now < endsBy

    /** When it ended as of [now]: its recorded end, or [endsBy] once that has passed. Null while it runs. */
    fun endedAt(now: Instant): Instant? = when {
        endedAt != null -> minOf(endedAt, endsBy)
        now >= endsBy -> endsBy
        else -> null
    }
}

/**
 * Whether fasting exists for this person at all.
 *
 * TODO(clinician): confirm which modes allow fasting, and that agreeing numbers have been hard
 * shuts it out for good, whatever mode is picked later.
 */
enum class FastingAccess {
    /** Not rendered anywhere: the release flag is off, or the person agreed numbers have been hard. */
    Hidden,

    /** Not part of the current mode (only Full numbers offers it). Not rendered either. */
    NotInThisMode,

    Offered,
    ;

    companion object {
        fun of(settings: Settings, released: Boolean): FastingAccess = when {
            // The statement wins over every mode, Full numbers included.
            !released || settings.onboarding.hard -> Hidden
            !Fasting.allowed(settings.numbers.preset) -> NotInThisMode
            else -> Offered
        }
    }
}

/** The timer is on and fasting is offered: the only state in which a fast may start. */
fun Settings.fastingTimerOn(released: Boolean): Boolean = fasting && FastingAccess.of(this, released) == FastingAccess.Offered

/** What the status card says. Times are local wall-clock times. */
sealed interface FastingStatus {
    /** A fast is running: [elapsed] since [since], never past the 16 hour cap. */
    data class Running(val since: LocalTime, val elapsed: Duration) : FastingStatus

    /** Inside the eating window, [until] it closes. */
    data class WindowOpen(val until: LocalTime) : FastingStatus

    /** After the window, and the evening's fast is over: ended [at], the next starts at [nextStart]. */
    data class Ended(val at: LocalTime, val nextStart: LocalTime) : FastingStatus

    /** After the window with no fast this evening (food was logged after it closed). */
    data class Resting(val nextStart: LocalTime) : FastingStatus
}

/** When a fast starts and what the status card says. Pure. */
object FastingSchedule {

    /** Inside [plan]'s eating window at [now]. Every plan opens in the morning and closes at night. */
    fun isWindowOpen(plan: FastingPlan, now: Instant, zone: TimeZone): Boolean {
        val time = now.toLocalDateTime(zone).time
        return time >= plan.opens && time < plan.closes
    }

    /** The most recent window close at or before [now]: its local date and the instant. */
    fun lastClose(plan: FastingPlan, now: Instant, zone: TimeZone): Pair<LocalDate, Instant> {
        val local = now.toLocalDateTime(zone)
        val day = if (local.time >= plan.closes) local.date else local.date.minus(1, DateTimeUnit.DAY)
        return day to LocalDateTime(day, plan.closes).toInstant(zone)
    }

    /**
     * The fast that should be running now and has not been written yet, or null. A fast starts
     * when the window closes; none starts while the window is open, for an evening that already
     * has one, or when food was logged after the window closed (logging food ends a fast, so it
     * also keeps one from starting).
     */
    fun due(plan: FastingPlan, now: Instant, zone: TimeZone, latest: Fast?, lastLoggedAt: Instant?): Fast? {
        if (isWindowOpen(plan, now, zone)) return null
        val (day, close) = lastClose(plan, now, zone)
        if (latest != null && latest.day >= day) return null
        if (lastLoggedAt != null && lastLoggedAt >= close) return null
        return Fast(day = day, startedAt = close, goalHours = plan.fastingHours)
    }

    fun status(plan: FastingPlan, now: Instant, zone: TimeZone, latest: Fast?): FastingStatus {
        if (latest != null && latest.isRunningAt(now)) {
            return FastingStatus.Running(
                since = latest.startedAt.toLocalDateTime(zone).time,
                elapsed = (now - latest.startedAt).coerceAtMost(latest.endsBy - latest.startedAt),
            )
        }
        if (isWindowOpen(plan, now, zone)) return FastingStatus.WindowOpen(until = plan.closes)
        val (day, _) = lastClose(plan, now, zone)
        val ended = latest?.takeIf { it.day >= day }?.endedAt(now)
        return if (ended != null) {
            FastingStatus.Ended(at = ended.toLocalDateTime(zone).time, nextStart = plan.closes)
        } else {
            FastingStatus.Resting(nextStart = plan.closes)
        }
    }
}

/**
 * The fasts on this device. Local-first like the diary: every write lands with its outbox row
 * and syncs later. Only the latest fast is read, because nothing compares fasts.
 */
interface FastingRepository {
    /** The most recent fast, running or not; null before the first. */
    fun observeLatest(): Flow<Fast?>

    /**
     * Starts [FastingSchedule.due] when fasting is offered and the timer is on. Idempotent, so
     * the screens that show the timer call it on every tick.
     */
    suspend fun startDue()

    /** "End fast". Nothing when no fast is running. */
    suspend fun end()

    /** The timer was switched off: a running fast ends with it. */
    suspend fun endForTimerOff()
}

/** Whether the `release.fasting` flag is on. Read each time, so a remote flip applies at once. */
fun interface FastingRelease {
    fun isOn(): Boolean
}

/**
 * The live timer's beat: now at once, then at each wall-clock minute. An interface so a view
 * model test can hold time still or step it; an endless delay loop would never let a test's
 * scheduler go idle.
 */
fun interface MinuteTicker {
    fun ticks(): Flow<Instant>
}

/** The real [MinuteTicker]'s flow: [Clock.now] at once, then at each wall-clock minute. */
fun minuteTicks(clock: Clock): Flow<Instant> = flow {
    while (true) {
        val now = clock.now()
        emit(now)
        val intoMinute = ((now.toEpochMilliseconds() % MINUTE_MS) + MINUTE_MS) % MINUTE_MS
        delay((MINUTE_MS - intoMinute).milliseconds)
    }
}

private const val MINUTE_MS = 60_000L
