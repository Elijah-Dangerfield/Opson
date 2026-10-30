package com.opson.libraries.goals.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.opson.libraries.goals.GoalFixtures
import com.opson.libraries.goals.TargetProjections
import com.opson.libraries.nutrition.Goal
import com.opson.libraries.nutrition.GoalAveraging
import com.opson.libraries.nutrition.GoalBaselines
import com.opson.libraries.nutrition.GoalKind
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.ui.PreviewLightDark
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.Clock

// The goal pieces on their own, from GoalFixtures: a loss, a gain, a target with no goal weight,
// the reveal at rest, and the inputs a goal is set with.

private val policy = VisibilityPolicy(NumbersSettings(), Clock.System, TimeZone.UTC)

private fun chart(goal: Goal = GoalFixtures.lose, unit: WeightUnit = WeightUnit.Kg): ProjectionChartUi =
    ProjectionCharts.ui(GoalFixtures.projection(goal)!!, goal.weight?.targetKg, goal.weight?.direction, unit, policy)!!

@Preview(widthDp = 390)
@Composable
private fun ProjectionChartLosePreview() = PreviewLightDark {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProjectionChart(chart())
        ProjectionForecastLines(chart())
    }
}

@Preview(widthDp = 390)
@Composable
private fun ProjectionChartGainPoundsPreview() = PreviewLightDark { ProjectionChart(chart(GoalFixtures.gain, WeightUnit.Lb)) }

/** A target with no goal weight: twelve weeks, and where the middle line ends. */
@Preview(widthDp = 390)
@Composable
private fun ProjectionChartOpenPreview() = PreviewLightDark {
    val projection = TargetProjections.weight(GoalBaselines.of(GoalFixtures.inputs), GoalFixtures.WEIGHT_KG, GoalFixtures.today, 1850, 2000, 2150)!!
    val open = ProjectionCharts.ui(projection, null, null, WeightUnit.Kg, policy)!!
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProjectionChart(open)
        ProjectionForecastLines(open)
    }
}

/** The reveal as a screenshot sees it: at rest, the forecast already in. */
@Preview(widthDp = 390)
@Composable
private fun GoalRevealPreview() = PreviewLightDark { GoalReveal(chart()) }

@Preview(widthDp = 390, heightDp = 900)
@Composable
private fun GoalInputsPreview() = PreviewLightDark {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GoalKindChoices(kinds = listOf(GoalKind.Weight, GoalKind.EnergyBalance, GoalKind.Meals), selected = GoalKind.EnergyBalance, onPick = {})
        AveragingChoice(selected = GoalAveraging.Weekly, onPick = {})
        BandChoice(options = Goal.TOLERANCES_KCAL, selected = 100, onPick = {}, low = "1,830", high = "2,030")
        GoalPanel { TypedStepper(text = "8000", onType = {}, onStep = {}, label = "Steps", unit = "steps a day") }
    }
}

@Preview(widthDp = 390, heightDp = 700)
@Composable
private fun GoalInputsLargeTextPreview() = PreviewLightDark(largeText = true) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AveragingChoice(selected = GoalAveraging.Daily, onPick = {})
        GoalPanel { TypedStepper(text = "450", onType = {}, onStep = {}, label = "Energy balance", unit = "kcal a day", canRaise = false) }
    }
}
