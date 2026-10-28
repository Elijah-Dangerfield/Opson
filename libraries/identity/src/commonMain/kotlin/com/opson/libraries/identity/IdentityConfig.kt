package com.opson.libraries.identity

/**
 * Per-environment Supabase wiring. The binding in `:libraries:identity:impl`
 * follows `BuildInfo.isDebug`: the dev project in debug builds, prod in release.
 *
 * `supabasePublishableKey` is the project's **publishable (public)** API key
 * — designed to be exposed in client builds. It's gated by Supabase Row Level
 * Security policies, not by secrecy. Don't confuse with the secret key
 * (server-only).
 */
interface IdentityConfig {
    /** e.g. `https://yuqrfhdoejonclgbixlw.supabase.co`. */
    val supabaseUrl: String

    /** Public publishable key — Supabase → Settings → API Keys → Publishable key. */
    val supabasePublishableKey: String
}
