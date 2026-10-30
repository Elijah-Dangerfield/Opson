package com.opson.libraries.diary

import com.opson.libraries.nutrition.BodySex
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Patterns in someone's own log that research links with a hard time around food, read on the
 * phone and never sent anywhere (`docs/research/ed-safety-review.md`, "Pattern check"). What it
 * finds only decides whether Today offers a kind note with ways to get support. It never names
 * a signal or a number to the person, and nothing here is stored.
 *
 * A day only counts when Opson can read it fairly: tracked, in the past or wrapped up, and with
 * an amount on everything in it. A forgotten lunch is not restriction, so a low day also has to
 * look complete: wrapped up, or food in at least [COMPLETE_MEALS] meals.
 *
 * Strong signals show the note on their own; supporting ones need a second.
 *
 * TODO(clinician): every threshold in this file was picked by an engineer from the literature and
 * from what MyFitnessPal publishes, and must be reviewed before 1.0.
 */
object HardStretch {

    /** The days most checks look back over, today included. */
    const val WINDOW_DAYS: Int = 14

    /** How far back someone's usual day ([baselineKcal]) and their weight trend are read. */
    const val BASELINE_DAYS: Int = 28

    /** Meals with food in them that make an unwrapped day look complete. */
    const val COMPLETE_MEALS: Int = 3

    /**
     * Under this a complete day is very low. MyFitnessPal's own "eating too little" warning when a
     * diary is completed starts at the same lines (under 1,000 kcal for women, 1,200 for men).
     */
    const val VERY_LOW_FEMALE_KCAL: Int = 1000
    const val VERY_LOW_MALE_KCAL: Int = 1200

    /** Very low days in [WINDOW_DAYS] that make [StretchSignal.LowDays]. */
    const val LOW_DAYS: Int = 4

    /** Complete days needed before someone's usual day means anything. */
    const val BASELINE_MIN_DAYS: Int = 7

    /** A day this far over someone's usual is big; this far under, with the floor, is low. */
    const val BIG_DAY_SHARE: Double = 1.5
    const val LOW_DAY_SHARE: Double = 0.6

    /** A big day and a low one this close together are a swing. */
    const val SWING_GAP_DAYS: Int = 2

    /** Big days with a low day beside them, in [SWING_WINDOW_DAYS], that make [StretchSignal.Swings]. */
    const val SWINGS: Int = 2
    const val SWING_WINDOW_DAYS: Int = 21

    /** Weigh-ins this far apart are separate (a scale that sends two readings is one). */
    val WEIGH_IN_GAP = 1.hours

    /** Separate weigh-ins in one day that make it a day of many. */
    const val WEIGH_INS_A_DAY: Int = 3

    /** Days of many weigh-ins in [WINDOW_DAYS] that make [StretchSignal.ManyWeighIns]. */
    const val MANY_WEIGH_IN_DAYS: Int = 3

    /**
     * Loss faster than this share of body weight a week, over at least [FAST_LOSS_SPAN_DAYS], is
     * [StretchSignal.FastLoss]. GoalSafety's healthy-pace ceiling is the same 1%; the span keeps the
     * water lost in a diet's first week or two from counting.
     */
    const val FAST_LOSS_SHARE_A_WEEK: Double = 0.01
    const val FAST_LOSS_SPAN_DAYS: Int = 21
    const val FAST_LOSS_MIN_WEIGH_INS: Int = 4

    /** Food logged from [LATE_FROM] to [LATE_UNTIL] on a big day counts as late. */
    val LATE_FROM: LocalTime = LocalTime(22, 30)
    val LATE_UNTIL: LocalTime = LocalTime(4, 0)

    /** Big days with food logged late, in [WINDOW_DAYS], that make [StretchSignal.LateAfterBigDays]. */
    const val LATE_BIG_DAYS: Int = 2

    /** Opens of "Why not cut back?" in [WINDOW_DAYS] that make [StretchSignal.CutBackLooks]. */
    const val CUT_BACK_LOOKS: Int = 3

    /** Days wrapped up as Hard in [WINDOW_DAYS] that make [StretchSignal.HardDays]. */
    const val HARD_DAYS: Int = 4

    /** Supporting signals needed when no strong one holds. */
    const val SUPPORTING_NEEDED: Int = 2

