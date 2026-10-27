package com.opson.features.logger.impl

import androidx.compose.runtime.Composable
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ui.previewVisibilityPolicy
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.dialog.bottomsheet.SheetFrame
import org.jetbrains.compose.ui.tooling.preview.Preview

// The exercise sheet (release.exercise), every state through ExerciseSheetStateBuilder over
// ExerciseFixtures. No design yet; these are the screens a design sync starts from.

private val full = previewVisibilityPolicy(PresetId.Full)

@Composable
private fun Exercise(state: ExerciseSheetState, dark: Boolean = false) = PreviewContent(darkTheme = dark) {
    SheetFrame { ExerciseSheetContent(state, onAction = {}) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ExerciseSheetPreview() = Exercise(ExerciseFixtures.formState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ExerciseSheetDarkPreview() = Exercise(ExerciseFixtures.formState(full), dark = true)

/** No weigh-in yet, so no estimate: the number is the answer. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ExerciseSheetNoWeightPreview() = Exercise(ExerciseFixtures.noWeightState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ExerciseSheetOtherPreview() = Exercise(ExerciseFixtures.otherState(full))

/** Editing, with a health app connected: the watch switch and Delete. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ExerciseSheetEditPreview() = Exercise(ExerciseFixtures.editState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ExerciseSheetPickPreview() = Exercise(ExerciseFixtures.pickState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ExerciseSheetSearchPreview() = Exercise(ExerciseFixtures.pickState(full, query = "run"))

/** The + sheet with release.exercise on: Exercise joins the grid after Fasting. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun AddSheetExercisePreview() = PreviewContent {
    SheetFrame { LoggerSheetContent(LoggerFixtures.sheetState(full, exercise = true), onAction = {}) }
}
