package com.opson.features.today.impl.celebration

import com.opson.libraries.diary.CelebrationStyle
import com.opson.libraries.diary.Milestone
import com.opson.libraries.diary.MilestoneFamily
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.celebrate_check_ins_30
import opson.libraries.resources.generated.resources.celebrate_check_ins_30_line
import opson.libraries.resources.generated.resources.celebrate_check_ins_7
import opson.libraries.resources.generated.resources.celebrate_check_ins_7_line
import opson.libraries.resources.generated.resources.celebrate_days_100
import opson.libraries.resources.generated.resources.celebrate_days_100_line
import opson.libraries.resources.generated.resources.celebrate_days_3
import opson.libraries.resources.generated.resources.celebrate_days_30
import opson.libraries.resources.generated.resources.celebrate_days_30_line
import opson.libraries.resources.generated.resources.celebrate_days_3_line
import opson.libraries.resources.generated.resources.celebrate_first_check_in
import opson.libraries.resources.generated.resources.celebrate_first_check_in_line
import opson.libraries.resources.generated.resources.celebrate_first_wrap_up
import opson.libraries.resources.generated.resources.celebrate_first_wrap_up_line
import opson.libraries.resources.generated.resources.celebrate_gentle_check_in
import opson.libraries.resources.generated.resources.celebrate_gentle_check_ins_30_line
import opson.libraries.resources.generated.resources.celebrate_gentle_check_ins_7_line
import opson.libraries.resources.generated.resources.celebrate_gentle_days_100_line
import opson.libraries.resources.generated.resources.celebrate_gentle_days_30_line
import opson.libraries.resources.generated.resources.celebrate_gentle_days_3_line
import opson.libraries.resources.generated.resources.celebrate_gentle_first_check_in_line
import opson.libraries.resources.generated.resources.celebrate_gentle_months_6_line
import opson.libraries.resources.generated.resources.celebrate_gentle_streak
import opson.libraries.resources.generated.resources.celebrate_gentle_week_1_line
import opson.libraries.resources.generated.resources.celebrate_gentle_weeks_2_line
import opson.libraries.resources.generated.resources.celebrate_gentle_wrap_up
import opson.libraries.resources.generated.resources.celebrate_gentle_wrap_up_line
import opson.libraries.resources.generated.resources.celebrate_gentle_year_1_line
import opson.libraries.resources.generated.resources.celebrate_months_6
import opson.libraries.resources.generated.resources.celebrate_months_6_line
import opson.libraries.resources.generated.resources.celebrate_next_days_100
import opson.libraries.resources.generated.resources.celebrate_next_days_3
import opson.libraries.resources.generated.resources.celebrate_next_days_30
import opson.libraries.resources.generated.resources.celebrate_next_months_6
import opson.libraries.resources.generated.resources.celebrate_next_week_1
import opson.libraries.resources.generated.resources.celebrate_next_weeks_2
import opson.libraries.resources.generated.resources.celebrate_next_year_1
import opson.libraries.resources.generated.resources.celebrate_week_1
import opson.libraries.resources.generated.resources.celebrate_week_1_line
import opson.libraries.resources.generated.resources.celebrate_weeks_2
import opson.libraries.resources.generated.resources.celebrate_weeks_2_line
import opson.libraries.resources.generated.resources.celebrate_year_1
import opson.libraries.resources.generated.resources.celebrate_year_1_line
import org.jetbrains.compose.resources.StringResource

/** A celebration's words: the big title and the one line under it. */
data class CelebrationCopy(val title: StringResource, val line: StringResource, val inARow: Boolean)

/**
 * The words for [milestone] in [style]. Counts says the milestone ("1 week", with "in a row" under a
 * run); Gentle says something kind and no count at all.
 */
fun celebrationCopy(milestone: Milestone, style: CelebrationStyle): CelebrationCopy =
    if (style == CelebrationStyle.Gentle) gentle(milestone) else counts(milestone)

private fun counts(milestone: Milestone): CelebrationCopy {
    val inARow = milestone.family == MilestoneFamily.Streak
    val (title, line) = when (milestone) {
        Milestone.FirstWrapUp -> Res.string.celebrate_first_wrap_up to Res.string.celebrate_first_wrap_up_line
        Milestone.Days3 -> Res.string.celebrate_days_3 to Res.string.celebrate_days_3_line
        Milestone.Week1 -> Res.string.celebrate_week_1 to Res.string.celebrate_week_1_line
        Milestone.Weeks2 -> Res.string.celebrate_weeks_2 to Res.string.celebrate_weeks_2_line
        Milestone.Days30 -> Res.string.celebrate_days_30 to Res.string.celebrate_days_30_line
        Milestone.Days100 -> Res.string.celebrate_days_100 to Res.string.celebrate_days_100_line
        Milestone.Months6 -> Res.string.celebrate_months_6 to Res.string.celebrate_months_6_line
        Milestone.Year1 -> Res.string.celebrate_year_1 to Res.string.celebrate_year_1_line
        Milestone.FirstCheckIn -> Res.string.celebrate_first_check_in to Res.string.celebrate_first_check_in_line
        Milestone.CheckIns7 -> Res.string.celebrate_check_ins_7 to Res.string.celebrate_check_ins_7_line
        Milestone.CheckIns30 -> Res.string.celebrate_check_ins_30 to Res.string.celebrate_check_ins_30_line
    }
    return CelebrationCopy(title, line, inARow)
}

private fun gentle(milestone: Milestone): CelebrationCopy {
    val title = when (milestone.family) {
        MilestoneFamily.WrapUp -> Res.string.celebrate_gentle_wrap_up
        MilestoneFamily.Streak -> Res.string.celebrate_gentle_streak
        MilestoneFamily.CheckIn -> Res.string.celebrate_gentle_check_in
    }
    val line = when (milestone) {
        Milestone.FirstWrapUp -> Res.string.celebrate_gentle_wrap_up_line
        Milestone.Days3 -> Res.string.celebrate_gentle_days_3_line
        Milestone.Week1 -> Res.string.celebrate_gentle_week_1_line
        Milestone.Weeks2 -> Res.string.celebrate_gentle_weeks_2_line
        Milestone.Days30 -> Res.string.celebrate_gentle_days_30_line
        Milestone.Days100 -> Res.string.celebrate_gentle_days_100_line
        Milestone.Months6 -> Res.string.celebrate_gentle_months_6_line
        Milestone.Year1 -> Res.string.celebrate_gentle_year_1_line
        Milestone.FirstCheckIn -> Res.string.celebrate_gentle_first_check_in_line
        Milestone.CheckIns7 -> Res.string.celebrate_gentle_check_ins_7_line
        Milestone.CheckIns30 -> Res.string.celebrate_gentle_check_ins_30_line
    }
    return CelebrationCopy(title, line, inARow = false)
}

/** A milestone's name as a badge says it: the same words as its celebration's title. */
val Milestone.badgeLabel: StringResource get() = counts(this).title

/** What a streak milestone is called in "3 more to a week". */
val Milestone.nextName: StringResource
    get() = when (this) {
        Milestone.Days3 -> Res.string.celebrate_next_days_3
        Milestone.Week1 -> Res.string.celebrate_next_week_1
        Milestone.Weeks2 -> Res.string.celebrate_next_weeks_2
        Milestone.Days30 -> Res.string.celebrate_next_days_30
        Milestone.Days100 -> Res.string.celebrate_next_days_100
        Milestone.Months6 -> Res.string.celebrate_next_months_6
        else -> Res.string.celebrate_next_year_1
    }
