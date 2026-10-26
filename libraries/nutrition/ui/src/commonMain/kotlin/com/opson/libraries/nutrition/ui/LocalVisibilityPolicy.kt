package com.opson.libraries.nutrition.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalInspectionMode
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * A policy built from fixed settings, for previews and tests. The app provides
 * the real one, built from the user's settings, at the root.
 */
fun previewVisibilityPolicy(
    preset: PresetId = PresetId.Full,
    overrides: Map<Metric, Visibility> = emptyMap(),
    clock: Clock = Clock.System,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): VisibilityPolicy = VisibilityPolicy(NumbersSettings(preset = preset, overrides = overrides), clock, timeZone)

// The default fails closed. A screen that renders outside the app's provider
// must not show calories to someone who chose Blind, so an unprovided policy
// hides everything in a running app. Only previews swap it for Full, in
// [currentVisibilityPolicy], so they still have numbers to look at.
private val Unprovided: VisibilityPolicy = previewVisibilityPolicy(PresetId.Blind)
private val PreviewDefault: VisibilityPolicy = previewVisibilityPolicy(PresetId.Full)

val LocalVisibilityPolicy = staticCompositionLocalOf { Unprovided }

/** The provided policy, or Full numbers inside a `@Preview` when nothing was provided. */
@Composable
@ReadOnlyComposable
fun currentVisibilityPolicy(): VisibilityPolicy {
    val policy = LocalVisibilityPolicy.current
    return if (policy === Unprovided && LocalInspectionMode.current) PreviewDefault else policy
}
