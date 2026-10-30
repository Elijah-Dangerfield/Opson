package com.opson.libraries.goals.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.confirm
import com.opson.libraries.ui.system.prefersStillness
import com.opson.system.AppTheme
import kotlinx.coroutines.launch
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.goal_chart_rough
import opson.libraries.resources.generated.resources.goal_forecast_band
import opson.libraries.resources.generated.resources.goal_forecast_middle
import opson.libraries.resources.generated.resources.goal_forecast_none
import org.jetbrains.compose.resources.stringResource

/**
 * A goal just set, made to feel like something (owner feedback, 2026-10-30: "animate it, play
 * haptics, make it feel like Duolingo"): the projection draws itself in, and when the line lands
 * the forecast fades up under it and the phone gives its success tap. Plays once per [chart].
 *
 * Under reduce motion everything is at rest from the start and the tap still plays: it is
 * feedback, not motion. Under a preview or screenshot nothing plays. The fade is read in the layer
 * phase only.
 */
@Composable
fun GoalReveal(chart: ProjectionChartUi, modifier: Modifier = Modifier, animate: Boolean = true) {
    val still = prefersStillness()
    val preview = LocalInspectionMode.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val shown = remember(chart.shape) { Animatable(if (animate && !still) 0f else 1f) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProjectionChart(
            chart = chart,
            animate = animate,
            onDrawn = {
                if (animate && !preview) haptics.confirm()
                scope.launch { shown.animateTo(1f, tween(FadeMillis)) }
            },
        )
        Column(
            modifier = Modifier.graphicsLayer { alpha = shown.value },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ProjectionForecastLines(chart)
        }
    }
}

/** The success tap for a goal saved with nothing to draw: once, never under a preview. */
@Composable
fun GoalSetTap() {
    val preview = LocalInspectionMode.current
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) { if (!preview) haptics.confirm() }
}

/**
 * The words under a projection: when the target gets there, the spread across the band, and that
 * it is rough. Without a goal weight, only the last.
 */
@Composable
fun ProjectionForecastLines(chart: ProjectionChartUi) {
    val forecast = chart.forecast
    if (forecast != null) {
        val middle = forecast.middle
        Text(
            text = if (middle != null) stringResource(Res.string.goal_forecast_middle, middle) else stringResource(Res.string.goal_forecast_none),
            typography = AppTheme.type.bodyBold,
            color = AppTheme.colors.ink,
        )
        val best = forecast.best
        val worst = forecast.worst
        if (best != null && worst != null && best != worst) {
            Text(text = stringResource(Res.string.goal_forecast_band, best, worst), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
    }
    Text(text = stringResource(Res.string.goal_chart_rough), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
}

private const val FadeMillis = 400
