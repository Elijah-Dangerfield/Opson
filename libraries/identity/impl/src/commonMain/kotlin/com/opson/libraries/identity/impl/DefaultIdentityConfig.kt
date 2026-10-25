package com.opson.libraries.identity.impl

import com.opson.libraries.core.SupabaseInfo
import com.opson.libraries.identity.IdentityConfig
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Supabase auth config read from build-time [SupabaseInfo] (set
 * `supabase.projectId` / `supabase.anonKey` in local.properties or the env
 * vars — see build-logic). The publishable key is a public client constant by
 * design; data is gated by Supabase RLS, not by secrecy.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DefaultIdentityConfig : IdentityConfig {
    override val supabaseUrl: String = SupabaseInfo.url
    override val supabasePublishableKey: String = SupabaseInfo.anonKey
}
