package com.opson.libraries.nutrition.ui

import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.ui.components.ChartReading
import org.jetbrains.compose.resources.StringResource

/**
 * A resolved [Display] as one line of an inspected chart point, tagged with [metric] like every
 * rendered number. `null` unless it is an amount the mode shows: hidden is nothing, and a softened
 * word describes the whole span ("Rising slowly"), so it never stands in for one point's amount.
 */
fun Display.chartReading(metric: Metric, label: StringResource? = null): ChartReading? =
    (this as? Display.Shown)?.let { ChartReading(value = it.text, label = label, tag = metric.testTag()) }
