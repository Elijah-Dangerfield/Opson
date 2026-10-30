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

/**
 * Whether a one-off moment (a chart drawing itself in, a reveal) should start at rest: under a
 * preview or screenshot, which must never wait on motion, or with the system's reduce-motion
 * setting on. Read it once, as [isReduceMotionEnabled] is.
 */
@Composable
fun prefersStillness(): Boolean = androidx.compose.ui.platform.LocalInspectionMode.current || isReduceMotionEnabled()
