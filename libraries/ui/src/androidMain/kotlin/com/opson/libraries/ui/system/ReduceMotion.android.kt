package com.opson.libraries.ui.system

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Accessibility's Remove animations sets the animator duration scale to 0, and so does turning
 * animations off in developer options. Read from settings rather than
 * `ValueAnimator.areAnimatorsEnabled()`, which needs API 26 (minSdk is 24).
 */
@Composable
internal actual fun isReduceMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
