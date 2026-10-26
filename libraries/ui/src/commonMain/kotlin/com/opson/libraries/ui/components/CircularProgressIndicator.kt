package com.opson.libraries.ui.components

import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import com.opson.libraries.ui.system.LocalColors
import androidx.compose.material3.CircularProgressIndicator as MaterialCircularProgressIndicator

/**
 * The indeterminate spinner. In a preview or screenshot it is drawn still, as
 * a fixed arc: an animation that never ends keeps a screenshot test waiting
 * for an idle frame forever (AGENTS.md, Known landmines).
 */
@Composable
fun CircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = LocalColors.current.accentPrimary.color,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth,
    trackColor: Color = ProgressIndicatorDefaults.circularTrackColor,
    strokeCap: StrokeCap = ProgressIndicatorDefaults.CircularIndeterminateStrokeCap,
) {
    if (LocalInspectionMode.current) {
        MaterialCircularProgressIndicator(
            progress = { STILL_PROGRESS },
            modifier = modifier,
            color = color,
            strokeWidth = strokeWidth,
            trackColor = trackColor,
            strokeCap = strokeCap,
        )
        return
    }
    MaterialCircularProgressIndicator(
        modifier = modifier,
        color = color,
        strokeWidth = strokeWidth,
        trackColor = trackColor,
        strokeCap = strokeCap,
    )
}

private const val STILL_PROGRESS = 0.3f
