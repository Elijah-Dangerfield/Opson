package com.opson.features.trends.impl.recap

import com.opson.features.trends.impl.TrendsFixtures
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayMark
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.RecapKind
import com.opson.libraries.diary.RecapPeriod
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.datetime.LocalDate

/**
 * Recaps for the previews, the builder tests and the leak test, from [TrendsFixtures]' history:
 * the week of Oct 19 to 25 seen on Monday the 26th, with Thursday empty and Wednesday marked
 * skipped, and September for a month. Nothing here builds a `Display`: every number goes through
 * the real builder and the policy the caller passes.
 */
object RecapFixtures {
    /** The Monday after the fixtures' week. */
    val today: LocalDate = LocalDate(2026, 10, 26)

    val week = RecapPeriod(RecapKind.Week, LocalDate(2026, 10, 19))
    val month = RecapPeriod(RecapKind.Month, LocalDate(2026, 9, 1))

    /** The fixtures' week as it stands on Sunday the 25th: still going. */
    val thisWeek = RecapPeriod(RecapKind.Week, LocalDate(2026, 10, 19))

    /** Wednesday the 21st: marked skipped, so the recap has one of each kind of day. */
    private val skipped = LocalDate(2026, 10, 21)

    fun days(): List<Day> = TrendsFixtures.days().map { if (it.date == skipped) it.copy(untracked = true) else it }

    private fun marks(days: List<Day>): List<DayMark> = days.map {
        DayMark(it.date, logged = it.hasLogged, untracked = it.untracked, checkedIn = it.checkIn != null)
    }

    /**
     * A recap with every section that can have something in it: Burned known each day
     * (`release.goalsAndEnergy`), check-ins, celebrations on. [policy] sets the mode; [hard] is
     * someone who said numbers have been hard.
     */
    fun inputs(
        policy: VisibilityPolicy? = null,
        period: RecapPeriod = week,
        today: LocalDate = this.today,
        hard: Boolean = false,
        deficit: Boolean = false,
        days: List<Day> = days(),
    ): RecapInputs {
        val base = Settings(numbers = policy?.settings ?: NumbersSettings(), onboarding = OnboardingAnswers(hard = hard))
        val settings = if (deficit) base.copy(targets = base.targets.copy(style = TargetStyle.Deficit(kcal = DEFICIT_KCAL, startingKcal = DEFICIT_KCAL))) else base
        return RecapInputs(
            today = today,
            period = period,
            settings = settings,
            days = days,
            weighIns = TrendsFixtures.weighIns(),
            timeZone = TrendsFixtures.timeZone,
            burnedByDay = TrendsFixtures.burned(),
            checkInOn = true,
            marks = marks(days),
            celebrations = settings.celebrationStyle,
        )
    }

    fun state(policy: VisibilityPolicy, period: RecapPeriod = week, hard: Boolean = false, deficit: Boolean = false): RecapState =
        RecapStateBuilder.build(inputs(policy, period, hard = hard, deficit = deficit), policy)

    /** The fixtures' week on its last day, at noon: so far. */
    fun inProgress(policy: VisibilityPolicy): RecapState =
        RecapStateBuilder.build(inputs(policy, thisWeek, today = TrendsFixtures.today), policy)

    /** A week with nothing logged in it. */
    fun empty(policy: VisibilityPolicy): RecapState =
        RecapStateBuilder.build(inputs(policy, days = days().filter { it.date < week.start }), policy)

    private const val DEFICIT_KCAL = 500
}
