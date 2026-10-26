package com.opson

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which server each build type talks to. Not covered: that the binding wins
 * over `DefaultNetworkConfig` in the merged component, which only the app
 * graph itself can show.
 */
class OpsonNetworkConfigTest {
    @Test
    fun debugBuildsUseTheDevServer() {
        assertEquals("https://opson-server-dev.fly.dev", apiBaseUrl(isDebug = true))
    }

    @Test
    fun releaseBuildsUseTheProdServer() {
        assertEquals("https://opson-server-prod.fly.dev", apiBaseUrl(isDebug = false))
    }
}
