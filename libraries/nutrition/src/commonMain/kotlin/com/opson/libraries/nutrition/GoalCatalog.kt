package com.opson.libraries.nutrition

/**
 * Which goal types are offered, by what the mode shows and what the person told onboarding
 * (docs/plans/goals-v2.md, "Goal types"). A type whose own number is hidden is not offered at all,
 * rather than offered and drawn blank.
 */
object GoalCatalog {

    /** The types the gentle side offers: no number, no amount, nothing about weight. */
    val GENTLE: List<GoalKind> = listOf(GoalKind.TrackEveryDay, GoalKind.Meals)

    /**
     * Offered types in the order the goal UI lists them. After "numbers have been hard", only
     * [GENTLE]. Weight needs weight on show and no pregnancy or breastfeeding; an energy balance
     * needs calories and energy out on show (a deficit says both) and no pregnancy or
     * breastfeeding; burn needs energy out; steps, a count rather than energy, need energy out not
     * to be hidden outright (there is no metric of their own).
     */
    fun offered(policy: VisibilityPolicy, hard: Boolean, stage: BodyStage = BodyStage.None, surface: Surface = Surface.Settings): List<GoalKind> {
        if (hard) return GENTLE
        fun shows(metric: Metric) = policy.effectiveLevel(metric, surface) == Visibility.Show
        val noStage = stage == BodyStage.None
        return buildList {
            if (shows(Metric.Weight) && noStage) add(GoalKind.Weight)
            if (shows(Metric.Calories) && shows(Metric.EnergyOut) && noStage) add(GoalKind.EnergyBalance)
            if (shows(Metric.EnergyOut)) add(GoalKind.Burn)
            if (policy.effectiveLevel(Metric.EnergyOut, surface) != Visibility.Hide) add(GoalKind.Steps)
            addAll(GENTLE)
        }
    }

    /**
     * Whether a weekly average may be offered: calories and energy out on show (the week is a sum
     * of both), never after numbers have been hard.
     */
    fun weeklyOffered(policy: VisibilityPolicy, hard: Boolean, surface: Surface = Surface.Settings): Boolean =
        !hard && policy.effectiveLevel(Metric.Calories, surface) == Visibility.Show &&
            policy.effectiveLevel(Metric.EnergyOut, surface) == Visibility.Show
}
