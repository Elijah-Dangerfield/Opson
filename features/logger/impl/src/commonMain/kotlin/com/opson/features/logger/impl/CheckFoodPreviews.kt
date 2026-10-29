package com.opson.features.logger.impl

import androidx.compose.runtime.Composable
import com.opson.libraries.foods.LabelNutrient
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.ui.previewVisibilityPolicy
import com.opson.libraries.ui.PreviewContent
import org.jetbrains.compose.ui.tooling.preview.Preview

// Every state comes from CheckFoodStateBuilder over a model, as the view model builds it.

private val full = previewVisibilityPolicy(PresetId.Full)
private val noNumbers = previewVisibilityPolicy(PresetId.NoNumbers)

private val granola = CheckModel(name = "Maple granola", canTakePhoto = true)

/** The granola: the label printed 230 kcal where Opson has 300 per serving. */
private val differs = PackComparison(
    pack = mapOf(LabelNutrient.Energy to 418.0, LabelNutrient.Protein to 5.5, LabelNutrient.Carbs to 67.3, LabelNutrient.Fat to 14.5),
    opson = mapOf(LabelNutrient.Energy to 545.0, LabelNutrient.Protein to 5.5, LabelNutrient.Carbs to 67.3, LabelNutrient.Fat to 14.5),
    differing = setOf(LabelNutrient.Energy),
    servingGrams = 55.0,
)

@Composable
private fun Check(model: CheckModel, policy: VisibilityPolicy = full, dark: Boolean = false) = PreviewContent(darkTheme = dark) {
    CheckFoodSheetContent(CheckFoodStateBuilder.build(model, policy), onAction = {}, onClose = {})
}

/** Just opened: what happens, and the ways to photograph the label. */
@Preview(widthDp = 390, heightDp = 700)
@Composable
private fun CheckFoodIntroPreview() = Check(granola)

/** The label matches what Opson has. */
@Preview(widthDp = 390, heightDp = 700)
@Composable
private fun CheckFoodMatchesPreview() = Check(granola.copy(step = CheckStep.Compared(differs.copy(differing = emptySet()))))

/** The label says something else: pack against Opson, per serving. */
@Preview(widthDp = 390, heightDp = 900)
@Composable
private fun CheckFoodDiffersPreview() = Check(granola.copy(step = CheckStep.Compared(differs)))

@Preview(widthDp = 390, heightDp = 900)
@Composable
private fun CheckFoodDiffersDarkPreview() = Check(granola.copy(step = CheckStep.Compared(differs)), dark = true)

/** In No numbers, a difference in words only. */
@Preview(widthDp = 390, heightDp = 700)
@Composable
private fun CheckFoodDiffersNoNumbersPreview() = Check(granola.copy(step = CheckStep.Compared(differs)), noNumbers)

/** No panel in that photo. */
@Preview(widthDp = 390, heightDp = 700)
@Composable
private fun CheckFoodUnreadablePreview() = Check(granola.copy(step = CheckStep.Unreadable))

/** Sent, and counted. */
@Preview(widthDp = 390, heightDp = 600)
@Composable
private fun CheckFoodConfirmedPreview() = Check(granola.copy(step = CheckStep.Done(CheckDone.Confirmed)))

/** Offline on Send. */
@Preview(widthDp = 390, heightDp = 700)
@Composable
private fun CheckFoodOfflinePreview() = Check(granola.copy(step = CheckStep.Compared(differs.copy(differing = emptySet())), problem = CheckProblem.Offline))
