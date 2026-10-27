package com.opson.libraries.ui.system

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
internal actual fun isReduceMotionEnabled(): Boolean = remember { UIAccessibilityIsReduceMotionEnabled() }
