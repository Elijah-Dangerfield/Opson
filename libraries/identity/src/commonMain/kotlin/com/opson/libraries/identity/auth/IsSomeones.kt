package com.opson.libraries.identity.auth

/**
 * Whether the app is someone's right now, for anything that opens it on their data from outside
 * (a widget, a tapped reminder): signed in, or unauthenticated with no reason for someone past
 * onboarding. That is a session that can't refresh offline (logging in a basement on an hour-old
 * token still counts), and also an onboarded device whose session is gone, which still opens on
 * Today until `SessionHealer` revives the session or sends it to sign in. Not after a sign-out or
 * an expired session, and not on a fresh install: a user change clears the onboarding flag, so a
 * relaunch afterwards with no session is nobody's either.
 */
fun AuthState.isSomeones(onboarded: Boolean): Boolean = when (this) {
    is AuthState.Authenticated -> true
    is AuthState.Unauthenticated -> reason == AuthState.Unauthenticated.Reason.None && onboarded
}
