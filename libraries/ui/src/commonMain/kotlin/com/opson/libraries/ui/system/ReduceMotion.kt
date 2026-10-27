package com.opson.libraries.ui.system

import androidx.compose.runtime.Composable

/**
 * Whether the person has asked the system for less motion: Remove animations on Android,
 * Reduce Motion on iOS. Decorative loops hold still when it is on.
 *
 * Read once per composition rather than observed. The loops that ask are short-lived (a loader),
 * so a setting changed while one is on screen applies the next time it appears.
 */
@Composable
internal expect fun isReduceMotionEnabled(): Boolean
