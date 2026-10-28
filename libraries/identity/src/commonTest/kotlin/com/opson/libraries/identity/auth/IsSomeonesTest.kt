package com.opson.libraries.identity.auth

import com.opson.libraries.identity.auth.AuthState.Unauthenticated.Reason
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsSomeonesTest {

    @Test
    fun aSignedInSession_isSomeones_evenBeforeOnboarding() {
        assertTrue(AuthState.Authenticated(userId = "u1", isAnonymous = false, email = "a@b.c").isSomeones(onboarded = false))
    }

    @Test
    fun anOnboardedDeviceWithNoSession_isStillSomeones() {
        assertTrue(AuthState.Unauthenticated(reason = Reason.None).isSomeones(onboarded = true))
    }

    @Test
    fun aFreshInstall_isNobodys() {
        assertFalse(AuthState.Unauthenticated(reason = Reason.None).isSomeones(onboarded = false))
    }

    @Test
    fun aSignOutOrAnExpiredSession_isNobodys_whateverTheFlagSays() {
        assertFalse(AuthState.Unauthenticated(reason = Reason.SignedOut).isSomeones(onboarded = true))
        assertFalse(AuthState.Unauthenticated(reason = Reason.SessionExpired).isSomeones(onboarded = true))
    }
}
