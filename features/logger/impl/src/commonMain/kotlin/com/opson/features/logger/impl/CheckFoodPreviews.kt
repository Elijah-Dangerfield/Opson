package com.opson.features.logger.impl

import androidx.compose.runtime.Composable
import com.opson.libraries.foods.LabelNutrient
import com.opson.libraries.foods.VerifyField
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.ui.previewVisibilityPolicy
import com.opson.libraries.scanner.MealPhoto
import com.opson.libraries.ui.PreviewContent
import org.jetbrains.compose.ui.tooling.preview.Preview

// Every state comes from CheckFoodStateBuilder over a model, as the view model builds it.

private val full = previewVisibilityPolicy(PresetId.Full)
private val noNumbers = previewVisibilityPolicy(PresetId.NoNumbers)

private val granola = CheckModel(name = "Maple granola", canTakePhoto = true)
private val front = MealPhoto(byteArrayOf(1, 2, 3))
private val label = MealPhoto(byteArrayOf(4, 5, 6))

/** The granola: the label printed 230 kcal where Opson has 300 per serving. */
private val differs = PackComparison(
    pack = mapOf(LabelNutrient.Energy to 418.0, LabelNutrient.Protein to 5.5, LabelNutrient.Carbs to 67.3, LabelNutrient.Fat to 14.5),
    opson = mapOf(LabelNutrient.Energy to 545.0, LabelNutrient.Protein to 5.5, LabelNutrient.Carbs to 67.3, LabelNutrient.Fat to 14.5),
    differing = setOf(LabelNutrient.Energy),
    servingGrams = 55.0,
)

/** "Something was wrong" as it opens on the granola: calories marked wrong, the label's 230 kcal per 55 g in the field. */
private val wrongCalories = granola.copy(
    step = CheckStep.Compared(differs),
    front = front,
    label = label,
    somethingWrong = true,
    basis = PackBasis("2/3 cup", 55.0),
    form = VerifyForm(
        issues = mapOf(VerifyField.Calories to FieldIssue.Wrong, VerifyField.Ingredients to FieldIssue.Missing),
        values = mapOf(VerifyForm.KCAL to "230", VerifyForm.NAME to "Maple granola"),
        note = "New recipe since spring",
    ),
)

@Composable
private fun Check(model: CheckModel, policy: VisibilityPolicy = full, dark: Boolean = false) = PreviewContent(darkTheme = dark) {
    CheckFoodSheetContent(CheckFoodStateBuilder.build(model, policy), onAction = {}, onClose = {})
}

/** Just opened: the front of the pack first, which can be skipped, then the label. */
@Preview(widthDp = 390, heightDp = 800)
@Composable
private fun CheckFoodIntroPreview() = Check(granola)

/** The front taken: the label is next. */
@Preview(widthDp = 390, heightDp = 800)
@Composable
private fun CheckFoodLabelNextPreview() = Check(granola.copy(front = front, step = CheckStep.Photos(PackSide.Label)))

/** The label matches what Opson has: Verify. */
@Preview(widthDp = 390, heightDp = 700)
@Composable
private fun CheckFoodMatchesPreview() = Check(granola.copy(front = front, label = label, step = CheckStep.Compared(differs.copy(differing = emptySet()))))

/** The label says something else: pack against Opson per serving, and "Something was wrong" on with calories marked. */
@Preview(widthDp = 390, heightDp = 1500)
@Composable
private fun CheckFoodDiffersPreview() = Check(wrongCalories)

@Preview(widthDp = 390, heightDp = 1500)
@Composable
private fun CheckFoodDiffersDarkPreview() = Check(wrongCalories, dark = true)

/** In No numbers, a difference in words only, and no number fields: the label's own figures go instead. */
@Preview(widthDp = 390, heightDp = 1300)
@Composable
private fun CheckFoodDiffersNoNumbersPreview() = Check(wrongCalories, noNumbers)

/** No panel in that photo. */
@Preview(widthDp = 390, heightDp = 700)
@Composable
private fun CheckFoodUnreadablePreview() = Check(granola.copy(front = front, step = CheckStep.Unreadable))

/** Verified on the spot. */
@Preview(widthDp = 390, heightDp = 600)
@Composable
private fun CheckFoodConfirmedPreview() = Check(granola.copy(step = CheckStep.Done(CheckDone.Confirmed)))

/** Sent for review. */
@Preview(widthDp = 390, heightDp = 600)
@Composable
private fun CheckFoodInReviewPreview() = Check(granola.copy(step = CheckStep.Done(CheckDone.InReview)))

/** Offline on Verify. */
@Preview(widthDp = 390, heightDp = 800)
@Composable
private fun CheckFoodOfflinePreview() = Check(
    granola.copy(front = front, label = label, step = CheckStep.Compared(differs.copy(differing = emptySet())), problem = CheckProblem.Offline),
)
