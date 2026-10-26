package com.opson.libraries.nutrition

import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

/** A ring's centre label: [value] over [unit], `1,245` over `kcal`, or `On` over `target`. */
data class RingValue(val value: String, val unit: String)

/**
 * The label inside a Today ring, or `null` when the ring shows no number.
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

/**
 * The Balance ring, eaten minus energy out: `−950` / `kcal`.
 *
 * TODO(clinician): Balance is 0.2 and needs watch data. A signed difference
 * between eating and burning is the number this app is most careful with, so it
 * ships only after review. Until then [availableRingModes] never offers it.
 */
fun VisibilityPolicy.balanceRingValue(
    eaten: Double,
    energyOut: Double,
    surface: Surface,
    context: FormatContext = FormatContext(),
): RingValue? {
    if (!eaten.isFinite() || !energyOut.isFinite()) return null
    val bothShown = effectiveLevel(Metric.Calories, surface, context) == Visibility.Show &&
        effectiveLevel(Metric.EnergyOut, surface, context) == Visibility.Show
    return if (bothShown) formatter.ringBalance(eaten - energyOut) else null
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

/** What the third ring on the Today summary shows. Saved in settings, so the names are stable. */
@Serializable
enum class SummaryRingMode { Target, Remaining, Balance }

/**
 * The ring modes the user can cycle through, in order. Target is always there.
 * Remaining needs [Metric.Remaining] configured at Show, as the prototype; a
 * delayed remainder would come and go with the clock. Balance needs energy out
 * effectively shown and real watch data, which 0.1 never has, so 0.1 callers
 * pass `hasEnergyOut = false`.
 */
fun VisibilityPolicy.availableRingModes(
    surface: Surface,
    hasEnergyOut: Boolean,
    context: FormatContext = FormatContext(),
): List<SummaryRingMode> = buildList {
    add(SummaryRingMode.Target)
    if (level(Metric.Remaining) == Visibility.Show) add(SummaryRingMode.Remaining)
    if (hasEnergyOut && effectiveLevel(Metric.EnergyOut, surface, context) == Visibility.Show) {
        add(SummaryRingMode.Balance)
    }
}

/** This mode when it is still on offer, else Target: a saved mode outlives the level that allowed it. */
fun SummaryRingMode.orTargetIn(available: List<SummaryRingMode>): SummaryRingMode =
    if (this in available) this else SummaryRingMode.Target

/** The mode after this one, wrapping, for the cycle button. */
fun SummaryRingMode.nextIn(available: List<SummaryRingMode>): SummaryRingMode {
    if (available.isEmpty()) return SummaryRingMode.Target
    val index = available.indexOf(orTargetIn(available))
    return available[(index + 1) % available.size]
}
