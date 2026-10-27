package com.opson.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Properties

/**
 * Which builds count as store builds, and so leave `:features:qa` out. The dependency graph itself
 * is checked by `:apps:compose:verifyQaExclusion`, which every store build runs.
 */
class ReleaseChannelTest {

    @Test
    fun storeIsAStoreBuild_devAndBetaAreNot() {
        assertTrue(isStoreChannel("store"))
        assertFalse(isStoreChannel("dev"))
        assertFalse(isStoreChannel("beta"))
    }

    @Test
    fun anUnknownChannel_countsAsStore() {
        assertTrue(isStoreChannel("Beta"))
        assertTrue(isStoreChannel("prod"))
        assertTrue(isStoreChannel(""))
    }

    @Test
    fun theEnvOverride_winsOverVersionsProperties() {
        val properties = Properties().apply { setProperty("releaseChannel", "dev") }

        assertEquals("store", resolveReleaseChannel("store", properties))
        assertEquals("dev", resolveReleaseChannel(null, properties))
        assertEquals("dev", resolveReleaseChannel(" ", properties))
    }

    @Test
    fun withNothingSet_theChannelIsDev() {
        assertEquals("dev", resolveReleaseChannel(null, Properties()))
    }
}
