package com.opson.features.logger.impl

import androidx.compose.runtime.Composable
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ui.previewVisibilityPolicy
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.components.dialog.bottomsheet.SheetFrame
import org.jetbrains.compose.ui.tooling.preview.Preview

// Every state comes from PhotoEstimateStateBuilder over PhotoFixtures, as in the app and the Blind
// leak test. The light, Full review is LoggerPhotoPreview in LoggerPreviews.kt, so it pairs with
// design sync 2's logger-previews__logger-photo; one theme per preview.

private val full = previewVisibilityPolicy(PresetId.Full)
private val blind = previewVisibilityPolicy(PresetId.Blind)

@Composable
private fun Photo(state: PhotoEstimateState, dark: Boolean = false) = PreviewContent(darkTheme = dark) {
    SheetFrame { PhotoEstimateSheetContent(state, onAction = {}) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerPhotoDarkPreview() = Photo(PhotoFixtures.reviewState(full), dark = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerPhotoBlindPreview() = Photo(PhotoFixtures.reviewState(blind))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerPhotoAmountPreview() = Photo(PhotoFixtures.reviewState(full, editing = true))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerPhotoSwapPreview() = Photo(PhotoFixtures.swapState(full))

/** Capturing: the note and the two ways to get a photo. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerPhotoStartPreview() = Photo(PhotoFixtures.startState(full, note = PhotoFixtures.NOTE))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerPhotoStartDarkPreview() = Photo(PhotoFixtures.startState(full), dark = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerPhotoEstimatingPreview() = Photo(PhotoFixtures.estimatingState(full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerPhotoUnavailablePreview() = Photo(PhotoFixtures.calmState(PhotoStep.Unavailable, full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerPhotoNothingFoundPreview() = Photo(PhotoFixtures.calmState(PhotoStep.NothingFound, full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerPhotoFailedPreview() = Photo(PhotoFixtures.calmState(PhotoStep.Failed(offline = true), full))

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerPhotoCameraOffPreview() = Photo(PhotoFixtures.calmState(PhotoStep.CameraOff(canAskAgain = false), full))
