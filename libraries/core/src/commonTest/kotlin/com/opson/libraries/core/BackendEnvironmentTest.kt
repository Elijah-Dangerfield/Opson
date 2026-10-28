package com.opson.libraries.core

import com.opson.buildinfo.OpsonBuildConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/** Each build type talks to one environment, server and Supabase project together. */
class BackendEnvironmentTest {

    @Test
    fun debugBuildsUseTheDevServerAndTheDevProject() {
        val dev = BackendEnvironment.forBuild(isDebug = true)

        assertEquals("https://opson-server-dev.fly.dev", dev.apiBaseUrl)
        assertEquals(OpsonBuildConfig.SUPABASE_PROJECT_ID, dev.supabaseProjectId)
        assertEquals(OpsonBuildConfig.SUPABASE_URL, dev.supabaseUrl)
        assertEquals(OpsonBuildConfig.SUPABASE_ANON_KEY, dev.supabaseAnonKey)
    }

    @Test
    fun releaseBuildsUseTheProdServerAndTheProdProject() {
        val prod = BackendEnvironment.forBuild(isDebug = false)

        assertEquals("https://opson-server-prod.fly.dev", prod.apiBaseUrl)
        assertEquals(OpsonBuildConfig.SUPABASE_PROD_PROJECT_ID, prod.supabaseProjectId)
        assertEquals(OpsonBuildConfig.SUPABASE_PROD_URL, prod.supabaseUrl)
        assertEquals(OpsonBuildConfig.SUPABASE_PROD_ANON_KEY, prod.supabaseAnonKey)
    }
}
