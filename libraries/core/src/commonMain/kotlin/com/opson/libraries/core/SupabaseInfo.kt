package com.opson.libraries.core

import com.opson.buildinfo.OpsonBuildConfig

/**
 * The Supabase project this build signs in to. Debug builds use the dev
 * project from `local.properties`; release builds use prod from the committed
 * `supabase.properties`. Keyed on [BuildInfo.isDebug], the same switch that
 * picks the API server, so the session and the server always belong to the
 * same environment. When they didn't (0.1.0 signed in to dev and called prod),
 * every request came back 401.
 */
object SupabaseInfo {
    val projectId: String
        get() = forBuild(OpsonBuildConfig.SUPABASE_PROJECT_ID, OpsonBuildConfig.SUPABASE_PROD_PROJECT_ID)

    val url: String
        get() = forBuild(OpsonBuildConfig.SUPABASE_URL, OpsonBuildConfig.SUPABASE_PROD_URL)

    val anonKey: String
        get() = forBuild(OpsonBuildConfig.SUPABASE_ANON_KEY, OpsonBuildConfig.SUPABASE_PROD_ANON_KEY)

    private fun forBuild(dev: String, prod: String): String = if (BuildInfo.isDebug) dev else prod
}
