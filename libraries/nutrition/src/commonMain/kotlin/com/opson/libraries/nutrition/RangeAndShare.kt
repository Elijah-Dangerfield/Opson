package com.opson.libraries.nutrition

/**
 * An energy range, `1,900 to 2,300` bare or `1,900 to 2,300 kcal`.
 *
 * Shown only when [metric] is effectively [Visibility.Show]. A range has no
 * soften word ("In your range to In your range" says nothing), so a softened
 * metric hides it, the same as Hide. Delay opens it where the policy would open
 * the amount itself.
 */
fun VisibilityPolicy.resolveRange(
    metric: Metric,
    min: Double,
    max: Double,
    surface: Surface,
    context: FormatContext = FormatContext(),
): Display {
    require(metric == Metric.Calories || metric == Metric.EnergyOut) { "An energy range needs an energy metric, got $metric" }
    if (effectiveLevel(metric, surface, context) != Visibility.Show) return Display.Hidden
    if (!min.isFinite() || !max.isFinite()) return Display.Hidden
    return Display.Shown(formatter.energyRange(min, max, context.bare))
}

/**
 * A macro's share of the energy target, `30%`, for the targets screen.
 *
 * Shown only when the macro is effectively [Visibility.Show]: a share is an
 * amount in disguise, and a soft word for it ("Plenty today") would be nonsense.
 */
fun VisibilityPolicy.share(
    metric: Metric,
    percent: Double,
    surface: Surface,
    context: FormatContext = FormatContext(),
): Display {
    require(metric.isMacro) { "A share of energy is for a macro, got $metric" }
    if (effectiveLevel(metric, surface, context) != Visibility.Show || !percent.isFinite()) return Display.Hidden
    return Display.Shown(formatter.percent(percent))
}

/**
 * One meal's share of the energy eaten, `21%`, for Patterns.
 *
 * Shown only when calories are effectively [Visibility.Show]: a share of energy
 * is an amount in disguise, so a mode that hides or softens calories hides it.
 */
fun VisibilityPolicy.energyShare(
    percent: Double,
    surface: Surface,
    context: FormatContext = FormatContext(),
): Display {
    if (effectiveLevel(Metric.Calories, surface, context) != Visibility.Show || !percent.isFinite()) return Display.Hidden
    return Display.Shown(formatter.percent(percent))
}

/**
 * What one portion is of a day's target, `12%`, for the food detail's Today with this.
 *
 * Shown only when [metric] is effectively [Visibility.Show]: a share of a target is an amount in
 * disguise, so a mode that softens or hides the metric hides it too.
 */
fun VisibilityPolicy.targetShare(
    metric: Metric,
    percent: Double,
    surface: Surface,
    context: FormatContext = FormatContext(),
): Display {
    if (effectiveLevel(metric, surface, context) != Visibility.Show || !percent.isFinite()) return Display.Hidden
    return Display.Shown(formatter.percent(percent))
}
