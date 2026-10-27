package com.opson.features.onboarding.impl

/**
 * Whether email sign-in is offered besides Apple (iOS) and Google.
 *
 * Off: email needs a verification code, and Supabase's built-in mailer only
 * delivers to the project's own team, so a new address would wait for a code
 * that never comes. Turn it on once custom SMTP is set up (docs/HANDOFF.md).
 *
 * Google has no switch. Accounts are required, and Android has no Apple, so
 * without Google nobody on Android could get in.
 */
internal object SignInMethods {
    const val EMAIL = false
}
