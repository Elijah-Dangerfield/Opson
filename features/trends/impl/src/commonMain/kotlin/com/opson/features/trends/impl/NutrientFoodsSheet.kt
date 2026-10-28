package com.opson.features.trends.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.opson.features.trends.NutrientsRange
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.Nutrient
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ui.NutrientFillBar
import com.opson.libraries.nutrition.ui.NutrientFillBarDefaults
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.DelayedLoadingIndicator
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.SheetHandle
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.Dimension
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.trends_foods_coverage
import opson.libraries.resources.generated.resources.trends_foods_more
import opson.libraries.resources.generated.resources.trends_foods_none
import opson.libraries.resources.generated.resources.trends_foods_ranked
import opson.libraries.resources.generated.resources.trends_loading_cd
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Which foods gave a nutrient: the foods in order, each with its amount and a bar for its share
 * where the mode shows them. Every row draws in the nutrient's own colour, the same for sodium as
 * for fiber, so nothing reads as a warning. The body apart from its `BottomSheet`, for previews
 * and the leak test.
 */
@Composable
fun NutrientFoodsSheetContent(
    state: NutrientFoodsState,
    nutrient: Nutrient,
    onAction: (NutrientFoodsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, top = Dimension.D400, bottom = Dimension.D900),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        SheetHandle(Modifier.align(Alignment.CenterHorizontally))
        SheetHeader(
            title = state.title?.let { stringResource(it) }.orEmpty(),
            subtitle = state.subtitle.takeIf { it.isNotEmpty() },
            onClose = { onAction(NutrientFoodsAction.CloseTapped) },
        )
        when {
            state.loading -> DelayedLoadingIndicator(contentDescription = stringResource(Res.string.trends_loading_cd))
            // The mode hides this nutrient: say nothing about it, its foods included.
            state.hidden -> Unit
            state.none -> Caption(stringResource(Res.string.trends_foods_none))
            else -> {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(AppTheme.shapes.groupedList)
                        .background(AppTheme.colors.surface)
                        .padding(horizontal = Dimension.D700),
                ) {
                    val color = when (nutrient) {
                        is Nutrient.Macro -> AppTheme.colors.of(nutrient.metric)
                        is Nutrient.Micro -> AppTheme.colors.of(nutrient.micro.group)
                    }
                    state.rows.forEachIndexed { index, row ->
                        if (index > 0) HorizontalDivider(color = AppTheme.colors.line)
                        FoodRow(row, nutrient.metric, color)
                    }
                    if (state.more > 0) {
                        HorizontalDivider(color = AppTheme.colors.line)
                        Text(
                            text = stringResource(Res.string.trends_foods_more, state.more),
                            typography = AppTheme.type.secondary,
                            color = AppTheme.colors.muted,
                            modifier = Modifier.padding(vertical = Dimension.D500),
                        )
                    }
                }
                if (state.rankedOnly) Caption(stringResource(Res.string.trends_foods_ranked))
                state.coverage?.let { Caption(stringResource(Res.string.trends_foods_coverage, it.listing, it.of)) }
            }
        }
    }
}

@Composable
private fun FoodRow(row: FoodShare, metric: Metric, color: ColorResource) {
    Column(Modifier.padding(vertical = Dimension.D500), verticalArrangement = Arrangement.spacedBy(Dimension.D300)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimension.D400),
        ) {
            Text(text = row.name, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink, modifier = Modifier.weight(1f), maxLines = 2)
            NutrientText(metric = metric, display = row.amount, typography = AppTheme.type.body.Tabular, color = AppTheme.colors.muted)
            NutrientText(
                metric = metric,
                display = row.share,
                typography = AppTheme.type.bodyBold.Tabular,
                color = AppTheme.colors.ink,
                textAlign = TextAlign.End,
                modifier = Modifier.width(Dimension.D1400),
            )
        }
        NutrientFillBar(metric = metric, display = row.share, color = color, height = NutrientFillBarDefaults.TallHeight)
    }
}

@Composable
private fun Caption(text: String) {
    Text(text = text, typography = AppTheme.type.body, color = AppTheme.colors.muted)
}

@Composable
private fun FoodsPreview(nutrient: Nutrient, preset: PresetId = PresetId.Full, range: NutrientsRange = NutrientsRange.Day, dark: Boolean = false) {
    val inputs = TrendsFixtures.nutrientFoodsInputs(nutrient, range)
    PreviewContent(darkTheme = dark) {
        NutrientFoodsSheetContent(NutrientFoodsStateBuilder.build(inputs, TrendsFixtures.policy(preset)), nutrient, onAction = {})
    }
}

/** Iron today: three of the four foods list it. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun NutrientFoodsIronPreview() = FoodsPreview(Nutrient.Micro(Micronutrient.Iron))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun NutrientFoodsIronDarkPreview() = FoodsPreview(Nutrient.Micro(Micronutrient.Iron), dark = true)

/** Sodium over the week, a limit drawn like any other nutrient. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun NutrientFoodsSodiumWeekPreview() = FoodsPreview(Nutrient.Micro(Micronutrient.Sodium), range = NutrientsRange.WeekAverage)

/** Nothing today lists vitamin D. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun NutrientFoodsNoneListedPreview() = FoodsPreview(Nutrient.Micro(Micronutrient.VitaminD))

/** Macros only: protein's foods in order, no grams and no shares. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun NutrientFoodsRankedOnlyPreview() = FoodsPreview(Nutrient.Macro(Metric.Protein), preset = PresetId.MacrosOnly)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun NutrientFoodsLoadingPreview() = PreviewContent {
    NutrientFoodsSheetContent(NutrientFoodsState(), Nutrient.Micro(Micronutrient.Iron), onAction = {})
}
