package com.opson.features.trends.impl

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.opson.features.trends.NutrientsRange
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.TrendsRange
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.ui.PreviewContent
import org.jetbrains.compose.ui.tooling.preview.Preview

// Every state here comes from the real builders over TrendsFixtures, the same path the app and
// the Blind leak test take. One theme per preview at the phone's 390 width, so each is also a
// design-sync screenshot; the names follow the v3 design's screen ids (trends-blind,
// nutrients-week, weight-hidden), with "Below" for the part of a page under the fold.

private val full = TrendsFixtures.policy()

@Composable
private fun Phone(dark: Boolean = false, content: @Composable () -> Unit) {
    PreviewContent(darkTheme = dark, content = content)
}

/** The page scrolled past the fold, to see the cards the 844 dp render cuts off. */
@Composable
private fun below(): Modifier = Modifier.verticalScroll(rememberScrollState(initial = BELOW_FOLD_PX))

private const val BELOW_FOLD_PX = 2_200

@Composable
private fun Trends(policy: VisibilityPolicy, inputs: TrendsInputs = TrendsFixtures.trendsInputs(settings = TrendsFixtures.settings(policy))) {
    TrendsScreen(TrendsStateBuilder.build(inputs, policy), onAction = {})
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TrendsPreview() = Phone { Trends(full) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TrendsDarkPreview() = Phone(dark = true) { Trends(full) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TrendsBelowPreview() = Phone {
    TrendsContent(TrendsStateBuilder.build(TrendsFixtures.trendsInputs(), full), onAction = {}, modifier = below())
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TrendsMonthPreview() =
    Phone { Trends(full, TrendsFixtures.trendsInputs(Settings(trendsRange = TrendsRange.FourWeeks))) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TrendsThreeMonthsPreview() =
    Phone { Trends(full, TrendsFixtures.trendsInputs(Settings(trendsRange = TrendsRange.ThreeMonths))) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TrendsEmptyPreview() =
    Phone { Trends(full, TrendsFixtures.trendsInputs(days = emptyList(), weighIns = emptyList())) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TrendsBlindPreview() = Phone { Trends(TrendsFixtures.policy(PresetId.Blind)) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TrendsCoastPreview() = Phone { Trends(TrendsFixtures.policy(PresetId.Coast)) }

/** Coast after Sunday's check has opened: the week's numbers, and no last-week card. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TrendsCoastOpenPreview() = Phone { Trends(TrendsFixtures.policy(PresetId.Coast, clock = TrendsFixtures.evening)) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TrendsNutrientFocusPreview() = Phone { Trends(TrendsFixtures.policy(PresetId.Nutrient)) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TrendsSettingsPreview() = Phone { TrendsSettingsSheetContent(TrendsSettingsStateBuilder.build(Settings(), full), onAction = {}) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TrendsSettingsBlindPreview() =
    Phone { TrendsSettingsSheetContent(TrendsSettingsStateBuilder.build(Settings(), TrendsFixtures.policy(PresetId.Blind)), onAction = {}) }

@Composable
private fun Nutrients(policy: VisibilityPolicy, inputs: NutrientsInputs, modifier: Modifier? = null) {
    val state = NutrientsStateBuilder.build(inputs, policy)
    if (modifier == null) NutrientsScreen(state, onAction = {}) else NutrientsContent(state, onAction = {}, modifier = modifier)
}

private val microsOn = TrendsFixtures.policy(overrides = mapOf(Metric.Micros to Visibility.Show))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun NutrientsDayPreview() = Phone { Nutrients(full, TrendsFixtures.nutrientsInputs()) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun NutrientsDayDarkPreview() = Phone(dark = true) { Nutrients(full, TrendsFixtures.nutrientsInputs()) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun NutrientsWeekPreview() = Phone { Nutrients(microsOn, TrendsFixtures.nutrientsInputs(NutrientsRange.WeekAverage)) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun NutrientsWeekBelowPreview() =
    Phone { Nutrients(microsOn, TrendsFixtures.nutrientsInputs(NutrientsRange.WeekAverage), below()) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun NutrientsEmptyDayPreview() = Phone { Nutrients(full, TrendsFixtures.nutrientsInputs(days = emptyList())) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun NutrientsFuelPreview() = Phone { Nutrients(TrendsFixtures.policy(PresetId.Fuel), TrendsFixtures.nutrientsInputs()) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun NutrientsBlindPreview() = Phone { Nutrients(TrendsFixtures.policy(PresetId.Blind), TrendsFixtures.nutrientsInputs()) }

@Composable
private fun Patterns(policy: VisibilityPolicy, modifier: Modifier? = null) {
    val state = PatternsStateBuilder.build(TrendsFixtures.patternsInputs(), policy)
    if (modifier == null) PatternsScreen(state, onAction = {}) else PatternsContent(state, onAction = {}, modifier = modifier)
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PatternsPreview() = Phone { Patterns(full) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PatternsBelowPreview() = Phone { Patterns(full, below()) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PatternsDarkPreview() = Phone(dark = true) { Patterns(full) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PatternsBlindPreview() = Phone { Patterns(TrendsFixtures.policy(PresetId.Blind)) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PatternsEmptyPreview() =
    Phone { PatternsScreen(PatternsStateBuilder.build(TrendsFixtures.patternsInputs(days = emptyList()), full), onAction = {}) }
