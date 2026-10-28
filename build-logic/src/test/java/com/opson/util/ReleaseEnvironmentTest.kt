package com.opson.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Properties

/**
 * What `verifyReleaseEnvironment` refuses. 0.1.0 and the first 0.2 TestFlight build
 * signed in to the dev Supabase project while calling the prod server, so every
 * request was a 401; these are the build-time checks that would have stopped it.
 */
class ReleaseEnvironmentTest {

    private val committed = properties(
        "supabase.prod.projectId" to PROD_REF,
        "supabase.prod.anonKey" to "sb_publishable_prodkey",
    )
    private val local = properties(
        "supabase.projectId" to DEV_REF,
        "supabase.anonKey" to "sb_publishable_devkey",
    )

    @Test
    fun theCommittedProdProject_passes() {
        assertEquals(emptyList<String>(), supabaseMetadata(local, committed, noEnv).releaseProblems())
    }

    @Test
    fun noSupabaseProperties_failsInsteadOfShippingThePlaceholder() {
        val metadata = supabaseMetadata(local, Properties(), noEnv)

        assertEquals(UNCONFIGURED_SUPABASE_PROJECT_ID, metadata.prodProjectId)
        assertEquals(2, metadata.releaseProblems().size)
    }

    @Test
    fun aMisspelledKey_fails() {
        val misspelled = properties("supabase.prod.projectid" to PROD_REF, "supabase.prod.anonKey" to "sb_publishable_x")

        assertEquals(1, supabaseMetadata(local, misspelled, noEnv).releaseProblems().size)
    }

    @Test
    fun aKeyThatIsNotPublishable_fails() {
        val wrongKey = properties("supabase.prod.projectId" to PROD_REF, "supabase.prod.anonKey" to "sb_secret_x")

        assertEquals(1, supabaseMetadata(local, wrongKey, noEnv).releaseProblems().size)
    }

    @Test
    fun theDevProjectCommittedAsProd_fails() {
        val devAsProd = properties("supabase.prod.projectId" to DEV_REF, "supabase.prod.anonKey" to "sb_publishable_x")

        val problems = supabaseMetadata(local, devAsProd, noEnv).releaseProblems()

        assertEquals(1, problems.size)
        assertTrue(problems.single().contains("dev project"))
    }

    @Test
    fun aStrayEnvVar_cannotRedirectProd() {
        val env = mapOf("SUPABASE_PROD_PROJECT_ID" to "otherprojectrefxxxxx", "SUPABASE_PROJECT_ID" to "envdevrefxxxxxxxxxxx")

        val metadata = supabaseMetadata(local, committed, env::get)

        assertEquals(PROD_REF, metadata.prodProjectId)
        // Dev still prefers local.properties over the environment.
        assertEquals(DEV_REF, metadata.projectId)
    }

    @Test
    fun ciWithNoLocalProperties_takesDevFromTheEnvironment() {
        val env = mapOf("SUPABASE_PROJECT_ID" to DEV_REF, "SUPABASE_ANON_KEY" to "sb_publishable_devkey")

        assertEquals(DEV_REF, supabaseMetadata(Properties(), committed, env::get).projectId)
    }

    @Test
    fun noSentryDsn_fails() {
        assertEquals(1, telemetry(sentryDsn = "").releaseProblems().size)
    }

    @Test
    fun grafanaOff_isAWarningNotAFailure() {
        val off = telemetry()

        assertEquals(emptyList<String>(), off.releaseProblems())
        assertEquals(1, off.releaseWarnings().size)
    }

    @Test
    fun grafanaHalfConfigured_fails() {
        val half = telemetry(baseUrl = "https://otlp.example", instanceId = "123")

        assertEquals(1, half.releaseProblems().size)
    }

    @Test
    fun grafanaFullyConfigured_isQuiet() {
        val on = telemetry(baseUrl = "https://otlp.example", instanceId = "123", token = "glc_x")

        assertEquals(emptyList<String>(), on.releaseProblems())
        assertEquals(emptyList<String>(), on.releaseWarnings())
    }

    private fun telemetry(sentryDsn: String = "https://key@o1.ingest.sentry.io/1", baseUrl: String = "", instanceId: String = "", token: String = "") =
        TelemetryMetadata(
            grafanaOtlpBaseUrl = baseUrl,
            grafanaOtlpInstanceId = instanceId,
            grafanaLogsWriteToken = token,
            sentryDsn = sentryDsn,
        )

    private fun properties(vararg entries: Pair<String, String>) = Properties().apply {
        entries.forEach { (key, value) -> setProperty(key, value) }
    }

    private companion object {
        const val PROD_REF = "qmftnivpklhhozthpxej"
        const val DEV_REF = "abcdefghijklmnopqrst"
        val noEnv: (String) -> String? = { null }
    }
}
