package com.opson.features.logger.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.opson.libraries.ui.PreviewContent
import com.opson.system.AppTheme
import com.opson.system.background
import org.jetbrains.compose.ui.tooling.preview.Preview

// The scanner in each stage, from the real builder over LoggerFixtures. The camera is a hero-coloured
// box: previews and screenshots never touch a device. No stage shows a number, so there is no Blind
// variant here; the food detail a hit opens has its own in FoodDetailPreview.kt.

@Composable
private fun Scanner(model: ScanModel, dark: Boolean = false) = PreviewContent(darkTheme = dark) {
    BarcodeScanScreen(
        state = LoggerFixtures.scanState(model),
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
