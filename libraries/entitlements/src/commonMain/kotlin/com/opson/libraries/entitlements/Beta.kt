@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.entitlements

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * `release.betaNotice`, as a screen reads it: while it is on, Opson is in its beta. Onboarding's
 * Welcome says so (Pro isn't here yet, and everyone who joins now gets a lifetime discount on
 * it), and every account that signs in is recorded on the server as joining during the beta
 * (`POST /v1/me/beta`, a `beta` grant), so the discount can be honoured when Pro arrives. Turned
 * off when the beta ends. A fun interface so a view model can default it to [Off].
 */
@HiddenFromObjC
fun interface BetaRelease {
    fun isOn(): Boolean

    companion object {
        val Off: BetaRelease = BetaRelease { false }
    }
}
