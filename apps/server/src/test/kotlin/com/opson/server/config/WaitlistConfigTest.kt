package com.opson.server.config

import kotlin.test.Test
import kotlin.test.assertEquals

class WaitlistConfigTest {

    private fun env(vararg pairs: Pair<String, String>): Env {
        val values = pairs.toMap()
        return Env(envFilePath = "does-not-exist", realEnv = { values[it] })
    }

    @Test
    fun originsAreExactAndBadEntriesAreDropped() {
        assertEquals(
            setOf("https://opson.app", "http://localhost:4321"),
            WaitlistConfig.parseOrigins(
                " https://Opson.app/ ,http://localhost:4321,,opson.app,https://*.evil.example,ftp://x.co",
            ),
        )
    }

    @Test
    fun devDefaultsToTheAstroServerAndPages() {
        assertEquals(setOf("http://localhost:4321", "https://elijah-dangerfield.github.io"), WaitlistConfig.DEV_ORIGINS)
        assertEquals(WaitlistConfig.DEV_ORIGINS, WaitlistConfig.fromEnv(env()).allowedOrigins)
        assertEquals(
            WaitlistConfig.DEV_ORIGINS,
            WaitlistConfig.fromEnv(env("FLY_APP_NAME" to "opson-server-dev")).allowedOrigins,
        )
    }

    @Test
    fun prodAllowsNothingUntilSet() {
        assertEquals(emptySet(), WaitlistConfig.fromEnv(env("FLY_APP_NAME" to "opson-server-prod")).allowedOrigins)
        assertEquals(
            setOf("https://elijah-dangerfield.github.io"),
            WaitlistConfig.fromEnv(
                env(
                    "FLY_APP_NAME" to "opson-server-prod",
                    "WAITLIST_ALLOWED_ORIGINS" to "https://elijah-dangerfield.github.io",
                ),
            ).allowedOrigins,
        )
    }
}
