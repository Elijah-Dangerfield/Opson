package com.opson.features.today.impl

import com.opson.libraries.nutrition.WaterProgress

/** A tap on the water card's glasses (owner feedback, 2026-10-29). */
internal object WaterTaps {
    /**
     * The glasses full after a tap on glass [tapped] (from 0) with [filled] full: up to and
     * including the one tapped, or one fewer when it is the last full glass, which is how a
     * mistaken tap is undone. A tap on an earlier full glass empties the ones after it.
     */
    fun glassesAfter(filled: Int, tapped: Int): Int = if (tapped + 1 == filled) filled - 1 else tapped + 1

    /**
     * The day's water after a tap on glass [tapped] of [goalGlasses], with [ml] logged.
     *
     * The tap moves the amount by whole glasses from what is there, never to an absolute
     * `glasses * glassMl`. The bar floors the amount and caps it at the goal, so writing what
     * the bar shows would drop the part glass from 830 ml, or most of a day from 3,000 ml. A tap
     * on the last full glass takes one glass off, even past the goal where the bar stays full.
     * Any other tap leaves the bar at the glass tapped, counted from the glasses really drunk,
     * and keeps the part glass.
     */
    fun mlAfter(ml: Int, glassMl: Int, goalGlasses: Int, tapped: Int): Int {
        val drunk = WaterProgress.wholeGlasses(ml.toDouble(), glassMl)
        val filled = drunk.coerceAtMost(goalGlasses)
        val after = if (tapped + 1 == filled) drunk - 1 else glassesAfter(filled, tapped)
        return (ml + (after - drunk) * glassMl).coerceAtLeast(0)
    }
}
