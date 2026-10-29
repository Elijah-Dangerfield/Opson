package com.opson.libraries.diary

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.FlagConfigValue
import com.opson.libraries.config.QaConfigValue
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/*
 * Showing up: the streak, the milestones and Your progress (`release.celebrations`). What counts is
 * coming back to the app for the day, never what or how much was eaten, so nothing here reads an
 * amount, a goal or a deficit. The rules, and why they forgive, are in docs/decisions.md
 * (2026-10-29, "Celebrations count showing up").
 */

/**
 * `release.celebrations`: milestone celebrations, the streak on Today, Your progress and the line
 * after wrapping up a day, and the Celebrations rows in More. Here rather than in Today so More
 * can read it. Read when a page builds its inputs; nothing runs at launch.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
@ContributesBinding(AppScope::class, boundType = CelebrationsRelease::class)
class CelebrationsReleaseFlag(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap), CelebrationsRelease {
    override val name = "Celebrations"

    // A literal on purpose: ConfigManifestRegistryDriftTest reads declared paths from source.
    override val path = "release.celebrations"
    override val default = false
    override val description = "Milestone celebrations, the streak on Today, Your progress, and the Celebrations rows in More"

    override fun isOn(): Boolean = value
}

/** [CelebrationsReleaseFlag] as a page reads it, so a view model can default it to [Off] and its tests stay as they are. */
fun interface CelebrationsRelease {
    fun isOn(): Boolean

    companion object {
        val Off: CelebrationsRelease = CelebrationsRelease { false }
    }
}

/** How celebrations speak ([Settings.celebrationStyle]). */
enum class CelebrationStyle {
    /** "4 days in a row", the milestone in big type. */
    Counts,

    /** A kind line and no counts: the default after numbers have been hard. */
    Gentle,

    Off,
}

/**
 * What one day says about showing up, and nothing about what was eaten. [logged] is an item or an
 * "ate, no details"; [closed] is wrapped up with Wrap up the day; [checkedIn] has a check-in.
 */
data class DayMark(
    val date: LocalDate,
    val logged: Boolean = false,
    val closed: Boolean = false,
    val untracked: Boolean = false,
    val checkedIn: Boolean = false,
) {
    /** Came back for the day: any one of them, on a day not marked untracked. */
    val showedUp: Boolean get() = !untracked && (logged || closed || checkedIn)
}

/**
 * Every date with something on it, oldest first; a date with nothing on it is left out. A cheap
 * read: dates and flags, never the foods. Its own interface so the many `DiaryRepository` fakes
 * stay as they are.
 */
fun interface DayMarks {
    fun observe(): Flow<List<DayMark>>
}

/** The three kinds of milestone, each counted its own way ([ShowingUp]). */
enum class MilestoneFamily { WrapUp, Streak, CheckIn }

/**
 * A milestone: [threshold] days in a row for [MilestoneFamily.Streak], days with a check-in for
 * [MilestoneFamily.CheckIn], days wrapped up for [MilestoneFamily.WrapUp]. [wire] is the route
 * argument and the app events' `milestone`; never rename one.
 */
enum class Milestone(val wire: String, val family: MilestoneFamily, val threshold: Int) {
    FirstWrapUp("first_wrap_up", MilestoneFamily.WrapUp, 1),
    Days3("days_3", MilestoneFamily.Streak, 3),
    Week1("week_1", MilestoneFamily.Streak, 7),
    Weeks2("weeks_2", MilestoneFamily.Streak, 14),
    Days30("days_30", MilestoneFamily.Streak, 30),
    Days100("days_100", MilestoneFamily.Streak, 100),

    /** 26 weeks. */
    Months6("months_6", MilestoneFamily.Streak, 182),
    Year1("year_1", MilestoneFamily.Streak, 365),
    FirstCheckIn("first_check_in", MilestoneFamily.CheckIn, 1),
    CheckIns7("check_ins_7", MilestoneFamily.CheckIn, 7),
    CheckIns30("check_ins_30", MilestoneFamily.CheckIn, 30),
    ;

    companion object {
        fun fromWire(value: String?): Milestone? = entries.firstOrNull { it.wire == value }

        val streak: List<Milestone> = entries.filter { it.family == MilestoneFamily.Streak }
    }
}

/** How one day reads for the streak. */
enum class StreakDay {
    /** Showed up. Adds one to the run. */
    Counted,

    /** Marked untracked: a holiday, a break. As if the day were not there; it neither adds nor breaks. */
    Rest,

    /** Nothing, but the run goes on: one quiet day between two counted ones is forgiven. */
    Bridged,

    /** Nothing, and the run ended here or had already ended. */
    Missed,

    /** Today, with nothing on it yet. Never a miss while the day is still going. */
    Pending,
}

/**
 * Where someone stands. [current] counts the days showed up in the run still going (a bridged or
 * rest day adds nothing), [longest] the longest run ever. [reached] is the day each milestone was
 * first reached; a milestone, once reached, stays reached when a run ends. [recent] is the last
 * seven days, oldest first, ending today.
 */
