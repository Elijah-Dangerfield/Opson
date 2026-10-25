package com.opson.libraries.core

import com.opson.buildinfo.OpsonBuildConfig

object SupabaseInfo {
    val projectId: String
        get() = OpsonBuildConfig.SUPABASE_PROJECT_ID

    val url: String
        get() = OpsonBuildConfig.SUPABASE_URL

    val anonKey: String
        get() = OpsonBuildConfig.SUPABASE_ANON_KEY
}
