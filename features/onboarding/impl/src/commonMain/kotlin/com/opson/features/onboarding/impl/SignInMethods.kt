package com.opson.features.onboarding.impl

/**
 * The sign-in methods this release offers besides Sign in with Apple.
 *
 * Both are off for 0.1. Google isn't configured in Supabase, so its button
 * could only fail. Email needs a verification code, and Supabase's built-in
 * mailer only delivers to the project's own team, so a new address would wait
 * for a code that never comes. Turn each on once its provider is set up
 * (docs/HANDOFF.md).
 */
internal object SignInMethods {
    const val GOOGLE = false
    const val EMAIL = false
}