data class Progress(
    val today: LocalDate,
    val current: Int = 0,
    val longest: Int = 0,
    val todayCounted: Boolean = false,
    val reached: Map<Milestone, LocalDate> = emptyMap(),
    val showedUpThisMonth: Int = 0,
    val checkIns: Int = 0,
    val recent: List<StreakDay> = emptyList(),
) {
    /** The next streak milestone the run is heading for, or null past a year. */
    val next: Milestone? get() = Milestone.streak.firstOrNull { it.threshold > current }

    /** Days showed up still to go to [next]. */
    val daysToNext: Int? get() = next?.let { it.threshold - current }

    val earned: List<Milestone> get() = Milestone.entries.filter { it in reached }

    /**
     * Milestones reached in the last [ShowingUp.FRESH_DAYS] days that are not in [celebrated], the
     * one to celebrate first. A run past a few milestones at once (a new phone with a long history,
     * a sync catching up) celebrates only the highest of them; [ShowingUp.coveredBy] says which
     * lower ones it covers. Never anything older: a celebration weeks late says nothing.
     */
    fun toCelebrate(celebrated: Set<Milestone>): Milestone? {
        val from = today.minus(ShowingUp.FRESH_DAYS - 1, DateTimeUnit.DAY)
        val fresh = reached.filter { (milestone, on) -> on >= from && milestone !in celebrated }.keys
        return fresh.maxWithOrNull(ShowingUp.celebrationOrder)
    }
}

/**
 * The streak and milestone rules, as one pure function of the marks and today's date. Dates are
 * the diary's own local dates, so a late-night log counts for the day it was logged on, wherever
 * the phone is now, and nothing here knows a time zone.
 *
 * - A day counts when someone showed up ([DayMark.showedUp]): logged anything, wrapped it up, or
 *   checked in. Any one is enough. Asking for "enough" logged would ask someone to eat a meal to
 *   keep a streak, which is the one thing this must never do.
 * - Untracked days are rests ([StreakDay.Rest]): they neither add nor break, however many there are.
 * - One quiet day between counted days is forgiven ([StreakDay.Bridged]); two in a row end the run.
 * - Today never breaks a run: until midnight it is [StreakDay.Pending].
 */
object ShowingUp {
    /** How long a reached milestone waits to be celebrated, today included. */
    const val FRESH_DAYS = 7

    /** Quiet days in a row that end a run. One is forgiven. */
    const val MISSES_TO_END = 2

    private const val RECENT_DAYS = 7

    /** Streaks first, highest first; then the first wrap-up; then check-ins, highest first. */
    val celebrationOrder: Comparator<Milestone> = compareBy<Milestone>(
        { if (it.family == MilestoneFamily.Streak) 2 else if (it.family == MilestoneFamily.WrapUp) 1 else 0 },
        { it.threshold },
    )

    /** What celebrating [shown] also covers: the lower milestones of its own family. */
    fun coveredBy(shown: Milestone): Set<Milestone> =
        Milestone.entries.filterTo(mutableSetOf()) { it.family == shown.family && it.threshold <= shown.threshold }

    fun compute(marks: List<DayMark>, today: LocalDate): Progress {
        val byDate = marks.filter { it.date <= today }.associateBy { it.date }
        val start = byDate.keys.minOrNull() ?: today
        val reached = mutableMapOf<Milestone, LocalDate>()
        val states = ArrayList<StreakDay>()
        var run = 0
        var longest = 0
        var misses = 0
        var wrapUps = 0
        var checkIns = 0
        var date = start
        while (date <= today) {
            val mark = byDate[date]
            if (mark?.closed == true && !mark.untracked) {
                wrapUps += 1
                reach(reached, MilestoneFamily.WrapUp, wrapUps, date)
            }
            if (mark?.checkedIn == true) {
                checkIns += 1
                reach(reached, MilestoneFamily.CheckIn, checkIns, date)
            }
            val state = when {
                mark?.untracked == true -> StreakDay.Rest
                mark?.showedUp == true -> StreakDay.Counted
                date == today -> StreakDay.Pending
                run == 0 -> StreakDay.Missed
                misses + 1 < MISSES_TO_END -> StreakDay.Bridged
                else -> {
                    // The run ends: the quiet day forgiven before this one was the start of the end.
                    val forgiven = states.indexOfLast { it != StreakDay.Rest }
                    if (forgiven >= 0 && states[forgiven] == StreakDay.Bridged) states[forgiven] = StreakDay.Missed
                    run = 0
                    StreakDay.Missed
                }
            }
            when (state) {
                StreakDay.Counted -> {
                    run += 1
                    misses = 0
                    longest = maxOf(longest, run)
                    reach(reached, MilestoneFamily.Streak, run, date)
                }
                StreakDay.Bridged, StreakDay.Missed -> misses += 1
                StreakDay.Rest, StreakDay.Pending -> Unit
            }
            states += state
            date = date.plus(1, DateTimeUnit.DAY)
        }
        val monthStart = LocalDate(today.year, today.month, 1)
        return Progress(
            today = today,
            current = run,
            longest = longest,
            todayCounted = byDate[today]?.showedUp == true,
            reached = reached,
            showedUpThisMonth = byDate.values.count { it.date >= monthStart && it.showedUp },
            checkIns = checkIns,
            // Days before the first mark had nothing on them, and read as quiet days.
            recent = (List(RECENT_DAYS) { StreakDay.Missed } + states).takeLast(RECENT_DAYS),
        )
    }

    private fun reach(reached: MutableMap<Milestone, LocalDate>, family: MilestoneFamily, count: Int, on: LocalDate) {
        Milestone.entries.firstOrNull { it.family == family && it.threshold == count }?.let { reached.getOrPut(it) { on } }
    }
}
