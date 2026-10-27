package com.opson.server.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppleSignInConfigTest {

    private fun env(vararg pairs: Pair<String, String>): Env {
        val values = pairs.toMap()
        return Env(envFilePath = "does-not-exist", realEnv = { values[it] })
    }

    private val all = arrayOf(
        "APPLE_TEAM_ID" to "TEAM",
        "APPLE_KEY_ID" to "KEY",
        "APPLE_PRIVATE_KEY" to "-----BEGIN PRIVATE KEY-----\\nabc\\n-----END PRIVATE KEY-----",
    )

    @Test
    fun allThreeSetTurnsItOn_withTheFlattenedNewlinesRestored() {
        val config = AppleSignInConfig.fromEnv(env(*all))

        assertEquals("TEAM", config?.teamId)
        assertEquals("KEY", config?.keyId)
        assertEquals("-----BEGIN PRIVATE KEY-----\nabc\n-----END PRIVATE KEY-----", config?.privateKeyPem)
        assertEquals(AppleSignInConfig.DEFAULT_CLIENT_ID, config?.clientId)
    }

    @Test
    fun anyOneMissingLeavesItOff() {
        for (missing in all.map { it.first }) {
            assertNull(AppleSignInConfig.fromEnv(env(*all.filter { it.first != missing }.toTypedArray())), missing)
        }
    }

    @Test
    fun itRidesOnTheSupabaseConfig() {
        val supabase = SupabaseConfig.fromEnv(env("SUPABASE_URL" to "https://x.supabase.co", *all, "APPLE_CLIENT_ID" to "com.example"))

        assertEquals("com.example", supabase?.appleSignIn?.clientId)
    }
}
