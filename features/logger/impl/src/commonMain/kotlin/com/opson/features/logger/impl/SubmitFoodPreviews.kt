package com.opson.features.logger.impl

import androidx.compose.runtime.Composable
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ui.previewVisibilityPolicy
import com.opson.libraries.ui.PreviewContent
import org.jetbrains.compose.ui.tooling.preview.Preview

// Every state comes from SubmitFoodStateBuilder over SubmitFoodFixtures, as in the app and the
// Blind leak test. One theme per preview.

private val full = previewVisibilityPolicy(PresetId.Full)
private val noNumbers = previewVisibilityPolicy(PresetId.NoNumbers)

@Composable
private fun Submit(state: SubmitFoodState, dark: Boolean = false) = PreviewContent(darkTheme = dark) {
    SubmitFoodScreen(state, onAction = {})
}

/** Before any photo: the two slots and why the photos are needed. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SubmitFoodPhotosPreview() = Submit(SubmitFoodFixtures.photosState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SubmitFoodPhotosDarkPreview() = Submit(SubmitFoodFixtures.photosState(full), dark = true)

/** Both photos taken: Retake on each, and Read the label. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SubmitFoodPhotosReadyPreview() = Submit(SubmitFoodFixtures.photosState(full, photos = 2))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SubmitFoodReadingPreview() = Submit(SubmitFoodFixtures.calmState(SubmitStep.Reading, full))

/** The reading, with the energy check and its two fixes, and protein marked Check this. */
@Preview(widthDp = 390, heightDp = 1600)
@Composable
private fun SubmitFoodReviewPreview() = Submit(SubmitFoodFixtures.reviewState(full))

@Preview(widthDp = 390, heightDp = 1600)
@Composable
private fun SubmitFoodReviewDarkPreview() = Submit(SubmitFoodFixtures.reviewState(full), dark = true)

/** From search, the energy fixed: no check left, and a barcode to type if the pack has one. */
@Preview(widthDp = 390, heightDp = 1600)
@Composable
private fun SubmitFoodReviewFixedPreview() = Submit(SubmitFoodFixtures.fixedState(full))

/** No numbers: the form without the label's numbers, and a word on whether they add up. */
@Preview(widthDp = 390, heightDp = 1200)
@Composable
private fun SubmitFoodReviewNoNumbersPreview() = Submit(SubmitFoodFixtures.reviewState(noNumbers))

/** The server cannot read labels: an empty form to fill in from the photos. */
@Preview(widthDp = 390, heightDp = 1600)
@Composable
private fun SubmitFoodByHandPreview() = Submit(SubmitFoodFixtures.byHandState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SubmitFoodSavingPreview() = Submit(SubmitFoodFixtures.calmState(SubmitStep.Saving, full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SubmitFoodNoLabelPreview() = Submit(SubmitFoodFixtures.calmState(SubmitStep.NoLabel, full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SubmitFoodTooManyPreview() = Submit(SubmitFoodFixtures.calmState(SubmitStep.TooMany, full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SubmitFoodReadFailedPreview() = Submit(SubmitFoodFixtures.calmState(SubmitStep.ReadFailed(offline = true), full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SubmitFoodCameraOffPreview() = Submit(SubmitFoodFixtures.calmState(SubmitStep.CameraOff(canAskAgain = false), full))

/** Logged and in their foods; only the copy for review did not go. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SubmitFoodSendFailedPreview() =
    Submit(SubmitFoodFixtures.calmState(SubmitStep.SendFailed(offline = true, tooMany = false), full))

/** From the Foods tab: no meal, so the form ends in Save food and logs nothing. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SubmitFoodSaveOnlyPhotosPreview() = Submit(SubmitFoodFixtures.saveOnlyPhotosState(full))

@Preview(widthDp = 390, heightDp = 1600)
@Composable
private fun SubmitFoodSaveOnlyReviewPreview() = Submit(SubmitFoodFixtures.saveOnlyReviewState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SubmitFoodPhotoFailedPreview() = Submit(SubmitFoodFixtures.photoFailedState(full))

/** Builder mode: front, label and the optional ingredients. */
@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun SubmitFoodBuilderPhotosPreview() = Submit(SubmitFoodFixtures.builderPhotosState(full))

/** Builder mode, read on the phone: every number to check against the pack. */
@Preview(widthDp = 390, heightDp = 1600)
@Composable
private fun SubmitFoodBuilderReviewPreview() = Submit(SubmitFoodFixtures.builderReviewState(full))
