package com.opson.features.trends.impl

import androidx.compose.runtime.Composable
import com.opson.features.trends.NutrientsRange
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.TrendsRange
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.ui.PreviewLightDark
import kotlinx.datetime.DayOfWeek
import org.jetbrains.compose.ui.tooling.preview.Preview

// Every preview builds its state through the real builder from TrendsFixtures,
// so a preview in Blind shows exactly what Blind would.

@Composable
private fun TrendsPreview(policy: VisibilityPolicy, inputs: TrendsInputs = TrendsFixtures.trendsInputs()) {
    PreviewLightDark { TrendsContent(TrendsStateBuilder.build(inputs, policy), onAction = {}) }
}

@Preview(widthDp = 390)
@Composable
private fun TrendsFullPreview() = TrendsPreview(TrendsFixtures.policy())

@Preview(widthDp = 390)
@Composable
private fun TrendsEmptyPreview() =
    TrendsPreview(TrendsFixtures.policy(), TrendsFixtures.trendsInputs(days = emptyList(), weighIns = emptyList()))

@Preview(widthDp = 390)
@Composable
private fun TrendsBlindPreview() = TrendsPreview(TrendsFixtures.policy(PresetId.Blind))

@Preview(widthDp = 390)
@Composable
private fun TrendsCoastPreview() = TrendsPreview(TrendsFixtures.policy(PresetId.Coast))

@Preview(widthDp = 390)
@Composable
private fun TrendsNutrientFocusPreview() = TrendsPreview(TrendsFixtures.policy(PresetId.Nutrient))

@Preview(widthDp = 390)
@Composable
private fun TrendsWeekStartsSaturdayPreview() =
    TrendsPreview(TrendsFixtures.policy(), TrendsFixtures.trendsInputs(Settings(weekStart = DayOfWeek.SATURDAY)))

@Preview(widthDp = 390)
@Composable
private fun TrendsThreeMonthsPreview() =
    TrendsPreview(TrendsFixtures.policy(), TrendsFixtures.trendsInputs(Settings(trendsRange = TrendsRange.ThreeMonths)))

@Composable
private fun TrendsSettingsPreview(policy: VisibilityPolicy) {
    PreviewLightDark { TrendsSettingsSheetContent(TrendsSettingsStateBuilder.build(Settings(), policy), onAction = {}) }
}

@Preview(widthDp = 390)
@Composable
private fun TrendsSettingsFullPreview() = TrendsSettingsPreview(TrendsFixtures.policy())

@Preview(widthDp = 390)
@Composable
private fun TrendsSettingsBlindPreview() = TrendsSettingsPreview(TrendsFixtures.policy(PresetId.Blind))

@Composable
private fun NutrientsPreview(policy: VisibilityPolicy, inputs: NutrientsInputs) {
    PreviewLightDark { NutrientsContent(NutrientsStateBuilder.build(inputs, policy), onAction = {}) }
}

@Preview(widthDp = 390)
@Composable
private fun NutrientsDayPreview() = NutrientsPreview(TrendsFixtures.policy(), TrendsFixtures.nutrientsInputs())

@Preview(widthDp = 390)
@Composable
private fun NutrientsEmptyDayPreview() =
    NutrientsPreview(TrendsFixtures.policy(), TrendsFixtures.nutrientsInputs(days = emptyList()))

@Preview(widthDp = 390)
@Composable
private fun NutrientsMicrosShownPreview() = NutrientsPreview(
    TrendsFixtures.policy(overrides = mapOf(Metric.Micros to Visibility.Show)),
    TrendsFixtures.nutrientsInputs(NutrientsRange.WeekAverage),
)

@Preview(widthDp = 390)
@Composable
private fun NutrientsWeekDelayedPreview() =
    NutrientsPreview(TrendsFixtures.policy(PresetId.Delayed), TrendsFixtures.nutrientsInputs(NutrientsRange.WeekAverage))

@Preview(widthDp = 390)
@Composable
private fun NutrientsFuelPreview() = NutrientsPreview(TrendsFixtures.policy(PresetId.Fuel), TrendsFixtures.nutrientsInputs())

@Preview(widthDp = 390)
@Composable
private fun NutrientsBlindPreview() = NutrientsPreview(TrendsFixtures.policy(PresetId.Blind), TrendsFixtures.nutrientsInputs())

@Composable
private fun WeightPreview(policy: VisibilityPolicy, inputs: WeightInputs = TrendsFixtures.weightInputs(), ui: WeightUi = WeightUi()) {
    PreviewLightDark { WeightContent(WeightStateBuilder.build(inputs, ui, policy), onAction = {}) }
}

@Preview(widthDp = 390)
@Composable
private fun WeightNumbersPreview() = WeightPreview(TrendsFixtures.policy())

@Preview(widthDp = 390)
@Composable
private fun WeightEmptyPreview() = WeightPreview(TrendsFixtures.policy(), TrendsFixtures.weightInputs(weighIns = emptyList()))

@Preview(widthDp = 390)
@Composable
private fun WeightTrendOnlyPreview() = WeightPreview(TrendsFixtures.policy(PresetId.Fuel))

@Preview(widthDp = 390)
@Composable
private fun WeightHiddenPreview() =
    WeightPreview(TrendsFixtures.policy(PresetId.Blind), ui = WeightUi(input = "72.4", saved = true))
