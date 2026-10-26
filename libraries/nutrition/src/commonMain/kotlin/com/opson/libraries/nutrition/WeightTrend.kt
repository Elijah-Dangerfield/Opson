package com.opson.libraries.nutrition

object WeightTrend {
    const val DEFAULT_ALPHA = 0.2

    /**
     * Exponential moving average, one point per input, seeded with the first
     * weigh-in. At 0.2 the line is smoothed over about a week, so day-to-day
     * water swings barely move it.
     */
    fun ema(values: List<Double>, alpha: Double = DEFAULT_ALPHA): List<Double> {
        require(alpha > 0.0 && alpha <= 1.0) { "alpha must be in (0, 1], was $alpha" }
        val out = ArrayList<Double>(values.size)
        for (value in values) {
            out += if (out.isEmpty()) value else out.last() * (1 - alpha) + value * alpha
        }
        return out
    }
}
