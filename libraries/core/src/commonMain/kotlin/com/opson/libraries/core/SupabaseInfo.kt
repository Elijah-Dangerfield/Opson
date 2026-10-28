package com.opson.libraries.core

/**
 * The Supabase project this build signs in to: [BackendEnvironment]'s half of it, picked by the
 * same switch as the API server.
 */
object SupabaseInfo {
    val projectId: String get() = BackendEnvironment.forBuild().supabaseProjectId

    val url: String get() = BackendEnvironment.forBuild().supabaseUrl

    val anonKey: String get() = BackendEnvironment.forBuild().supabaseAnonKey
}
