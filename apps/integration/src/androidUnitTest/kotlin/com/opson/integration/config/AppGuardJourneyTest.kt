package com.opson.integration.config

import com.opson.features.upgrade.AppGuardState
import com.opson.integration.helpers.IntegrationAuth
import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.TestClient
import com.opson.integration.helpers.putFlagRule
import com.opson.integration.helpers.randomUserId
import com.opson.integration.helpers.removeFlagRule
import com.opson.integration.helpers.setFlagBase
import com.opson.libraries.core.BuildInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * **The app guard over the wire.** The kill switches set through the admin API reach the guard
 * the app shell draws, through `/v1/app-config`, the config repository and its stream, on the
 * same edges a real phone has: launch, a return to the app, and Try again.
 *
 * Each switch is set by a rule naming one user, which is how an operator stops one person's phone
 * to look at it, and it proves the stop lands on that phone and nobody else's.
 */
class AppGuardJourneyTest : IntegrationTest() {

    @Test
    fun maintenance_stopsTheTargetedPhone_failsOpenOffline_andLiftsOnTryAgain() = integration {
        val held = randomUserId()
        putFlagRule("upgrade.maintenanceMessage", JsonPrimitive("Moving to a bigger database. Back by 3pm."), onlyFor(held))
        val rule = putFlagRule("upgrade.maintenanceMode", JsonPrimitive("blocking"), onlyFor(held))

        val heldPhone = client(held)
        val otherPhone = client()
        heldPhone.lifecycle.foreground()
        otherPhone.lifecycle.foreground()

        heldPhone.awaitGuard(AppGuardState.MaintenanceBlocking("Moving to a bigger database. Back by 3pm."))
        otherPhone.guard.retry() // a fetch that has landed, so Normal is the server's answer
        assertEquals(AppGuardState.Normal, otherPhone.guard.state.value, "the rule names one user")

        // Offline the phone cannot tell maintenance has ended, so it steps aside, and logging works.
        heldPhone.offline.value = true
        heldPhone.awaitGuard(AppGuardState.MaintenanceBanner("Moving to a bigger database. Back by 3pm.", loggingContinues = true))
        heldPhone.log(name = "Soup", kcal = 200.0)
        assertEquals(1, heldPhone.pendingChanges(), "the log waits in the outbox")
        heldPhone.offline.value = false
        heldPhone.awaitGuard(AppGuardState.MaintenanceBlocking("Moving to a bigger database. Back by 3pm."))

        // The operator lifts it; Try again sees it at once, whatever the app's refresh throttle says.
        removeFlagRule(rule)
        server.clock.advance(31.seconds) // past the server's 30-second config cache
        heldPhone.guard.retry()
        heldPhone.awaitGuard(AppGuardState.Normal)
    }

    @Test
    fun theVersionFloor_stopsOlderBuilds_andAStoppedAppRechecksOnEveryReturn() = integration {
        val held = randomUserId()
        val rule = putFlagRule(
            "upgrade.minSupportedVersionCode",
            JsonPrimitive(BuildInfo.versionCode + 1),
            onlyFor(held),
        )

        val phone = client(held)
        phone.lifecycle.foreground()
        phone.awaitGuard(AppGuardState.UpgradeRequired)

        removeFlagRule(rule)
        server.clock.advance(31.seconds)
        // No change to the phone's clock: an unstopped app would wait out its five-minute
        // throttle here, a stopped one asks again as soon as it is opened.
        phone.lifecycle.background()
        phone.lifecycle.foreground()
        phone.awaitGuard(AppGuardState.Normal)
    }

    @Test
    fun theUpdateOffer_reachesOlderBuilds_andCanBePutAway() = integration {
        val user = randomUserId()
        // A rule needs its flag to exist. 0.1's manifest has no latestVersionCode, so until 0.2's
        // manifest is uploaded the operator adds the base first, at the default, as here.
        setFlagBase("upgrade.latestVersionCode", JsonPrimitive(0))
        putFlagRule("upgrade.latestVersionCode", JsonPrimitive(BuildInfo.versionCode + 1), onlyFor(user))
        val phone = client(user)
        phone.lifecycle.foreground()
        phone.awaitGuard(AppGuardState.UpdateAvailable)

        phone.guard.dismissUpdate()
        phone.awaitGuard(AppGuardState.Normal)
    }

    @Test
    fun aPhoneThatCannotReachUs_isNeverStopped() = integration {
        val held = randomUserId()
        putFlagRule("upgrade.maintenanceMode", JsonPrimitive("blocking"), onlyFor(held))
        val proxy = faultProxy().apply { goDown() }

        val phone = client(held, through = proxy)
        phone.lifecycle.foreground()
        phone.guard.retry() // waits out the failed fetch
        assertEquals(AppGuardState.Normal, phone.guard.state.value, "no config ever loaded, so the guard fails open")
    }

    /**
     * The 0.2 TestFlight build signed in to the dev Supabase project and called the prod server,
     * so every token it sent was one this server cannot verify. The config fetch is meant to work
     * with no session at all, because the version floor and the kill switch are how such a build
     * is stopped. A token it cannot verify has to count as no token there, not as a 401.
     */
    @Test
    fun aPhoneWithAForeignToken_stillGetsTheVersionFloor() = integration {
        // An operator can't name a user the server can't identify, so the rule names the build.
        val build = "0.0.${Random.nextInt(1_000_000)}"
        val rule = putFlagRule(
            "upgrade.minSupportedVersionCode",
            JsonPrimitive(BuildInfo.versionCode + 1),
            buildJsonObject {
                put("minAppVersion", JsonPrimitive(build))
                put("maxAppVersion", JsonPrimitive(build))
            },
        )
        try {
            val user = randomUserId()
            val phone = client(user, device = { it.copy(appVersion = build) }, setUpSession = { it.signWith(IntegrationAuth::mintForeignJwt) })
            phone.lifecycle.foreground()
            phone.awaitGuard(AppGuardState.UpgradeRequired)
        } finally {
            removeFlagRule(rule)
        }
    }

    private fun onlyFor(userId: String) = buildJsonObject {
        put("userAllow", buildJsonArray { add(JsonPrimitive(userId)) })
    }

    private suspend fun TestClient.awaitGuard(expected: AppGuardState) {
        withTimeout(20_000) { guard.state.first { it == expected } }
    }
}