    fun veryLowKcal(sex: BodySex?): Int = when (sex) {
        BodySex.Male -> VERY_LOW_MALE_KCAL
        BodySex.Female -> VERY_LOW_FEMALE_KCAL
        BodySex.Unsaid, null -> (VERY_LOW_FEMALE_KCAL + VERY_LOW_MALE_KCAL) / 2
    }

    fun check(input: StretchInput): StretchResult {
        val readable = input.days
            .filter { it.date > input.today.minus(DatePeriod(days = BASELINE_DAYS)) && it.date <= input.today }
            .mapNotNull { readDay(it, input) }
            .sortedBy { it.date }
        val baseline = baselineKcal(readable)
        val floor = veryLowKcal(input.sex)
        val signals = buildSet {
            if (lowDays(readable, input.today, floor) >= LOW_DAYS) add(StretchSignal.LowDays)
            if (baseline != null && swings(readable, input.today, floor, baseline) >= SWINGS) add(StretchSignal.Swings)
            if (manyWeighInDays(input) >= MANY_WEIGH_IN_DAYS) add(StretchSignal.ManyWeighIns)
            if (isFastLoss(input)) add(StretchSignal.FastLoss)
            if (baseline != null && lateBigDays(readable, input.today, baseline) >= LATE_BIG_DAYS) add(StretchSignal.LateAfterBigDays)
            if (input.cutBackLooks.count { it.inWindow(input) } >= CUT_BACK_LOOKS) add(StretchSignal.CutBackLooks)
            if (readable.count { it.hard && it.date.inWindow(input.today, WINDOW_DAYS) } >= HARD_DAYS) add(StretchSignal.HardDays)
        }
        return StretchResult(signals)
    }

    /** The median of complete days with an amount, or null with fewer than [BASELINE_MIN_DAYS] of them. */
    internal fun baselineKcal(days: List<ReadDay>): Double? {
        val complete = days.filter { it.complete }.mapNotNull { it.kcal }.sorted()
        if (complete.size < BASELINE_MIN_DAYS) return null
        val middle = complete.size / 2
        return if (complete.size % 2 == 1) complete[middle] else (complete[middle - 1] + complete[middle]) / 2
    }

    private fun lowDays(days: List<ReadDay>, today: LocalDate, floor: Int): Int =
        days.count { it.date.inWindow(today, WINDOW_DAYS) && it.complete && (it.kcal ?: Double.MAX_VALUE) < floor }

    /** Big days in [SWING_WINDOW_DAYS] with a low day within [SWING_GAP_DAYS] of them, before or after. */
    private fun swings(days: List<ReadDay>, today: LocalDate, floor: Int, baseline: Double): Int {
        fun isLow(day: ReadDay) = day.complete && day.kcal != null && (day.kcal < floor || day.kcal < baseline * LOW_DAY_SHARE)
        val lows = days.filter(::isLow).map { it.date }
        return days.count { day ->
            day.date.inWindow(today, SWING_WINDOW_DAYS) && isBig(day, baseline) &&
                lows.any { abs((it.toEpochDays() - day.date.toEpochDays()).toInt()) in 1..SWING_GAP_DAYS }
        }
    }

    private fun isBig(day: ReadDay, baseline: Double): Boolean = (day.kcal ?: 0.0) > baseline * BIG_DAY_SHARE

    private fun lateBigDays(days: List<ReadDay>, today: LocalDate, baseline: Double): Int =
        days.count { it.date.inWindow(today, WINDOW_DAYS) && it.loggedLate && isBig(it, baseline) }

    private fun manyWeighInDays(input: StretchInput): Int =
        input.weighIns
            .groupBy { it.measuredAt.toLocalDateTime(input.timeZone).date }
            .filterKeys { it.inWindow(input.today, WINDOW_DAYS) }
            .values
            .count { separateWeighIns(it.map(WeighIn::measuredAt)) >= WEIGH_INS_A_DAY }

    private fun separateWeighIns(times: List<Instant>): Int {
        var count = 0
        var last: Instant? = null
        for (at in times.sorted()) {
            val previous = last
            if (previous == null || at - previous >= WEIGH_IN_GAP) {
                count++
                last = at
            }
        }
        return count
    }

