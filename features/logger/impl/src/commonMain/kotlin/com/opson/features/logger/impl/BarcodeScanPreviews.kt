package com.opson.features.logger.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ui.previewVisibilityPolicy
import com.opson.libraries.ui.PreviewContent
import com.opson.system.AppTheme
import com.opson.system.background
import org.jetbrains.compose.ui.tooling.preview.Preview

// The scanner in each stage, from the real builder over LoggerFixtures. The camera is a hero-coloured
// box: previews and screenshots never touch a device. Only "Pick a match" shows numbers, so only it
// has mode variants; the food detail a hit opens has its own in FoodDetailPreview.kt.

@Composable
private fun Scanner(model: ScanModel, dark: Boolean = false, preset: PresetId = PresetId.Full) = PreviewContent(darkTheme = dark) {
    BarcodeScanScreen(
        state = LoggerFixtures.scanState(model, previewVisibilityPolicy(preset)),
        onAction = {},
        viewfinder = { _, modifier -> Box(modifier = modifier.background(AppTheme.colors.hero)) },
    )
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerScanPreview() = Scanner(LoggerFixtures.scanning)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerScanDarkPreview() = Scanner(LoggerFixtures.scanning, dark = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerScanLightOnPreview() = Scanner(LoggerFixtures.scanningLit)

/** The camera finds the kitchen too dark and the light is off: the hint suggests it. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerScanDimPreview() = Scanner(LoggerFixtures.scanningDim)

/** A Builder with Also read the label on: after the code, the label. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerScanReadLabelPreview() = Scanner(LoggerFixtures.scanning.copy(canReadLabel = true, readLabel = true))

/** The instant a code is caught, before a lookup has been slow enough for the spinner. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerScanReadPreview() = Scanner(LoggerFixtures.lookingUp)

/** Past 400 ms: a first lookup at an outside source. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerScanLookingUpPreview() = Scanner(LoggerFixtures.lookingUpSlow)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerScanMissPreview() = Scanner(LoggerFixtures.missed)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerScanMissDarkPreview() = Scanner(LoggerFixtures.missed, dark = true)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerScanMissNamedPreview() = Scanner(LoggerFixtures.missedNamed)

/** `release.submitFood` on: photograph the pack, before the name. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerScanMissAddFoodPreview() = Scanner(LoggerFixtures.missedAddFood)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerScanUnavailablePreview() = Scanner(LoggerFixtures.unavailable)

/** The camera was refused and only Settings can turn it back on (any denial on iOS). */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerScanCameraOffPreview() = Scanner(LoggerFixtures.cameraOff)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerScanCameraOffDarkPreview() = Scanner(LoggerFixtures.cameraOff, dark = true)

/** Refused once on Android, so the prompt can still be shown again. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerScanCameraAskPreview() = Scanner(LoggerFixtures.cameraAskAgain)

/** Allowed, but no camera can run: the iOS simulator, or another app holding it. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerScanCameraUnavailablePreview() = Scanner(LoggerFixtures.cameraUnavailable)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerScanCameraUnavailableDarkPreview() = Scanner(LoggerFixtures.cameraUnavailable, dark = true)

/** Typing the digits under the barcode, part way through. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerScanTypingPreview() = Scanner(LoggerFixtures.typing)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerScanTypingMisreadPreview() = Scanner(LoggerFixtures.typingMisread)

/** The code is in OPDB, Open Food Facts and USDA Branded: OPDB's row stands for Open Food Facts too, and leads. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerScanMatchesPreview() = Scanner(LoggerFixtures.matched)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerScanMatchesDarkPreview() = Scanner(LoggerFixtures.matched, dark = true)

/** Three that differ, one of them with numbers the energy check doubts. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerScanMatchesThreePreview() = Scanner(LoggerFixtures.matchedThree)

/** Macros only: no calories, so nothing said about them either. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerScanMatchesMacrosOnlyPreview() = Scanner(LoggerFixtures.matchedThree, preset = PresetId.MacrosOnly)

/** No numbers: names, servings and databases only. */
@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LoggerScanMatchesNoNumbersPreview() = Scanner(LoggerFixtures.matchedThree, preset = PresetId.NoNumbers)
