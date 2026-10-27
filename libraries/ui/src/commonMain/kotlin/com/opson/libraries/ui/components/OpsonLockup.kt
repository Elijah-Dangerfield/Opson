package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import com.opson.libraries.ui.PreviewContent
import com.opson.libraries.ui.PreviewLightDark
import com.opson.system.AppTheme
import com.opson.system.typography.BrandFontFamily
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.ds_wordmark
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The stacked lockup: the mark, with the wordmark ("opson" in Young Serif) under it.
 *
 * The mark is centred in the bounds and the wordmark hangs below it, so going from the boot
 * loader or the iOS launch image to the lockup (or back) keeps the bowl where it was. Given more
 * room than it needs, it centres the mark in all of it.
 *
 * Proportions are the brand export's stacked lockup: the wordmark's em is 0.44 of the mark's box
 * and its baseline sits 0.42 of the box below the box's bottom edge. The wordmark scales with the
 * mark, not with the font-size setting, because it is part of a logo.
 *
 * [wordmarkAlpha] is read in the draw phase, for a splash that brings the wordmark in after a
 * mark that was already on screen.
 */
@Composable
fun OpsonLockup(
    modifier: Modifier = Modifier,
    markSize: Dp = OpsonLoaderDefaults.ScreenSize,
    wordmarkAlpha: () -> Float = { 1f },
) {
    val family = BrandFontFamily
    val ink = AppTheme.colors.ink.color
    val fontSize = with(LocalDensity.current) { (markSize * WordmarkEm).toSp() }
    val style = remember(family, ink, fontSize) { TextStyle(fontFamily = family, fontSize = fontSize, color = ink) }
    Layout(
        modifier = modifier,
        content = {
            OpsonLoader(size = markSize)
            BasicText(
                text = stringResource(Res.string.ds_wordmark),
                style = style,
                modifier = Modifier.graphicsLayer { alpha = wordmarkAlpha() },
            )
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val mark = measurables[0].measure(loose)
        val wordmark = measurables[1].measure(loose)
        val baseline = wordmark[FirstBaseline]
        val baselineBelowCentre = mark.height / 2 + (mark.height * WordmarkBaseline).roundToInt()
        val wordmarkBelowCentre = baselineBelowCentre - baseline + wordmark.height
        val width = constraints.constrainWidth(max(mark.width, wordmark.width))
        val height = constraints.constrainHeight(2 * max(mark.height / 2, wordmarkBelowCentre))
        layout(width, height) {
            val centreY = height / 2
            mark.place((width - mark.width) / 2, centreY - mark.height / 2)
            wordmark.place((width - wordmark.width) / 2, centreY + baselineBelowCentre - baseline)
        }
    }
}

private const val WordmarkEm = 0.44f
private const val WordmarkBaseline = 0.42f

@Preview
@Composable
private fun OpsonLockupPreview() {
    PreviewLightDark {
        OpsonLockup()
    }
}

/** The lockup as the iOS splash shows it: the mark at the centre of the screen. */
@Preview
@Composable
private fun OpsonLockupScreenPreview() {
    PreviewContent {
        OpsonLockup(Modifier.fillMaxSize())
    }
}

@Preview
@Composable
private fun OpsonLockupScreenDarkPreview() {
    PreviewContent(darkTheme = true) {
        OpsonLockup(Modifier.fillMaxSize())
    }
}
