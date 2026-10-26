package com.opson.libraries.ui.components.dialog.bottomsheet

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

object BottomSheetDefaults {

    internal val PositionalThreshold = 56.dp

    internal val VelocityThreshold = 125.dp

    /** The handoff's full-height sheet (the logger), 93% tall so the screen behind still peeks out. */
    const val FullHeightFraction = 0.93f
}