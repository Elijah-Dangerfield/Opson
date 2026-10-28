package com.opson.libraries.core

import com.opson.buildinfo.OpsonBuildConfig

/**
 * Everything a build talks to: the API server and the Supabase project it signs in to. One
 * switch picks all of it, [BuildInfo.isDebug], so the session and the server always belong to
 * the same environment. When they didn't (0.1.0 signed in to dev and called prod), every request
 * came back 401.
 *
 * Keyed on the build type rather than the release channel, because a TestFlight build promoted
 * to the App Store keeps saying `beta` forever (AGENTS.md, Known landmines) and must still reach
 * prod. Debug's Supabase project comes from `local.properties`; release's from the committed
 * `supabase.properties`.
 */
data class BackendEnvironment(
    val apiBaseUrl: String,
    val supabaseProjectId: String,
    val supabaseUrl: String,
    val supabaseAnonKey: String,
) {
    companion object {
        /** The Fly app in `apps/server/fly.toml`. */
        const val DEV_API_BASE_URL: String = "https://opson-server-dev.fly.dev"

        /** The Fly app in `apps/server/fly.prod.toml`. */
        const val PROD_API_BASE_URL: String = "https://opson-server-prod.fly.dev"

        fun forBuild(isDebug: Boolean = BuildInfo.isDebug): BackendEnvironment = if (isDebug) {
            BackendEnvironment(
                apiBaseUrl = DEV_API_BASE_URL,
                supabaseProjectId = OpsonBuildConfig.SUPABASE_PROJECT_ID,
                supabaseUrl = OpsonBuildConfig.SUPABASE_URL,
                supabaseAnonKey = OpsonBuildConfig.SUPABASE_ANON_KEY,
            )
        } else {
            BackendEnvironment(
                apiBaseUrl = PROD_API_BASE_URL,
                supabaseProjectId = OpsonBuildConfig.SUPABASE_PROD_PROJECT_ID,
                supabaseUrl = OpsonBuildConfig.SUPABASE_PROD_URL,
                supabaseAnonKey = OpsonBuildConfig.SUPABASE_PROD_ANON_KEY,
            )
        }
    }
}
