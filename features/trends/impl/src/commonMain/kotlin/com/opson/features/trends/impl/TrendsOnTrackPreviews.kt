package com.opson.features.trends.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.TrendsRange
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.ui.PreviewContent
import com.opson.system.AppTheme
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.ui.tooling.preview.Preview

// The on-track calendar, the running total, the check-ins' hunger and the day sheet, built by the
// real builders over TrendsFixtures with a 500 kcal deficit and Burned known every day.

private val full = TrendsFixtures.policy()

@Composable
private fun Cards(
    inputs: TrendsInputs = TrendsFixtures.deficitInputs(),
    policy: VisibilityPolicy = full,
    ui: TrendsUi = TrendsUi(),
    dark: Boolean = false,
    content: @Composable (TrendsState) -> Unit,
) = PreviewContent(darkTheme = dark) {
    Column(Modifier.padding(AppTheme.spacing.page), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section)) {
        content(TrendsStateBuilder.build(inputs, policy, ui))
    }
}

/** October against the deficit: on plan, a bit off and over plan, the empty Thursday gray, today ringed. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnTrackCalendarPreview() = Cards { state -> state.calendar?.let { CalendarCardView(it, onAction = {}) } }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnTrackCalendarDarkPreview() = Cards(dark = true) { state -> state.calendar?.let { CalendarCardView(it, onAction = {}) } }

/** The (i) open: how to read a deficit's calendar, for someone who has never had one. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnTrackCalendarExplainedPreview() =
    Cards(ui = TrendsUi(calendarExplained = true)) { state -> state.calendar?.let { CalendarCardView(it, onAction = {}) } }

/** A plain range, a month back: off plan either way, under included. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnTrackCalendarRangePreview() = Cards(inputs = TrendsFixtures.trendsInputs(), ui = TrendsUi(calendarPage = 1)) { state ->
    state.calendar?.let { CalendarCardView(it, onAction = {}) }
}

/** No numbers, and anyone who said numbers have been hard: logged or not, nothing more. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnTrackCalendarLoggedOnlyPreview() = Cards(policy = TrendsFixtures.policy(PresetId.NoNumbers), ui = TrendsUi(calendarExplained = true)) { state ->
    state.calendar?.let { CalendarCardView(it, onAction = {}) }
}

/** Thirty days against Burned in pounds, with the weight trend beside the estimate. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RunningTotalPreview() = Cards(inputs = TrendsFixtures.deficitInputs(Settings(weightUnit = WeightUnit.Lb))) { state ->
    state.runningTotal?.let { RunningTotalCardView(it, onAction = {}) }
    state.weight?.let { WeightCardView(it, onClick = {}) }
}

/** The (i) open, in kilograms, the last seven days. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RunningTotalExplainedPreview() = Cards(dark = true, ui = TrendsUi(runningSpan = RunningSpan.Week, runningExplained = true)) { state ->
    state.runningTotal?.let { RunningTotalCardView(it, onAction = {}) }
}

/** A weekly goal setting the target since 5 October: each day colored by what its week asked of it. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnTrackCalendarGoalPreview() = Cards(inputs = TrendsFixtures.goalInputs()) { state ->
    state.calendar?.let { CalendarCardView(it, onAction = {}) }
    state.runningTotal?.let { RunningTotalCardView(it, onAction = {}) }
}

/** The check-ins' hunger over the week, above the meal notes'. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TrendsHungerCheckInsPreview() = Cards { state -> HungerCardView(state.hunger, state.checkInHunger) }

/** Month: a dot a week with the week's range behind it. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TrendsHungerCheckInsMonthPreview() =
    Cards(inputs = TrendsFixtures.deficitInputs(Settings(trendsRange = TrendsRange.FourWeeks))) { state -> HungerCardView(state.hunger, state.checkInHunger) }

/** The whole page against the deficit, tall enough to reach the Hunger card. */
@Preview(widthDp = 390, heightDp = 3200)
@Composable
private fun TrendsDeficitPreview() = PreviewContent {
    TrendsContent(TrendsStateBuilder.build(TrendsFixtures.deficitInputs(), full), onAction = {})
}

@Composable
private fun DaySheet(date: LocalDate, policy: VisibilityPolicy = full, dark: Boolean = false) = PreviewContent(darkTheme = dark) {
    val inputs = TrendsFixtures.deficitInputs()
    val day = inputs.days.firstOrNull { it.date == date } ?: Day.empty(date)
    TrendsDaySheetContent(TrendsDayStateBuilder.build(day, inputs.today, inputs.settings, policy, inputs.burnedByDay?.get(date)), onAction = {})
}

/** The first day this month the calendar shows with [tone], so the previews follow the fixture. */
private fun dayWith(tone: PlanTone): LocalDate =
    TrendsStateBuilder.build(TrendsFixtures.deficitInputs(), full).calendar!!.shown.cells.filterNotNull().let { days ->
        (days.firstOrNull { it.tone == tone } ?: days.first { it.tone != PlanTone.None }).date
    }

@Preview(widthDp = 390, heightDp = 600)
@Composable
private fun TrendsDayOnPlanPreview() = DaySheet(dayWith(PlanTone.On))

@Preview(widthDp = 390, heightDp = 600)
@Composable
private fun TrendsDayOffPreview() = DaySheet(dayWith(PlanTone.Off), dark = true)

@Preview(widthDp = 390, heightDp = 600)
@Composable
private fun TrendsDayNotLoggedPreview() = DaySheet(LocalDate(2026, 10, 22))

/** No numbers: logged, and the way to the day. */
@Preview(widthDp = 390, heightDp = 600)
@Composable
private fun TrendsDayLoggedOnlyPreview() = DaySheet(LocalDate(2026, 10, 21), TrendsFixtures.policy(PresetId.NoNumbers))
