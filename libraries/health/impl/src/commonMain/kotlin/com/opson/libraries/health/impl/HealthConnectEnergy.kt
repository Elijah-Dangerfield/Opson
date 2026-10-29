package com.opson.libraries.health.impl

/**
 * Health Connect keeps total and active energy, not resting energy, so resting is the one less
 * the other. Here so the rule is tested off the device; `HealthConnectReader` is its only caller.
 */
internal object HealthConnectEnergy {
    /**
     * Resting energy from a [totalKcal] and [activeKcal] read over the same time. Null without both:
     * with no active reading (not allowed, or no app writes it) the total is everything burned,
     * and calling that resting put a whole day's burn, 2,600 or so, where resting energy goes.
     */
    fun resting(totalKcal: Double?, activeKcal: Double?): Double? =
        if (totalKcal == null || activeKcal == null) null else (totalKcal - activeKcal).coerceAtLeast(0.0)
}
