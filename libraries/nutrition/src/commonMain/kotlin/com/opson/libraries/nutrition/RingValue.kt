package com.opson.libraries.nutrition

import kotlin.math.roundToLong

/** A ring's centre label: [value] over [unit], `1,245` over `kcal`, or `On` over `target`. */
data class RingValue(val value: String, val unit: String)

/**
 * A ring's centre label, `1,245` over `kcal`, or `null` when the ring shows no number. The
 * widget's ring reads it.
 *
 * Only an effective [Visibility.Show] produces one. A ring has room for a
 * number and a unit, not a soften word, so a softened metric gets the soft card
 * instead, and Hide and an unrevealed Delay get nothing.
 *
 * [Metric.Remaining] never says 0 and never goes negative: inside the range it
 * is `On` / `target`, above it `Past` / `range`, and a remainder that rounds to
 * zero or below is `On` / `target` even when [context] carries no range.
 * Below the range it is the amount left, `1,060` / `kcal`.
 */
fun VisibilityPolicy.ringValue(
    metric: Metric,
    value: Double?,
    surface: Surface,
    context: FormatContext = FormatContext(),
): RingValue? {
    require(metric in RING_METRICS) { "No ring label for $metric" }
    if (value == null || !value.isFinite()) return null
    if (effectiveLevel(metric, surface, context) != Visibility.Show) return null
    return when (metric) {
        Metric.Remaining -> remainingRing(value, context)
        Metric.Calories, Metric.EnergyOut -> formatter.ringEnergy(value)
        else -> formatter.ringGrams(value)
    }
}

private fun VisibilityPolicy.remainingRing(value: Double, context: FormatContext): RingValue {
    val eaten = context.eaten
    val min = context.min
    val max = context.max
    return when {
        eaten != null && max != null && eaten > max -> formatter.ringAboveRange()
        eaten != null && min != null && max != null && eaten >= min -> formatter.ringOnTarget()
        value.roundToLong() <= 0 -> formatter.ringOnTarget()
        else -> formatter.ringEnergy(value)
    }
}

private val RING_METRICS = setOf(
    Metric.Calories,
    Metric.Remaining,
    Metric.EnergyOut,
    Metric.Protein,
    Metric.Carbs,
    Metric.Fat,
    Metric.Fiber,
)
