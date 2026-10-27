package com.opson.integration.config

import com.opson.features.logger.impl.BarcodeScanEnabled
import com.opson.features.logger.impl.PhotoEstimateEnabled
import com.opson.integration.helpers.Harness
import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.TestClient
import com.opson.integration.helpers.putFlagRule
import com.opson.integration.helpers.randomUserId
import com.opson.integration.helpers.removeFlagRule
import com.opson.integration.helpers.setFlagBase
import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.networking.ClientHeaders
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * **Targeting, end to end.** A rule written through the admin API the console uses decides a
 * release flag on each phone by who it is: the user, the platform, the build and the staged
 * rollout bucket. Each case reads the flag through its real `ConfiguredValue` class from the
 * config the phone fetched over `/v1/app-config`, the way the screen it gates reads it.
 *
 * Every fetch waits for a fresh marker value in the base config, so "off" means the server said
 * off, not that the fetch had not landed yet. The Postgres container is shared by the whole JVM,
 * so a rule that could match another test's phone is deleted when its test ends.
 */
class FlagTargetingTest : IntegrationTest() {

    @Test
    fun aRuleForOneUser_turnsTheFlagOnForThatUserOnly() = targeting {
        val alice = randomUserId()
        val bob = randomUserId()
        rule(BARCODE, JsonPrimitive(true), buildJsonObject { put("userAllow", buildJsonArray { add(JsonPrimitive(alice)) }) })

        assertEquals(true, barcodeOn(client(alice)))
        assertEquals(false, barcodeOn(client(bob)), "the rule names Alice, so Bob keeps the base value")
    }

    @Test
    fun aPlatformRule_reachesThatPlatformOnly() = targeting {
        rule(BARCODE, JsonPrimitive(true), buildJsonObject { put("platforms", buildJsonArray { add(JsonPrimitive("ios")) }) })

        assertEquals(true, barcodeOn(client(device = { it.copy(platform = "ios") })))
        assertEquals(false, barcodeOn(client(device = { it.copy(platform = "android") })))
    }

    @Test
    fun aVersionRule_reachesTheBuildsInItsRange() = targeting {
        // "0.2 and later", the way a release flag is turned on for the release that carries it.
        rule(BARCODE, JsonPrimitive(true), buildJsonObject { put("minAppVersion", "0.2.0") })
        assertEquals(false, barcodeOn(client(device = { it.copy(appVersion = "0.1.0") })))
        assertEquals(true, barcodeOn(client(device = { it.copy(appVersion = "0.2.0") })))

        // And by build number, the axis the version floor uses.
        rule(PHOTO, JsonPrimitive(true), buildJsonObject { put("minVersionCode", 50) })
        assertEquals(false, photoOn(client(device = { it.copy(buildNumber = "49") })))
        assertEquals(true, photoOn(client(device = { it.copy(buildNumber = "50") })))
    }

    @Test
    fun aStagedRollout_splitsPeopleStably_andRampingOnlyAddsPeople() = targeting {
        val half = rule(BARCODE, JsonPrimitive(true), buildJsonObject { put("rolloutPercent", 50) })

        // Fixed ids, so the split is the same on every run: the bucket is a hash of user and flag.
        val phones = ROLLOUT_USERS.map { user -> user to client(user) }
        val atHalf = phones.associate { (user, phone) -> user to barcodeOn(phone) }
        val inAtHalf = atHalf.filterValues { it }.keys
        assertTrue(
            inAtHalf.isNotEmpty() && inAtHalf.size < ROLLOUT_USERS.size,
            "50% of ${ROLLOUT_USERS.size} fixed users should split them, got $atHalf",
        )

        // The same people get the same answer when they ask again.
        phones.forEach { (user, phone) -> assertEquals(atHalf[user], barcodeOn(phone, refetch = true), user) }

        putFlagRule(BARCODE, JsonPrimitive(true), buildJsonObject { put("rolloutPercent", 100) }, ruleId = half)
        val atFull = phones.associate { (user, phone) -> user to barcodeOn(phone, refetch = true) }
        assertTrue(atFull.values.all { it }, "100% reaches everyone, those already in included: $atFull")
    }

    /** A harness whose rules are deleted afterwards, with both flags' base values off. */
    private fun targeting(block: suspend Targeting.() -> Unit) = integration {
        val targeting = Targeting(this)
        setFlagBase(BARCODE, JsonPrimitive(false))
        setFlagBase(PHOTO, JsonPrimitive(false))
        try {
            targeting.block()
        } finally {
            targeting.rules.forEach { removeFlagRule(it) }
        }
    }

    private class Targeting(private val harness: Harness) {
        val rules = mutableListOf<String>()
        private var markerValue = 0

        fun client(userId: String? = randomUserId(), device: (ClientHeaders) -> ClientHeaders = { it }) =
            harness.client(userId, device = device)

        suspend fun rule(path: String, value: JsonElement, conditions: JsonObject): String =
            harness.putFlagRule(path, value, conditions).also(rules::add)

        suspend fun putFlagRule(path: String, value: JsonElement, conditions: JsonObject, ruleId: String) =
            harness.putFlagRule(path, value, conditions, ruleId = ruleId)

        suspend fun barcodeOn(phone: TestClient, refetch: Boolean = false): Boolean =
            fetched(phone, refetch).let { it.value(BarcodeScanEnabled(it)) }

        suspend fun photoOn(phone: TestClient): Boolean =
            fetched(phone).let { it.value(PhotoEstimateEnabled(it)) }

        /**
         * The config [phone] fetched: its cold boot the first time, or a foreground past the app's
         * throttle when [refetch] is set. Waits for a fresh [MARKER] value set just before, so the
         * answer is the server's current one and not the shipped fallback or an older fetch.
         */
        private suspend fun fetched(phone: TestClient, refetch: Boolean = false): AppConfigMap {
            val marker = ++markerValue
            harness.setFlagBase(MARKER, JsonPrimitive(marker))
            harness.server.clock.advance(31.seconds) // past the server's 30-second config cache
            if (refetch) {
                phone.clock.advance(6.minutes) // past the app's five-minute refresh throttle
                phone.lifecycle.background()
            }
            phone.lifecycle.foreground()
            return withTimeout(20_000) {
                phone.appConfigFlow.first { (it.at(MARKER) as? Number)?.toInt() == marker }
            }
        }

        private fun AppConfigMap.at(path: String): Any? =
            path.split('.').fold(map as Any?) { node, key -> (node as? Map<*, *>)?.get(key) }
    }

    private companion object {
        const val BARCODE = "release.barcode"
        const val PHOTO = "release.photo"
        const val MARKER = "test.marker"

        val ROLLOUT_USERS = listOf(
            "0b9a3c2e-1f4d-4a6b-9c8e-0d1f2a3b4c01",
            "1c8b4d3f-2e5a-4b7c-8d9f-1e2a3b4c5d02",
            "2d7c5e4a-3f6b-4c8d-9e0a-2f3b4c5d6e03",
            "3e6d6f5b-4a7c-4d9e-8f1b-3a4c5d6e7f04",
            "4f5e7a6c-5b8d-4e0f-9a2c-4b5d6e7f8a05",
            "5a4f8b7d-6c9e-4f1a-8b3d-5c6e7f8a9b06",
            "6b3a9c8e-7d0f-4a2b-9c4e-6d7f8a9b0c07",
            "7c2b0d9f-8e1a-4b3c-8d5f-7e8a9b0c1d08",
        )
    }
}
