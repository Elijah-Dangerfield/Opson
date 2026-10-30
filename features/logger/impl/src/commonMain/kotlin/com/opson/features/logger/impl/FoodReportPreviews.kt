package com.opson.features.logger.impl

import androidx.compose.runtime.Composable
import com.opson.libraries.foods.FoodReportReason
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ui.previewVisibilityPolicy
import com.opson.libraries.ui.PreviewContent
import org.jetbrains.compose.ui.tooling.preview.Preview
import com.opson.libraries.scanner.MealPhoto

// Every state comes from FoodReportStateBuilder over a model, as the view model builds it.
// One theme per preview.

private val full = previewVisibilityPolicy(PresetId.Full)
private val noNumbers = previewVisibilityPolicy(PresetId.NoNumbers)

/** Monster Energy Zero Ultra at its label's serving, as the detail hands it over. */
private val monster = FoodReportModel(
    name = "Monster Energy Zero Ultra",
    amountText = "8.0 fl oz (240 ml)",
    amountGrams = 240.0,
    canTakePhoto = true,
)

@Composable
private fun Report(model: FoodReportModel, policy: com.opson.libraries.nutrition.VisibilityPolicy = full, dark: Boolean = false) =
    PreviewContent(darkTheme = dark) {
        FoodReportSheetContent(FoodReportStateBuilder.build(model, policy), onAction = {}, onClose = {})
    }

/** Just opened: the reasons, the photo and the note. Send waits for a reason. */
@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun FoodReportPreview() = Report(monster)

@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun FoodReportDarkPreview() = Report(monster, dark = true)

/** Wrong numbers and missing information: the right numbers for the serving on screen, typed in, and a note. */
@Preview(widthDp = 390, heightDp = 1700)
@Composable
private fun FoodReportCaloriesPreview() = Report(
    monster.copy(
        picked = setOf(FoodReportReason.WrongNumbers, FoodReportReason.MissingInfo),
        fields = mapOf(FoodReportStateBuilder.KCAL to "5", FoodReportStateBuilder.CARBS to "2", FoodReportStateBuilder.SERVING to "1 can, 473 ml"),
        note = "The can says 10 calories",
    ),
)

/** A label photo read on the phone: the numbers it found, filled in, with the note to check them. */
@Preview(widthDp = 390, heightDp = 1500)
@Composable
private fun FoodReportFromPhotoPreview() = Report(
    monster.copy(
        reason = FoodReportReason.WrongNutrients,
        fields = mapOf(FoodReportStateBuilder.KCAL to "10", FoodReportStateBuilder.CARBS to "3"),
        fromPhoto = setOf(FoodReportStateBuilder.KCAL, FoodReportStateBuilder.CARBS),
    ),
)

/** No numbers: the reason and the serving, never a calorie or a macro to type. */
@Preview(widthDp = 390, heightDp = 1300)
@Composable
private fun FoodReportNoNumbersPreview() = Report(monster.copy(picked = setOf(FoodReportReason.WrongServing)), policy = noNumbers)

/** Offline: everything kept, and a word on sending it later. */
@Preview(widthDp = 390, heightDp = 1500)
@Composable
private fun FoodReportOfflinePreview() = Report(
    monster.copy(picked = setOf(FoodReportReason.WrongNumbers), fields = mapOf(FoodReportStateBuilder.KCAL to "5"), problem = ReportProblem.Offline),
)

/** Sending. */
@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun FoodReportSendingPreview() = Report(monster.copy(picked = setOf(FoodReportReason.Duplicate), sending = true))

/** Both photos of the pack added: the front and the back. */
@Preview(widthDp = 390, heightDp = 1300)
@Composable
private fun FoodReportPhotosPreview() = Report(
    monster.copy(picked = setOf(FoodReportReason.WrongName), front = MealPhoto(byteArrayOf(1, 2, 3)), photo = MealPhoto(byteArrayOf(4, 5, 6))),
)

/** The camera was refused: the library still works. */
@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun FoodReportCameraOffPreview() = Report(monster.copy(picked = setOf(FoodReportReason.NotFood), cameraOff = true))
