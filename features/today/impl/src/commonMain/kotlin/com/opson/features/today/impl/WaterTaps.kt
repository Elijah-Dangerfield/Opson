package com.opson.features.today.impl

/** A tap on the water card's glasses (owner feedback, 2026-10-29). */
internal object WaterTaps {
    /**
     * The glasses full after a tap on glass [tapped] (from 0) with [filled] full: up to and
     * including the one tapped, or one fewer when it is the last full glass, which is how a
     * mistaken tap is undone. A tap on an earlier full glass empties the ones after it.
     */
    fun glassesAfter(filled: Int, tapped: Int): Int = if (tapped + 1 == filled) filled - 1 else tapped + 1
}
