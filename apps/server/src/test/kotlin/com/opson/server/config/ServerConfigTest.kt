package com.opson.server.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerConfigTest {

    private fun env(vararg pairs: Pair<String, String>): Env {
        val values = pairs.toMap()
        return Env(envFilePath = "does-not-exist", realEnv = { values[it] })
    }

    @Test
    fun withNothingSet_everyFeatureReadsOff() {
        assertEquals(
            "database=off auth=off accountDeletion=off appleRevoke=off sentry=off otlp=off admin=off " +
                "configWebhook=off ai=off nutritionix=off offSearch=off deviceCheck=off playIntegrity=off",
            ServerConfig.fromEnv(env()).featureSummary(),
        )
    }

    @Test
    fun eachVariableTurnsOnItsFeature_andNoValueIsPrinted() {
        val summary = ServerConfig.fromEnv(
            env(
                "DATABASE_URL" to "postgresql://user:hunter2@localhost:5432/db",
                "SUPABASE_URL" to "https://x.supabase.co",
                "SUPABASE_SERVICE_ROLE_KEY" to "service-role-secret",
                "APPLE_TEAM_ID" to "TEAM",
                "APPLE_KEY_ID" to "KEY",
                "APPLE_PRIVATE_KEY" to "apple-secret",
                "SENTRY_DSN" to "https://dsn-secret@sentry.example/1",
                "OTEL_EXPORTER_OTLP_ENDPOINT" to "https://otlp.example",
                "ADMIN_API_TOKEN" to "admin-secret",
                "CONFIG_CHANGE_WEBHOOK_URL" to "https://hooks.example/secret",
                "ANTHROPIC_API_KEY" to "anthropic-secret",
                "AI_CALLS_ENABLED" to "true",
                "NUTRITIONIX_APP_ID" to "id",
                "NUTRITIONIX_APP_KEY" to "nutritionix-secret",
                "FOOD_SEARCH_OFF_ENABLED" to "true",
                "APPLE_DEVICECHECK_KEY_ID" to "DCKEY",
                "APPLE_DEVICECHECK_PRIVATE_KEY" to "devicecheck-secret",
                "PLAY_INTEGRITY_SERVICE_ACCOUNT_JSON" to "{\"private_key\":\"play-secret\"}",
            ),
        ).featureSummary()

        assertEquals(
            "database=on auth=on accountDeletion=on appleRevoke=on sentry=on otlp=on admin=on " +
                "configWebhook=on ai=on nutritionix=on offSearch=on deviceCheck=on playIntegrity=on",
            summary,
        )
    }

    @Test
    fun aKeyAloneCallsNoModel() {
        val sources = FoodSourcesConfig.fromEnv(env("ANTHROPIC_API_KEY" to "anthropic-secret"))

        assertNull(sources.anthropic)
        assertTrue(sources.anthropicKeyIgnored, "the boot log says the key goes unused")
    }

    @Test
    fun theSwitchAloneCallsNoModel() {
        val sources = FoodSourcesConfig.fromEnv(env("AI_CALLS_ENABLED" to "true"))

        assertNull(sources.anthropic)
        assertFalse(sources.anthropicKeyIgnored)
    }

    @Test
    fun onlyTheWordTrueTurnsTheSwitchOn() {
        listOf("1", "yes", "on", "false", " ").forEach { value ->
            val sources = FoodSourcesConfig.fromEnv(env("ANTHROPIC_API_KEY" to "anthropic-secret", "AI_CALLS_ENABLED" to value))
            assertNull(sources.anthropic, "AI_CALLS_ENABLED=$value")
        }
        val on = FoodSourcesConfig.fromEnv(env("ANTHROPIC_API_KEY" to "anthropic-secret", "AI_CALLS_ENABLED" to " TRUE "))
        assertNotNull(on.anthropic)
        assertFalse(on.anthropicKeyIgnored)
    }
}
