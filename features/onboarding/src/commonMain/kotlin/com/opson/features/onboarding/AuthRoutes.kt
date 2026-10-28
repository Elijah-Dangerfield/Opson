package com.opson.features.onboarding

import com.opson.libraries.navigation.AnimationType
import com.opson.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * Email-password sign-in, with Google and Apple beside it. Reachable from
 * the sign-in step's "Continue with email" while email is on. A returning
 * account goes to Today; a new one goes to onboarding's questions.
 */
@Serializable
class SignInRoute : Route(
    enter = AnimationType.SlideUp,
    exit = AnimationType.SlideDown,
    popExit = AnimationType.SlideDown,
)

/**
 * Sign-up entry. Reachable from the "Don't have an account?" link on
 * [SignInRoute]. On submission the user is sent a verification email and
 * we navigate to [VerifyEmailRoute].
 */
@Serializable
class SignUpRoute : Route(
    enter = AnimationType.SlideInFromRight,
    exit = AnimationType.SlideOutToLeft,
    popExit = AnimationType.SlideOutToRight,
)

/**
 * "Check your email" screen. Reachable after [SignUpRoute] submission and
 * via the `opson://auth/confirmed` deep-link bounced back from the
 * verification email.
 *
 * Email is nullable so the deep-link mapping can land here without the
 * URL having to carry the address (the cold-launch case where the user
 * killed the app before tapping the link in their inbox). The VM resolves
 * the missing email from the active session on init; the screen falls back
 * to a generic body string while the resolve is in flight or if no email
 * is on the session at all.
 *
 * [guestLink] marks the verification as started by an anonymous guest
 * linking an email identity (the claim-account flow), as opposed to a
 * brand-new sign-up or a returning user's unconfirmed-email sign-in. On
 * confirmation the guest keeps their existing progress and account, so
 * they land back on Today instead of re-entering onboarding. The
 * cold-launch deep-link (`opson://auth/confirmed`) can't carry it,
 * so a guest who kills the app between requesting and tapping the link
 * falls back to the plain Today route, never the wrong one.
 */
@Serializable
data class VerifyEmailRoute(
    val email: String? = null,
    val guestLink: Boolean = false,
) : Route(
    enter = AnimationType.SlideInFromRight,
    exit = AnimationType.SlideOutToLeft,
    popExit = AnimationType.SlideOutToRight,
)

/**
 * "Forgot password" email-entry screen. Reachable from [SignInRoute]'s
 * inline link. Submitting kicks off the auth provider's password-reset
 * email; the recovery deep-link target (where the user actually picks a
 * new password) lives in a follow-up once the redirect URL is wired.
 */
@Serializable
class ForgotPasswordRoute : Route(
    enter = AnimationType.SlideInFromRight,
    exit = AnimationType.SlideOutToLeft,
    popExit = AnimationType.SlideOutToRight,
)
