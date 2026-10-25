package com.opson.libraries.config.impl

import com.opson.libraries.config.getValueForPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * How a malformed remote value resolves.
 *
 * The rule the whole fail-open guarantee rests on: a value the client cannot
 * parse must resolve to **null**, so the caller falls back to its declared
 * default. A parse that invents an answer is worse than no answer at all —
 * the default was chosen deliberately and is written down; the invented value
 * is whatever the coercion happened to produce.
 */
class MapExtTest {

    @Test
    fun anUnparseableBooleanFallsBackRatherThanReadingAsFalse() {
        // `"banana".toBoolean()` is `false`. That was the bug: a string typed
        // into a boolean flag in the admin console disabled the feature on
        // every device that fetched the config, with no log and no fallback.
        listOf("banana", "", "1", "yes", "off", "null").forEach { junk ->
            assertNull(
                mapOf("features" to mapOf("enabled" to junk)).getValueForPath<Boolean>("features.enabled"),
                "\"$junk\" must not resolve to a boolean",
            )
        }
    }

    @Test
    fun realBooleansStillResolveInEitherCase() {
        // The companion assertion. A guard that rejected everything would pass
        // the test above and break every flag in the app.
        fun read(raw: Any) =
            mapOf("features" to mapOf("enabled" to raw)).getValueForPath<Boolean>("features.enabled")

        assertEquals(true, read(true))
        assertEquals(false, read(false))
        assertEquals(true, read("true"))
        assertEquals(false, read("false"))
        // The admin console lets an operator type a raw value, and casing is
        // not a mistake worth punishing.
        assertEquals(true, read("TRUE"))
        assertEquals(false, read("False"))
    }

    @Test
    fun unparseableNumbersAlreadyFellBackAndStillDo() {
        // Why the boolean case stood out: every numeric branch of the same
        // `when` already returns null instead of inventing a zero.
        val map = mapOf("config" to mapOf("refreshThrottleMs" to "banana"))

        assertNull(map.getValueForPath<Int>("config.refreshThrottleMs"))
        assertNull(map.getValueForPath<Double>("config.refreshThrottleMs"))
        assertNull(map.getValueForPath<Long>("config.refreshThrottleMs"))
    }

    @Test
    fun aMissingPathIsNullRatherThanAnError() {
        val map = mapOf("features" to mapOf("enabled" to true))

        assertNull(map.getValueForPath<Boolean>("features.somethingElse"))
        assertNull(map.getValueForPath<Boolean>("nothing.here"))
        // A leaf where a branch was expected must not throw either.
        assertNull(map.getValueForPath<Boolean>("features.enabled.deeper"))
    }
}
