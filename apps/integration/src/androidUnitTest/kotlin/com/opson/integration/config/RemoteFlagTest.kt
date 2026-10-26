package com.opson.integration.config

import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.TestClock
import com.opson.integration.helpers.awaitState
import com.opson.integration.helpers.awaitUntil
import com.opson.integration.helpers.randomUserId
import com.opson.integration.helpers.removeFlagRule
import com.opson.integration.helpers.setFlagFor
import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.diary.FastingAccess
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.todayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * **Remote config.** A flag set with the admin API for one user reaches that
 * user's phone through `/v1/app-config` into [com.opson.libraries.config.AppConfigFlow]
 * on the next foreground past the app's refresh throttle (and the server's
 * short config cache), and a release flag read
 * from it turns the feature on and, when the rule goes, off again. The feature
 * here is fasting, whose timer starts nothing while `release.fasting` is off.
 */
class RemoteFlagTest : IntegrationTest() {

    @Test
    fun aReleaseFlagSetForTheUser_flowsToTheClient_andSwitchesTheFeature() = integration {
        val user = randomUserId()
        val zone = TimeZone.currentSystemDefault()
        val yesterday = Clock.System.todayIn(zone).minus(1, DateTimeUnit.DAY)
        val clock = TestClock().apply { shiftTo(LocalDateTime(yesterday, LocalTime(21, 0)).toInstant(zone)) }
        val phone = client(user, clock = clock)
        val released: (AppConfigMap) -> Boolean = { it.value(phone.fastingRelease) }

        phone.lifecycle.foreground() // cold boot: always fetches
        phone.syncNow().getOrThrow()
        phone.settings.isKnown.awaitState { it }
        phone.settings.update { it.copy(fasting = true) }
        phone.fasting.startDue()
        assertNull(phone.fasting.observeLatest().first(), "flag off: no fast starts")
        assertEquals(false, released(phone.appConfigFlow.first()))

        val rule = setFlagFor(user, "release.fasting")
        server.clock.advance(31.seconds) // past the server's 30-second config cache
        clock.advance(6.minutes) // past the app's five-minute refresh throttle
        phone.lifecycle.foreground()
        withTimeout(20_000) { phone.appConfigFlow.first(released) }

        phone.fasting.startDue()
        assertNotNull(phone.fasting.observeLatest().first(), "flag on: the evening's fast starts")

        removeFlagRule(rule)
        server.clock.advance(31.seconds)
        clock.advance(6.minutes)
        phone.lifecycle.foreground()
        withTimeout(20_000) { phone.appConfigFlow.first { !released(it) } }
        awaitUntil { !phone.fastingRelease.isOn() }
        assertEquals(
            FastingAccess.Hidden,
            FastingAccess.of(phone.settings.settings.value, phone.fastingRelease.isOn()),
            "flag off again: nothing about fasting renders",
        )
    }
}