    /** A least-squares line through the last [BASELINE_DAYS] of weigh-ins, falling faster than the safe pace. */
    private fun isFastLoss(input: StretchInput): Boolean {
        val points = input.weighIns
            .map { it.measuredAt.toLocalDateTime(input.timeZone).date to it.kg }
            .filter { (date, kg) -> kg > 0 && date.inWindow(input.today, BASELINE_DAYS) }
        if (points.size < FAST_LOSS_MIN_WEIGH_INS) return false
        val xs = points.map { it.first.toEpochDays().toDouble() }
        if (xs.max() - xs.min() < FAST_LOSS_SPAN_DAYS) return false
        val ys = points.map { it.second }
        val meanX = xs.average()
        val meanY = ys.average()
        val spread = xs.sumOf { (it - meanX) * (it - meanX) }
        if (spread == 0.0) return false
        val kgPerDay = xs.indices.sumOf { (xs[it] - meanX) * (ys[it] - meanY) } / spread
        return -kgPerDay * DAYS_A_WEEK > meanY * FAST_LOSS_SHARE_A_WEEK
    }

    /** What one day says, or null when it can't be read fairly: untracked, in the future, nothing logged. */
    private fun readDay(day: Day, input: StretchInput): ReadDay? {
        if (day.untracked || day.date > input.today) return null
        if (!day.hasLogged && day.feeling == null) return null
        val past = day.date < input.today || day.closedByUser
        val kcal = day.totals().kcal.takeIf { it.unknownCount == 0 && it.knownCount > 0 }?.known
        val complete = past && kcal != null && (day.closedByUser || day.doneMealCount >= COMPLETE_MEALS)
        val loggedLate = day.allItems.any { item -> isLate(item.loggedAt, day.date, input.timeZone) }
        return ReadDay(
            date = day.date,
            kcal = kcal.takeIf { past },
            complete = complete,
            hard = day.feeling == DayFeeling.Hard,
            loggedLate = loggedLate,
        )
    }

    /** Logged at night on [date], or in the small hours after it. */
    private fun isLate(at: Instant, date: LocalDate, timeZone: TimeZone): Boolean {
        val local = at.toLocalDateTime(timeZone)
        return when (local.date) {
            date -> local.time >= LATE_FROM
            date.plus(DatePeriod(days = 1)) -> local.time < LATE_UNTIL
            else -> false
        }
    }

    private fun LocalDate.inWindow(today: LocalDate, days: Int): Boolean =
        this <= today && this > today.minus(DatePeriod(days = days))

    private fun Instant.inWindow(input: StretchInput): Boolean =
        toLocalDateTime(input.timeZone).date.inWindow(input.today, WINDOW_DAYS)

    private const val DAYS_A_WEEK = 7
}

/**
 * Everything [HardStretch.check] reads: the last [HardStretch.BASELINE_DAYS] of [days] and
 * [weighIns], when "Why not cut back?" was opened, and the sex the floors depend on.
 */
data class StretchInput(
    val today: LocalDate,
    val timeZone: TimeZone,
    val days: List<Day>,
    val weighIns: List<WeighIn> = emptyList(),
    val cutBackLooks: List<Instant> = emptyList(),
    val sex: BodySex? = null,
)

/** One day as the check reads it. [kcal] is null while the day is still going. */
internal data class ReadDay(
    val date: LocalDate,
    val kcal: Double?,
    val complete: Boolean,
    val hard: Boolean,
    val loggedLate: Boolean,
)

enum class StretchSignal(val strong: Boolean) {
    /** Several complete days under the very-low line. */
    LowDays(strong = true),

    /** Big days next to very low ones, more than once: the restrict and binge cycle. */
    Swings(strong = true),

    /** Weighing in several times a day, on several days. */
    ManyWeighIns(strong = true),

    /** Weight falling faster than the healthy pace, for weeks. */
    FastLoss(strong = true),

    /** Food logged late at night on big days. */
    LateAfterBigDays(strong = false),

    /** "Why not cut back?" opened again and again. */
    CutBackLooks(strong = false),

    /** Days wrapped up as Hard. */
    HardDays(strong = false),
}

data class StretchResult(val signals: Set<StretchSignal>) {
    /** Whether Today may offer the note: one strong signal, or [HardStretch.SUPPORTING_NEEDED] supporting ones. */
    val shows: Boolean
        get() = signals.any { it.strong } || signals.count { !it.strong } >= HardStretch.SUPPORTING_NEEDED
}
